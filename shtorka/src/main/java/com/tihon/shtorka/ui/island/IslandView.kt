package com.tihon.shtorka.ui.island

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.tihon.shtorka.R
import com.tihon.shtorka.core.Spring
import com.tihon.shtorka.system.Controls
import com.tihon.shtorka.system.MediaRepo
import com.tihon.shtorka.ui.ArtView
import com.tihon.shtorka.ui.Fonts
import com.tihon.shtorka.ui.MediaPanel
import com.tihon.shtorka.ui.Palette
import com.tihon.shtorka.ui.dp
import com.tihon.shtorka.ui.text
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Островок, сросшийся с каплей камеры. Форма — чёрная «капля-пилюля»,
 * которая вогнутыми скруглениями переходит в верхний край экрана, поэтому
 * выглядит продолжением выреза, а не отдельной плашкой.
 */
@SuppressLint("ViewConstructor")
class IslandView(ctx: Context, private val ctl: IslandController) : FrameLayout(ctx) {

    enum class Kind { HIDDEN, MEDIA, MEDIA_EXPANDED, CHARGING, RINGER }

    var notch = Rect()
    var screenW = 0

    var kind = Kind.HIDDEN
        private set

    // текущая форма
    private var shapeW = 0f
    private var shapeH = 0f
    private var radius = 0f
    private val flare get() = dp(8)

    // place() во время пружины — только если не ждём замера: layout() у детей
    // сбрасывает их флаг «перемерь меня», и новый текст остался бы нулевой ширины.
    private val springW = Spring(0f, 0.4f) { shapeW = it; invalidate(); if (!isLayoutRequested) place() }
    private val springH = Spring(0f, 0.4f) { shapeH = it; invalidate(); if (!isLayoutRequested) place() }
    private val springR = Spring(0f, 0.4f) { radius = it; invalidate() }
    private var pendingEnds = 0

