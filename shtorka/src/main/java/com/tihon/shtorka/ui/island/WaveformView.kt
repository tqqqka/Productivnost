package com.tihon.shtorka.ui.island

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import com.tihon.shtorka.ui.lerp
import kotlin.math.min
import kotlin.random.Random

/** Пляшущие столбики «играет музыка». Цвет — из обложки. */
class WaveformView(ctx: Context) : View(ctx) {
    var playing = false
        set(v) { field = v; postInvalidateOnAnimation() }
    var color = 0xFFFFFFFF.toInt()
        set(v) { field = v; paint.color = v; invalidate() }

    private val n = 5
    private val cur = FloatArray(n) { 0.3f }
    private val target = FloatArray(n) { 0.3f }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private var lastPick = 0L

    override fun onDraw(c: Canvas) {
        val now = System.currentTimeMillis()
        if (playing && now - lastPick > 120) {
            lastPick = now
            for (i in 0 until n) target[i] = 0.25f + Random.nextFloat() * 0.75f
        }
        if (!playing) for (i in 0 until n) target[i] = 0.18f
        var moving = false
        for (i in 0 until n) {
            cur[i] = lerp(cur[i], target[i], 0.18f)
            if (kotlin.math.abs(cur[i] - target[i]) > 0.01f) moving = true
        }
        val w = width.toFloat()
        val h = height.toFloat()
        val bw = w / (n * 2 - 1)
        for (i in 0 until n) {
            val bh = h * cur[i]
            val x = i * bw * 2
            c.drawRoundRect(x, (h - bh) / 2, x + bw, (h + bh) / 2, min(bw, bh) / 2, min(bw, bh) / 2, paint)
        }
        if ((playing || moving) && isShown) postInvalidateOnAnimation()
    }
}
