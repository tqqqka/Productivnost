package com.tihon.shtorka.ui.cc

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import com.tihon.shtorka.R
import com.tihon.shtorka.system.MediaRepo
import com.tihon.shtorka.ui.ArtView
import com.tihon.shtorka.ui.Fonts
import com.tihon.shtorka.ui.GlyphButton
import com.tihon.shtorka.ui.Palette
import com.tihon.shtorka.ui.blend
import com.tihon.shtorka.ui.dp
import com.tihon.shtorka.ui.fitWidth
import com.tihon.shtorka.ui.text
import kotlin.math.roundToInt

/** Блок связи 2×2: авиарежим, сотовые данные, Wi-Fi, Bluetooth. */
class ConnectivityModule(ctx: Context) : ModuleView(ctx) {
    val airplane = CircleToggle(ctx, R.drawable.g_airplane, Palette.ORANGE)
    val cellular = CircleToggle(ctx, R.drawable.g_cellular, Palette.GREEN)
    val wifi = CircleToggle(ctx, R.drawable.g_wifi, Palette.BLUE)
    val bluetooth = CircleToggle(ctx, R.drawable.g_bluetooth, Palette.BLUE)
    val circles = listOf(airplane, cellular, wifi, bluetooth)

    init {
        circles.forEach { addView(it) }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val d = (w / 2f * 0.70f).roundToInt()
        val spec = MeasureSpec.makeMeasureSpec(d, MeasureSpec.EXACTLY)
        circles.forEach { it.measure(spec, spec) }
        setMeasuredDimension(w, h)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val w = r - l
        val h = b - t
        circles.forEachIndexed { i, v ->
            val cx = w * (if (i % 2 == 0) 0.27f else 0.73f)
            val cy = h * (if (i < 2) 0.27f else 0.73f)
            val d = v.measuredWidth
            v.layout((cx - d / 2f).roundToInt(), (cy - d / 2f).roundToInt(), (cx + d / 2f).roundToInt(), (cy + d / 2f).roundToInt())
        }
    }
}

/** «Фокусирование» 2×1: луна и подпись; включённый — белый, как в iOS. */
class FocusModule(ctx: Context) : ModuleView(ctx) {
    private val moon = CircleToggle(ctx, R.drawable.g_moon, Palette.PURPLE, offFill = 0x33FFFFFF, glyphFraction = 0.5f)
    private val label: TextView = ctx.text(15f, Fonts.semibold)
    private val sub: TextView = ctx.text(12.5f, Fonts.medium, 0x8C000000.toInt())
    private var on = false
    private var t = 0f
    private var anim: ValueAnimator? = null

    init {
        addView(moon)
        addView(label)
        addView(sub)
        label.text = "Фокусирование"
        sub.text = "Не беспокоить"
        sub.alpha = 0f
    }

    fun set(enabled: Boolean, animate: Boolean = true) {
        if (enabled == on && anim?.isRunning != true) {
            if ((t == 1f) == enabled) return
        }
        on = enabled
        moon.setActive(enabled, animate)
        label.text = if (enabled) "Не беспокоить" else "Фокусирование"
        requestLayout()
        anim?.cancel()
        val target = if (enabled) 1f else 0f
        if (!animate || !isShown) { t = target; apply(); return }
        anim = ValueAnimator.ofFloat(t, target).apply {
            duration = 230
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener { t = it.animatedValue as Float; apply() }
            start()
        }
    }

