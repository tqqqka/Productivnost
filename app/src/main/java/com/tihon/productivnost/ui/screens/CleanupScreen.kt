package com.tihon.productivnost.ui.screens

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tihon.productivnost.feature.cleanup.PhotoDeleter
import com.tihon.productivnost.feature.cleanup.PhotoGroup
import com.tihon.productivnost.feature.cleanup.PhotoItem
import com.tihon.productivnost.feature.cleanup.PhotoScanner
import com.tihon.productivnost.feature.cleanup.ScanResult
import com.tihon.productivnost.feature.cleanup.rememberThumbnail
import com.tihon.productivnost.ui.ScreenHeader
import com.tihon.productivnost.ui.components.GhostButton
import com.tihon.productivnost.ui.components.PrimaryButton
import com.tihon.productivnost.ui.components.SoftCard
import com.tihon.productivnost.ui.theme.LocalExtra
import com.tihon.productivnost.ui.theme.P
import com.tihon.productivnost.util.Fmt
import com.tihon.productivnost.util.Permissions
import kotlinx.coroutines.launch

private enum class Category { NONE, DUPLICATES, SIMILAR, SCREENSHOTS, HEAVY }

@Composable
fun CleanupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val e = LocalExtra.current
    val scope = rememberCoroutineScope()

    var granted by remember { mutableStateOf(Permissions.hasMedia(context)) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    var scanning by remember { mutableStateOf(false) }
    var done by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<ScanResult?>(null) }
    var category by remember { mutableStateOf(Category.NONE) }
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var freed by remember { mutableStateOf(0L) }

    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == android.app.Activity.RESULT_OK) {
            val res = result
            if (res != null) {
                freed += bytesOf(res, selected)
                result = removeDeleted(res, selected)
                selected = emptySet()
            }
        }
    }

    fun startScan() {
        if (scanning) return
        scanning = true
        done = 0; total = 0
        scope.launch {
            val r = PhotoScanner.scan(context) { d, t -> done = d; total = t }
            result = r
            scanning = false
        }
    }

    fun deleteSelected() {
        val res = result ?: return
        val uris = urisOf(res, selected)
        if (uris.isEmpty()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val sender = PhotoDeleter.deleteRequest(context, uris)
            if (sender != null) deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
        } else {
            freed += bytesOf(res, selected)
            PhotoDeleter.deleteDirect(context, uris)
            result = removeDeleted(res, selected)
            selected = emptySet()
        }
    }

    AnimatedContent(
        targetState = category,
        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
        label = "cleanup",
    ) { cat ->
        if (cat == Category.NONE) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    ScreenHeader(
                        "Чистка фотографий",
                        "Ищем повторы, серии почти одинаковых кадров и лишний вес",
                        onBack,
                    )
                }
                if (!granted) {
                    item {
                        SoftCard {
                            Text("Нужен доступ к галерее", style = MaterialTheme.typography.titleMedium, color = P.amber)
                            Text(
                                "Фотографии не покидают телефон — всё считается на месте",
                                style = MaterialTheme.typography.bodySmall, color = e.faint,
                            )
                            Spacer(Modifier.height(12.dp))
                            PrimaryButton("Разрешить", Modifier.fillMaxWidth()) {
                                permLauncher.launch(Permissions.mediaPermission())
                            }
                        }
                    }
                } else if (scanning) {
                    item {
                        SoftCard {
                            Text("Сканирую галерею", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { if (total > 0) done / total.toFloat() else 0f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(CircleShape),
                                color = P.leaf,
                                trackColor = e.outline,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("$done из $total", style = MaterialTheme.typography.bodySmall, color = e.faint)
                        }
                    }
                } else {
                    val res = result
                    if (res == null) {
                        item {
                            SoftCard {
                                Text("🗂️", style = MaterialTheme.typography.displaySmall)
                                Spacer(Modifier.height(10.dp))
                                Text("Готов посмотреть", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    "Разбираем до 3000 последних снимков. Ничего не удаляется без твоего подтверждения.",
                                    style = MaterialTheme.typography.bodySmall, color = e.faint,
                                )
                                Spacer(Modifier.height(14.dp))
                                PrimaryButton("Сканировать", Modifier.fillMaxWidth()) { startScan() }
                            }
                        }
                    } else {
                        if (freed > 0) {
                            item {
                                SoftCard {
                                    Text("Освобождено ${Fmt.bytes(freed)}", style = MaterialTheme.typography.titleMedium, color = P.leaf)
                                }
                            }
                        }
                        item {
                            CategoryCard("Точные дубликаты", "Один и тот же кадр несколько раз",
                                res.duplicates.sumOf { it.extras.size }, res.duplicateBytes, P.rose) {
                                selected = res.duplicates.flatMap { g -> g.extras.map { it.id } }.toSet()
                                category = Category.DUPLICATES
                            }
                        }
                        item {
                            CategoryCard("Похожие серии", "Снято подряд, отличается едва заметно",
                                res.similar.sumOf { it.extras.size }, res.similarBytes, P.amber) {
                                selected = res.similar.flatMap { g -> g.extras.map { it.id } }.toSet()
                                category = Category.SIMILAR
                            }
                        }
                        item {
                            CategoryCard("Скриншоты", "Обычно нужны один раз",
                                res.screenshots.size, res.screenshotBytes, P.sky) {
                                selected = emptySet()
                                category = Category.SCREENSHOTS
                            }
                        }
                        item {
                            CategoryCard("Тяжёлые файлы", "Больше 8 МБ каждый",
                                res.heavy.size, res.heavyBytes, P.violet) {
                                selected = emptySet()
                                category = Category.HEAVY
                            }
                        }
                        item {
                            GhostButton("Сканировать заново", Modifier.fillMaxWidth()) { startScan() }
                        }
                    }
                }
            }
        } else {
            val res = result
            if (res == null) {
                LaunchedEffect(Unit) { category = Category.NONE }
            } else {
                CategoryDetail(
                    category = cat,
                    result = res,
                    selected = selected,
                    onToggle = { id -> selected = if (id in selected) selected - id else selected + id },
                    onSelectAll = { ids -> selected = ids },
                    onBack = { category = Category.NONE; selected = emptySet() },
                    onDelete = { deleteSelected() },
                )
            }
        }
    }
}

