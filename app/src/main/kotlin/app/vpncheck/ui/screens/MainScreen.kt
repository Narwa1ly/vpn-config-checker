@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.vpncheck.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.vpncheck.core.network.NetworkType
import app.vpncheck.data.subscription.ListKind
import app.vpncheck.data.repo.RunState
import app.vpncheck.ui.ConfigRow
import app.vpncheck.ui.ConfirmAction
import app.vpncheck.ui.Freshness
import app.vpncheck.ui.SourceRow
import app.vpncheck.ui.Filter
import app.vpncheck.ui.MainViewModel
import app.vpncheck.ui.RowStatus
import app.vpncheck.ui.UiState
import app.vpncheck.ui.flagEmoji
import app.vpncheck.ui.formatDateTime
import app.vpncheck.ui.formatLatency
import app.vpncheck.ui.hostLabel
import app.vpncheck.ui.prettyName
import app.vpncheck.ui.theme.Neutral
import app.vpncheck.ui.theme.goodColor
import app.vpncheck.ui.theme.goodContainer
import app.vpncheck.ui.theme.warnColor

@Composable
fun MainScreen(state: UiState, vm: MainViewModel, onShare: (String) -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.dismissMessage()
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("VPN Config Checker", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                actions = {
                    IconButton(onClick = { onShare(vm.workingLinksText()) }, enabled = state.okCount > 0) {
                        Icon(Icons.Default.Share, contentDescription = "Поделиться рабочими")
                    }
                    IconButton(onClick = { vm.openSettings(true) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Настройки")
                    }
                    var menuOpen by remember { mutableStateOf(false) }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Ещё")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Удалить нерабочие на «${state.tab.label}» (${state.failedDeletableCount})") },
                            leadingIcon = { Icon(Icons.Default.DeleteSweep, null) },
                            enabled = state.failedDeletableCount > 0 && !state.runState.isActive,
                            onClick = { menuOpen = false; vm.requestConfirm(ConfirmAction.DELETE_FAILED) },
                        )
                        DropdownMenuItem(
                            text = { Text("Удалить всё и скачать заново") },
                            leadingIcon = { Icon(Icons.Default.RestartAlt, null) },
                            enabled = !state.runState.isActive && !state.refreshing,
                            onClick = { menuOpen = false; vm.requestConfirm(ConfirmAction.RESET_ALL) },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            NetworkBanner(state)
            TabRow(selectedTabIndex = if (state.tab == NetworkType.MOBILE) 0 else 1) {
                Tab(
                    selected = state.tab == NetworkType.MOBILE,
                    onClick = { vm.selectTab(NetworkType.MOBILE) },
                    text = { Text("Мобильный" + tabCount(state, NetworkType.MOBILE)) },
                    icon = { Icon(Icons.Default.SignalCellularAlt, null) },
                )
                Tab(
                    selected = state.tab == NetworkType.WIFI,
                    onClick = { vm.selectTab(NetworkType.WIFI) },
                    text = { Text("Wi‑Fi" + tabCount(state, NetworkType.WIFI)) },
                    icon = { Icon(Icons.Default.Wifi, null) },
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { ConfigsCard(state, vm) }
                item { SummaryCard(state, vm) }
                item { Filters(state, vm) }
                if (state.rows.isEmpty()) {
                    item { EmptyHint(state) }
                }
                items(state.rows, key = { it.id }) { row ->
                    ConfigCard(row = row, otherLabel = if (state.tab == NetworkType.MOBILE) "Wi‑Fi" else "мобильном", onClick = { vm.select(row.id) })
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
    when (state.confirm) {
        ConfirmAction.DELETE_FAILED -> ConfirmDialog(
            title = "Удалить нерабочие конфиги?",
            text = "Будет удалено ${state.failedDeletableCount} конфигов, которые не работают на «${state.tab.label}» и не отмечены рабочими на другой сети. " +
                "Если они ещё есть в подписках, при следующем скачивании они вернутся как непроверенные.",
            confirmLabel = "Удалить",
            onConfirm = { vm.deleteFailed() },
            onDismiss = { vm.requestConfirm(null) },
        )
        ConfirmAction.RESET_ALL -> ConfirmDialog(
            title = "Удалить всё и скачать заново?",
            text = "Будут удалены все ${state.totalConfigs} конфигов и результаты проверок в обеих сетях, затем подписки скачаются заново.",
            confirmLabel = "Удалить и скачать",
            onConfirm = { vm.resetAll() },
            onDismiss = { vm.requestConfirm(null) },
        )
        null -> Unit
    }
    state.selectedRow?.let { row ->
        ConfigDetailsSheet(
            row = row,
            tab = state.tab,
            canRecheck = state.canStart,
            onRecheck = { vm.recheck(row.id) },
            onDismiss = { vm.select(null) },
        )
    }
}

@Composable
private fun ConfirmDialog(title: String, text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

private fun tabCount(state: UiState, t: NetworkType): String {
    val n = if (t == state.tab) state.okCount else state.okCountOther
    return if (n > 0) " · $n" else ""
}

@Composable
private fun NetworkBanner(state: UiState) {
    val net = state.network
    val (text, warn) = when {
        net.vpnActive -> "Активен VPN: скачивать конфиги можно, проверять нельзя. Отключите VPN перед проверкой" to true
        net.type == NetworkType.NONE -> "Нет подключения к сети" to true
        net.type == NetworkType.OTHER -> "Сеть: другая (Ethernet/неизвестно) — проверка недоступна" to true
        else -> "Сеть сейчас: ${if (net.type == NetworkType.MOBILE) "мобильный интернет" else "Wi‑Fi"}" to (net.bucket != state.tab)
    }
    val bg = if (warn) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (warn) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().background(bg).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = when {
            warn -> Icons.Default.Warning
            net.type == NetworkType.WIFI -> Icons.Default.Wifi
            else -> Icons.Default.SignalCellularAlt
        }
        Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = fg)
    }
}

@Composable
private fun SummaryCard(state: UiState, vm: MainViewModel) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${state.okCount}",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.padding(bottom = 6.dp)) {
                    Text(
                        "рабочих на «${state.tab.label}»",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        "проверено ${state.checkedCount} из ${state.totalConfigs} · последний прогон: ${formatDateTime(state.lastRunAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                    if (state.whiteCount > 0) {
                        Text(
                            "${ListKind.WHITE.emoji} из белых списков: ${state.okWhiteCount} рабочих из ${state.whiteCount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        )
                    }
                }
            }
            val rs = state.runState
            when {
                rs is RunState.Running -> {
                    LinearProgressIndicator(progress = { if (rs.total == 0) 0f else rs.done.toFloat() / rs.total }, modifier = Modifier.fillMaxWidth())
                    Text(
                        "${rs.done}/${rs.total} · рабочих ${rs.ok}" + (rs.current.firstOrNull()?.let { " · ${prettyName(it)}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    OutlinedButton(onClick = { vm.stopRun() }) { Icon(Icons.Default.Stop, null); Spacer(Modifier.width(6.dp)); Text("Остановить") }
                }
                rs is RunState.Fetching && !state.refreshing -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Обновление подписок ${rs.done}/${rs.total} перед проверкой…", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { vm.stopRun() }) { Icon(Icons.Default.Stop, null); Spacer(Modifier.width(6.dp)); Text("Остановить") }
                }
                else -> {
                    val resumable = state.uncheckedCount > 0 && state.checkedCount > 0
                    if (resumable) {
                        Button(onClick = { vm.startRunUnchecked() }, enabled = state.canStart, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.PlayArrow, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Продолжить: непроверенных ${state.uncheckedCount}")
                        }
                        OutlinedButton(onClick = { vm.startRun() }, enabled = state.canStart, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Replay, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Проверить все заново: ${state.totalConfigs}")
                        }
                    } else {
                        Button(onClick = { vm.startRun() }, enabled = state.canStart, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.PlayArrow, null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (state.totalConfigs == 0) "Сначала скачайте конфиги" else "Проверить все: ${state.totalConfigs}")
                        }
                    }
                    if (state.totalConfigs > 0 && state.rows.isNotEmpty() && state.rows.size < state.totalConfigs) {
                        OutlinedButton(onClick = { vm.startRunFiltered() }, enabled = state.canStart, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.FilterList, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Проверить показанные: ${state.rows.size}")
                        }
                    }
                    val note = when (rs) {
                        is RunState.Finished -> buildString {
                            if (rs.cancelled) append("Остановлено: проверено ${rs.done} из ${rs.total}, рабочих ${rs.ok}")
                            else append("Готово: ${rs.ok} рабочих из ${rs.total}")
                            rs.note?.let { append(" · $it") }
                        }
                        is RunState.Failed -> rs.message
                        else -> state.startBlockReason
                    }
                    if (note != null) {
                        Text(
                            note,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (rs is RunState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigsCard(state: UiState, vm: MainViewModel) {
    val freshColor = when (state.freshness) {
        Freshness.FRESH -> goodColor()
        Freshness.AGING -> warnColor()
        Freshness.STALE -> MaterialTheme.colorScheme.error
        Freshness.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val enabledSources = state.sources.filter { it.enabled }
    val okSources = enabledSources.count { it.status?.lastAttemptOk == true }
    val attempted = enabledSources.count { it.status != null }
    val allFailed = attempted > 0 && enabledSources.all { it.status?.lastError != null }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudDownload, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (state.totalConfigs == 0) "Конфиги не скачаны" else "Конфигов: ${state.totalConfigs}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (state.lastDownloadAt == null) "Скачайте подписки — без этого проверять нечего"
                        else "скачаны ${formatDateTime(state.lastDownloadAt)} · ${state.freshness.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = freshColor,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            if (state.totalConfigs > 0) {
                Text(
                    "🏴 чёрные: ${state.blackCount} · 🏳️ белые: ${state.whiteCount} · источников скачано: $okSources из ${enabledSources.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.downloading) {
                LinearProgressIndicator(
                    progress = { if (state.downloadTotal == 0) 0f else state.downloadDone.toFloat() / state.downloadTotal },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Скачивание ${state.downloadDone}/${state.downloadTotal}…",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { vm.cancelDownload() }, enabled = state.refreshing) { Text("Отмена") }
                }
            } else {
                val needsDownload = state.totalConfigs == 0 || state.freshness == Freshness.STALE
                if (needsDownload) {
                    Button(onClick = { vm.refreshConfigs() }, enabled = !state.runState.isActive, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Download, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Скачать конфиги")
                    }
                } else {
                    FilledTonalButton(onClick = { vm.refreshConfigs() }, enabled = !state.runState.isActive, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Download, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Обновить конфиги")
                    }
                }
            }
            Text(
                "Скачивание идёт с зеркал GitHub и работает в любой сети, в том числе через включённый VPN. Проверка конфигов работает только без VPN.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (allFailed) {
                Text(
                    "Ни один источник не скачался. Подключитесь к другой сети или включите VPN и повторите.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            HorizontalDivider()
            enabledSources.forEach { SourceLine(it) }
        }
    }
}

@Composable
private fun SourceLine(row: SourceRow) {
    val st = row.status
    val good = goodColor()
    val (icon, tint) = when {
        st == null -> Icons.Default.RadioButtonUnchecked to Neutral
        st.lastError == null -> Icons.Default.CheckCircle to good
        else -> Icons.Default.Error to MaterialTheme.colorScheme.error
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "${row.source.kind.emoji} ${row.source.title}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                when {
                    st == null -> "не скачивался"
                    st.lastError == null -> "${st.lastCount}" + (st.lastMirror?.let { " · $it" } ?: "")
                    st.lastSuccessAt != null -> "ошибка · в кэше ${st.lastCount}"
                    else -> "ошибка"
                },
                style = MaterialTheme.typography.labelSmall,
                color = tint,
            )
        }
        if (st?.lastError != null) {
            Text(
                st.lastError,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 24.dp),
            )
        }
    }
}

@Composable
private fun Filters(state: UiState, vm: MainViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Filter.values().forEach { f ->
                FilterChip(selected = state.filter == f, onClick = { vm.setFilter(f) }, label = { Text(f.label) })
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ListKind.values().forEach { k ->
                FilterChip(
                    selected = state.listFilter == k,
                    onClick = { vm.setListFilter(k) },
                    label = { Text("${k.emoji} ${k.label}") },
                )
            }
        }
        if (state.protocols.size > 1) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.protocols.forEach { p ->
                    FilterChip(selected = state.protocolFilter == p, onClick = { vm.setProtocolFilter(p) }, label = { Text(protocolLabel(p)) })
                }
            }
        }
    }
}

@Composable
private fun EmptyHint(state: UiState) {
    val text = when {
        state.totalConfigs == 0 -> "Конфиги ещё не скачаны. Нажмите «Скачать конфиги» в карточке выше — это работает и через VPN."
        state.listFilter != null && state.rows.isEmpty() && state.filter == Filter.WORKING ->
            "Пока нет рабочих конфигов из категории «${state.listFilter.label}» для «${state.tab.label}»."
        state.filter == Filter.WORKING -> "Пока нет рабочих конфигов для «${state.tab.label}». Запустите проверку в этой сети."
        else -> "Ничего не найдено по текущему фильтру."
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp))
}

fun protocolLabel(p: String): String = when (p) {
    "VLESS" -> "VLESS"
    "VMESS" -> "VMess"
    "TROJAN" -> "Trojan"
    "SHADOWSOCKS" -> "SS"
    "HYSTERIA2" -> "Hy2"
    else -> p
}

@Composable
fun ConfigCard(row: ConfigRow, otherLabel: String, onClick: () -> Unit) {
    val good = goodColor()
    val statusColor = when (row.status) {
        RowStatus.OK -> good
        RowStatus.FAILED -> MaterialTheme.colorScheme.error
        RowStatus.UNCHECKED -> Neutral
    }
    val container = when (row.status) {
        RowStatus.OK -> goodContainer()
        else -> MaterialTheme.colorScheme.surface
    }
    val uriHandler = LocalUriHandler.current
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = if (row.status == RowStatus.OK) 1.dp else 0.dp),
        border = if (row.status != RowStatus.OK) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(statusColor, CircleShape))
                Spacer(Modifier.width(10.dp))
                Text(
                    prettyName(row.name),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                row.result?.latencyMs?.let {
                    Text(formatLatency(it), style = MaterialTheme.typography.labelMedium, color = statusColor, fontWeight = FontWeight.SemiBold)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Tag("${protocolLabel(row.protocol)} · ${row.transport}" + if (row.security != "none") " · ${row.security}" else "")
                row.kinds.sortedBy { it.ordinal }.forEach { k -> Tag("${k.emoji} ${k.shortLabel}", outlined = k == ListKind.WHITE) }
                Text(
                    hostLabel(row.host, row.port),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (row.otherResult?.ok == true) Tag("и на $otherLabel", accent = true)
            }
            when (row.status) {
                RowStatus.OK -> {
                    val r = row.result!!
                    val p = row.provider
                    val geo = listOfNotNull(
                        flagEmoji(p?.countryCode ?: r.countryCode).ifBlank { null },
                        (p?.country ?: r.country),
                        (p?.city ?: r.city),
                    ).joinToString(" · ")
                    val providerName = p?.displayName ?: r.asnName ?: "провайдер не определён"
                    Text("$geo · $providerName".trim(' ', '·'), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val site = p?.website
                    if (site != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { runCatching { uriHandler.openUri(site) } },
                        ) {
                            Text(
                                site.removePrefix("https://").removePrefix("http://").removeSuffix("/"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                        }
                    } else {
                        Text("сайт провайдера не найден" + (r.exitIp?.let { " · IP $it" } ?: ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                RowStatus.FAILED -> Text(
                    row.result?.error ?: "ошибка",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                RowStatus.UNCHECKED -> Text(
                    row.unsupportedReason ?: "ещё не проверялся в этой сети",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun Tag(text: String, accent: Boolean = false, outlined: Boolean = false) {
    val shape = RoundedCornerShape(6.dp)
    val bg = when {
        accent -> MaterialTheme.colorScheme.primary
        outlined -> Color.Transparent
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = when {
        accent -> MaterialTheme.colorScheme.onPrimary
        outlined -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val base = if (outlined) {
        Modifier.border(1.dp, MaterialTheme.colorScheme.outline, shape)
    } else {
        Modifier.background(bg, shape)
    }
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        modifier = base.padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
