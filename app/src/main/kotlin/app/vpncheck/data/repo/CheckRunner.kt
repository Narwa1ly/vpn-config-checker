package app.vpncheck.data.repo

import android.util.Log
import app.vpncheck.core.check.CheckOutcome
import app.vpncheck.core.check.ConnectivityChecker
import app.vpncheck.core.engine.CoreRunner
import app.vpncheck.core.network.NetworkType
import app.vpncheck.core.network.NetworkTypeDetector
import app.vpncheck.core.parser.LinkParser
import app.vpncheck.core.parser.ProxyConfig
import app.vpncheck.core.provider.ProviderJsonParser
import app.vpncheck.core.provider.ProviderResolver
import app.vpncheck.data.db.AppDatabase
import app.vpncheck.data.db.CheckResultEntity
import app.vpncheck.data.db.ConfigEntity
import app.vpncheck.data.db.ProviderEntity
import app.vpncheck.data.db.SourceStatusEntity
import app.vpncheck.data.settings.SettingsStore
import app.vpncheck.data.subscription.SourceMembership
import app.vpncheck.data.subscription.FetchResult
import app.vpncheck.data.subscription.ListKind
import app.vpncheck.data.subscription.RetentionPolicy
import app.vpncheck.data.subscription.SubscriptionFetcher
import app.vpncheck.data.subscription.SubscriptionSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeUnit

/** Orchestrates: fetch subscriptions -> upsert configs -> run cores -> hit 2ip.io -> resolve provider -> persist. */
class CheckRunner(
    private val db: AppDatabase,
    private val settings: SettingsStore,
    private val fetcher: SubscriptionFetcher,
    private val coreRunner: CoreRunner,
    private val detector: NetworkTypeDetector,
    private val providerResolver: ProviderResolver,
) {
    private val _state = MutableStateFlow<RunState>(RunState.Idle)
    val state: StateFlow<RunState> = _state.asStateFlow()

    private val _lastFetchNote = MutableStateFlow<String?>(null)
    val lastFetchNote: StateFlow<String?> = _lastFetchNote.asStateFlow()

    private var job: Job? = null
    private val runMutex = Mutex()

    fun cancel() {
        job?.cancel(CancellationException("Остановлено пользователем"))
    }

    /** Downloads enabled subscriptions and stores new configs. Returns number of links seen. */
    suspend fun refreshConfigs(standalone: Boolean = true): Result<Int> {
        if (standalone && !runMutex.tryLock()) return Result.failure(IllegalStateException("Идёт проверка"))
        val previous = _state.value
        try {
            val result = doRefresh()
            result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            return result
        } finally {
            if (standalone) {
                // A stale Failed banner from an earlier run is meaningless once configs were re-downloaded.
                _state.value = if (previous.isActive || previous is RunState.Failed) RunState.Idle else previous
                runMutex.unlock()
            }
        }
    }

    private suspend fun doRefresh(): Result<Int> = runCatching {
        val cfg = settings.current()
        val enabled = SubscriptionSource.ALL.filter { it.id in cfg.enabledSources }
        if (enabled.isEmpty()) throw IllegalStateException("Все источники выключены в настройках")
        val disabledIds = SubscriptionSource.ALL.map { it.id }.toSet() - cfg.enabledSources
        _state.value = RunState.Fetching(0, enabled.size)
        val now = System.currentTimeMillis()
        val progress = Mutex()
        var done = 0
        // All sources are downloaded concurrently; each source races its mirrors internally.
        val results: List<FetchResult> = coroutineScope {
            enabled.map { src ->
                async {
                    val r = fetcher.fetch(src)
                    progress.withLock { done++; _state.value = RunState.Fetching(done, enabled.size) }
                    r
                }
            }.awaitAll()
        }
        val errors = mutableListOf<String>()
        val failedIds = HashSet<String>()
        // config id -> parsed config + ids of subscriptions that list it in this refresh
        val seen = LinkedHashMap<String, Pair<ProxyConfig, MutableSet<String>>>()
        for (res in results) {
            val src = res.source
            val prev = db.sourceStatusDao().byId(src.id)
            if (!res.ok) {
                errors += "${src.title}: ${res.error}"
                failedIds += src.id
                db.sourceStatusDao().upsert(
                    SourceStatusEntity(
                        sourceId = src.id, lastAttemptAt = now, lastSuccessAt = prev?.lastSuccessAt,
                        lastCount = prev?.lastCount ?: 0, lastMirror = prev?.lastMirror,
                        lastError = (listOf(res.error ?: "ошибка") + res.mirrorErrors).joinToString(" · "),
                    )
                )
                continue
            }
            var parsedCount = 0
            for (link in res.links) {
                val parsed = LinkParser.parse(link).getOrNull() ?: continue
                parsedCount++
                seen.getOrPut(parsed.id) { parsed to LinkedHashSet() }.second += src.id
            }
            db.sourceStatusDao().upsert(
                SourceStatusEntity(
                    sourceId = src.id, lastAttemptAt = now, lastSuccessAt = now,
                    lastCount = parsedCount, lastMirror = res.usedMirror, lastError = null,
                )
            )
        }
        val existing = db.configDao().all().associateBy { it.id }
        val fresh = ArrayList<ConfigEntity>()
        for ((id, pair) in seen) {
            val (parsed, srcIds) = pair
            val prev = existing[id]
            // Membership in sources that failed to download is carried over; disabled sources are dropped.
            val merged = SourceMembership.merge(
                previous = prev?.let { SourceMembership.decode(it.sourceIds) } ?: emptySet(),
                seenNow = srcIds,
                notRefreshed = failedIds,
            )
            if (prev == null) fresh += parsed.toEntity(SourceMembership.encode(merged), now)
            else db.configDao().touch(id, now, parsed.name, SourceMembership.encode(merged))
        }
        if (fresh.isNotEmpty()) db.configDao().insertIgnore(fresh)

        // Configs that are no longer listed: drop them unless they still work somewhere or their
        // source could not be checked. Keeps the database close to the live subscriptions.
        val okIds = db.checkResultDao().okConfigIds().toHashSet()
        val toDelete = ArrayList<String>()
        for ((id, prev) in existing) {
            if (id in seen) continue
            val prevSources = SourceMembership.decode(prev.sourceIds)
            when (val d = RetentionPolicy.forUnseen(prevSources, disabledIds, failedIds, id in okIds)) {
                RetentionPolicy.Decision.Keep -> Unit
                is RetentionPolicy.Decision.UpdateSources -> db.configDao().updateSources(id, SourceMembership.encode(d.sourceIds))
                RetentionPolicy.Decision.Delete -> toDelete += id
            }
        }
        toDelete.chunked(900).forEach { db.configDao().deleteByIds(it) }
        // Backstop: even working configs that vanished from every list are dropped after 14 days unseen.
        if (errors.size < enabled.size) {
            db.configDao().pruneOlderThan(now - TimeUnit.DAYS.toMillis(14))
        }
        db.checkResultDao().pruneOrphans()
        _lastFetchNote.value = if (errors.isEmpty()) null else errors.joinToString("\n")
        if (seen.isEmpty() && errors.isNotEmpty()) throw IllegalStateException(errors.joinToString("\n"))
        seen.size
    }

    /**
     * Runs checks for the given [mode]. Must be called from a coroutine that lives as long as the
     * run (the foreground service). [onlyIds] restricts the run to specific configs.
     */
    suspend fun run(mode: NetworkType, onlyIds: List<String>? = null) {
        if (!runMutex.tryLock()) return
        job = currentCoroutineContext()[Job]
        try {
            runLocked(mode, onlyIds)
        } finally {
            // Safety net: whatever happened, the UI must never be left in an active state.
            val s = _state.value
            if (s.isActive) {
                val r = s as? RunState.Running
                _state.value = RunState.Finished(
                    network = mode, total = r?.total ?: 0, done = r?.done ?: 0, ok = r?.ok ?: 0,
                    cancelled = true, finishedAt = System.currentTimeMillis(), note = "Проверка прервана",
                )
            }
            job = null
            runMutex.unlock()
        }
    }

    private suspend fun runLocked(mode: NetworkType, onlyIds: List<String>?) {
        var okCount = 0
        var total = 0
        var done = 0
        var cancelled = false
        var note: String? = null
        try {
            if (!coreRunner.binariesPresent()) {
                _state.value = RunState.Failed("В APK нет бинарников ядра (libxray.so / libsingbox.so). Пересоберите приложение.")
                return
            }
            val netNow = detector.current()
            if (netNow.vpnActive) {
                _state.value = RunState.Failed("Активен сторонний VPN — отключите его, иначе результаты будут неверными.")
                return
            }
            if (netNow.bucket != mode) {
                _state.value = RunState.Failed("Сейчас активна сеть «${netNow.type.label}», а выбран режим «${mode.label}». Переключите сеть и повторите.")
                return
            }

            val cfg = settings.current()
            if (onlyIds == null && cfg.refreshBeforeCheck) {
                val r = refreshConfigs(standalone = false)
                if (r.isFailure) note = "Подписки не обновились, проверены ранее скачанные конфиги"
            }
            if (db.configDao().count() == 0) {
                _state.value = RunState.Failed("Конфиги ещё не скачаны. Нажмите «Скачать конфиги» — это работает в любой сети, в том числе через VPN.")
                return
            }

            // SQLite allows at most 999 bound variables per statement.
            val entities = if (onlyIds != null) onlyIds.chunked(900).flatMap { db.configDao().byIds(it) } else db.configDao().all()
            val parsed = entities.mapNotNull { e -> LinkParser.parse(e.rawLink).getOrNull() }
            total = parsed.size
            if (total == 0) {
                _state.value = RunState.Failed("Нет конфигов для проверки. Проверьте источники в настройках.")
                return
            }
            val checker = ConnectivityChecker(connectTimeoutSec = 10, readTimeoutSec = cfg.timeoutSec.toLong())
            val progress = Mutex()
            val current = LinkedHashSet<String>()

            // On mobile networks white-list configs are checked first, then previously working ones.
            val previous = db.checkResultDao().byType(mode.name).associateBy { it.configId }
            val kindsById = entities.associate { it.id to SubscriptionSource.kindsOf(SourceMembership.decode(it.sourceIds)) }
            val ordered = RunOrder.sort(parsed, mode) { c ->
                RunOrder.Info(isWhite = ListKind.WHITE in (kindsById[c.id] ?: emptySet()), previousOk = previous[c.id]?.ok)
            }
            _state.value = RunState.Running(mode, 0, total, 0, emptyList())

            // Fixed-size worker pool over an ordered queue keeps the priority order exact.
            val queue = Channel<ProxyConfig>(Channel.UNLIMITED)
            ordered.forEach { queue.trySend(it) }
            queue.close()
            coroutineScope {
                repeat(cfg.concurrency.coerceIn(1, 8)) {
                    launch {
                        for (config in queue) {
                            progress.withLock { current += config.name; publish(mode, done, total, okCount, current) }
                            val result = try {
                                withTimeout(90_000) { checkOne(config, mode, checker) }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                failedResult(config, mode, e.message ?: e.javaClass.simpleName, null)
                            }
                            db.checkResultDao().upsert(result)
                            progress.withLock {
                                done++
                                if (result.ok) okCount++
                                current -= config.name
                                publish(mode, done, total, okCount, current)
                            }
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            cancelled = true
            note = e.message?.takeIf { it.isNotBlank() && !it.contains("cancelled", ignoreCase = true) }
                ?: "остановлено пользователем"
        } catch (e: NetworkChangedException) {
            cancelled = true
            note = e.message
        } catch (e: Exception) {
            Log.e(TAG, "run failed", e)
            _state.value = RunState.Failed(e.message ?: e.javaClass.simpleName)
            return
        }
        // After a cancellation the coroutine is already cancelled: any ordinary suspend call would throw
        // again and the state would be stuck in Running. Finish the bookkeeping non-cancellably.
        withContext(NonCancellable) {
            runCatching { settings.markRun(mode.name, System.currentTimeMillis()) }
        }
        _state.value = RunState.Finished(mode, total, done, okCount, cancelled, System.currentTimeMillis(), note)
    }

    private fun publish(mode: NetworkType, done: Int, total: Int, ok: Int, current: Set<String>) {
        _state.value = RunState.Running(mode, done, total, ok, current.toList())
    }

    class NetworkChangedException(message: String) : Exception(message)

    private suspend fun checkOne(config: ProxyConfig, mode: NetworkType, checker: ConnectivityChecker): CheckResultEntity {
        config.unsupportedReason()?.let { return failedResult(config, mode, it, null) }
        val net = detector.current()
        if (net.bucket != mode || net.vpnActive) {
            throw NetworkChangedException("Сеть изменилась во время проверки (${net.type.label}), прогон остановлен")
        }
        val handle = try {
            coreRunner.start(config)
        } catch (e: CoreRunner.CoreStartException) {
            return failedResult(config, mode, e.message ?: "core failed", e.logTail)
        }
        try {
            val outcome = checker.check(handle.localPort)
            if (outcome.ok && outcome.exitIp != null) {
                ensureProvider(outcome, handle.localPort)
            }
            return outcome.toEntity(config, mode, if (outcome.ok) null else handle.logTail())
        } finally {
            handle.stop()
        }
    }

    private suspend fun ensureProvider(outcome: CheckOutcome, localPort: Int) {
        val ip = outcome.exitIp ?: return
        val cached = db.providerDao().byIp(ip)
        val fresh = cached != null && System.currentTimeMillis() - cached.resolvedAt < TimeUnit.DAYS.toMillis(30)
        if (fresh && cached?.website != null) return
        val info = providerResolver.resolve(ip, localPort)
            ?: ProviderJsonParser.fromAsnOnly(ip, outcome.asnId, outcome.asnName, outcome.country, outcome.countryCode, outcome.city)
        db.providerDao().upsert(
            ProviderEntity(
                ip = ip,
                asn = info.asn ?: outcome.asnId,
                org = info.org ?: outcome.asnName,
                isp = info.isp,
                domain = info.domain,
                website = info.website ?: cached?.website,
                country = info.country ?: outcome.country,
                countryCode = info.countryCode ?: outcome.countryCode,
                city = info.city ?: outcome.city,
                source = info.source,
                resolvedAt = System.currentTimeMillis(),
            )
        )
    }

    private fun failedResult(config: ProxyConfig, mode: NetworkType, error: String, log: String?) = CheckResultEntity(
        configId = config.id, networkType = mode.name, ok = false, latencyMs = null, exitIp = null,
        country = null, countryCode = null, city = null, asnId = null, asnName = null, httpStatus = null,
        error = error, coreLog = log?.takeIf { it.isNotBlank() }, checkedAt = System.currentTimeMillis(),
    )

    private fun CheckOutcome.toEntity(config: ProxyConfig, mode: NetworkType, log: String?) = CheckResultEntity(
        configId = config.id, networkType = mode.name, ok = ok, latencyMs = latencyMs, exitIp = exitIp,
        country = country, countryCode = countryCode, city = city, asnId = asnId, asnName = asnName,
        httpStatus = httpStatus, error = error, coreLog = log?.takeIf { it.isNotBlank() }, checkedAt = System.currentTimeMillis(),
    )

    private fun ProxyConfig.toEntity(sourceIds: String, now: Long) = ConfigEntity(
        id = id, rawLink = rawLink, protocol = protocol.name, transport = transport, security = security,
        host = host, port = port, name = name, sourceIds = sourceIds, firstSeenAt = now, lastSeenAt = now,
        unsupportedReason = unsupportedReason(),
    )

    companion object {
        private const val TAG = "CheckRunner"
    }
}
