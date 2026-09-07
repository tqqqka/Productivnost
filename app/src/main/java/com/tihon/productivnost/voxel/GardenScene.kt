package com.tihon.productivnost.voxel

import android.util.LruCache
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.tihon.productivnost.core.Plant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Угол, под которым виден сад: ромб углом к зрителю, слегка наклонённый. */
const val GARDEN_YAW = (PI / 4).toFloat()
const val GARDEN_PITCH = 0.62f

/** Размер клетки и толщина грядки в мировых вокселях. */
private const val CELL = 14f
private const val DEPTH = 3f
/** Запас по высоте на крону — самое высокое дерево примерно столько и занимает. */
private const val TREE_H = 30f
private val SIN_P = sin(GARDEN_PITCH)
private val COS_P = cos(GARDEN_PITCH)
private const val ISO = 0.70710678f

private val GRASS = intArrayOf(0xFF5AA95A.toInt(), 0xFF529E54.toInt())
private val SOIL = 0xFF4A3728.toInt()
private val TILLED = intArrayOf(0xFF6B5236.toInt(), 0xFF614A31.toInt())

private class GardenSprite(val image: ImageBitmap, val originX: Float, val originY: Float)

/** Кеш спрайтов ограничен памятью, а не количеством: растры крупные. */
private class SpriteStore {
    val cache = object : LruCache<String, GardenSprite>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: GardenSprite) =
            value.image.width * value.image.height * 4
    }
    var version by mutableIntStateOf(0)
}

/** Геометрия сада на экране при текущем масштабе и сдвиге. */
private class Layout(
    val n: Int,
    val ex: Float,
    val ey: Float,
    val cx0: Float,
    val cy0: Float,
    val voxelScale: Float,
    val blockPx: Float,
)

private fun layoutOf(size: IntSize, n: Int, zoom: Float, pan: Offset): Layout {
    val w = size.width.toFloat()
    val h = size.height.toFloat()
    // Всё считается в долях полуширины клетки: и грядка, и запас на кроны.
    val perEx = TREE_H * COS_P / (ISO * CELL)
    val blockPerEx = DEPTH * COS_P / (ISO * CELL)
    val heightPerEx = perEx + 2f * n * SIN_P + blockPerEx
    val base = minOf(w * 0.94f / (2f * n), h * 0.94f / heightPerEx).coerceIn(14f, 96f)
    val ex = base * zoom
    val ey = ex * SIN_P
    val voxelScale = ex / (ISO * CELL)
    val blockPx = DEPTH * COS_P * voxelScale
    val treePx = TREE_H * COS_P * voxelScale
    val cx0 = w / 2f + pan.x
    val cy0 = h / 2f - ((2f * n - 2f) * ey + blockPx - treePx) / 2f + pan.y
    return Layout(n, ex, ey, cx0, cy0, voxelScale, blockPx)
}

private fun gridSize(count: Int) = maxOf(4, ceil(sqrt(count.toFloat().coerceAtLeast(1f))).toInt())

/**
 * Сад как единая воксельная грядка: квадрат из клеток, повёрнутый углом к зрителю.
 *
 * Земля рисуется напрямую тремя гранями на клетку — она плоская, воксели ей ни к чему,
 * а растения кладутся сверху заранее подготовленными спрайтами. Спрайт считается один
 * раз в фоновом потоке и потом просто копируется, поэтому кадр остаётся дешёвым даже
 * когда в саду сотня деревьев.
 */
