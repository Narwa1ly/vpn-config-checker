@file:OptIn(ExperimentalMaterial3Api::class)

package app.vpncheck.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.vpncheck.data.subscription.ListKind
import app.vpncheck.data.subscription.SubscriptionSource
import app.vpncheck.ui.MainViewModel
import app.vpncheck.ui.UiState

@Composable
fun SettingsScreen(state: UiState, vm: MainViewModel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("Источники конфигов")
            Text(
                "Файлы из репозитория igareck/vpn-configs-for-russia, скачиваются через зеркала (githack, GitHub, GitLab, Codeberg, Bitbucket). " +
                    "Один и тот же конфиг может входить и в чёрные, и в белые списки: в карточке показываются обе метки.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ListKind.values().forEach { kind ->
                Text(
                    "${kind.emoji} ${kind.label}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
                SubscriptionSource.byKind(kind).forEach { src ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(src.title, style = MaterialTheme.typography.bodyLarge)
                            Text(src.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(src.fileName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = src.id in state.settings.enabledSources,
                            onCheckedChange = { vm.setSourceEnabled(src.id, it) },
                            enabled = !state.runState.isActive,
                        )
                    }
                }
            }

            HorizontalDivider()
            SectionTitle("Проверка")
            Text("Параллельных проверок: ${state.settings.concurrency}", style = MaterialTheme.typography.bodyLarge)
            Slider(
                value = state.settings.concurrency.toFloat(),
                onValueChange = { vm.setConcurrency(it.toInt()) },
                valueRange = 1f..8f,
                steps = 6,
                enabled = !state.runState.isActive,
            )
            Text(
                "Больше — быстрее, но на слабых сетях таймауты растут. Каждая проверка запускает отдельный процесс ядра.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Таймаут ответа 2ip.io: ${state.settings.timeoutSec} с", style = MaterialTheme.typography.bodyLarge)
            Slider(
                value = state.settings.timeoutSec.toFloat(),
                onValueChange = { vm.setTimeout(it.toInt()) },
                valueRange = 5f..60f,
                steps = 10,
                enabled = !state.runState.isActive,
            )

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Обновлять подписки перед каждой проверкой", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Выключено: проверка идёт по уже скачанным конфигам, а скачивание — отдельной кнопкой. Удобно, когда GitHub недоступен в проверяемой сети.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.settings.refreshBeforeCheck,
                    onCheckedChange = { vm.setRefreshBeforeCheck(it) },
                    enabled = !state.runState.isActive,
                )
            }

            HorizontalDivider()
            SectionTitle("Отображение")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Показывать нерабочие в списке «Все»", style = MaterialTheme.typography.bodyLarge)
                }
                Switch(checked = state.settings.showFailed, onCheckedChange = { vm.setShowFailed(it) })
            }

            HorizontalDivider()
            SectionTitle("Как это работает")
            Text(
                "Для каждого конфига приложение запускает Xray-core (VLESS, VMess, Trojan, Shadowsocks) или sing-box (Hysteria2) " +
                    "как отдельный процесс с локальным HTTP-прокси и пытается загрузить api.2ip.io и 2ip.io через туннель. " +
                    "Результаты хранятся отдельно для мобильной сети и Wi‑Fi: проверка засчитывается в ту сеть, которая активна на телефоне в момент запуска. " +
                    "Провайдер определяется по внешнему IP, который видит 2ip.io (ipwho.is → ip-api.com → встроенная таблица ASN). " +
                    "Системный VPN не поднимается — приложение только тестирует конфиги.\n\n" +
                    "Порядок проверки: на мобильной сети сначала конфиги из белых списков, затем ранее рабочие, потом непроверенные и в конце нерабочие. " +
                    "На Wi‑Fi — ранее рабочие, непроверенные, нерабочие.\n\n" +
                    "Хранение: при каждом скачивании конфиги, исчезнувшие из подписок и не работающие ни в одной сети, удаляются; " +
                    "рабочие остаются, пока не провалят проверку. Нерабочие можно удалить вручную через меню ⋮ на главном экране.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
}
