package com.tihon.productivnost.ui.screens

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tihon.productivnost.core.Plant
import com.tihon.productivnost.core.PlantKind
import com.tihon.productivnost.core.Store
import com.tihon.productivnost.ui.Route
import com.tihon.productivnost.ui.components.Chip
import com.tihon.productivnost.ui.components.StatTile
import com.tihon.productivnost.ui.theme.LocalExtra
import com.tihon.productivnost.ui.theme.P
import com.tihon.productivnost.util.Fmt
import com.tihon.productivnost.voxel.PlantModelCache
import com.tihon.productivnost.voxel.VoxelPlantView
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
        }.sortedByDescending { it.plantedAt }
    }
    val totalMinutes = remember(garden) { garden.sumOf { it.minutes } }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpanMax() }) {
            Column(Modifier.padding(horizontal = 4.dp)) {
                Text("Сад", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    "Всё, что выросло, остаётся здесь навсегда",
                    style = MaterialTheme.typography.bodyMedium,
                    color = e.faint,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("${garden.size}", "растений", P.leaf, Modifier.weight(1f))
                    StatTile(Fmt.duration(totalMinutes), "сосредоточенности", P.sky, Modifier.weight(1f))
                }
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Chip("Все · ${garden.size}", filter == 0) { filter = 0 }
                    Chip("Фокус · ${garden.count { it.kind == PlantKind.FOCUS }}", filter == 1) { filter = 1 }
                    Chip(
                        "Учёба · ${garden.count { it.kind == PlantKind.STUDY }}",
                        filter == 2, accent = P.sky,
                    ) { filter = 2 }
                }
                Spacer(Modifier.height(6.dp))
            }
        }

        if (shown.isEmpty()) {
            item(span = { GridItemSpanMax() }) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 60.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("🌱", style = MaterialTheme.typography.displayMedium)
                    Spacer(Modifier.height(12.dp))
                    Text("Здесь пока пусто", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "Проведи 30 минут без телефона — и вырастет первое дерево",
                        style = MaterialTheme.typography.bodyMedium,
                        color = e.faint,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp, start = 30.dp, end = 30.dp),
                    )
                }
            }
        }

        items(shown, key = { it.id }) { plant ->
            PlantCell(plant) { navigate(Route.PlantDetail(plant.id)) }
        }
    }
}

@Composable
private fun PlantCell(plant: Plant, onClick: () -> Unit) {
    val e = LocalExtra.current
    val model = remember(plant.id) { PlantModelCache.get(plant) }
    Column(
        Modifier
            .clip(MaterialTheme.shapes.medium)
            .background(e.card)
            .border(1.dp, e.outline, MaterialTheme.shapes.medium)
            .clickable { onClick() }
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        VoxelPlantView(
            model = model,
            growth = 1f,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            interactive = false,
            padding = 0.04f,
            verticalBias = 0.62f,
            renderScale = 0.62f,
        )
        Text(
            "${plant.minutes} мин",
            style = MaterialTheme.typography.labelSmall,
            color = if (plant.kind == PlantKind.STUDY) P.sky else e.faint,
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }
}

private fun GridItemSpanMax() = androidx.compose.foundation.lazy.grid.GridItemSpan(3)

fun plantDate(at: Long): String = DateTimeFormatter
    .ofPattern("d MMMM yyyy, HH:mm")
    .withZone(ZoneId.systemDefault())
    .format(Instant.ofEpochMilli(at))
