package com.tihon.productivnost.voxel

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Экранная рамка модели при заданном масштабе: размер растра и место мировой точки (0,0,0). */
data class Framing(val width: Int, val height: Int, val originX: Float, val originY: Float)

/** Готовый спрайт растения вместе с точкой привязки к земле. */
class Sprite(val bitmap: Bitmap, val originX: Float, val originY: Float)

/**
 * Ортографический растеризатор воксельных моделей: у каждого кубика видны ровно
 * три грани, порядок отрисовки — алгоритм художника по глубине.
 *
 * Проекция линейна, поэтому смещения восьми углов кубика считаются один раз,
 * а для каждого вокселя остаётся только сложение.
 *
 * Буферы у экземпляра свои: спрайты сада готовятся в фоновом потоке
 * параллельно с отрисовкой экрана.
 */
class VoxelRasterizer {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL_AND_STROKE
        strokeWidth = 0.9f
        strokeJoin = Paint.Join.MITER
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var sortBuf = LongArray(0)
    private val cox = FloatArray(8)
    private val coy = FloatArray(8)

    private var ux = 0f; private var uy = 0f
    private var vy = 0f
    private var wx = 0f; private var wy = 0f
    private var dxk = 0f; private var dyk = 0f; private var dzk = 0f
    private var faceX = VoxelRenderer.FACE_X_NEG
    private var faceZ = VoxelRenderer.FACE_Z_NEG

    private fun setup(yaw: Float, pitch: Float) {
        val cosY = cos(yaw); val sinY = sin(yaw)
        val cosP = cos(pitch); val sinP = sin(pitch)
        ux = cosY; uy = sinP * sinY
        vy = -cosP
        wx = -sinY; wy = sinP * cosY
        dxk = sinY * cosP; dyk = sinP; dzk = cosY * cosP
        for (i in 0 until 8) {
            val dx = ((i shr 2) and 1).toFloat()
            val dy = ((i shr 1) and 1).toFloat()
            val dz = (i and 1).toFloat()
            cox[i] = dx * ux + dz * wx
            coy[i] = dx * uy + dy * vy + dz * wy
        }
        faceX = if (sinY > 0f) VoxelRenderer.FACE_X_POS else VoxelRenderer.FACE_X_NEG
        faceZ = if (cosY > 0f) VoxelRenderer.FACE_Z_POS else VoxelRenderer.FACE_Z_NEG
    }

    /** Габариты модели в экранных единицах при единичном масштабе. */
    private fun bounds(model: VoxelModel): FloatArray {
        var minSx = Float.MAX_VALUE; var maxSx = -Float.MAX_VALUE
        var minSy = Float.MAX_VALUE; var maxSy = -Float.MAX_VALUE
        for (c in 0 until 8) {
            val x = (if ((c shr 2) and 1 == 1) model.maxX + 1 else model.minX).toFloat()
            val y = (if ((c shr 1) and 1 == 1) model.maxY + 1 else model.minY).toFloat()
            val z = (if (c and 1 == 1) model.maxZ + 1 else model.minZ).toFloat()
            val sx = x * ux + z * wx
            val sy = x * uy + y * vy + z * wy
            minSx = min(minSx, sx); maxSx = max(maxSx, sx)
            minSy = min(minSy, sy); maxSy = max(maxSy, sy)
        }
        return floatArrayOf(minSx, maxSx, minSy, maxSy)
    }

    fun measure(model: VoxelModel, yaw: Float, pitch: Float, scale: Float, pad: Float = 1f): Framing {
        setup(yaw, pitch)
        val b = bounds(model)
        val w = ((b[1] - b[0]) * scale + pad * 2).roundToInt().coerceAtLeast(1)
        val h = ((b[3] - b[2]) * scale + pad * 2).roundToInt().coerceAtLeast(1)
        return Framing(w, h, pad - b[0] * scale, pad - b[2] * scale)
    }

    /** Рисует модель так, чтобы мировая точка (0,0,0) попала в ([originX], [originY]). */
    fun drawAt(
        canvas: Canvas,
        model: VoxelModel,
        count: Int,
        yaw: Float,
        pitch: Float,
        scale: Float,
        originX: Float,
        originY: Float,
    ) {
        val n = count.coerceIn(0, model.size)
        if (n == 0 || scale <= 0f) return
        setup(yaw, pitch)

        if (sortBuf.size < n) sortBuf = LongArray(max(n, 1024))
        var m = 0
        for (i in 0 until n) {
            if (model.hideFrom[i] <= n) continue
            val d = model.xs[i] * dxk + model.ys[i] * dyk + model.zs[i] * dzk
            val q = ((d + 512f) * 512f).toLong().coerceIn(0L, (1L shl 38) - 1)
            sortBuf[m++] = (q shl 22) or i.toLong()
        }
        java.util.Arrays.sort(sortBuf, 0, m)

        for (k in 0 until m) {
            val i = (sortBuf[k] and 0x3FFFFF).toInt()
            val bx = originX + (model.xs[i] * ux + model.zs[i] * wx) * scale
            val by = originY + (model.xs[i] * uy + model.ys[i] * vy + model.zs[i] * wy) * scale
            val base = model.colors[i]
            val j = model.shade[i]
            face(canvas, bx, by, scale, VoxelRenderer.FACE_TOP, VoxelRenderer.shadeColor(base, VoxelRenderer.SHADE_TOP * j))
            face(canvas, bx, by, scale, faceX, VoxelRenderer.shadeColor(base, VoxelRenderer.SHADE_X * j))
            face(canvas, bx, by, scale, faceZ, VoxelRenderer.shadeColor(base, VoxelRenderer.SHADE_Z * j))
        }
    }

