package com.tihon.shtorka.ui.island

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.view.View
import com.tihon.shtorka.ui.lerp
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/**
 * Волна «играет музыка». Высоты считаются от времени суммой синусов с разными
 * фазами — движение плавное и не зависит от частоты кадров (60 или 120 Гц).
 */
class WaveformView(ctx: Context) : View(ctx) {
    var playing = false
        set(v) {
            if (field == v) return
            field = v
            postInvalidateOnAnimation()
        }
    var color = 0xFFFFFFFF.toInt()
        set(v) { field = v; paint.color = v; invalidate() }

    private val n = 5
    private val amp = FloatArray(n) { 0.2f }
    private val speed = floatArrayOf(5.1f, 7.3f, 6.2f, 8.4f, 5.7f)
    private val phase = floatArrayOf(0f, 1.3f, 2.1f, 0.7f, 2.9f)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private var last = 0L

    override fun onDraw(c: Canvas) {
        val now = SystemClock.uptimeMillis()
        val dt = if (last == 0L) 0.016f else ((now - last) / 1000f).coerceIn(0f, 0.05f)
        last = now
        val t = now / 1000f
        var moving = false
        for (i in 0 until n) {
            val target = if (playing) {
                0.32f + 0.68f * abs(sin(t * speed[i] + phase[i]) * 0.65f + sin(t * speed[i] * 0.53f + phase[i] * 1.7f) * 0.35f)
            } else 0.2f
            // экспоненциальное сглаживание по времени, а не по кадрам
            val k = 1f - kotlin.math.exp(-dt * 18f)
            amp[i] = lerp(amp[i], target, k)
            if (abs(amp[i] - target) > 0.005f) moving = true
        }
        val w = width.toFloat()
        val h = height.toFloat()
        val bw = w / (n * 2 - 1)
        for (i in 0 until n) {
            val bh = h * amp[i]
            val x = i * bw * 2
            val r = min(bw, bh) / 2
            c.drawRoundRect(x, (h - bh) / 2, x + bw, (h + bh) / 2, r, r, paint)
        }
        if ((playing || moving) && isShown) postInvalidateOnAnimation() else last = 0L
    }
}