    private val black = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.BLACK }
    private val path = Path()
    private val arc = android.graphics.RectF()

    // компактное содержимое
    private val art = ArtView(ctx).apply { radiusFraction = 0.28f }
    private val wave = WaveformView(ctx)
    private val leftIcon = ImageView(ctx)
    private val leftText: TextView = ctx.text(12.5f, Fonts.semibold)
    private val rightText: TextView = ctx.text(12.5f, Fonts.semibold)
    private val battery = BatteryGlyph(ctx)
    private val compactViews = listOf<View>(art, wave, leftIcon, leftText, rightText, battery)

    // раскрытое
    private val panel = MediaPanel(ctx, withVolume = false)

    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var tracking = false

    init {
        setWillNotDraw(false)
        clipChildren = true
        compactViews.forEach { addView(it); it.alpha = 0f }
        addView(panel)
        panel.alpha = 0f
        panel.visibility = INVISIBLE
        panel.onOpenApp = { ctl.openMediaApp() }
        panel.onOutput = { ctl.openOutput() }
    }

    // ---------------- форма ----------------

    private fun target(k: Kind): FloatArray {
        val nw = notch.width().toFloat()
        val nb = notch.bottom.toFloat()
        // Компактный островок — чуть ниже капли, с полностью круглыми нижними углами.
        val ch = max(nb + dp(3), dp(28))
        return when (k) {
            Kind.HIDDEN -> floatArrayOf(nw * 0.7f, nb * 0.6f, nb * 0.3f)
            Kind.MEDIA -> floatArrayOf(nw + dp(44) * 2, ch, ch / 2)
            Kind.CHARGING -> floatArrayOf(nw + ear(rightText.paint.measureText(rightText.text.toString()) + dp(5) + dp(25)) * 2, ch, ch / 2)
            Kind.RINGER -> floatArrayOf(nw + ear(rightText.paint.measureText(rightText.text.toString())) * 2, ch, ch / 2)
            Kind.MEDIA_EXPANDED -> {
                val w = screenW - dp(16) * 2
                floatArrayOf(w, nb + dp(2) + panel.preferredHeight(), dp(52))
            }
        }
    }

    /** «Ухо» слева/справа от капли: под самое широкое содержимое, не шире нужного. */
    private fun ear(content: Float) = max(dp(18), content) + dp(10) + dp(8)

    /** Размер окна под форму (с запасом на «перелёт» пружины и скругления-крылья). */
    fun windowSizeFor(w: Float, h: Float): IntArray =
        intArrayOf((w * 1.06f + flare * 2 + dp(8)).roundToInt(), (h * 1.06f + dp(6)).roundToInt())

    fun setKind(k: Kind, animate: Boolean = true) {
        val prev = kind
        kind = k
        bindContent()
        val t = target(k)
        if (prev == Kind.HIDDEN && k != Kind.HIDDEN) {
            // Растём прямо из капли.
            val h = target(Kind.HIDDEN)
            springW.snap(h[0]); springH.snap(h[1]); springR.snap(h[2])
            visibility = VISIBLE
        }
        ctl.growWindow(max(shapeW, t[0]), max(shapeH, t[1]))
        if (!animate) {
            springW.snap(t[0]); springH.snap(t[1]); springR.snap(t[2])
            crossfade(prev, k, immediate = true)
            settled()
            return
        }
        val bigger = t[0] > shapeW || t[1] > shapeH
        val stiff = if (bigger) 230f else 360f
        val damp = if (bigger) 0.66f else 0.86f
        pendingEnds = 2
        val end: (Boolean) -> Unit = { canceled -> if (!canceled && --pendingEnds == 0) settled() }
        springW.animateTo(t[0], stiffness = stiff, damping = damp, onEnd = end)
        springH.animateTo(t[1], stiffness = stiff * 1.1f, damping = damp, onEnd = end)
        springR.animateTo(t[2], stiffness = stiff, damping = 0.9f)
        crossfade(prev, k)
    }

    private fun settled() {
        if (kind == Kind.HIDDEN) {
            visibility = INVISIBLE
            ctl.onHidden()
        }
        ctl.fitWindow(shapeW, shapeH)
    }

    override fun onDraw(c: Canvas) {
        if (shapeW <= 0f || shapeH <= 0f) return
        val cx = width / 2f
        val l = cx - shapeW / 2
        val r = cx + shapeW / 2
        val b = shapeH
        val f = min(flare, shapeW / 4)
        val rad = min(radius, min(shapeW / 2, b - f)).coerceAtLeast(0f)
        path.reset()
        path.moveTo(l - f, -1f)
        path.lineTo(l - f, 0f)
        // вогнутое крыло: верхний край плавно уходит в бок островка
        path.cubicTo(l - f * 0.45f, 0f, l, f * 0.45f, l, f)
        path.lineTo(l, b - rad)
        // нижние углы — настоящие дуги окружности, без «срезанных» кривых
        arc.set(l, b - rad * 2, l + rad * 2, b)
        path.arcTo(arc, 180f, -90f, false)
        path.lineTo(r - rad, b)
        arc.set(r - rad * 2, b - rad * 2, r, b)
        path.arcTo(arc, 90f, -90f, false)
        path.lineTo(r, f)
        path.cubicTo(r, f * 0.45f, r + f * 0.45f, 0f, r + f, 0f)
        path.lineTo(r + f, -1f)
        path.close()
        c.drawPath(path, black)
    }

    // ---------------- содержимое ----------------

    private fun bindContent() {
        val n = MediaRepo.now
        when (kind) {
            Kind.MEDIA -> {
                art.setBitmap(n?.art)
                wave.playing = n?.playing == true
                wave.color = n?.accent ?: Palette.WHITE
            }
            Kind.MEDIA_EXPANDED -> panel.bind(n)
            Kind.CHARGING -> {
                val bat = Controls.battery(context)
                leftIcon.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.g_bolt)!!.mutate().also { it.setTint(Palette.GREEN) })
                leftText.text = ""
                rightText.text = "${bat.level}%"
                rightText.setTextColor(Palette.GREEN)
                battery.level = bat.level
            }
            Kind.RINGER -> {
                val m = Controls.ringerMode(context)
                val icon = when (m) {
                    android.media.AudioManager.RINGER_MODE_NORMAL -> R.drawable.g_bell
                    android.media.AudioManager.RINGER_MODE_VIBRATE -> R.drawable.g_vibration
                    else -> R.drawable.g_bell_off
                }
                val color = if (m == android.media.AudioManager.RINGER_MODE_NORMAL) Palette.WHITE else Palette.RED
                leftIcon.setImageDrawable(ContextCompat.getDrawable(context, icon)!!.mutate().also { it.setTint(color) })
                leftText.text = ""
                rightText.text = when (m) {
                    android.media.AudioManager.RINGER_MODE_NORMAL -> "Звонок"
                    android.media.AudioManager.RINGER_MODE_VIBRATE -> "Вибрация"
                    else -> "Бесшумно"
                }
                rightText.setTextColor(color)
            }
            Kind.HIDDEN -> Unit
        }
        requestLayout()
    }

    /** Обновить музыку без смены формы. */
    fun refreshMedia() {
        val n = MediaRepo.now
        when (kind) {
            Kind.MEDIA -> {
                art.setBitmap(n?.art)
                wave.playing = n?.playing == true
                wave.color = n?.accent ?: Palette.WHITE
            }
            Kind.MEDIA_EXPANDED -> panel.bind(n)
            else -> Unit
        }
    }

    private fun visibleFor(k: Kind): List<View> = when (k) {
        Kind.MEDIA -> listOf(art, wave)
        Kind.CHARGING -> listOf(leftIcon, rightText, battery)
        Kind.RINGER -> listOf(leftIcon, rightText)
        Kind.MEDIA_EXPANDED -> listOf(panel)
        Kind.HIDDEN -> emptyList()
    }

    private fun crossfade(from: Kind, to: Kind, immediate: Boolean = false) {
        val show = visibleFor(to)
        (compactViews + panel).forEach { v ->
            v.animate().cancel()
            if (immediate) {
                v.alpha = if (v in show) 1f else 0f
                v.scaleX = 1f; v.scaleY = 1f
                v.visibility = if (v in show) VISIBLE else INVISIBLE
                return@forEach
            }
            if (v in show) {
                v.visibility = VISIBLE
                v.scaleX = 0.8f; v.scaleY = 0.8f
                if (from == to) { v.alpha = 1f; v.scaleX = 1f; v.scaleY = 1f; return@forEach }
                v.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(if (to == Kind.MEDIA_EXPANDED) 120 else 90)
                    .setDuration(220).start()
            } else if (v.alpha > 0f) {
                v.animate().alpha(0f).setStartDelay(0).setDuration(90).withEndAction {
                    if (v === panel) panel.visibility = INVISIBLE
                }.start()
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val ch = target(Kind.MEDIA)[1]
        val icon = (ch - dp(10)).roundToInt()
        fun ex(v: Int) = MeasureSpec.makeMeasureSpec(v, MeasureSpec.EXACTLY)
        val un = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        art.measure(ex(icon), ex(icon))
        wave.measure(ex(dp(24).roundToInt()), ex(dp(14).roundToInt()))
        val ic = dp(18).roundToInt()
        leftIcon.measure(ex(ic), ex(ic))
        val tw = MeasureSpec.makeMeasureSpec(dp(120).roundToInt(), MeasureSpec.AT_MOST)
        leftText.measure(tw, un)
        rightText.measure(tw, un)
        battery.measure(ex(dp(25).roundToInt()), ex(dp(12).roundToInt()))
        val pw = (screenW - dp(16) * 2).roundToInt()
        panel.measure(ex(pw), MeasureSpec.makeMeasureSpec(panel.preferredHeight(), MeasureSpec.EXACTLY))
        setMeasuredDimension(w, h)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) = place()

    /** Раскладка относительно текущей ширины формы — содержимое едет вместе с краями. */
    private fun place() {
        if (width == 0) return
        val cx = width / 2f
        val tw = if (kind == Kind.MEDIA_EXPANDED || kind == Kind.HIDDEN) target(Kind.MEDIA)[0] else target(kind)[0]
        val curW = if (kind == Kind.HIDDEN) shapeW else max(shapeW, tw * 0.6f)
        val left = cx - curW / 2
        val right = cx + curW / 2
        val ch = target(Kind.MEDIA)[1]
        val cy = ch / 2 - dp(0.5f)
        fun put(v: View, x: Float) {
            val y = cy - v.measuredHeight / 2f
            v.layout(x.roundToInt(), y.roundToInt(), (x + v.measuredWidth).roundToInt(), (y + v.measuredHeight).roundToInt())
        }
        val pad = dp(6)
        put(art, left + pad)
        put(wave, right - pad - dp(4) - wave.measuredWidth)
        put(leftIcon, left + pad + dp(3))
        put(leftText, left + pad + dp(3) + leftIcon.measuredWidth + dp(5))
        put(battery, right - pad - dp(4) - battery.measuredWidth)
        val rtRight = if (kind == Kind.CHARGING) right - pad - dp(4) - battery.measuredWidth - dp(5) else right - pad - dp(4)
        put(rightText, rtRight - rightText.measuredWidth)
        val pw = panel.measuredWidth
        val px = (cx - pw / 2f).roundToInt()
        val py = (notch.bottom + dp(2)).roundToInt()
        panel.layout(px, py, px + pw, py + panel.measuredHeight)
    }

    // ---------------- касания ----------------

    private fun inShape(x: Float, y: Float): Boolean {
        val cx = width / 2f
        return x in (cx - shapeW / 2)..(cx + shapeW / 2) && y <= shapeH
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_OUTSIDE -> {
                if (kind == Kind.MEDIA_EXPANDED) ctl.collapse()
                return false
            }
            MotionEvent.ACTION_DOWN -> {
                if (!inShape(e.x, e.y)) return false
                downX = e.x; downY = e.y
                tracking = true
                animate().scaleX(0.97f).scaleY(0.97f).setDuration(100).start()
                pivotX = width / 2f; pivotY = 0f
            }
            MotionEvent.ACTION_MOVE -> {
                if (tracking && kind == Kind.MEDIA_EXPANDED && downY - e.y > dp(28)) {
                    tracking = false
                    animate().scaleX(1f).scaleY(1f).setDuration(160).start()
                    ctl.collapse()
                } else if (tracking && (abs(e.x - downX) > slop * 2 || abs(e.y - downY) > slop * 2)) {
                    tracking = false
                    animate().scaleX(1f).scaleY(1f).setDuration(160).start()
                }
            }
            MotionEvent.ACTION_UP -> {
                animate().scaleX(1f).scaleY(1f).setDuration(160).start()
                if (tracking) ctl.onTap()
                tracking = false
            }
            MotionEvent.ACTION_CANCEL -> {
                animate().scaleX(1f).scaleY(1f).setDuration(160).start()
                tracking = false
            }
        }
        return true
    }

    /** Маленькая батарейка для режима зарядки. */
    class BatteryGlyph(ctx: Context) : View(ctx) {
        var level = 100
            set(v) { field = v; invalidate() }
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(c: Canvas) {
            com.tihon.shtorka.ui.Glyphs.battery(c, 0f, height / 2f, width.toFloat(), height.toFloat(), level, Palette.GREEN, p)
        }
    }
}
