package com.tihon.productivnost.voxel

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Ортографический рендер воксельной модели: у каждого кубика видны ровно три грани,
 * порядок отрисовки — алгоритм художника по глубине.
 *
 * Проекция линейна, поэтому смещения восьми углов кубика считаются один раз,
 * а для каждого вокселя остаётся только сложение.
 */
object VoxelRenderer {

    private const val SHADE_TOP = 1.00f
    private const val SHADE_X = 0.70f
    private const val SHADE_Z = 0.87f

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

    private val faceTop = intArrayOf(2, 6, 7, 3)
    private val faceXPos = intArrayOf(4, 6, 7, 5)
    private val faceXNeg = intArrayOf(0, 2, 3, 1)
    private val faceZPos = intArrayOf(1, 5, 7, 3)
    private val faceZNeg = intArrayOf(0, 4, 6, 2)

    private fun shadeColor(color: Int, k: Float): Int {
        val a = (color ushr 24) and 0xFF
        val r = (((color ushr 16) and 0xFF) * k).toInt().coerceIn(0, 255)
        val g = (((color ushr 8) and 0xFF) * k).toInt().coerceIn(0, 255)
        val b = ((color and 0xFF) * k).toInt().coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    /**
     * Рисует [count] первых вокселей модели, вписывая её целиком (по полным границам,
     * чтобы растение не «прыгало» во время роста).
     */
    fun draw(
        canvas: Canvas,
        width: Float,
        height: Float,
        model: VoxelModel,
        count: Int,
        yaw: Float,
        pitch: Float = 0.62f,
        padding: Float = 0.06f,
        shadow: Boolean = true,
        verticalBias: Float = 0.5f,
    ) {
        if (width <= 0f || height <= 0f || model.size == 0) return
        val n = count.coerceIn(0, model.size)
        if (n == 0) return

        val cosY = cos(yaw); val sinY = sin(yaw)
        val cosP = cos(pitch); val sinP = sin(pitch)

        val ux = cosY; val uy = sinP * sinY
        val vy = -cosP
        val wx = -sinY; val wy = sinP * cosY

        for (i in 0 until 8) {
            val dx = ((i shr 2) and 1).toFloat()
            val dy = ((i shr 1) and 1).toFloat()
            val dz = (i and 1).toFloat()
            cox[i] = dx * ux + dz * wx
            coy[i] = dx * uy + dy * vy + dz * wy
        }

        // Габариты по полной модели.
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
        val padPx = min(width, height) * padding
        val availW = width - padPx * 2
        val availH = height - padPx * 2
        val spanX = max(0.001f, maxSx - minSx)
        val spanY = max(0.001f, maxSy - minSy)
        val scale = min(availW / spanX, availH / spanY)
        val originX = padPx + (availW - spanX * scale) / 2f - minSx * scale
        val originY = padPx + (availH - spanY * scale) * verticalBias - minSy * scale

        if (shadow) {
            val groundY = originY + (0f * vy) * scale
            val rx = spanX * scale * 0.42f
            val ry = rx * 0.32f
            shadowPaint.shader = RadialGradient(
                0f, 0f, 1f,
                intArrayOf(0x50000000, 0x2A000000, 0x00000000),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.save()
            canvas.translate(originX + spanX * scale * 0f, groundY)
            canvas.scale(rx, ry)
            canvas.drawCircle(0f, 0f, 1f, shadowPaint)
            canvas.restore()
            shadowPaint.shader = null
        }

        // Сортировка по глубине: дальние рисуются первыми.
        if (sortBuf.size < n) sortBuf = LongArray(max(n, 1024))
        var m = 0
        val dxk = sinY * cosP
        val dyk = sinP
        val dzk = cosY * cosP
        for (i in 0 until n) {
            if (model.hideFrom[i] <= n) continue
            val d = model.xs[i] * dxk + model.ys[i] * dyk + model.zs[i] * dzk
            val q = ((d + 512f) * 512f).toLong().coerceIn(0L, (1L shl 38) - 1)
            sortBuf[m++] = (q shl 22) or i.toLong()
        }
        java.util.Arrays.sort(sortBuf, 0, m)

        val xPos = sinY > 0f
        val zPos = cosY > 0f
        val faceX = if (xPos) faceXPos else faceXNeg
        val faceZ = if (zPos) faceZPos else faceZNeg

        for (k in 0 until m) {
            val i = (sortBuf[k] and 0x3FFFFF).toInt()
            val x = model.xs[i].toFloat()
            val y = model.ys[i].toFloat()
            val z = model.zs[i].toFloat()
            val bx = originX + (x * ux + z * wx) * scale
            val by = originY + (x * uy + y * vy + z * wy) * scale
            val base = model.colors[i]
            val jitter = model.shade[i]

            drawFace(canvas, bx, by, scale, faceTop, shadeColor(base, SHADE_TOP * jitter))
            drawFace(canvas, bx, by, scale, faceX, shadeColor(base, SHADE_X * jitter))
            drawFace(canvas, bx, by, scale, faceZ, shadeColor(base, SHADE_Z * jitter))
        }
    }

    private fun drawFace(canvas: Canvas, bx: Float, by: Float, s: Float, face: IntArray, color: Int) {
        path.rewind()
        path.moveTo(bx + cox[face[0]] * s, by + coy[face[0]] * s)
        path.lineTo(bx + cox[face[1]] * s, by + coy[face[1]] * s)
        path.lineTo(bx + cox[face[2]] * s, by + coy[face[2]] * s)
        path.lineTo(bx + cox[face[3]] * s, by + coy[face[3]] * s)
        path.close()
        fill.color = color
        canvas.drawPath(path, fill)
    }

    /** Рендер в переиспользуемый bitmap. */
    fun renderToBitmap(
        target: Bitmap?,
        width: Int,
        height: Int,
        model: VoxelModel,
        count: Int,
        yaw: Float,
        pitch: Float = 0.62f,
        padding: Float = 0.06f,
        shadow: Boolean = true,
        verticalBias: Float = 0.5f,
    ): Bitmap {
        val bmp = if (target != null && target.width == width && target.height == height && !target.isRecycled) {
            target.eraseColor(0); target
        } else {
            Bitmap.createBitmap(max(1, width), max(1, height), Bitmap.Config.ARGB_8888)
        }
        val c = Canvas(bmp)
        draw(c, bmp.width.toFloat(), bmp.height.toFloat(), model, count, yaw, pitch, padding, shadow, verticalBias)
        return bmp
    }

    /** Небольшая нормализация угла, чтобы кеш не рос бесконечно. */
    fun normalizeYaw(yaw: Float): Float {
        val tau = (Math.PI * 2).toFloat()
        var y = yaw % tau
        if (y < 0) y += tau
        return y
    }

    fun approx(a: Float, b: Float, eps: Float = 0.0001f) = abs(a - b) < eps
}