@Composable
private fun CategoryCard(title: String, subtitle: String, count: Int, bytes: Long, tint: Color, onClick: () -> Unit) {
    val e = LocalExtra.current
    SoftCard(onClick = if (count > 0) onClick else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = e.faint)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("$count", style = MaterialTheme.typography.headlineSmall, color = if (count > 0) tint else e.faint)
                Text(Fmt.bytes(bytes), style = MaterialTheme.typography.labelSmall, color = e.faint)
            }
        }
    }
}

@Composable
private fun CategoryDetail(
    category: Category,
    result: ScanResult,
    selected: Set<Long>,
    onToggle: (Long) -> Unit,
    onSelectAll: (Set<Long>) -> Unit,
    onBack: () -> Unit,
    onDelete: () -> Unit,
) {
    val e = LocalExtra.current
    val groups: List<PhotoGroup> = when (category) {
        Category.DUPLICATES -> result.duplicates
        Category.SIMILAR -> result.similar
        else -> emptyList()
    }
    val flat: List<PhotoItem> = when (category) {
        Category.SCREENSHOTS -> result.screenshots
        Category.HEAVY -> result.heavy
        else -> emptyList()
    }
    val title = when (category) {
        Category.DUPLICATES -> "Точные дубликаты"
        Category.SIMILAR -> "Похожие серии"
        Category.SCREENSHOTS -> "Скриншоты"
        Category.HEAVY -> "Тяжёлые файлы"
        Category.NONE -> ""
    }
    val allIds = if (groups.isNotEmpty()) groups.flatMap { g -> g.extras.map { it.id } }.toSet()
    else flat.map { it.id }.toSet()

    Box(Modifier.fillMaxSize()) {
        if (groups.isNotEmpty()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    ScreenHeader(title, "Лучший кадр в каждой группе отмечен и остаётся", onBack)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GhostButton("Выбрать все лишние", tint = P.leaf) { onSelectAll(allIds) }
                        GhostButton("Снять всё") { onSelectAll(emptySet()) }
                    }
                }
                items(groups, key = { it.key }) { group ->
                    SoftCard(padding = PaddingValues(12.dp)) {
                        Text(
                            "${group.items.size} кадра · можно освободить ${Fmt.bytes(group.reclaimable)}",
                            style = MaterialTheme.typography.bodySmall, color = e.faint,
                            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp),
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(group.items, key = { it.id }) { item ->
                                PhotoTile(
                                    item = item,
                                    checked = item.id in selected,
                                    keep = item.id == group.keep.id,
                                    modifier = Modifier.size(104.dp),
                                    onClick = { if (item.id != group.keep.id) onToggle(item.id) },
                                )
                            }
                        }
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 110.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                    Column(Modifier.padding(horizontal = 4.dp)) {
                        ScreenHeader(title, "Отметь всё, что не жалко", onBack)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GhostButton("Выбрать всё", tint = P.leaf) { onSelectAll(allIds) }
                            GhostButton("Снять всё") { onSelectAll(emptySet()) }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
                items(flat, key = { it.id }) { item ->
                    PhotoTile(
                        item = item,
                        checked = item.id in selected,
                        keep = false,
                        modifier = Modifier.aspectRatio(1f),
                        onClick = { onToggle(item.id) },
                    )
                }
            }
        }

        if (selected.isNotEmpty()) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(20.dp)
                    .fillMaxWidth(),
            ) {
                PrimaryButton(
                    "Удалить ${selected.size} · ${Fmt.bytes(bytesOf(result, selected))}",
                    Modifier.fillMaxWidth(),
                    color = P.rose,
                    onClick = onDelete,
                )
            }
        }
    }
}

