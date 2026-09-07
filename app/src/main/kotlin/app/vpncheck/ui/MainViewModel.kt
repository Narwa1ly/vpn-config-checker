package app.vpncheck.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.vpncheck.App
import app.vpncheck.core.network.NetworkState
import app.vpncheck.core.network.NetworkType
import app.vpncheck.data.db.CheckResultEntity
import app.vpncheck.data.db.ConfigWithResults
import app.vpncheck.data.db.ProviderEntity
import app.vpncheck.data.db.SourceStatusEntity
import app.vpncheck.data.repo.RunState
import app.vpncheck.data.settings.AppSettings
import app.vpncheck.data.subscription.ListKind
import app.vpncheck.data.subscription.SourceMembership
import app.vpncheck.data.subscription.SubscriptionSource
import app.vpncheck.service.CheckService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Filter(val label: String) { ALL("Все"), WORKING("Рабочие"), FAILED("Нерабочие"), UNCHECKED("Непроверенные") }

enum class RowStatus { OK, FAILED, UNCHECKED }

/** Destructive actions that need a confirmation dialog. */
enum class ConfirmAction { DELETE_FAILED, RESET_ALL }

data class ConfigRow(
    val id: String,
    val name: String,
    val protocol: String,
    val transport: String,
    val security: String,
    val host: String,
    val port: Int,
    val rawLink: String,
    val sourceIds: Set<String>,
    val unsupportedReason: String?,
    val result: CheckResultEntity?,
    val otherResult: CheckResultEntity?,
    val provider: ProviderEntity?,
) {
    val status: RowStatus
        get() = when {
            result == null -> RowStatus.UNCHECKED
            result.ok -> RowStatus.OK
            else -> RowStatus.FAILED
        }
    val kinds: Set<ListKind> get() = SubscriptionSource.kindsOf(sourceIds)
    val isWhite: Boolean get() = ListKind.WHITE in kinds
    val isBlack: Boolean get() = ListKind.BLACK in kinds
    val sourceTitles: List<String> get() = sourceIds.sorted().map { SubscriptionSource.byId(it)?.title ?: it }
}

data class SourceRow(val source: SubscriptionSource, val enabled: Boolean, val status: SourceStatusEntity?)

data class UiState(
    val tab: NetworkType = NetworkType.MOBILE,
    val network: NetworkState = NetworkState(NetworkType.NONE, false),
    val runState: RunState = RunState.Idle,
    val rows: List<ConfigRow> = emptyList(),
    val totalConfigs: Int = 0,
    val checkedCount: Int = 0,
    val okCount: Int = 0,
    val okCountOther: Int = 0,
    val whiteCount: Int = 0,
    val blackCount: Int = 0,
    val okWhiteCount: Int = 0,
    /** Configs without a result for the current tab; used by the "continue" button. */
    val uncheckedIds: List<String> = emptyList(),
    /** Configs that failed on the current tab and are not known to work on the other one. */
    val failedDeletableIds: List<String> = emptyList(),
    val confirm: ConfirmAction? = null,
    val sources: List<SourceRow> = emptyList(),
    val lastDownloadAt: Long? = null,
    val freshness: Freshness = Freshness.NONE,
    val downloading: Boolean = false,
    val downloadDone: Int = 0,
    val downloadTotal: Int = 0,
    val lastRunAt: Long? = null,
    val filter: Filter = Filter.ALL,
    val listFilter: ListKind? = null,
    val protocolFilter: String? = null,
    val protocols: List<String> = emptyList(),
    val settings: AppSettings = AppSettings.DEFAULT,
    val fetchNote: String? = null,
    val selectedId: String? = null,
    val showSettings: Boolean = false,
    val refreshing: Boolean = false,
    val message: String? = null,
) {
    val startBlockReason: String?
        get() = when {
            runState.isActive -> null
            totalConfigs == 0 -> "Сначала скачайте конфиги"
            network.vpnActive -> "Отключите VPN перед проверкой (скачивать конфиги через VPN можно)"
            network.type == NetworkType.NONE -> "Нет подключения к сети"
            network.bucket != tab -> "Сейчас активна сеть «${network.type.label}». Переключитесь на «${tab.label}» или откройте другую вкладку."
            else -> null
        }
    val canStart: Boolean get() = !runState.isActive && startBlockReason == null
    val uncheckedCount: Int get() = uncheckedIds.size
    val failedDeletableCount: Int get() = failedDeletableIds.size
    val selectedRow: ConfigRow? get() = selectedId?.let { id -> rows.firstOrNull { it.id == id } }
}

class MainViewModel(private val app: Application) : ViewModel() {
    private val container = App.container(app)
    private val runner = container.runner

    private val tab = MutableStateFlow(NetworkType.MOBILE)
    private val filter = MutableStateFlow(Filter.ALL)
    private val protocolFilter = MutableStateFlow<String?>(null)
    private val listFilter = MutableStateFlow<ListKind?>(null)
    private val selectedId = MutableStateFlow<String?>(null)
    private val showSettings = MutableStateFlow(false)
    private val refreshing = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val confirm = MutableStateFlow<ConfirmAction?>(null)

