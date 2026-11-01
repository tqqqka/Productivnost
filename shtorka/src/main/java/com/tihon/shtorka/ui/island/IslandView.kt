package com.tihon.shtorka.ui.island

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Region
import android.media.AudioManager
import android.os.Build
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.Chronometer
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.tihon.shtorka.R
import com.tihon.shtorka.core.Prefs
import com.tihon.shtorka.core.Spring
import com.tihon.shtorka.system.Controls
import com.tihon.shtorka.system.Kind
import com.tihon.shtorka.system.Live
import com.tihon.shtorka.ui.ArtView
import com.tihon.shtorka.ui.Fonts
import com.tihon.shtorka.ui.Palette
import com.tihon.shtorka.ui.dp
import com.tihon.shtorka.ui.text
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Островок, сросшийся с каплей камеры. Чёрная форма вогнутыми «крыльями»
 * переходит в верхний край экрана, поэтому выглядит продолжением выреза.
 *
 * Форма всегда рисуется точно над каплей в координатах экрана, поэтому
 * когда окно меняет размер, островок не дёргается ни влево, ни вправо.
 */
@SuppressLint("ViewConstructor")
class IslandView(ctx: Context, private val ctl: IslandController) : FrameLayout(ctx) {

    enum class Mode { HIDDEN, IDLE, COMPACT, EXPANDED }

    var notch = Rect()
    var screenW = 0

    var mode = Mode.HIDDEN
        private set
    var primary: Live? = null
        private set
    var secondary: Live? = null
        private set

    // ---------- форма ----------
    private var shapeW = 0f
    private var shapeH = 0f
    private var radius = 0f
    private var bubble = 0f
    private var target = FloatArray(3)
    private val springW = Spring(0f, 0.3f) { shapeW = it; frame() }
    private val springH = Spring(0f, 0.3f) { shapeH = it; frame() }
    private val springR = Spring(0f, 0.3f) { radius = it; invalidate() }
    private val springB = Spring(0f, 0.002f) { bubble = it; frame() }
    private var pending = 0

