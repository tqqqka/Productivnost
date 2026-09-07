package com.tihon.productivnost.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tihon.productivnost.core.Store
import com.tihon.productivnost.ui.Route
import com.tihon.productivnost.ui.ScreenHeader
import com.tihon.productivnost.ui.components.Bullet
import com.tihon.productivnost.ui.components.Chip
import com.tihon.productivnost.ui.components.Divider
import com.tihon.productivnost.ui.components.GhostButton
import com.tihon.productivnost.ui.components.SectionTitle
import com.tihon.productivnost.ui.components.SoftCard
import com.tihon.productivnost.ui.theme.LocalExtra
import com.tihon.productivnost.ui.theme.P
import com.tihon.productivnost.util.Fmt

@Composable
fun SettingsScreen(onBack: () -> Unit, navigate: (Route) -> Unit) {
    val e = LocalExtra.current
    val s by Store.settings.collectAsStateWithLifecycle()
    val garden by Store.garden.collectAsStateWithLifecycle()
    var confirmReset by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader("Настройки", null, onBack) }

        item { SectionTitle("Напоминания") }
        item {
            SoftCard {
                ToggleRow("Напоминания включены", "Вода, разминка, прогулка", s.remindersEnabled) { v ->
                    Store.updateSettings { it.copy(remindersEnabled = v) }
                }
                Spacer(Modifier.height(14.dp)); Divider(); Spacer(Modifier.height(14.dp))
                Text("Интервал", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "Отсчитывается по времени, которое ты реально провёл в телефоне",
                    style = MaterialTheme.typography.bodySmall, color = e.faint,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(15, 20, 30, 45, 60).forEach { m ->
                        Chip("$m мин", s.intervalMinutes == m) {
                            Store.updateSettings { it.copy(intervalMinutes = m) }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp)); Divider(); Spacer(Modifier.height(14.dp))
                ToggleRow("Звук", "Спокойный колокольчик", s.soundEnabled) { v ->
                    Store.updateSettings { it.copy(soundEnabled = v) }
                }
                Spacer(Modifier.height(12.dp))
                ToggleRow("Вибрация", null, s.vibrationEnabled) { v ->
                    Store.updateSettings { it.copy(vibrationEnabled = v) }
                }
            }
        }

        item { SectionTitle("Ночной режим") }
        item {
            SoftCard {
                ToggleRow(
                    "Пора спать",
                    "С ${Fmt.timeOfDay(s.sleepFromMinute)} до ${Fmt.timeOfDay(s.sleepToMinute)} каждые ${s.sleepEveryMinutes} мин",
                    s.sleepEnabled,
                ) { v -> Store.updateSettings { it.copy(sleepEnabled = v) } }
                Spacer(Modifier.height(14.dp)); Divider(); Spacer(Modifier.height(14.dp))
                Text("Начало", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(20 * 60, 20 * 60 + 30, 21 * 60, 21 * 60 + 30, 22 * 60).forEach { m ->
                        Chip(Fmt.timeOfDay(m), s.sleepFromMinute == m, accent = P.violet) {
                            Store.updateSettings { st ->
                                st.copy(sleepFromMinute = m, sleepToMinute = maxOf(st.sleepToMinute, m + 30))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("Отбой", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(21 * 60, 22 * 60, 23 * 60, 24 * 60 - 1).forEach { m ->
                        Chip(Fmt.timeOfDay(if (m == 24 * 60 - 1) 0 else m), s.sleepToMinute == m, accent = P.violet) {
                            Store.updateSettings { it.copy(sleepToMinute = m) }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("Как часто", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 15, 30).forEach { m ->
                        Chip("$m мин", s.sleepEveryMinutes == m, accent = P.violet) {
                            Store.updateSettings { it.copy(sleepEveryMinutes = m) }
                        }
                    }
                }
            }
        }

        item { SectionTitle("Отжимания") }
        item {
            SoftCard {
                Text("Сколько минут даёт одно отжимание", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1.0, 2.0, 2.5, 5.0).forEach { v ->
                        Chip("$v мин", s.minutesPerPushup == v) {
                            Store.updateSettings { it.copy(minutesPerPushup = v) }
                        }
                    }
                }
            }
        }

        item { SectionTitle("Правила") }
        item {
            SoftCard(onClick = { navigate(Route.AllowedApps) }) {
                Bullet("✅", "Разрешённые приложения", "${s.allowedApps.size} выбрано", P.leaf)
            }
        }
        item {
            SoftCard(onClick = { navigate(Route.Limits) }) {
                Bullet("⏳", "Лимиты приложений", "Дневное время и продление", P.amber)
            }
        }
        item {
            SoftCard(onClick = { navigate(Route.Tasks) }) {
                Bullet("🔔", "Привычки", "Зубы, душ и свои напоминания", P.violet)
            }
        }

        item { SectionTitle("Опасная зона") }
        item {
            SoftCard {
                Text("Очистить сад", style = MaterialTheme.typography.titleMedium, color = P.rose)
                Text(
                    "Удалит все ${garden.size} растений безвозвратно",
                    style = MaterialTheme.typography.bodySmall, color = e.faint,
                )
                Spacer(Modifier.height(12.dp))
                GhostButton(
                    if (confirmReset) "Точно удалить всё?" else "Очистить",
                    Modifier.fillMaxWidth(),
                    tint = P.rose,
                ) {
                    if (confirmReset) {
                        Store.clearGarden()
                        confirmReset = false
                    } else confirmReset = true
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Text("Продуктивность · 1.0", style = MaterialTheme.typography.labelSmall, color = e.faint)
            }
        }
    }
}

@Composable
fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val e = LocalExtra.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = e.faint)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = androidx.compose.ui.graphics.Color(0xFF061109),
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = e.faint,
                uncheckedTrackColor = e.cardHigh,
                uncheckedBorderColor = e.outline,
            ),
        )
    }
}
