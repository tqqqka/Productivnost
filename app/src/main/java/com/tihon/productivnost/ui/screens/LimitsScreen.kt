package com.tihon.productivnost.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tihon.productivnost.core.AppLimit
import com.tihon.productivnost.core.Store
import com.tihon.productivnost.ui.ScreenHeader
import com.tihon.productivnost.ui.components.AppIcon
import com.tihon.productivnost.ui.components.Chip
import com.tihon.productivnost.ui.components.SoftCard
import com.tihon.productivnost.ui.theme.LocalExtra
import com.tihon.productivnost.ui.theme.P
import com.tihon.productivnost.util.AppInfo
import com.tihon.productivnost.util.Apps
import com.tihon.productivnost.util.Fmt

@Composable
fun LimitsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val e = LocalExtra.current
    val limits by Store.limits.collectAsStateWithLifecycle()
    val usage by Store.usage.collectAsStateWithLifecycle()
    val settings by Store.settings.collectAsStateWithLifecycle()
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { apps = Apps.launchable(context) }

    val ordered = remember(apps, limits, query) {
        val withLimit = limits.map { it.pkg }.toSet()
        apps.filter { query.isBlank() || it.label.contains(query, true) }
            .sortedWith(compareByDescending<AppInfo> { it.pkg in withLimit }.thenBy { it.label.lowercase() })
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            ScreenHeader(
                "Лимиты приложений",
                "Когда дневное время кончится, появится заслонка. " +
                    "Продлить можно отжиманиями: одно — ${settings.minutesPerPushup} мин.",
                onBack,
            )
        }
        item { SearchField(query, e.card, e.outline, e.faint) { query = it } }

        items(ordered, key = { it.pkg }) { app ->
            val limit = limits.firstOrNull { it.pkg == app.pkg }
            val used = usage.perApp[app.pkg] ?: 0L
            val bonus = usage.bonusMs[app.pkg] ?: 0L
            val open = expanded == app.pkg
            SoftCard(onClick = { expanded = if (open) null else app.pkg }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(app.pkg, Modifier.size(36.dp))
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text(app.label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            when {
                                limit == null -> "без лимита · сегодня ${Fmt.minutes(used)}"
                                else -> "лимит ${Fmt.duration(limit.dailyMinutes)}" +
                                    (if (bonus > 0) " +${Fmt.minutes(bonus)}" else "") +
                                    " · использовано ${Fmt.minutes(used)}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (limit != null && used >= limit.dailyMinutes * 60_000L + bonus) P.rose else e.faint,
                        )
                    }
                    if (limit != null) {
                        Box(
                            Modifier
                                .clip(MaterialTheme.shapes.small)
                                .background(P.amber.copy(alpha = 0.16f))
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        ) {
                            Text(
                                Fmt.duration(limit.dailyMinutes),
                                style = MaterialTheme.typography.labelSmall,
                                color = P.amber,
                            )
                        }
                    }
                }
                AnimatedVisibility(open) {
                    Column {
                        Spacer(Modifier.height(14.dp))
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Chip("Без лимита", limit == null, accent = P.leaf) {
                                Store.updateLimits { l -> l.filterNot { it.pkg == app.pkg } }
                            }
                            listOf(10, 15, 30, 60, 90, 120, 180).forEach { m ->
                                Chip("$m мин", limit?.dailyMinutes == m, accent = P.amber) {
                                    Store.updateLimits { l ->
                                        l.filterNot { it.pkg == app.pkg } + AppLimit(app.pkg, m)
                                    }
                                }
                            }
                        }
                        if (bonus > 0) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Начислено отжиманиями сегодня: ${Fmt.minutes(bonus)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = P.leaf,
                            )
                        }
                    }
                }
            }
        }
    }
}
