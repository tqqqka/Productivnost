package com.tihon.productivnost.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tihon.productivnost.core.Store
import com.tihon.productivnost.feature.pushups.PushupActivity
import com.tihon.productivnost.ui.Route
import com.tihon.productivnost.ui.components.Bullet
import com.tihon.productivnost.ui.components.SectionTitle
import com.tihon.productivnost.ui.components.SoftCard
import com.tihon.productivnost.ui.components.rememberResumeKey
import com.tihon.productivnost.ui.theme.LocalExtra
import com.tihon.productivnost.ui.theme.P
import com.tihon.productivnost.util.Fmt
import com.tihon.productivnost.util.Permissions

@Composable
fun MoreScreen(navigate: (Route) -> Unit) {
    val context = LocalContext.current
    val e = LocalExtra.current
    val usage by Store.usage.collectAsStateWithLifecycle()
    val settings by Store.settings.collectAsStateWithLifecycle()
    val limits by Store.limits.collectAsStateWithLifecycle()
    val resumeKey = rememberResumeKey()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Ещё", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        }

        item { SectionTitle("Инструменты") }
        item {
            SoftCard(onClick = { context.startActivity(PushupActivity.intent(context, null)) }) {
                Bullet(
                    "💪", "Отжимания",
                    "1 отжимание = ${settings.minutesPerPushup} мин · сегодня ${usage.pushupsToday}",
                    P.leaf,
                )
            }
        }
        item {
            SoftCard(onClick = { navigate(Route.Cleanup) }) {
                Bullet("🗂️", "Чистка фотографий", "Дубликаты, серии кадров, скриншоты", P.sky)
            }
        }

        item { SectionTitle("Правила") }
        item {
            SoftCard(onClick = { navigate(Route.AllowedApps) }) {
                Bullet("✅", "Разрешённые приложения", "${settings.allowedApps.size} выбрано", P.leaf)
            }
        }
        item {
            SoftCard(onClick = { navigate(Route.Limits) }) {
                Bullet(
                    "⏳", "Лимиты приложений",
                    if (limits.isEmpty()) "не заданы" else "${limits.size} с лимитом · продление отжиманиями",
                    P.amber,
                )
            }
        }
        item {
            SoftCard(onClick = { navigate(Route.Tasks) }) {
                Bullet("🔔", "Напоминания и привычки", "Зубы, душ, свои задачи", P.violet)
            }
        }

        item { SectionTitle("Приложение") }
        item {
            SoftCard(onClick = { navigate(Route.Settings) }) {
                Bullet("⚙️", "Настройки", "Интервалы, звук, ночной режим", P.textDim)
            }
        }
        item {
            val ok = remember(resumeKey) { Permissions.hasUsageAccess(context) && Permissions.hasOverlay(context) }
            SoftCard(onClick = { navigate(Route.Access) }) {
                Bullet(
                    if (ok) "🛡️" else "⚠️",
                    "Разрешения",
                    if (ok) "всё на месте" else "чего-то не хватает",
                    if (ok) P.leaf else P.amber,
                )
            }
        }
        item {
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Text(
                    "Экранное время сегодня — ${Fmt.minutes(usage.activeUseMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = e.faint,
                )
            }
        }
    }
}
