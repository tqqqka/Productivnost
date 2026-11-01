package com.tihon.shtorka.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.TextView
import com.tihon.shtorka.R
import com.tihon.shtorka.core.Spring
import com.tihon.shtorka.system.Controls
import com.tihon.shtorka.system.MediaRepo
import kotlin.math.roundToInt

/** Горизонтальная полоска: прогресс трека или громкость. При касании утолщается. */
class HBar(ctx: Context) : android.view.View(ctx) {
    var value = 0f
        private set
    var onChange: ((Float) -> Unit)? = null
    var onRelease: ((Float) -> Unit)? = null
    var dragging = false
        private set
    /** false — только показывает значение, касания не берёт. */
    var interactive = true
    private var grow = 0f
    private val growSpring = Spring(0f, 0.002f) { grow = it; invalidate() }
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x40FFFFFF }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE6FFFFFF.toInt() }
    private val r = RectF()

    fun set(v: Float) {
        if (dragging) return
        value = v.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val h = lerp(dp(6), dp(12), grow)
        val cy = height / 2f
        val inset = lerp(0f, -dp(4), grow)
        r.set(inset, cy - h / 2, width - inset, cy + h / 2)
        c.drawRoundRect(r, h / 2, h / 2, track)
        val save = c.save()
        c.clipRect(r.left, r.top, r.left + r.width() * value, r.bottom)
        fill.color = blend(0xB3FFFFFF.toInt(), Palette.WHITE, grow)
        c.drawRoundRect(r, h / 2, h / 2, fill)
        c.restoreToCount(save)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!interactive) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                dragging = true
                growSpring.animateTo(1f, stiffness = 700f, damping = 0.8f)
                update(e.x)
            }
            MotionEvent.ACTION_MOVE -> update(e.x)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                dragging = false
                growSpring.animateTo(0f, stiffness = 500f, damping = 0.75f)
                onRelease?.invoke(value)
            }
        }
        return true
    }

    private fun update(x: Float) {
        val nv = (x / width).coerceIn(0f, 1f)
        if ((nv >= 1f && value < 1f) || (nv <= 0f && value > 0f)) Haptics.edge(this)
        value = nv
        onChange?.invoke(nv)
        invalidate()
    }
}

/**
 * Плеер «Исполняется»: обложка, название, прогресс, кнопки и (в Пункте
 * управления) громкость. Тот же блок стоит в Центре уведомлений.
 */
class MediaPanel(ctx: Context, private val withVolume: Boolean) : FrameLayout(ctx) {
    private val art = ArtView(ctx).apply { radiusFraction = 0.16f }
    private val title: TextView = ctx.text(17f, Fonts.semibold)
    private val artist: TextView = ctx.text(15f, Fonts.medium, Palette.TEXT_SECONDARY)
    private val output = GlyphButton(ctx, R.drawable.g_airplay, 0.6f, 0xCCFFFFFF.toInt())
    private val progress = HBar(ctx)
    private val elapsed: TextView = ctx.text(12f, Fonts.medium, 0x80FFFFFF.toInt())
    private val remain: TextView = ctx.text(12f, Fonts.medium, 0x80FFFFFF.toInt(), gravity = android.view.Gravity.END)
    private val prev = GlyphButton(ctx, R.drawable.g_prev, 0.6f)
    private val play = GlyphButton(ctx, R.drawable.g_play, 0.74f)
    private val next = GlyphButton(ctx, R.drawable.g_next, 0.6f)
    private val volume = HBar(ctx)
    private var now: MediaRepo.Now? = null

    var onOpenApp: (() -> Unit)? = null
    var onOutput: (() -> Unit)? = null

    private val tick = object : Runnable {
        override fun run() {
            updateProgress()
            if (isAttachedToWindow && now?.playing == true) postDelayed(this, 500)
        }
    }

    init {
        listOf(art, title, artist, output, progress, elapsed, remain, prev, play, next).forEach { addView(it) }
        if (withVolume) addView(volume)
        prev.onTap = { MediaRepo.prev() }
        play.onTap = { MediaRepo.playPause() }
        next.onTap = { MediaRepo.next() }
        output.onTap = { onOutput?.invoke() }
        art.setOnClickListener { onOpenApp?.invoke() }
        progress.onRelease = { v -> now?.let { n -> if (n.duration > 0) MediaRepo.seekTo((v * n.duration).toLong()) } }
        progress.onChange = { v -> now?.let { n -> showTimes((v * n.duration).toLong(), n.duration) } }
        volume.onChange = { v -> Controls.setVolume(context, v) }
        bind(MediaRepo.now)
    }

