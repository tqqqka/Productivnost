package com.tihon.productivnost.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tihon.productivnost.core.PlantKind
import com.tihon.productivnost.core.SessionPhase
import com.tihon.productivnost.core.Sessions
import com.tihon.productivnost.core.Store
import com.tihon.productivnost.service.SessionController
import com.tihon.productivnost.ui.Route
import com.tihon.productivnost.ui.components.Chip
import com.tihon.productivnost.ui.components.GhostButton
import com.tihon.productivnost.ui.components.PrimaryButton
import com.tihon.productivnost.ui.components.ProgressRing
import com.tihon.productivnost.ui.components.SectionTitle
import com.tihon.productivnost.ui.components.Segmented
import com.tihon.productivnost.ui.components.SoftCard
import com.tihon.productivnost.ui.components.rememberNow
import com.tihon.productivnost.ui.components.rememberResumeKey
import com.tihon.productivnost.ui.theme.LocalExtra
import com.tihon.productivnost.ui.theme.P
import com.tihon.productivnost.util.Fmt
import com.tihon.productivnost.util.Permissions
import com.tihon.productivnost.voxel.PlantGenerator
import com.tihon.productivnost.voxel.PlantModelCache
import com.tihon.productivnost.voxel.VoxelPlantView

@Composable
fun SessionsScreen(navigate: (Route) -> Unit) {
    val focus by Store.focus.collectAsStateWithLifecycle()
    val study by Store.study.collectAsStateWithLifecycle()
    var mode by remember { mutableIntStateOf(if (study != null) 1 else 0) }
    val effectiveMode = when {
        focus != null -> 0
        study != null -> 1
        else -> mode
    }

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("Сессии", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        }
        item {
            Segmented(
                listOf("Дерево фокуса", "Учёба"),
                effectiveMode,
                accent = if (effectiveMode == 0) P.leaf else P.sky,
                onSelect = { if (focus == null && study == null) mode = it },
            )
        }
        item {
            AnimatedContent(
                targetState = effectiveMode,
                transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(180)) },
                label = "mode",
            ) { m ->
                if (m == 0) FocusPane(navigate) else StudyPane(navigate)
            }
        }
    }
}

// --------------------------------------------------------------------- фокус

@Composable
private fun FocusPane(navigate: (Route) -> Unit) {
    val context = LocalContext.current
    val e = LocalExtra.current
    val session by Store.focus.collectAsStateWithLifecycle()
    val settings by Store.settings.collectAsStateWithLifecycle()
    val now = rememberNow(400L, session != null)

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        val s = session
        if (s != null) {
            val progress = Sessions.focusProgress(s, now)
            val model = remember(s.seed, s.species) { PlantModelCache.get(s.seed, PlantKind.FOCUS, s.species) }
            SoftCard(highlight = true, padding = PaddingValues(20.dp)) {
                VoxelPlantView(
                    model = model,
                    growth = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.05f),
                    verticalBias = 0.68f,
                )
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ProgressRing(progress, Modifier.size(168.dp), color = P.leaf, stroke = 9.dp) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                Fmt.clock(Sessions.focusRemaining(s, now)),
                                style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = e.faint)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Не открывай ничего, кроме разрешённых приложений — иначе росток засохнет.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = e.dim,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                GhostButton("Сдаться", Modifier.fillMaxWidth(), tint = P.rose) {
                    SessionController.giveUpFocus(context)
                }
            }
            SoftCard(onClick = { navigate(Route.AllowedApps) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Разрешённые приложения", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text("${settings.allowedApps.size} выбрано", style = MaterialTheme.typography.bodySmall, color = e.faint)
                    }
                    Text("›", style = MaterialTheme.typography.headlineSmall, color = e.faint)
                }
            }
            return@Column
        }

        var species by remember { mutableIntStateOf((0 until PlantGenerator.speciesCount(PlantKind.FOCUS)).random()) }
        var minutes by remember { mutableIntStateOf(settings.focusMinutes) }
        val trees = Sessions.treesFor(minutes)
        val model = remember(species) { PlantModelCache.get(1000L + species, PlantKind.FOCUS, species) }

        SoftCard(highlight = true, padding = PaddingValues(20.dp)) {
            VoxelPlantView(
                model = model,
                growth = 1f,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.1f),
                verticalBias = 0.68f,
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    PlantGenerator.paletteName(PlantKind.FOCUS, species),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoundIcon("‹") {
                        val n = PlantGenerator.speciesCount(PlantKind.FOCUS)
                        species = (species - 1 + n) % n
                    }
                    RoundIcon("›") {
                        species = (species + 1) % PlantGenerator.speciesCount(PlantKind.FOCUS)
                    }
                }
            }
            Text(
                "Крути пальцем, чтобы рассмотреть",
                style = MaterialTheme.typography.bodySmall,
                color = e.faint,
            )
        }

        SoftCard {
            SectionTitle("Сколько держимся")
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Sessions.focusOptions.forEach { opt ->
                    Chip(Fmt.duration(opt), minutes == opt) { minutes = opt }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Вырастет ${Fmt.trees(trees)} — по одному за каждые 30 минут.",
                style = MaterialTheme.typography.bodyMedium,
                color = e.dim,
            )
        }

        SoftCard(onClick = { navigate(Route.AllowedApps) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Разрешённые приложения", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        if (settings.allowedApps.isEmpty()) "ничего не выбрано — открыть можно только звонилку"
                        else "${settings.allowedApps.size} выбрано",
                        style = MaterialTheme.typography.bodySmall, color = e.faint,
                    )
                }
                Text("›", style = MaterialTheme.typography.headlineSmall, color = e.faint)
            }
        }

        val resumeKey = rememberResumeKey()
        val ready = remember(resumeKey) { Permissions.hasUsageAccess(context) && Permissions.hasOverlay(context) }
        if (!ready) {
            SoftCard(onClick = { navigate(Route.Access) }) {
                Text("Сначала выдай разрешения", style = MaterialTheme.typography.titleMedium, color = P.amber)
                Text(
                    "Без доступа к статистике приложение не увидит, что ты открыл что-то запретное",
                    style = MaterialTheme.typography.bodySmall, color = e.faint,
                )
            }
        }
        PrimaryButton("Посадить семечко", Modifier.fillMaxWidth(), enabled = ready) {
            SessionController.startFocus(context, minutes, species)
        }
    }
}