    init {
        viewModelScope.launch {
            val saved = container.settings.flow.first().selectedTab
            tab.value = runCatching { NetworkType.valueOf(saved) }.getOrDefault(NetworkType.MOBILE)
                .takeIf { it == NetworkType.MOBILE || it == NetworkType.WIFI } ?: NetworkType.MOBILE
        }
    }

    private data class Local(
        val tab: NetworkType, val filter: Filter, val protocol: String?, val selected: String?,
        val settingsOpen: Boolean, val refreshing: Boolean, val message: String?, val listKind: ListKind?,
        val confirm: ConfirmAction?,
    )

    private val local = combine(tab, filter, protocolFilter, selectedId, showSettings) { t, f, p, s, so ->
        Local(t, f, p, s, so, false, null, null, null)
    }.let { base ->
        combine(base, refreshing, message, listFilter, confirm) { l, r, m, lk, c ->
            l.copy(refreshing = r, message = m, listKind = lk, confirm = c)
        }
    }

    private data class Data(
        val configs: List<ConfigWithResults>, val providers: List<ProviderEntity>,
        val settings: AppSettings, val network: NetworkState, val sourceStatus: List<SourceStatusEntity>,
    )

    private val data = combine(
        container.db.configDao().observeAll(),
        container.db.providerDao().observeAll(),
        container.settings.flow,
        container.detector.observe(),
        container.db.sourceStatusDao().observeAll(),
    ) { c, p, s, n, st -> Data(c, p, s, n, st) }