    fun bind(n: MediaRepo.Now?) {
        now = n
        art.setBitmap(n?.art)
        title.text = n?.title ?: "Не исполняется"
        artist.text = n?.artist.orEmpty()
        play.setIcon(if (n?.playing == true) R.drawable.g_pause else R.drawable.g_play)
        listOf(prev, play, next).forEach { it.isEnabled = n != null }
        progress.visibility = if (n != null && n.duration > 0) VISIBLE else INVISIBLE
        elapsed.visibility = progress.visibility
        remain.visibility = progress.visibility
        updateProgress()
        refreshVolume()
        removeCallbacks(tick)
        if (n?.playing == true && isAttachedToWindow) post(tick)
    }

    fun refreshVolume() {
        if (withVolume && !volume.dragging) volume.set(Controls.volume(context))
    }

    private fun updateProgress() {
        val n = now ?: return
        if (n.duration <= 0 || progress.dragging) return
        val pos = n.position()
        progress.set(pos / n.duration.toFloat())
        showTimes(pos, n.duration)
    }

    private fun showTimes(pos: Long, dur: Long) {
        elapsed.text = fmt(pos)
        remain.text = "−" + fmt((dur - pos).coerceAtLeast(0))
    }

    private fun fmt(ms: Long): String {
        val s = ms / 1000
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (now?.playing == true) post(tick)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    private val pad get() = dp(20).roundToInt()
    private val artSize get() = dp(58).roundToInt()

    /** Высота для заданной ширины — чтобы карточка знала, сколько места взять. */
    fun preferredHeight(): Int {
        fun d(v: Int) = dp(v).roundToInt()
        var h = pad + artSize + d(14) + d(20) + d(2) + d(18) + d(54)
        if (withVolume) h += d(14) + d(20)
        return h + pad
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        fun ex(v: Int) = MeasureSpec.makeMeasureSpec(v, MeasureSpec.EXACTLY)
        val un = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        art.measure(ex(artSize), ex(artSize))
        val o = dp(40).roundToInt()
        output.measure(ex(o), ex(o))
        val tw = MeasureSpec.makeMeasureSpec((w - pad * 2 - artSize - dp(12).roundToInt() - o).coerceAtLeast(0), MeasureSpec.AT_MOST)
        title.measure(tw, un)
        artist.measure(tw, un)
        progress.measure(ex(w - pad * 2), ex(dp(20).roundToInt()))
        val half = MeasureSpec.makeMeasureSpec((w - pad * 2) / 2, MeasureSpec.EXACTLY)
        elapsed.measure(half, un)
        remain.measure(half, un)
        val b = dp(54).roundToInt()
        prev.measure(ex(b), ex(b)); play.measure(ex(b), ex(b)); next.measure(ex(b), ex(b))
        volume.measure(ex(w - pad * 2), ex(dp(20).roundToInt()))
        val h = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) MeasureSpec.getSize(heightMeasureSpec) else preferredHeight()
        setMeasuredDimension(w, h)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val w = r - l
        var y = pad
        art.layout(pad, y, pad + artSize, y + artSize)
        val tx = pad + artSize + dp(12).roundToInt()
        val th = title.measuredHeight + dp(3).roundToInt() + artist.measuredHeight
        var ty = y + (artSize - th) / 2
        title.layout(tx, ty, tx + title.measuredWidth, ty + title.measuredHeight)
        ty += title.measuredHeight + dp(3).roundToInt()
        artist.layout(tx, ty, tx + artist.measuredWidth, ty + artist.measuredHeight)
        val o = output.measuredWidth
        output.layout(w - pad - o + dp(8).roundToInt(), y + (artSize - o) / 2, w - pad + dp(8).roundToInt(), y + (artSize + o) / 2)
        y += artSize + dp(14).roundToInt()
        progress.layout(pad, y, w - pad, y + progress.measuredHeight)
        y += progress.measuredHeight + dp(2).roundToInt()
        elapsed.layout(pad, y, pad + elapsed.measuredWidth, y + elapsed.measuredHeight)
        remain.layout(w - pad - remain.measuredWidth, y, w - pad, y + remain.measuredHeight)
        y += dp(18).roundToInt()
        val bs = prev.measuredWidth
        listOf(prev, play, next).forEachIndexed { i, v ->
            val cx = (w * (0.25f + 0.25f * i)).roundToInt()
            v.layout(cx - bs / 2, y, cx + bs / 2, y + bs)
        }
        y += bs + dp(14).roundToInt()
        if (withVolume) volume.layout(pad, y, w - pad, y + volume.measuredHeight)
    }
}
