package com.tihon.productivnost.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tihon.productivnost.core.PlantKind
import com.tihon.productivnost.core.Store
import com.tihon.productivnost.ui.ScreenHeader
import com.tihon.productivnost.ui.components.SoftCard
import com.tihon.productivnost.ui.theme.LocalExtra
import com.tihon.productivnost.ui.theme.P
import com.tihon.productivnost.util.Fmt
import com.tihon.productivnost.voxel.PlantGenerator
import com.tihon.productivnost.voxel.PlantModelCache
import com.tihon.productivnost.voxel.VoxelPlantView

@Composable
fun PlantDetailScreen(id: Long, onBack: () -> Unit) {
    val e = LocalExtra.current
    val garden by Store.garden.collectAsStateWithLifecycle()
    val plant = remember(garden, id) { garden.firstOrNull { it.id == id } }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (plant == null) {
            ScreenHeader("Растение не найдено", onBack = onBack)
            return@Column
        }
        val name = PlantGenerator.paletteName(plant.kind, plant.species)
        ScreenHeader(
            name,
            if (plant.kind == PlantKind.STUDY) "Дерево учёбы" else "Дерево фокуса",
            onBack,
        )
        val model = remember(plant.id) { PlantModelCache.get(plant) }
        SoftCard(highlight = true, padding = PaddingValues(14.dp)) {
            VoxelPlantView(
                model = model,
                growth = 1f,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.94f),
                verticalBias = 0.68f,
            )
            Text(
                "Проведи пальцем, чтобы покрутить",
                style = MaterialTheme.typography.bodySmall,
                color = e.faint,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        SoftCard {
            InfoRow("Выросло", plantDate(plant.plantedAt))
            Spacer(Modifier.height(12.dp))
            InfoRow("Стоило", Fmt.duration(plant.minutes) + " без телефона")
            Spacer(Modifier.height(12.dp))
            InfoRow("Вид", name)
            Spacer(Modifier.height(12.dp))
            InfoRow("Семечко", "#" + (plant.seed.toULong() % 100000u).toString().padStart(5, '0'))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val e = LocalExtra.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = e.faint)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