    val state: StateFlow<UiState> = combine(local, data, runner.state, runner.lastFetchNote) { l, d, run, note ->
        buildState(l, d, run, note)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    private fun buildState(l: Local, d: Data, run: RunState, note: String?): UiState {
        val providersByIp = d.providers.associateBy { it.ip }
        val other = if (l.tab == NetworkType.MOBILE) NetworkType.WIFI else NetworkType.MOBILE
        val allRows = d.configs.map { cw ->
            val res = cw.results.firstOrNull { it.networkType == l.tab.name }
            val otherRes = cw.results.firstOrNull { it.networkType == other.name }
            ConfigRow(
                id = cw.config.id,
                name = cw.config.name,
                protocol = cw.config.protocol,
                transport = cw.config.transport,
                security = cw.config.security,
                host = cw.config.host,
                port = cw.config.port,
                rawLink = cw.config.rawLink,
                sourceIds = SourceMembership.decode(cw.config.sourceIds),
                unsupportedReason = cw.config.unsupportedReason,
                result = res,
                otherResult = otherRes,
                provider = res?.exitIp?.let { providersByIp[it] },
            )
        }
        val sorted = allRows.sortedWith(
            compareBy<ConfigRow> {
                when (it.status) {
                    RowStatus.OK -> 0
                    RowStatus.UNCHECKED -> 1
                    RowStatus.FAILED -> 2
                }
            }.thenBy { it.result?.latencyMs ?: Long.MAX_VALUE }.thenBy { it.name.lowercase() }
        )
        val filtered = sorted.filter { row ->
            (l.protocol == null || row.protocol == l.protocol) &&
                (l.listKind == null || l.listKind in row.kinds) &&
                when (l.filter) {
                Filter.ALL -> d.settings.showFailed || row.status != RowStatus.FAILED
                Filter.WORKING -> row.status == RowStatus.OK
                Filter.FAILED -> row.status == RowStatus.FAILED
                Filter.UNCHECKED -> row.status == RowStatus.UNCHECKED
            }
        }
        val statusById = d.sourceStatus.associateBy { it.sourceId }
        val sources = SubscriptionSource.ALL.map { SourceRow(it, it.id in d.settings.enabledSources, statusById[it.id]) }
        val lastDownloadAt = sources.filter { it.enabled }.mapNotNull { it.status?.lastSuccessAt }.maxOrNull()
        val fetching = run as? RunState.Fetching
        return UiState(
            tab = l.tab,
            network = d.network,
            runState = run,
            rows = filtered,
            totalConfigs = allRows.size,
            checkedCount = allRows.count { it.result != null },
            okCount = allRows.count { it.status == RowStatus.OK },
            okCountOther = allRows.count { it.otherResult?.ok == true },
            whiteCount = allRows.count { it.isWhite },
            blackCount = allRows.count { it.isBlack },
            okWhiteCount = allRows.count { it.isWhite && it.status == RowStatus.OK },
            uncheckedIds = allRows.filter { it.status == RowStatus.UNCHECKED }.map { it.id },
            failedDeletableIds = allRows.filter { it.status == RowStatus.FAILED && it.otherResult?.ok != true }.map { it.id },
            confirm = l.confirm,
            sources = sources,
            lastDownloadAt = lastDownloadAt,
            freshness = Freshness.of(lastDownloadAt, System.currentTimeMillis()),
            downloading = l.refreshing || fetching != null,
            downloadDone = fetching?.done ?: 0,
            downloadTotal = fetching?.total ?: 0,
            lastRunAt = if (l.tab == NetworkType.WIFI) d.settings.lastRunWifiAt else d.settings.lastRunMobileAt,
            filter = l.filter,
            listFilter = l.listKind,
            protocolFilter = l.protocol,
            protocols = allRows.map { it.protocol }.distinct().sorted(),
            settings = d.settings,
            fetchNote = note,
            selectedId = l.selected,
            showSettings = l.settingsOpen,
            refreshing = l.refreshing,
            message = l.message,
        )
    }

    // ---- actions ---------------------------------------------------------------------------

    fun selectTab(t: NetworkType) {
        tab.value = t
        viewModelScope.launch { container.settings.setSelectedTab(t.name) }
    }

    fun setFilter(f: Filter) { filter.value = f }
    fun setProtocolFilter(p: String?) { protocolFilter.value = if (protocolFilter.value == p) null else p }
    fun setListFilter(k: ListKind?) { listFilter.value = if (listFilter.value == k) null else k }
    fun select(id: String?) { selectedId.value = id }
    fun openSettings(open: Boolean) { showSettings.value = open }
    fun dismissMessage() { message.value = null }

    fun startRun() {
        val s = state.value
        if (!s.canStart) {
            message.value = s.startBlockReason
            return
        }
        CheckService.start(app, s.tab)
    }

    fun stopRun() = CheckService.stop(app)

    fun recheck(id: String) {
        val s = state.value
        if (s.runState.isActive) return
        if (s.startBlockReason != null) {
            message.value = s.startBlockReason
            return
        }
        CheckService.start(app, s.tab, listOf(id))
    }

    private var refreshJob: Job? = null

    /** Downloads subscriptions. Works over any network, VPN included; does not run checks. */
    fun refreshConfigs() {
        if (refreshing.value || state.value.runState.isActive) return
        refreshJob = viewModelScope.launch {
            refreshing.value = true
            try {
                val r = runner.refreshConfigs()
                message.value = r.fold(
                    onSuccess = { "Скачано $it конфигов" },
                    onFailure = { "Не удалось скачать: ${it.message}" },
                )
            } catch (e: CancellationException) {
                message.value = "Скачивание отменено"
            } finally {
                refreshing.value = false
            }
        }
    }

    fun cancelDownload() {
        refreshJob?.cancel()
    }

    fun requestConfirm(action: ConfirmAction?) {
        confirm.value = action
    }

    /** Deletes configs that failed on the current tab and are not working on the other one. */
    fun deleteFailed() {
        val s = state.value
        confirm.value = null
        val ids = s.failedDeletableIds
        if (ids.isEmpty() || s.runState.isActive) return
        viewModelScope.launch {
            ids.chunked(900).forEach { container.db.configDao().deleteByIds(it) }
            container.db.checkResultDao().pruneOrphans()
            message.value = "Удалено ${ids.size} нерабочих конфигов. Если они ещё есть в подписках, вернутся при следующем скачивании как непроверенные."
        }
    }

    /** Wipes configs, results and source statuses, then downloads subscriptions again. */
    fun resetAll() {
        confirm.value = null
        if (state.value.runState.isActive || refreshing.value) return
        viewModelScope.launch {
            container.db.checkResultDao().deleteAll()
            container.db.configDao().deleteAll()
            container.db.sourceStatusDao().deleteAll()
            refreshConfigs()
        }
    }

    /** Continues an interrupted run: checks only configs that have no result for the current tab yet. */
    fun startRunUnchecked() {
        val s = state.value
        if (s.uncheckedIds.isEmpty()) return
        if (!s.canStart) {
            message.value = s.startBlockReason
            return
        }
        CheckService.start(app, s.tab, s.uncheckedIds)
    }

    /** Checks only the rows currently shown (after filters), e.g. white-list configs only. */
    fun startRunFiltered() {
        val s = state.value
        val ids = s.rows.map { it.id }
        if (ids.isEmpty()) return
        if (!s.canStart) {
            message.value = s.startBlockReason
            return
        }
        CheckService.start(app, s.tab, ids)
    }

    /** Working links for the current tab as a plain-text subscription. */
    fun workingLinksText(): String {
        val s = state.value
        val working = s.rows.filter { it.status == RowStatus.OK }
        if (working.isEmpty()) return ""
        val header = "# VPN Config Checker — рабочие на «${s.tab.label}» (${formatDateTime(System.currentTimeMillis())})\n"
        return header + working.joinToString("\n") { it.rawLink }
    }

    fun setSourceEnabled(id: String, enabled: Boolean) = viewModelScope.launch { container.settings.setSourceEnabled(id, enabled) }
    fun setConcurrency(v: Int) = viewModelScope.launch { container.settings.setConcurrency(v) }
    fun setTimeout(v: Int) = viewModelScope.launch { container.settings.setTimeout(v) }
    fun setShowFailed(v: Boolean) = viewModelScope.launch { container.settings.setShowFailed(v) }
    fun setRefreshBeforeCheck(v: Boolean) = viewModelScope.launch { container.settings.setRefreshBeforeCheck(v) }

    class Factory(private val app: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(app) as T
    }
}