    private fun face(canvas: Canvas, bx: Float, by: Float, s: Float, f: IntArray, color: Int) {
        path.rewind()
        path.moveTo(bx + cox[f[0]] * s, by + coy[f[0]] * s)
        path.lineTo(bx + cox[f[1]] * s, by + coy[f[1]] * s)
        path.lineTo(bx + cox[f[2]] * s, by + coy[f[2]] * s)
        path.lineTo(bx + cox[f[3]] * s, by + coy[f[3]] * s)
        path.close()
        fill.color = color
        canvas.drawPath(path, fill)
    }

    /** Вписывает модель целиком в прямоугольник — по полным границам, чтобы растение не «прыгало» при росте. */
    fun draw(
        canvas: Canvas,
        width: Float,
        height: Float,
        model: VoxelModel,
        count: Int,
        yaw: Float,
        pitch: Float,
        padding: Float,
        shadow: Boolean,
        verticalBias: Float,
    ) {
        if (width <= 0f || height <= 0f || model.size == 0) return
        setup(yaw, pitch)
        val b = bounds(model)
        val padPx = min(width, height) * padding
        val availW = width - padPx * 2
        val availH = height - padPx * 2
        val spanX = max(0.001f, b[1] - b[0])
        val spanY = max(0.001f, b[3] - b[2])
        val scale = min(availW / spanX, availH / spanY)
        val originX = padPx + (availW - spanX * scale) / 2f - b[0] * scale
        val originY = padPx + (availH - spanY * scale) * verticalBias - b[2] * scale

        if (shadow) {
            val rx = spanX * scale * 0.42f
            val ry = rx * 0.32f
            shadowPaint.shader = RadialGradient(
                0f, 0f, 1f,
                intArrayOf(0x50000000, 0x2A000000, 0x00000000),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.save()
            canvas.translate(originX, originY)
            canvas.scale(rx, ry)
            canvas.drawCircle(0f, 0f, 1f, shadowPaint)
            canvas.restore()
            shadowPaint.shader = null
        }
        drawAt(canvas, model, count, yaw, pitch, scale, originX, originY)
    }
}

/** Фасад: у каждого потока свой растеризатор, поэтому буферы не пересекаются. */
object VoxelRenderer {

    const val SHADE_TOP = 1.00f
    const val SHADE_X = 0.70f
    const val SHADE_Z = 0.87f

    val FACE_TOP = intArrayOf(2, 6, 7, 3)
    val FACE_X_POS = intArrayOf(4, 6, 7, 5)
    val FACE_X_NEG = intArrayOf(0, 2, 3, 1)
    val FACE_Z_POS = intArrayOf(1, 5, 7, 3)
    val FACE_Z_NEG = intArrayOf(0, 4, 6, 2)

    private val local = ThreadLocal.withInitial { VoxelRasterizer() }

    fun rasterizer(): VoxelRasterizer = local.get()!!

    fun shadeColor(color: Int, k: Float): Int {
        val a = (color ushr 24) and 0xFF
        val r = (((color ushr 16) and 0xFF) * k).toInt().coerceIn(0, 255)
        val g = (((color ushr 8) and 0xFF) * k).toInt().coerceIn(0, 255)
        val b = ((color and 0xFF) * k).toInt().coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun renderToBitmap(
        target: Bitmap?,
        width: Int,
        height: Int,
        model: VoxelModel,
        count: Int,
        yaw: Float,
        pitch: Float = 0.62f,
        padding: Float = 0.03f,
        shadow: Boolean = true,
        verticalBias: Float = 0.55f,
    ): Bitmap {
        val bmp = if (target != null && target.width == width && target.height == height && !target.isRecycled) {
            target.eraseColor(0); target
        } else {
            Bitmap.createBitmap(max(1, width), max(1, height), Bitmap.Config.ARGB_8888)
        }
        rasterizer().draw(
            Canvas(bmp), bmp.width.toFloat(), bmp.height.toFloat(),
            model, count, yaw, pitch, padding, shadow, verticalBias,
        )
        return bmp
    }

    /** Спрайт растения в фиксированном масштабе — для сцены сада. */
    fun renderSprite(model: VoxelModel, count: Int, yaw: Float, pitch: Float, scale: Float): Sprite {
        val r = rasterizer()
        val framing = r.measure(model, yaw, pitch, scale)
        val bmp = Bitmap.createBitmap(framing.width, framing.height, Bitmap.Config.ARGB_8888)
        r.drawAt(Canvas(bmp), model, count, yaw, pitch, scale, framing.originX, framing.originY)
        return Sprite(bmp, framing.originX, framing.originY)
    }

    fun normalizeYaw(yaw: Float): Float {
        val tau = (Math.PI * 2).toFloat()
        var y = yaw % tau
        if (y < 0) y += tau
        return y
    }
}
