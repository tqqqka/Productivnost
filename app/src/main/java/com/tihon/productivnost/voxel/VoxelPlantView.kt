package com.tihon.productivnost.voxel

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer

import com.tihon.productivnost.core.Plant
import com.tihon.productivnost.core.PlantKind
import kotlin.math.roundToInt
import kotlin.math.sin

/** Модели дорогие в генерации — держим их в кеше. */
object PlantModelCache {
    private val cache = LruCache<String, VoxelModel>(48)

    fun get(seed: Long, kind: PlantKind, species: Int): VoxelModel {
        val key = "$seed/${kind.name}/$species"
        cache.get(key)?.let { return it }
        val model = PlantGenerator.build(seed, kind, species)
        cache.put(key, model)
        return model
    }

    fun get(plant: Plant) = get(plant.seed, plant.kind, plant.species)
}

private class Buffers {
    var a: Bitmap? = null
    var b: Bitmap? = null
    var flip = false
    fun next(w: Int, h: Int): Bitmap? {
        flip = !flip
        return if (flip) a else b
    }
    fun store(bmp: Bitmap) {
        if (flip) a = bmp else b = bmp
    }
}

/**
 * Отрисовка воксельного растения.
 *
 * Тяжёлая часть (проекция и заливка граней) кешируется в bitmap и пересчитывается
 * только при изменении роста, поворота или размера. Каждый кадр остаётся
 * лишь дешёвое преобразование холста — покачивание на ветру.
 */
@Composable
fun VoxelPlantView(
    model: VoxelModel,
    growth: Float,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    sway: Boolean = true,
    pitch: Float = 0.62f,
    padding: Float = 0.03f,
    verticalBias: Float = 0.55f,
    startYaw: Float = 0.62f,
    /** Внутреннее разрешение растра: 0.6 заметно дешевле и почти не портит вокселей. */
    renderScale: Float = 1f,
) {
    var yaw by remember(model) { mutableFloatStateOf(startYaw) }
    val buffers = remember { Buffers() }
    val currentGrowth by rememberUpdatedState(growth)

    val swayPhase = if (sway) {
        val t = rememberInfiniteTransition(label = "sway")
        t.animateFloat(
            initialValue = 0f,
            targetValue = (Math.PI * 2).toFloat(),
            animationSpec = infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart),
            label = "swayPhase",
        ).value
    } else 0f

    val gesture = if (interactive) {
        Modifier.pointerInput(model) {
            detectDragGestures { change, drag ->
                change.consume()
                yaw = VoxelRenderer.normalizeYaw(yaw - drag.x / 190f)
            }
        }
    } else Modifier

    Spacer(
        modifier = modifier
            .then(gesture)
            .drawWithCache {
                // Растр ограничен по размеру: воксели крупные, апскейл незаметен,
                // а стоимость заливки граней растёт как площадь.
                val cap = 768f
                val longest = maxOf(size.width, size.height) * renderScale
                val k = renderScale * if (longest > cap) cap / longest else 1f
                val w = (size.width * k).roundToInt().coerceAtLeast(1)
                val h = (size.height * k).roundToInt().coerceAtLeast(1)
                val count = model.countFor(currentGrowth)
                val target = buffers.next(w, h)
                val bmp = VoxelRenderer.renderToBitmap(
                    target, w, h, model, count, yaw, pitch, padding,
                    shadow = true, verticalBias = verticalBias,
                )
                buffers.store(bmp)
                val image = bmp.asImageBitmap()
                onDrawBehind {
                    val amp = if (sway) 1.15f else 0f
                    val deg = sin(swayPhase.toDouble()).toFloat() * amp +
                        sin(swayPhase * 2.13 + 0.7).toFloat() * amp * 0.35f
                    withTransform({
                        rotate(deg, pivot = Offset(size.width / 2f, size.height * 0.92f))
                    }) {
                        drawImage(
                            image = image,
                            srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
                            srcSize = androidx.compose.ui.unit.IntSize(image.width, image.height),
                            dstOffset = androidx.compose.ui.unit.IntOffset.Zero,
                            dstSize = androidx.compose.ui.unit.IntSize(
                                size.width.roundToInt(), size.height.roundToInt(),
                            ),
                        )
                    }
                }
            },
    )
}