@Composable
fun VoxelGardenScene(
    plants: List<Plant>,
    modifier: Modifier = Modifier,
    onSelect: (Plant) -> Unit,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val store = remember { SpriteStore() }

    val n = gridSize(plants.size)
    val layout = layoutOf(size, n, zoom, pan)

    // Растр пересчитывается только при заметном изменении масштаба, а промежуточные
    // значения зума догоняются растяжением спрайта — иначе деревья прыгали бы
    // ступеньками, пока грядка масштабируется плавно.
    val scaleBucket = (layout.voxelScale / 1.5f).roundToInt().coerceAtLeast(1)
    val spriteScale = scaleBucket * 1.5f

    // Готовим спрайты только для клеток, которые видны на экране.
    val range = visibleRange(size, layout, n)
    LaunchedEffect(plants, scaleBucket, range) {
        if (size == IntSize.Zero) return@LaunchedEffect
        withContext(Dispatchers.Default) {
            for (i in range.first..range.second) {
                for (j in range.third..range.fourth) {
                    coroutineContext.ensureActive()
                    val index = i * n + j
                    val plant = plants.getOrNull(index) ?: continue
                    val key = "${plant.id}@$scaleBucket"
                    if (store.cache.get(key) != null) continue
                    val model = PlantModelCache.get(plant, withGround = false)
                    val sprite = VoxelRenderer.renderSprite(
                        model, model.size, GARDEN_YAW, GARDEN_PITCH, spriteScale,
                    )
                    store.cache.put(key, GardenSprite(sprite.bitmap.asImageBitmap(), sprite.originX, sprite.originY))
                    store.version++
                }
            }
        }
    }

    val sway by rememberInfiniteTransition(label = "gardenSway").animateFloat(
        initialValue = 0f,
        targetValue = (PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(tween(6400, easing = LinearEasing), RepeatMode.Restart),
        label = "gardenSwayPhase",
    )

    val plantsRef = rememberUpdatedState(plants)
    val path = remember { Path() }

    Canvas(
        modifier
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTransformGestures { _, panChange, zoomChange, _ ->
                    zoom = (zoom * zoomChange).coerceIn(0.5f, 2.6f)
                    pan += panChange
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { tap ->
                    val current = plantsRef.value
                    val cells = gridSize(current.size)
                    pick(
                        tap, current, layoutOf(size, cells, zoom, pan), cells,
                        store, scaleBucket, layout.voxelScale / spriteScale,
                    )?.let(onSelect)
                }
            },
    ) {
        @Suppress("UNUSED_EXPRESSION")
        store.version
        drawGarden(path, plants, layout, n, store, scaleBucket, layout.voxelScale / spriteScale, sway)
    }
}

/** Диапазон клеток, попадающих в видимую область, с запасом на кроны деревьев. */
private fun visibleRange(size: IntSize, l: Layout, n: Int): Quad {
    if (size == IntSize.Zero) return Quad(0, minOf(n, 6) - 1, 0, minOf(n, 6) - 1)
    val margin = 3
    var iMin = n; var iMax = 0; var jMin = n; var jMax = 0
    for (i in 0 until n) for (j in 0 until n) {
        val x = l.cx0 + (j - i) * l.ex
        val y = l.cy0 + (j + i) * l.ey
        if (x > -l.ex * 3 && x < size.width + l.ex * 3 && y > -l.ey * 10 && y < size.height + l.ey * 4) {
            if (i < iMin) iMin = i
            if (i > iMax) iMax = i
            if (j < jMin) jMin = j
            if (j > jMax) jMax = j
        }
    }
    if (iMin > iMax) return Quad(0, -1, 0, -1)
    return Quad(
        (iMin - margin).coerceAtLeast(0), (iMax + margin).coerceAtMost(n - 1),
        (jMin - margin).coerceAtLeast(0), (jMax + margin).coerceAtMost(n - 1),
    )
}

private data class Quad(val first: Int, val second: Int, val third: Int, val fourth: Int)

private fun DrawScope.drawGarden(
    path: Path,
    plants: List<Plant>,
    l: Layout,
    n: Int,
    store: SpriteStore,
    scaleBucket: Int,
    spriteZoom: Float,
    swayPhase: Float,
) {
    // Клетки рисуются по диагоналям от дальней к ближней: боковые грани
    // внутренних блоков закрывает следующий, наружу торчит только передний край.
    for (d in 0..(2 * n - 2)) {
        for (i in 0 until n) {
            val j = d - i
            if (j < 0 || j >= n) continue
            val x = l.cx0 + (j - i) * l.ex
            val y = l.cy0 + (j + i) * l.ey
            if (x < -l.ex * 4 || x > size.width + l.ex * 4) continue
            if (y < -l.ey * 14 || y > size.height + l.ey * 6) continue

            val index = i * n + j
            val plant = plants.getOrNull(index)
            val even = (i + j) % 2 == 0
            val top = if (plant != null) {
                if (even) GRASS[0] else GRASS[1]
            } else {
                if (even) TILLED[0] else TILLED[1]
            }

            quad(path, x, y - l.ey, x + l.ex, y, x, y + l.ey, x - l.ex, y, top, VoxelRenderer.SHADE_TOP)
            quad(
                path, x + l.ex, y, x, y + l.ey, x, y + l.ey + l.blockPx, x + l.ex, y + l.blockPx,
                SOIL, VoxelRenderer.SHADE_X,
            )
            quad(
                path, x - l.ex, y, x, y + l.ey, x, y + l.ey + l.blockPx, x - l.ex, y + l.blockPx,
                SOIL, VoxelRenderer.SHADE_Z,
            )

            if (plant == null) continue
            val sprite = store.cache.get("${plant.id}@$scaleBucket") ?: continue
            val anchorY = y + COS_P * l.voxelScale
            val deg = sin(swayPhase + (plant.id % 619) * 0.31f) * 1.1f
            val pivot = Offset(x, anchorY)
            withTransform({
                rotate(deg, pivot)
                scale(spriteZoom, spriteZoom, pivot)
            }) {
                drawImage(sprite.image, topLeft = Offset(x - sprite.originX, anchorY - sprite.originY))
            }
        }
    }
}

private fun DrawScope.quad(
    path: Path,
    x0: Float, y0: Float, x1: Float, y1: Float,
    x2: Float, y2: Float, x3: Float, y3: Float,
    color: Int, shade: Float,
) {
    path.rewind()
    path.moveTo(x0, y0)
    path.lineTo(x1, y1)
    path.lineTo(x2, y2)
    path.lineTo(x3, y3)
    path.close()
    drawPath(path, Color(VoxelRenderer.shadeColor(color, shade)))
}

/** Клик по растению: сначала пробуем ближние спрайты, потом просто клетку земли. */
private fun pick(
    tap: Offset,
    plants: List<Plant>,
    l: Layout,
    n: Int,
    store: SpriteStore,
    scaleBucket: Int,
    spriteZoom: Float,
): Plant? {
    for (d in (2 * n - 2) downTo 0) {
        for (i in 0 until n) {
            val j = d - i
            if (j < 0 || j >= n) continue
            val plant = plants.getOrNull(i * n + j) ?: continue
            val sprite = store.cache.get("${plant.id}@$scaleBucket") ?: continue
            val x = l.cx0 + (j - i) * l.ex
            val y = l.cy0 + (j + i) * l.ey + COS_P * l.voxelScale
            val left = x - sprite.originX * spriteZoom
            val top = y - sprite.originY * spriteZoom
            if (tap.x >= left && tap.x <= left + sprite.image.width * spriteZoom &&
                tap.y >= top && tap.y <= top + sprite.image.height * spriteZoom
            ) {
                return plant
            }
        }
    }
    val dx = (tap.x - l.cx0) / l.ex
    val dy = (tap.y - l.cy0) / l.ey
    val j = ((dx + dy) / 2f).roundToInt()
    val i = ((dy - dx) / 2f).roundToInt()
    if (i !in 0 until n || j !in 0 until n) return null
    return plants.getOrNull(i * n + j)
}
