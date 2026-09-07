package com.tihon.productivnost.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tihon.productivnost.core.PlantKind
import com.tihon.productivnost.core.SessionPhase
import com.tihon.productivnost.core.Sessions
import com.tihon.productivnost.core.Store
import com.tihon.productivnost.ui.Route
import com.tihon.productivnost.ui.components.Bullet
import com.tihon.productivnost.ui.components.Divider
import com.tihon.productivnost.ui.components.PrimaryButton
import com.tihon.productivnost.ui.components.ProgressRing
import com.tihon.productivnost.ui.components.SectionTitle
import com.tihon.productivnost.ui.components.SoftCard
import com.tihon.productivnost.ui.components.StatTile
import com.tihon.productivnost.ui.components.rememberNow
import com.tihon.productivnost.ui.components.rememberResumeKey
import com.tihon.productivnost.ui.theme.LocalExtra
import com.tihon.productivnost.ui.theme.P
import com.tihon.productivnost.util.Fmt
import com.tihon.productivnost.util.Permissions
import com.tihon.productivnost.voxel.PlantModelCache
import com.tihon.productivnost.voxel.VoxelPlantView
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HomeScreen(navigate: (Route) -> Unit) {
    val context = LocalContext.current
    val e = LocalExtra.current
    val garden by Store.garden.collectAsStateWithLifecycle()
    val usage by Store.usage.collectAsStateWithLifecycle()
    val tasks by Store.tasks.collectAsStateWithLifecycle()
    val focus by Store.focus.collectAsStateWithLifecycle()
    val study by Store.study.collectAsStateWithLifecycle()
    val now = rememberNow(500L, focus != null || study != null)

    val resumeKey = rememberResumeKey()
    val accessOk = remember(resumeKey) {
        Permissions.hasUsageAccess(context) && Permissions.hasOverlay(context)
    }
    val today = Store.today()
    val due = tasks.filter { it.enabled && it.dueToday(today) }.sortedBy { it.minuteOfDay }

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column {
                Text(greeting(), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground)
                Text(dateLine(), style = MaterialTheme.typography.bodyMedium, color = e.faint, modifier = Modifier.padding(top = 4.dp))
            }
        }

        item {
            AnimatedVisibility(!accessOk, enter = fadeIn(tween(300)), exit = fadeOut(tween(200))) {
                SoftCard(onClick = { navigate(Route.Access) }) {
                    Bullet("⚙️", "Нужны разрешения", "Без них напоминания и заслонка не сработают", P.amber)
                }
            }
        }

        item {
            when {
                focus != null -> {
                    val s = focus!!
                    val progress = Sessions.focusProgress(s, now)
                    val model = remember(s.seed, s.species) { PlantModelCache.get(s.seed, PlantKind.FOCUS, s.species) }
                    SoftCard(onClick = { navigate(Route.Sessions) }, highlight = true) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            VoxelPlantView(
                                model = model,
                                growth = progress,
                                modifier = Modifier.size(112.dp),
                                interactive = false,
                                verticalBias = 0.62f,
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Дерево растёт", style = MaterialTheme.typography.titleLarge, color = P.leaf)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    Fmt.clock(Sessions.focusRemaining(s, now)),
                                    style = MaterialTheme.typography.displaySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    "в конце — ${Fmt.trees(s.trees)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = e.faint,
                                )
                            }
                        }
                    }
                }
                study != null -> {
                    val s = study!!
                    val work = s.phase == SessionPhase.WORK
                    SoftCard(onClick = { navigate(Route.Sessions) }, highlight = true) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ProgressRing(
                                progress = Sessions.studyPhaseProgress(s, now),
                                modifier = Modifier.size(96.dp),
                                color = if (work) P.sky else P.amber,
                                stroke = 7.dp,
                            ) {
                                Text(if (work) "📚" else "☕", style = MaterialTheme.typography.headlineSmall)
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (work) "Учёба · работа" else "Учёба · перерыв",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = if (work) P.sky else P.amber,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    Fmt.clock(Sessions.studyPhaseRemaining(s, now)),
                                    style = MaterialTheme.typography.displaySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    "блоков закрыто: ${s.completedBlocks}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = e.faint,
                                )
                            }
                        }
                    }
                }
                else -> {
                    SoftCard(highlight = true) {
                        val model = remember { PlantModelCache.get(7L, PlantKind.FOCUS, 0) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            VoxelPlantView(
                                model = model,
                                growth = 1f,
                                modifier = Modifier.size(104.dp),
                                interactive = false,
                                verticalBias = 0.62f,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Посади дерево", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "30 минут без телефона — одно дерево в саду навсегда",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = e.faint,
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        PrimaryButton("Начать сессию", Modifier.fillMaxWidth()) { navigate(Route.Sessions) }
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(Fmt.minutes(usage.activeUseMs), "в телефоне сегодня", P.amber, Modifier.weight(1f))
                StatTile("${garden.size}", "растений в саду", P.leaf, Modifier.weight(1f))
                StatTile("${usage.pushupsToday}", "отжиманий", P.sky, Modifier.weight(1f))
            }
        }

        if (due.isNotEmpty()) {
            item { SectionTitle("Сегодня") }
            item {
                SoftCard(onClick = { navigate(Route.Tasks) }) {
                    due.forEachIndexed { i, task ->
                        val done = task.lastFiredDay == today
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Box(Modifier.weight(1f)) {
                                Bullet(
                                    task.icon,
                                    task.title,
                                    if (done) "уже напомнили" else "в ${Fmt.timeOfDay(task.minuteOfDay)}",
                                    if (done) P.leafDeep else P.sky,
                                )
                            }
                            if (done) {
                                Box(
                                    Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(P.leaf.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center,
                                ) { Text("✓", style = MaterialTheme.typography.labelMedium, color = P.leaf) }
                            }
                        }
                        if (i != due.lastIndex) {
                            Spacer(Modifier.height(12.dp))
                            Divider()
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
            }
        }

        item { SectionTitle("Быстро") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SoftCard(Modifier.weight(1f), padding = PaddingValues(16.dp), onClick = { navigate(Route.More) }) {
                    Text("💪", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text("Отжимания", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text("продлить приложение", style = MaterialTheme.typography.bodySmall, color = e.faint)
                }
                SoftCard(Modifier.weight(1f), padding = PaddingValues(16.dp), onClick = { navigate(Route.Cleanup) }) {
                    Text("🗂️", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text("Чистка фото", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text("дубликаты и серии", style = MaterialTheme.typography.bodySmall, color = e.faint)
                }
            }
        }
    }
}

private fun greeting(): String {
    val h = LocalTime.now().hour
    return when (h) {
        in 5..11 -> "Доброе утро"
        in 12..17 -> "Добрый день"
        in 18..22 -> "Добрый вечер"
        else -> "Доброй ночи"
    }
}

private fun dateLine(): String {
    val d = LocalDate.now()
    val month = d.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale("ru"))
    val week = d.dayOfWeek.getDisplayName(TextStyle.FULL_STANDALONE, Locale("ru"))
    return "${d.dayOfMonth} $month, $week".replaceFirstChar { it.lowercase() }
}
