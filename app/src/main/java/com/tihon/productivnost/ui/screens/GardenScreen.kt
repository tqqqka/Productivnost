package com.tihon.productivnost.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tihon.productivnost.core.PlantKind
import com.tihon.productivnost.core.Store
import com.tihon.productivnost.ui.Route
import com.tihon.productivnost.ui.components.Chip
import com.tihon.productivnost.ui.components.StatTile
import com.tihon.productivnost.ui.theme.LocalExtra
import com.tihon.productivnost.ui.theme.P
import com.tihon.productivnost.util.Fmt
import com.tihon.productivnost.voxel.VoxelGardenScene
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun GardenScreen(navigate: (Route) -> Unit) {
    val e = LocalExtra.current
    val garden by Store.garden.collectAsStateWithLifecycle()
    var filter by remember { mutableIntStateOf(0) }

    val shown = remember(garden, filter) {
        when (filter) {
            1 -> garden.filter { it.kind == PlantKind.FOCUS }
            2 -> garden.filter { it.kind == PlantKind.STUDY }
            else -> garden
        }.sortedBy { it.plantedAt }
    }
    val totalMinutes = remember(garden) { garden.sumOf { it.minutes } }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
            Text("Сад", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            Text(
                "Всё, что выросло за всё время, стоит здесь",
                style = MaterialTheme.typography.bodyMedium,
                color = e.faint,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("${garden.size}", "растений", P.leaf, Modifier.weight(1f))
                StatTile(Fmt.duration(totalMinutes), "сосредоточенности", P.sky, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Chip("Все · ${garden.size}", filter == 0) { filter = 0 }
                Chip("Фокус · ${garden.count { it.kind == PlantKind.FOCUS }}", filter == 1) { filter = 1 }
                Chip("Учёба · ${garden.count { it.kind == PlantKind.STUDY }}", filter == 2, accent = P.sky) { filter = 2 }
            }
        }

        Box(Modifier.weight(1f)) {
            VoxelGardenScene(
                plants = shown,
                modifier = Modifier.fillMaxSize(),
                onSelect = { navigate(Route.PlantDetail(it.id)) },
            )
            if (garden.isEmpty()) {
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Грядка ждёт", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "Проведи 30 минут без телефона — и первое дерево займёт свою клетку",
                        style = MaterialTheme.typography.bodyMedium,
                        color = e.faint,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp, start = 40.dp, end = 40.dp),
                    )
                }
            }
            Text(
                "щипок — приблизить · тап по дереву — подробности",
                style = MaterialTheme.typography.labelSmall,
                color = e.faint,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 96.dp),
            )
        }
    }
}

fun plantDate(at: Long): String = DateTimeFormatter
    .ofPattern("d MMMM yyyy, HH:mm")
    .withZone(ZoneId.systemDefault())
    .format(Instant.ofEpochMilli(at))