// --------------------------------------------------------------------- учёба

@Composable
private fun StudyPane(navigate: (Route) -> Unit) {
    val context = LocalContext.current
    val e = LocalExtra.current
    val session by Store.study.collectAsStateWithLifecycle()
    val settings by Store.settings.collectAsStateWithLifecycle()
    val now = rememberNow(400L, session != null)

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        val s = session
        if (s != null) {
            val work = s.phase == SessionPhase.WORK
            val accent = if (work) P.sky else P.amber
            val model = remember(s.seed) { PlantModelCache.get(s.seed, PlantKind.STUDY, 0) }
            SoftCard(highlight = true, padding = PaddingValues(20.dp)) {
                VoxelPlantView(
                    model = model,
                    growth = if (work) Sessions.studyPhaseProgress(s, now) else 1f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.05f),
                    verticalBias = 0.68f,
                )
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ProgressRing(Sessions.studyPhaseProgress(s, now), Modifier.size(168.dp), color = accent, stroke = 9.dp) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                if (work) "РАБОТА" else "ПЕРЕРЫВ",
                                style = MaterialTheme.typography.labelSmall,
                                color = accent,
                            )
                            Text(
                                Fmt.clock(Sessions.studyPhaseRemaining(s, now)),
                                style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (s.completedBlocks == 0) {
                        Text("Первый блок ещё не закрыт", style = MaterialTheme.typography.bodyMedium, color = e.faint)
                    } else {
                        Text(
                            "Закрыто блоков: ${s.completedBlocks} · это ${Fmt.trees(s.completedBlocks)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = e.dim,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (work) "Пока идёт работа, чужие приложения закрыты заслонкой."
                    else "Перерыв: можно листать что угодно.",
                    style = MaterialTheme.typography.bodySmall,
                    color = e.faint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                PrimaryButton("Завершить сессию", Modifier.fillMaxWidth(), color = accent) {
                    SessionController.finishStudy(context)
                }
            }
            return@Column
        }

        var work by remember { mutableIntStateOf(settings.studyWorkMinutes) }
        var brk by remember { mutableIntStateOf(settings.studyBreakMinutes) }
        val model = remember { PlantModelCache.get(42L, PlantKind.STUDY, 0) }

        SoftCard(highlight = true, padding = PaddingValues(20.dp)) {
            VoxelPlantView(
                model = model,
                growth = 1f,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.1f),
                verticalBias = 0.68f,
            )
            Text("Дерево учёбы", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                "Растёт с книгами у корней. По одному за каждый закрытый блок работы.",
                style = MaterialTheme.typography.bodySmall,
                color = e.faint,
            )
        }

        SoftCard {
            SectionTitle("Работа")
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(15, 25, 30, 45, 50).forEach { Chip("$it мин", work == it, accent = P.sky) { work = it } }
            }
            Spacer(Modifier.height(16.dp))
            SectionTitle("Перерыв")
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(3, 5, 10, 15).forEach { Chip("$it мин", brk == it, accent = P.amber) { brk = it } }
            }
        }

        SoftCard {
            Text("Как это работает", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))
            listOf(
                "Во время работы всё, кроме разрешённых приложений, закрыто заслонкой",
                "В перерыв можно пользоваться телефоном свободно",
                "Каждый закрытый блок по $work мин — одно дерево учёбы",
                "Сессия идёт, пока сам не нажмёшь «Завершить»",
            ).forEach {
                Row(Modifier.padding(vertical = 4.dp)) {
                    Text("·  ", style = MaterialTheme.typography.bodyMedium, color = P.sky)
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = e.dim)
                }
            }
        }

        SoftCard(onClick = { navigate(Route.AllowedApps) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Разрешённые приложения", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text("${settings.allowedApps.size} выбрано", style = MaterialTheme.typography.bodySmall, color = e.faint)
                }
                Text("›", style = MaterialTheme.typography.headlineSmall, color = e.faint)
            }
        }

        val resumeKey = rememberResumeKey()
        val ready = remember(resumeKey) { Permissions.hasUsageAccess(context) && Permissions.hasOverlay(context) }
        PrimaryButton("Начать сессию учёбы", Modifier.fillMaxWidth(), color = P.sky, enabled = ready) {
            SessionController.startStudy(context, work, brk)
        }
        if (!ready) {
            GhostButton("Выдать разрешения", Modifier.fillMaxWidth()) { navigate(Route.Access) }
        }
    }
}

@Composable
private fun RoundIcon(symbol: String, onClick: () -> Unit) {
    val e = LocalExtra.current
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(e.cardHigh)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Text(symbol, style = MaterialTheme.typography.titleLarge, color = e.dim) }
}
