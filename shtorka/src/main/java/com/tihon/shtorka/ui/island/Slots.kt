package com.tihon.shtorka.ui.island

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import android.view.animation.OvershootInterpolator
import com.tihon.shtorka.ui.Glyphs
import com.tihon.shtorka.ui.Palette
import kotlin.math.abs
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
            startDelay = 180
            duration = 360
            interpolator = OvershootInterpolator(1.4f)
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
        // дужка поднимается, но не выше верхнего края значка
        val lift = open.coerceIn(0f, 1.15f) * h * 0.1f
        val top = h * 0.12f - lift
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

/**
 * Кольцо прогресса: загрузка, заряд наушников. Новое значение доезжает плавно;
 * если прогресс неизвестен — по кольцу бежит дуга.
 */
class ProgressRing(ctx: Context) : View(ctx) {
    private var shown = 0f
    private var anim: ValueAnimator? = null
    var progress = 0f
        set(v) {
            val t = v.coerceIn(0f, 1f)
            field = t
            anim?.cancel()
            if (!isShown || abs(t - shown) < 0.002f) { shown = t; invalidate(); return }
            anim = ValueAnimator.ofFloat(shown, t).apply {
                duration = 350
                addUpdateListener { shown = it.animatedValue as Float; invalidate() }
                start()
            }
        }
    var indeterminate = false
        set(v) { field = v; invalidate() }
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
        if (indeterminate) {
            val a = (SystemClock.uptimeMillis() % 1100L) / 1100f * 360f
            c.drawArc(r, a - 90f, 90f, false, arc)
            if (isShown) postInvalidateOnAnimation()
        } else {
            c.drawArc(r, -90f, 360f * shown, false, arc)
        }
    }
}

/**
 * Время для таймера, секундомера, звонка и записи: «4:59», «1:02:03».
 * Обратный отсчёт останавливается на 0:00, а не уходит в минус, как Chronometer.
 * Тикает раз в секунду, ровно на смене секунды, и только пока виден.
 */
class TimeText(ctx: Context) : TextView(ctx) {
    private var base = 0L
    private var countDown = false
    private val tick = object : Runnable {
        override fun run() {
            update()
            if (isAttachedToWindow && isShown && base != 0L) {
                val now = SystemClock.elapsedRealtime()
                val ms = (if (countDown) base - now else now - base).mod(1000L)
                postDelayed(this, (if (countDown) ms else 1000 - ms).coerceIn(20, 1000) + 5)
            }
        }
    }

    init {
        includeFontPadding = false
        fontFeatureSettings = "tnum"
        maxLines = 1
    }

    fun bind(base: Long, countDown: Boolean) {
        this.base = base
        this.countDown = countDown
        removeCallbacks(tick)
        tick.run()
    }

    fun stop() = removeCallbacks(tick)

    private fun update() {
        if (base == 0L) { text = ""; return }
        val now = SystemClock.elapsedRealtime()
        val ms = if (countDown) (base - now).coerceAtLeast(0) else (now - base).coerceAtLeast(0)
        // обратный отсчёт округляем вверх: 4:59.4 показывается как 5:00, как в iOS
        val sec = if (countDown) (ms + 999) / 1000 else ms / 1000
        text = format(sec)
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        removeCallbacks(tick)
        if (isVisible && base != 0L) tick.run()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    companion object {
        fun format(sec: Long): String = if (sec >= 3600) "%d:%02d:%02d".format(sec / 3600, sec / 60 % 60, sec % 60)
        else "%d:%02d".format(sec / 60, sec % 60)
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
