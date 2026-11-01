package com.tihon.shtorka.ui.island

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import android.view.animation.OvershootInterpolator
import com.tihon.shtorka.ui.Glyphs
import com.tihon.shtorka.ui.Palette
import kotlin.math.min

/** Замок, который открывается при разблокировке — как Face ID в островке. */
class LockGlyph(ctx: Context) : View(ctx) {
    private var open = 0f
    private var anim: ValueAnimator? = null
    private val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.WHITE }
    private val shackle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val r = RectF()
    private val p = Path()

    fun play() {
        anim?.cancel()
        open = 0f
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            startDelay = 260
            duration = 420
            interpolator = OvershootInterpolator(2.2f)
            addUpdateListener { open = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val bw = w * 0.74f
        val bh = h * 0.48f
        r.set((w - bw) / 2, h - bh, (w + bw) / 2, h)
        c.drawRoundRect(r, bh * 0.22f, bh * 0.22f, body)
        shackle.strokeWidth = w * 0.13f
        val sw = bw * 0.62f
        val left = (w - sw) / 2
        val lift = open * h * 0.16f
        val top = h * 0.1f - lift
        p.reset()
        // правая ножка остаётся в корпусе, левая выходит из него при открытии
        p.moveTo(left + sw, h - bh)
        p.lineTo(left + sw, top + sw / 2)
        r.set(left, top, left + sw, top + sw)
        p.arcTo(r, 0f, -180f, false)
        p.lineTo(left, h - bh - lift - open * h * 0.06f)
        c.drawPath(p, shackle)
    }
}

/** Красная точка записи, мягко «дышит». */
class RecDot(ctx: Context) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.RED }
    private val pulse = ValueAnimator.ofFloat(1f, 0.35f).apply {
        duration = 900
        repeatMode = ValueAnimator.REVERSE
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener { paint.alpha = ((it.animatedValue as Float) * 255).toInt(); invalidate() }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); pulse.start() }
    override fun onDetachedFromWindow() { pulse.cancel(); super.onDetachedFromWindow() }
    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible && isAttachedToWindow) { if (!pulse.isStarted) pulse.start() } else pulse.cancel()
    }

    override fun onDraw(c: Canvas) {
        c.drawCircle(width / 2f, height / 2f, min(width, height) * 0.36f, paint)
    }
}

/** Кольцо прогресса: загрузка, заряд наушников. */
class ProgressRing(ctx: Context) : View(ctx) {
    var progress = 0f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }
    var color = Palette.BLUE
        set(v) { field = v; arc.color = v; invalidate() }
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0x40FFFFFF }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = Palette.BLUE }
    private val r = RectF()

    override fun onDraw(c: Canvas) {
        val sw = min(width, height) * 0.16f
        track.strokeWidth = sw
        arc.strokeWidth = sw
        r.set(sw / 2, sw / 2, width - sw / 2, height - sw / 2)
        c.drawOval(r, track)
        c.drawArc(r, -90f, 360f * progress, false, arc)
    }
}

/** Батарейка для зарядки и низкого заряда. */
class BatteryGlyph(ctx: Context) : View(ctx) {
    var level = 100
        set(v) { field = v; invalidate() }
    var fill = Palette.GREEN
        set(v) { field = v; invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        Glyphs.battery(c, 0f, height / 2f, width.toFloat(), height.toFloat(), level, fill, p)
    }
}