    private fun apply() {
        platterColor = blend(Palette.GLASS, 0xF2FFFFFF.toInt(), t)
        rimAlpha = 1f - t
        label.setTextColor(blend(Palette.WHITE, Palette.BLACK, t))
        sub.alpha = t
        sub.text = "Вкл."
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val d = (h * 0.56f).roundToInt()
        moon.measure(MeasureSpec.makeMeasureSpec(d, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(d, MeasureSpec.EXACTLY))
        val avail = (w - d - (h - d) / 2f - dp(8) - dp(9)).roundToInt().coerceAtLeast(0)
        label.fitWidth(avail, 15f, 11f)
        val textW = MeasureSpec.makeMeasureSpec(avail, MeasureSpec.AT_MOST)
        val unspec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        label.measure(textW, unspec)
        sub.measure(textW, unspec)
        setMeasuredDimension(w, h)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val h = b - t
        val d = moon.measuredWidth
        val pad = (h - d) / 2
        moon.layout(pad, pad, pad + d, pad + d)
        val x = pad + d + dp(8).roundToInt()
        val total = label.measuredHeight + (if (on || this.t > 0f) sub.measuredHeight + dp(2).roundToInt() else 0)
        var y = (h - total) / 2
        label.layout(x, y, x + label.measuredWidth, y + label.measuredHeight)
        y += label.measuredHeight + dp(2).roundToInt()
        sub.layout(x, y, x + sub.measuredWidth, y + sub.measuredHeight)
    }

}

/** «Не воспроизводится» / обложка, название и кнопки — малый плеер 2×2. */
class MediaModule(ctx: Context) : ModuleView(ctx) {
    private val art = ArtView(ctx).apply { radiusFraction = 0.2f }
    val output = GlyphButton(ctx, R.drawable.g_airplay, 0.62f, 0xCCFFFFFF.toInt())
    private val title = ctx.text(15f, Fonts.semibold)
    private val artist = ctx.text(13f, Fonts.medium, Palette.TEXT_SECONDARY)
    val prev = GlyphButton(ctx, R.drawable.g_prev, 0.62f)
    val play = GlyphButton(ctx, R.drawable.g_play, 0.70f)
    val next = GlyphButton(ctx, R.drawable.g_next, 0.62f)

    init {
        addView(art); addView(output); addView(title); addView(artist)
        addView(prev); addView(play); addView(next)
        prev.onTap = { MediaRepo.prev() }
        play.onTap = { MediaRepo.playPause() }
        next.onTap = { MediaRepo.next() }
        bind(null)
    }

    fun bind(n: MediaRepo.Now?) {
        art.setBitmap(n?.art)
        title.text = n?.title ?: "Не исполняется"
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15f)
        title.setTextColor(if (n == null) Palette.TEXT_SECONDARY else Palette.WHITE)
        artist.text = n?.artist.orEmpty()
        artist.visibility = if (n?.artist.isNullOrBlank()) GONE else VISIBLE
        play.setIcon(if (n?.playing == true) R.drawable.g_pause else R.drawable.g_play)
        listOf(prev, play, next).forEach { it.isEnabled = n != null }
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val pad = w * 0.11f
        val artS = (w * 0.28f).roundToInt()
        art.measure(exact(artS), exact(artS))
        val o = (w * 0.2f).roundToInt()
        output.measure(exact(o), exact(o))
        val tw = MeasureSpec.makeMeasureSpec((w - pad * 2).roundToInt(), MeasureSpec.AT_MOST)
        val un = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        if (artist.visibility != VISIBLE) title.fitWidth((w - pad * 2).roundToInt(), 15f, 12f)
        title.measure(tw, un)
        artist.measure(tw, un)
        val b = (w * 0.24f).roundToInt()
        prev.measure(exact(b), exact(b)); play.measure(exact(b), exact(b)); next.measure(exact(b), exact(b))
        setMeasuredDimension(w, h)
    }

    private fun exact(v: Int) = MeasureSpec.makeMeasureSpec(v, MeasureSpec.EXACTLY)

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val w = r - l
        val h = b - t
        val pad = (w * 0.11f).roundToInt()
        art.layout(pad, pad, pad + art.measuredWidth, pad + art.measuredHeight)
        val o = output.measuredWidth
        val oy = pad + (art.measuredHeight - o) / 2
        output.layout(w - pad - o + o / 5, oy, w - pad + o / 5, oy + o)
        val by = h - pad - prev.measuredHeight + prev.measuredHeight / 6
        val bs = prev.measuredWidth
        listOf(prev, play, next).forEachIndexed { i, v ->
            val cx = (w * (0.22f + 0.28f * i)).roundToInt()
            v.layout(cx - bs / 2, by, cx + bs / 2, by + bs)
        }
        // Текст — между обложкой и кнопками, ближе к кнопкам.
        val textH = title.measuredHeight + if (artist.visibility == VISIBLE) artist.measuredHeight + dp(2).roundToInt() else 0
        val top = pad + art.measuredHeight
        val y = top + ((by - top) - textH) / 2 + dp(2).roundToInt()
        title.layout(pad, y, pad + title.measuredWidth, y + title.measuredHeight)
        val ay = y + title.measuredHeight + dp(2).roundToInt()
        artist.layout(pad, ay, pad + artist.measuredWidth, ay + artist.measuredHeight)
    }
}
