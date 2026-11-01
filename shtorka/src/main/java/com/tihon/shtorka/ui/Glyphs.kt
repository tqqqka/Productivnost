package com.tihon.shtorka.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Значки, которые меняют форму вместе со значением: солнце, динамик, сеть, батарея. */
object Glyphs {
    private val path = Path()
    private val rect = RectF()

    /** Солнце: лучи вытягиваются с ростом яркости. */
    fun sun(c: Canvas, cx: Float, cy: Float, size: Float, level: Float, p: Paint) {
        p.style = Paint.Style.FILL
        c.drawCircle(cx, cy, size * 0.19f, p)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = size * 0.085f
        val r1 = size * 0.32f
        val r2 = r1 + size * (0.07f + 0.1f * clamp01(level))
        for (i in 0 until 8) {
            val a = i * PI / 4
            val ca = cos(a).toFloat()
            val sa = sin(a).toFloat()
            c.drawLine(cx + ca * r1, cy + sa * r1, cx + ca * r2, cy + sa * r2, p)
        }
        p.style = Paint.Style.FILL
    }

    /** Динамик: 0 — перечёркнут, дальше одна, две или три волны. */
    fun speaker(c: Canvas, cx: Float, cy: Float, size: Float, level: Float, p: Paint) {
        val s = size
        val ox = cx - s * 0.14f
        path.reset()
        path.moveTo(ox - s * 0.30f, cy - s * 0.12f)
        path.lineTo(ox - s * 0.12f, cy - s * 0.12f)
        path.lineTo(ox + s * 0.08f, cy - s * 0.31f)
        path.lineTo(ox + s * 0.08f, cy + s * 0.31f)
        path.lineTo(ox - s * 0.12f, cy + s * 0.12f)
        path.lineTo(ox - s * 0.30f, cy + s * 0.12f)
        path.close()
        p.style = Paint.Style.FILL_AND_STROKE
        p.strokeJoin = Paint.Join.ROUND
        p.strokeWidth = s * 0.05f
        c.drawPath(path, p)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = s * 0.075f
        if (level <= 0.001f) {
            c.drawLine(ox - s * 0.30f, cy - s * 0.38f, ox + s * 0.42f, cy + s * 0.38f, p)
        } else {
            val waves = when {
                level < 0.34f -> 1
                level < 0.67f -> 2
                else -> 3
            }
            for (i in 0 until waves) {
                val r = s * (0.17f + 0.14f * i)
                rect.set(ox + s * 0.08f - r, cy - r, ox + s * 0.08f + r, cy + r)
                c.drawArc(rect, -42f, 84f, false, p)
            }
        }
        p.style = Paint.Style.FILL
    }

    /** Четыре столбика сотовой сети. */
    fun signal(c: Canvas, left: Float, bottom: Float, h: Float, level: Int, on: Int, off: Int, p: Paint) {
        val bw = h * 0.24f
        val gap = h * 0.13f
        p.style = Paint.Style.FILL
        for (i in 0 until 4) {
            val bh = h * (0.36f + 0.213f * i)
            val x = left + i * (bw + gap)
            p.color = if (i < level) on else off
            rect.set(x, bottom - bh, x + bw, bottom)
            c.drawRoundRect(rect, bw * 0.35f, bw * 0.35f, p)
        }
    }

    fun signalWidth(h: Float) = h * 0.24f * 4 + h * 0.13f * 3

    /** Батарея как в строке состояния iOS. */
    fun battery(c: Canvas, left: Float, cy: Float, w: Float, h: Float, level: Int, fill: Int, p: Paint) {
        val body = w * 0.9f
        rect.set(left, cy - h / 2, left + body, cy + h / 2)
        p.style = Paint.Style.STROKE
        p.strokeWidth = h * 0.09f
        p.color = 0x73FFFFFF
        c.drawRoundRect(rect, h * 0.3f, h * 0.3f, p)
        p.style = Paint.Style.FILL
        val inset = h * 0.17f
        val inner = (body - inset * 2) * (level.coerceIn(0, 100) / 100f)
        p.color = fill
        rect.set(left + inset, cy - h / 2 + inset, left + inset + inner.coerceAtLeast(h * 0.12f), cy + h / 2 - inset)
        c.drawRoundRect(rect, h * 0.16f, h * 0.16f, p)
        p.color = 0x73FFFFFF
        rect.set(left + body + w * 0.025f, cy - h * 0.19f, left + w, cy + h * 0.19f)
        c.drawRoundRect(rect, h * 0.12f, h * 0.12f, p)
    }
}