    private val black = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.BLACK }
    private val path = Path()
    private val arc = RectF()
    private val loc = IntArray(2)

    // ---------- компактное содержимое ----------
    private val leftBox = FrameLayout(ctx)
    private val rightBox = FrameLayout(ctx)
    private val lArt = ArtView(ctx).apply { radiusFraction = 0.28f }
    private val lIcon = ImageView(ctx)
    private val lLock = LockGlyph(ctx)
    private val lDot = RecDot(ctx)
    private val lBat = BatteryGlyph(ctx)
    private val rWave = WaveformView(ctx)
    private val rChrono = Chronometer(ctx).apply {
        typeface = Fonts.semibold
        fontFeatureSettings = "tnum"
        includeFontPadding = false
    }
    private val rText: TextView = ctx.text(12.5f, Fonts.semibold)
    private val rRing = ProgressRing(ctx)
    private val rBat = BatteryGlyph(ctx)
    private val lefts = listOf<View>(lArt, lIcon, lLock, lDot, lBat)
    private val rights = listOf<View>(rWave, rChrono, rText, rRing, rBat)

    // ---------- вторая активность («капелька» справа) ----------
    private val bubbleBox = FrameLayout(ctx)
    private val bArt = ArtView(ctx).apply { circle = true }
    private val bIcon = ImageView(ctx)

    // ---------- раскрытое ----------
    private val cardBox = FrameLayout(ctx)
    private var card: IslandCard? = null
    private val cards = HashMap<String, IslandCard>()

    // ---------- касания ----------
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var tracking = false
    private var inBubble = false
    private var longFired = false
    private val longPress = Runnable {
        if (tracking) {
            longFired = true
            tracking = false
            if (inBubble) ctl.onBubbleLongPress() else ctl.onLongPress()
        }
    }

    init {
        setWillNotDraw(false)
        clipChildren = false
        lefts.forEach { leftBox.addView(it, LayoutParams(0, 0)) }
        rights.forEach { rightBox.addView(it, LayoutParams(-2, -2)) }
        addView(leftBox, LayoutParams(-2, -2))
        addView(rightBox, LayoutParams(-2, -2))
        bubbleBox.addView(bArt, LayoutParams(0, 0))
        bubbleBox.addView(bIcon, LayoutParams(0, 0))
        addView(bubbleBox, LayoutParams(0, 0))
        addView(cardBox, LayoutParams(0, -2))
        listOf<View>(leftBox, rightBox, cardBox, bubbleBox).forEach { it.alpha = 0f }
        cardBox.visibility = INVISIBLE
    }

    // ---------- размеры ----------

    private val s get() = Prefs.sizeScale
    private fun sd(v: Number) = dp(v) * s
    private val flare get() = dp(8)
    private val compactH get() = max(notch.bottom + sd(3), sd(28))
    private val bubbleGap get() = sd(6)
    private val expandedW get() = screenW - dp(14) * 2

    /** Центр капли в координатах этого окна — откуда бы окно ни начиналось. */
    private fun cx(): Float {
        getLocationOnScreen(loc)
        return notch.exactCenterX() - loc[0]
    }

    private fun ear(): Float {
        val content = max(leftBox.measuredWidth, rightBox.measuredWidth).toFloat()
        return max(content, sd(18)) + sd(9) + sd(7)
    }

    private fun targetFor(m: Mode): FloatArray {
        val nw = notch.width().toFloat()
        val nb = notch.bottom.toFloat()
        val ch = compactH
        return when (m) {
            Mode.HIDDEN -> floatArrayOf(nw * 0.7f, nb * 0.6f, nb * 0.3f)
            Mode.IDLE -> floatArrayOf(nw + sd(20) * 2, ch, ch / 2)
            Mode.COMPACT -> floatArrayOf(nw + ear() * 2, ch, ch / 2)
            Mode.EXPANDED -> {
                val h = nb + dp(2) + (card?.preferredHeight(expandedW.roundToInt()) ?: dp(80).roundToInt())
                floatArrayOf(expandedW, h, min(dp(46), h / 2))
            }
        }
    }

    /** Нужный окну размер под форму (с запасом на «перелёт» пружины). */
    fun windowNeed(w: Float, h: Float, withBubble: Boolean): IntArray {
        val half = w / 2 + if (withBubble) bubbleGap + compactH else 0f
        return intArrayOf(((half * 1.06f + flare) * 2 + dp(8)).roundToInt(), (h * 1.08f + dp(8)).roundToInt())
    }

    // ---------- главное: показать состояние ----------

    fun render(p: Live?, sec: Live?, expanded: Boolean, animate: Boolean = true) {
        val newMode = when {
            p == null -> if (Prefs.idlePill && ctl.canShowIdle()) Mode.IDLE else Mode.HIDDEN
            expanded -> Mode.EXPANDED
            else -> Mode.COMPACT
        }
        val same = newMode == mode && p?.key == primary?.key && p?.kind == primary?.kind
        val prevMode = mode
        primary = p
        secondary = sec
        mode = newMode

        if (prevMode == Mode.HIDDEN && newMode != Mode.HIDDEN) {
            val h = targetFor(Mode.HIDDEN)
            springW.snap(h[0]); springH.snap(h[1]); springR.snap(h[2])
            visibility = VISIBLE
        }

        when (newMode) {
            Mode.COMPACT -> bindCompact(p!!)
            Mode.EXPANDED -> bindCard(p!!)
            else -> Unit
        }
        bindBubble(if (newMode == Mode.COMPACT) sec else null)

        target = targetFor(newMode)
        val wantBubble = newMode == Mode.COMPACT && sec != null
        ctl.growWindow(windowNeed(max(shapeW, target[0]), max(shapeH, target[1]), wantBubble || bubble > 0.01f))
        updateTouchRegion(max(shapeW, target[0]), max(shapeH, target[1]), wantBubble || bubble > 0.01f)
        requestLayout()

        if (!animate) {
            springW.snap(target[0]); springH.snap(target[1]); springR.snap(target[2])
            springB.snap(if (wantBubble) 1f else 0f)
            showContent(prevMode, newMode, same, immediate = true)
            settled()
            return
        }
        val grow = target[0] > shapeW + 1f || target[1] > shapeH + 1f
        val stiff = if (grow) 260f else 380f
        val damp = if (grow) 0.68f else 0.86f
        pending = 2
        val end: (Boolean) -> Unit = { c -> if (!c && --pending == 0) settled() }
        springW.animateTo(target[0], stiffness = stiff, damping = damp, onEnd = end)
        springH.animateTo(target[1], stiffness = stiff * 1.1f, damping = damp, onEnd = end)
        springR.animateTo(target[2], stiffness = stiff, damping = 0.9f)
        springB.animateTo(if (wantBubble) 1f else 0f, stiffness = 300f, damping = 0.62f)
        if (!same) showContent(prevMode, newMode, same = false, immediate = false)
    }

    private fun settled() {
        if (mode == Mode.HIDDEN) {
            visibility = INVISIBLE
            card?.let { stopChronos(it) }
            rChrono.stop()
        }
        val withBubble = mode == Mode.COMPACT && secondary != null
        ctl.fitWindow(windowNeed(shapeW, shapeH, withBubble))
        updateTouchRegion(shapeW, shapeH, withBubble)
    }

    private fun stopChronos(v: View) {
        if (v is Chronometer) v.stop()
        if (v is android.view.ViewGroup) for (i in 0 until v.childCount) stopChronos(v.getChildAt(i))
    }

    /** Андроид 14+: окно на всю ширину, но касания берём только на самом островке. */
    private fun updateTouchRegion(w: Float, h: Float, withBubble: Boolean) {
        if (Build.VERSION.SDK_INT < 34 || !isAttachedToWindow) return
        val c = cx()
        val right = c + w / 2 + flare + if (withBubble) bubbleGap + compactH else 0f
        val r = Rect((c - w / 2 - flare).roundToInt(), 0, right.roundToInt(), (h + dp(4)).roundToInt())
        runCatching { rootSurfaceControl?.setTouchableRegion(Region(r)) }
    }

    private fun showContent(prev: Mode, now: Mode, same: Boolean, immediate: Boolean) {
        val show = when (now) {
            Mode.COMPACT -> listOf<View>(leftBox, rightBox)
            Mode.EXPANDED -> listOf<View>(cardBox)
            else -> emptyList()
        }
        listOf<View>(leftBox, rightBox, cardBox).forEach { v ->
            v.animate().cancel()
            val on = v in show
            if (immediate || same) {
                v.alpha = if (on) 1f else 0f
                v.scaleX = 1f; v.scaleY = 1f
                v.visibility = if (on) VISIBLE else INVISIBLE
                return@forEach
            }
            if (on) {
                v.visibility = VISIBLE
                v.alpha = 0f
                v.scaleX = 0.82f; v.scaleY = 0.82f
                v.animate().alpha(1f).scaleX(1f).scaleY(1f)
                    .setStartDelay(if (now == Mode.EXPANDED) 110 else 80).setDuration(230).start()
            } else if (v.alpha > 0f) {
                v.animate().alpha(0f).scaleX(0.9f).scaleY(0.9f).setStartDelay(0).setDuration(90)
                    .withEndAction { v.visibility = INVISIBLE }.start()
            }
        }
        if (now != Mode.EXPANDED && prev == Mode.EXPANDED) card?.let { stopChronos(it) }
    }

    // ---------- компактный вид ----------

    private fun only(views: List<View>, keep: View?) = views.forEach { it.visibility = if (it === keep) VISIBLE else GONE }

    private fun icon(res: Int, color: Int) {
        lIcon.setImageDrawable(ContextCompat.getDrawable(context, res)!!.mutate().also { it.setTint(color) })
    }

    private fun bindCompact(l: Live) {
        val ch = compactH
        val artS = (ch - sd(10)).roundToInt()
        val glyph = sd(17).roundToInt()
        var left: View? = null
        var right: View? = null
        rText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f * s)
        rChrono.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f * s)
        rText.setTextColor(l.accent)
        rChrono.setTextColor(l.accent)

        fun chronoOrText(): View? = when {
            l.chronoBase != 0L -> { rChrono.bindTo(l); rChrono }
            !l.value.isNullOrBlank() -> { rText.text = l.value; rText }
            else -> null
        }

        when (l.kind) {
            Kind.MUSIC -> {
                lArt.circle = false
                lArt.setBitmap(l.art)
                left = lArt
                rWave.playing = l.playing
                rWave.color = l.accent
                right = rWave
            }
            Kind.CALL, Kind.CALL_INCOMING -> {
                icon(R.drawable.g_call, Palette.GREEN); left = lIcon
                right = chronoOrText() ?: rText.also { it.text = l.title }
            }
            Kind.TIMER, Kind.STOPWATCH -> { icon(R.drawable.g_timer, Palette.ORANGE); left = lIcon; right = chronoOrText() }
            Kind.RECORDING -> { left = lDot; right = chronoOrText() ?: rText.also { it.text = "REC"; it.setTextColor(Palette.RED) } }
            Kind.NAVIGATION -> {
                val d = l.largeIcon
                if (d != null) { lArt.circle = false; lArt.setDrawable(d); left = lArt } else { icon(R.drawable.g_navigation, Palette.WHITE); left = lIcon }
                rText.setTextColor(Palette.WHITE)
                right = chronoOrText()
            }
            Kind.PROGRESS -> {
                lArt.circle = false; lArt.setDrawable(l.appIcon); left = lArt
                rRing.color = l.accent; rRing.progress = l.progress.coerceAtLeast(0f); right = rRing
            }
            Kind.CHARGING -> {
                icon(R.drawable.g_bolt, Palette.GREEN); left = lIcon
                rText.text = "${l.level}%"; rText.setTextColor(Palette.GREEN); right = rText
            }
            Kind.LOW_BATTERY -> {
                lBat.level = l.level; lBat.fill = Palette.RED; left = lBat
                rText.text = "${l.level}%"; rText.setTextColor(Palette.RED); right = rText
            }
            Kind.RINGER -> {
                val m = Controls.ringerMode(context)
                val color = if (m == AudioManager.RINGER_MODE_NORMAL) Palette.WHITE else Palette.RED
                icon(
                    when (m) {
                        AudioManager.RINGER_MODE_NORMAL -> R.drawable.g_bell
                        AudioManager.RINGER_MODE_VIBRATE -> R.drawable.g_vibration
                        else -> R.drawable.g_bell_off
                    }, color,
                )
                left = lIcon
                rText.text = when (m) {
                    AudioManager.RINGER_MODE_NORMAL -> "Звонок"
                    AudioManager.RINGER_MODE_VIBRATE -> "Вибрация"
                    else -> "Бесшумно"
                }
                rText.setTextColor(color)
                right = rText
            }
            Kind.UNLOCK -> { left = lLock; lLock.play() }
            Kind.HEADPHONES -> {
                icon(R.drawable.g_headphones, Palette.WHITE); left = lIcon
                if (l.level >= 0) { rRing.color = Palette.GREEN; rRing.progress = l.level / 100f; right = rRing }
                else { rText.text = "Подключено"; rText.setTextColor(Palette.WHITE); right = rText }
            }
            Kind.BANNER -> { lArt.setDrawable(l.appIcon); left = lArt }
        }
        if (right !== rChrono) rChrono.stop()
        only(lefts, left)
        only(rights, right)

        // размеры слотов
        fun ex(v: Int) = MeasureSpec.makeMeasureSpec(v, MeasureSpec.EXACTLY)
        val un = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        val wide = MeasureSpec.makeMeasureSpec(sd(110).roundToInt(), MeasureSpec.AT_MOST)
        val lSize = when (left) {
            lArt -> artS
            lLock -> (glyph * 1.05f).roundToInt()
            lDot -> glyph
            lBat -> sd(24).roundToInt()
            else -> glyph
        }
        val lH = if (left === lBat) sd(11.5f).roundToInt() else lSize
        left?.layoutParams = LayoutParams(lSize, lH)
        left?.measure(ex(lSize), ex(lH))
        when (right) {
            rWave -> { rWave.layoutParams = LayoutParams(sd(22).roundToInt(), sd(13).roundToInt()) }
            rRing -> { val r = sd(17).roundToInt(); rRing.layoutParams = LayoutParams(r, r) }
            else -> right?.layoutParams = LayoutParams(-2, -2)
        }
        leftBox.measure(un, un)
        rightBox.measure(wide, un)
        leftBox.layoutParams = LayoutParams(leftBox.measuredWidth, leftBox.measuredHeight)
        rightBox.layoutParams = LayoutParams(min(rightBox.measuredWidth, sd(110).roundToInt()), rightBox.measuredHeight)
    }

    private fun bindBubble(l: Live?) {
        if (l == null) return
        val d = (compactH * 0.62f).roundToInt()
        if (l.kind == Kind.MUSIC && l.art != null) {
            bArt.setBitmap(l.art); bArt.visibility = VISIBLE; bIcon.visibility = GONE
            bArt.layoutParams = LayoutParams(d, d)
        } else {
            val res = when (l.kind) {
                Kind.MUSIC -> R.drawable.g_music
                Kind.CALL, Kind.CALL_INCOMING -> R.drawable.g_call
                Kind.TIMER, Kind.STOPWATCH -> R.drawable.g_timer
                Kind.RECORDING -> R.drawable.g_record
                Kind.NAVIGATION -> R.drawable.g_navigation
                else -> R.drawable.g_download
            }
            bIcon.setImageDrawable(ContextCompat.getDrawable(context, res)!!.mutate().also { it.setTint(l.accent) })
            bIcon.visibility = VISIBLE; bArt.visibility = GONE
            val g = (compactH * 0.5f).roundToInt()
            bIcon.layoutParams = LayoutParams(g, g)
        }
    }

    // ---------- раскрытый вид ----------

    private fun bindCard(l: Live) {
        val group = when (l.kind) {
            Kind.MUSIC -> "music"
            Kind.CALL, Kind.CALL_INCOMING -> "call"
            Kind.BANNER -> "banner"
            else -> "live"
        }
        val c = cards.getOrPut(group) {
            when (group) {
                "music" -> MusicCard(context).apply { panel.onOpenApp = { ctl.onTap() }; panel.onOutput = { ctl.openOutput() } }
                "call" -> CallCard(context)
                "banner" -> BannerCard(context)
                else -> LiveCard(context)
            }.also { it.onAction = { a -> ctl.runAction(a) } }
        }
        if (card !== c) {
            cardBox.removeAllViews()
            cardBox.addView(c, LayoutParams(-1, -2))
            card = c
        }
        c.bind(l)
        cardBox.layoutParams = LayoutParams(expandedW.roundToInt(), -2)
    }

    // ---------- раскладка ----------

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val un = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        fun ex(v: Int) = MeasureSpec.makeMeasureSpec(v, MeasureSpec.EXACTLY)
        leftBox.measure(un, un)
        rightBox.measure(MeasureSpec.makeMeasureSpec(sd(110).roundToInt(), MeasureSpec.AT_MOST), un)
        val d = compactH.roundToInt()
        bubbleBox.measure(ex(d), ex(d))
        cardBox.measure(ex(expandedW.roundToInt()), un)
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val c = cx()
        val ch = compactH
        val tw = if (mode == Mode.COMPACT) target[0] else targetFor(Mode.COMPACT)[0]
        val pad = sd(9)
        fun put(v: View, x: Float, centerY: Float) {
            val y = centerY - v.measuredHeight / 2f
            v.layout(x.roundToInt(), y.roundToInt(), (x + v.measuredWidth).roundToInt(), (y + v.measuredHeight).roundToInt())
        }
        put(leftBox, c - tw / 2 + pad, ch / 2)
        put(rightBox, c + tw / 2 - pad - rightBox.measuredWidth, ch / 2)
        val cw = cardBox.measuredWidth
        val cl = (c - cw / 2f).roundToInt()
        val ct = (notch.bottom + dp(2)).roundToInt()
        cardBox.layout(cl, ct, cl + cw, ct + cardBox.measuredHeight)
        cardBox.pivotX = cw / 2f
        cardBox.pivotY = 0f
        val d = bubbleBox.measuredWidth
        bubbleBox.layout(0, 0, d, d)
        layoutBubbleChild(bArt, d)
        layoutBubbleChild(bIcon, d)
        frame()
    }

    private fun layoutBubbleChild(v: View, d: Int) {
        val lp = v.layoutParams
        val w = if (lp.width > 0) lp.width else d / 2
        v.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY))
        val o = (d - w) / 2
        v.layout(o, o, o + w, o + w)
    }

    /**
     * Кадр анимации: только сдвиги и масштаб (свойства RenderNode),
     * без перекладки — содержимое едет вместе с краями формы.
     */
    private fun frame() {
        invalidate()
        if (width == 0) return
        val tw = if (mode == Mode.COMPACT) target[0] else targetFor(Mode.COMPACT)[0]
        val shift = (tw - shapeW) / 2f
        leftBox.translationX = if (mode == Mode.COMPACT) shift else 0f
        rightBox.translationX = if (mode == Mode.COMPACT) -shift else 0f
        val d = compactH
        bubbleBox.translationX = bubbleCenter() - d / 2
        bubbleBox.translationY = 0f
        bubbleBox.alpha = (bubble * 1.4f - 0.3f).coerceIn(0f, 1f)
        bubbleBox.pivotX = d / 2
        bubbleBox.pivotY = d / 2
        bubbleBox.scaleX = bubble.coerceAtLeast(0f)
        bubbleBox.scaleY = bubble.coerceAtLeast(0f)
    }

    override fun onDraw(c: Canvas) {
        if (shapeW <= 0f || shapeH <= 0f) return
        val cxx = cx()
        val l = cxx - shapeW / 2
        val r = cxx + shapeW / 2
        val b = shapeH
        val f = min(flare, shapeW / 4)
        val rad = min(radius, min(shapeW / 2, b - f)).coerceAtLeast(0f)
        path.reset()
        path.moveTo(l - f, -1f)
        path.lineTo(l - f, 0f)
        path.cubicTo(l - f * 0.45f, 0f, l, f * 0.45f, l, f)
        path.lineTo(l, b - rad)
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

        // «капелька» второй активности: выкатывается из правого бока островка
        if (bubble > 0.01f) {
            val d = compactH
            c.drawCircle(bubbleCenter(), d / 2, d / 2 * bubble, black)
        }
    }

    /** Центр «капельки»: при появлении выкатывается из правого края островка. */
    private fun bubbleCenter(): Float {
        val d = compactH
        val right = cx() + shapeW / 2
        return right + (bubbleGap + d / 2) * bubble.coerceIn(0f, 1.2f)
    }

    // ---------- касания ----------

    private fun inShape(x: Float, y: Float): Boolean {
        val c = cx()
        return x in (c - shapeW / 2 - flare)..(c + shapeW / 2 + flare) && y <= shapeH + dp(4)
    }

    private fun inBubbleArea(x: Float, y: Float): Boolean {
        if (bubble < 0.5f) return false
        val c = cx()
        val d = compactH
        val l = c + shapeW / 2 + bubbleGap
        return x in (l - dp(2))..(l + d + dp(4)) && y <= d + dp(4)
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        if (mode != Mode.EXPANDED) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = e.x; downY = e.y }
            MotionEvent.ACTION_MOVE -> if (downY - e.y > dp(24) && abs(e.y - downY) > abs(e.x - downX)) {
                ctl.onSwipeUp()
                return true
            }
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_OUTSIDE -> { ctl.onOutside(); return false }
            MotionEvent.ACTION_DOWN -> {
                inBubble = inBubbleArea(e.x, e.y)
                if (!inBubble && !inShape(e.x, e.y)) return false
                downX = e.x; downY = e.y
                tracking = true
                longFired = false
                postDelayed(longPress, 380)
                pressAnim(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.x - downX
                val dy = e.y - downY
                if (tracking && mode == Mode.COMPACT && !inBubble && abs(dx) > dp(34) && abs(dx) > abs(dy)) {
                    endTracking()
                    ctl.onSwipeAway()
                } else if (tracking && mode == Mode.EXPANDED && -dy > dp(24)) {
                    endTracking()
                    ctl.onSwipeUp()
                } else if (tracking && (abs(dx) > slop * 2 || abs(dy) > slop * 2)) {
                    removeCallbacks(longPress)
                }
            }
            MotionEvent.ACTION_UP -> {
                val wasTap = tracking && !longFired && abs(e.x - downX) < slop * 2 && abs(e.y - downY) < slop * 2
                endTracking()
                if (wasTap) { if (inBubble) ctl.onBubbleTap() else ctl.onTap() }
            }
            MotionEvent.ACTION_CANCEL -> endTracking()
        }
        return true
    }

    private fun endTracking() {
        tracking = false
        removeCallbacks(longPress)
        pressAnim(false)
    }

    private fun pressAnim(down: Boolean) {
        pivotX = cx()
        pivotY = 0f
        if (down) animate().scaleX(1.03f).scaleY(1.03f).setDuration(140).start()
        else animate().scaleX(1f).scaleY(1f).setDuration(220).start()
    }

    /** Обновить содержимое без смены формы (например, новый трек). */
    fun rebind() {
        val p = primary ?: return
        when (mode) {
            Mode.COMPACT -> bindCompact(p)
            Mode.EXPANDED -> card?.bind(p)
            else -> Unit
        }
        bindBubble(secondary)
    }

    override fun onDetachedFromWindow() {
        rChrono.stop()
        card?.let { stopChronos(it) }
        super.onDetachedFromWindow()
    }
}
