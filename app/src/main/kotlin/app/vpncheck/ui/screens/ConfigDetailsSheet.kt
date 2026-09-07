@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.vpncheck.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.vpncheck.core.network.NetworkType
import app.vpncheck.data.subscription.ListKind
import app.vpncheck.data.subscription.SubscriptionSource
import app.vpncheck.ui.ConfigRow
import app.vpncheck.ui.RowStatus
import app.vpncheck.ui.flagEmoji
import app.vpncheck.ui.formatDateTime
import app.vpncheck.ui.formatLatency
import app.vpncheck.ui.hostLabel
import app.vpncheck.ui.prettyName
import app.vpncheck.ui.theme.goodColor

@Composable
fun ConfigDetailsSheet(row: ConfigRow, tab: NetworkType, canRecheck: Boolean, onRecheck: () -> Unit, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(prettyName(row.name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Tag(protocolLabel(row.protocol))
                Tag(row.transport)
                if (row.security != "none") Tag(row.security)
            }
            Text("Источники", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                row.sourceIds.sorted().forEach { id ->
                    val src = SubscriptionSource.byId(id)
                    Tag(src?.let { "${it.kind.emoji} ${it.title}" } ?: id, outlined = src?.kind == ListKind.WHITE)
                }
                if (row.sourceIds.isEmpty()) Tag("неизвестно")
            }

            val r = row.result
            val statusText = when (row.status) {
                RowStatus.OK -> "Работает на «${tab.label}»" + (r?.latencyMs?.let { " · ${formatLatency(it)}" } ?: "")
                RowStatus.FAILED -> "Не работает на «${tab.label}»"
                RowStatus.UNCHECKED -> "Не проверялся на «${tab.label}»"
            }
            Text(
                statusText,
                style = MaterialTheme.typography.titleMedium,
                color = when (row.status) {
                    RowStatus.OK -> goodColor()
                    RowStatus.FAILED -> MaterialTheme.colorScheme.error
                    RowStatus.UNCHECKED -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            row.otherResult?.let { o ->
                val otherName = if (tab == NetworkType.MOBILE) "Wi‑Fi" else "Мобильный"
                Text(
                    if (o.ok) "Также работает на «$otherName» (${formatDateTime(o.checkedAt)})" else "На «$otherName» не работал (${formatDateTime(o.checkedAt)})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider()

            InfoRow("Сервер", hostLabel(row.host, row.port))
            r?.checkedAt?.let { InfoRow("Проверено", formatDateTime(it)) }
            if (row.status == RowStatus.OK) {
                val p = row.provider
                r?.exitIp?.let { InfoRow("Внешний IP", it) }
                val geo = listOfNotNull(flagEmoji(p?.countryCode ?: r?.countryCode).ifBlank { null }, p?.country ?: r?.country, p?.city ?: r?.city).joinToString(" · ")
                if (geo.isNotBlank()) InfoRow("Локация", geo)
                InfoRow("Провайдер", p?.displayName ?: r?.asnName ?: "не определён")
                (p?.asn ?: r?.asnId)?.let { InfoRow("ASN", "AS$it" + (r?.asnName?.let { n -> " ($n)" } ?: "")) }
                p?.website?.let { site ->
                    Button(onClick = { runCatching { uriHandler.openUri(site) } }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Сайт провайдера: ${site.removePrefix("https://").removePrefix("http://").removeSuffix("/")}")
                    }
                }
                p?.source?.let { Text("Источник данных о провайдере: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (row.status == RowStatus.FAILED) {
                InfoRow("Ошибка", r?.error ?: "—")
                r?.httpStatus?.let { InfoRow("HTTP", it.toString()) }
                r?.coreLog?.takeIf { it.isNotBlank() }?.let { log ->
                    Text("Лог ядра", style = MaterialTheme.typography.labelMedium)
                    Text(
                        log.trim(),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                            .horizontalScroll(rememberScrollState()),
                    )
                }
            }
            row.unsupportedReason?.let { InfoRow("Ограничение", it) }

            HorizontalDivider()

            Text("Ссылка", style = MaterialTheme.typography.labelMedium)
            SelectionContainer {
                Text(
                    row.rawLink,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(8.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { clipboard.setText(AnnotatedString(row.rawLink)) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.ContentCopy, null); Spacer(Modifier.width(6.dp)); Text("Копировать")
                }
                OutlinedButton(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, row.rawLink) }
                        context.startActivity(Intent.createChooser(send, "Конфиг"))
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Share, null); Spacer(Modifier.width(6.dp)); Text("Поделиться")
                }
            }
            TextButton(onClick = { onRecheck(); onDismiss() }, enabled = canRecheck, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Проверить этот конфиг ещё раз")
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(110.dp))
        SelectionContainer(Modifier.weight(1f)) {
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