@Composable
private fun PhotoTile(
    item: PhotoItem,
    checked: Boolean,
    keep: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val e = LocalExtra.current
    val thumb = rememberThumbnail(context, item.uri, 192)
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(e.cardHigh)
            .border(
                2.dp,
                when {
                    keep -> P.leaf
                    checked -> P.rose
                    else -> Color.Transparent
                },
                RoundedCornerShape(14.dp),
            )
            .clickable { onClick() },
    ) {
        if (thumb != null) {
            Image(thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        if (checked) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(P.rose.copy(alpha = 0.28f)),
            )
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(
                    when {
                        keep -> P.leaf
                        checked -> P.rose
                        else -> Color(0x99000000)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (keep) "★" else if (checked) "✓" else "",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF0A0F0D),
            )
        }
        Text(
            Fmt.bytes(item.size),
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xCCFFFFFF),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(5.dp),
        )
    }
}

private fun allItems(res: ScanResult): List<PhotoItem> =
    res.duplicates.flatMap { it.items } + res.similar.flatMap { it.items } + res.screenshots + res.heavy

private fun urisOf(res: ScanResult, ids: Set<Long>): List<Uri> =
    allItems(res).filter { it.id in ids }.distinctBy { it.id }.map { it.uri }

private fun bytesOf(res: ScanResult, ids: Set<Long>): Long =
    allItems(res).distinctBy { it.id }.filter { it.id in ids }.sumOf { it.size }

private fun removeDeleted(res: ScanResult, ids: Set<Long>): ScanResult = res.copy(
    duplicates = res.duplicates.mapNotNull { g ->
        val items = g.items.filter { it.id !in ids }
        if (items.size > 1) PhotoGroup(g.key, items) else null
    },
    similar = res.similar.mapNotNull { g ->
        val items = g.items.filter { it.id !in ids }
        if (items.size > 1) PhotoGroup(g.key, items) else null
    },
    screenshots = res.screenshots.filter { it.id !in ids },
    heavy = res.heavy.filter { it.id !in ids },
)
