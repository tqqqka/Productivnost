package com.tihon.shtorka.ui.cc

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import com.tihon.shtorka.MainActivity
import com.tihon.shtorka.R
import com.tihon.shtorka.core.Bus
import com.tihon.shtorka.core.Hub
import com.tihon.shtorka.core.Prefs
import com.tihon.shtorka.core.Spring
import com.tihon.shtorka.system.Controls
import com.tihon.shtorka.system.Launch
import com.tihon.shtorka.system.MediaRepo
import com.tihon.shtorka.system.Torch
import com.tihon.shtorka.ui.GlyphButton
import com.tihon.shtorka.ui.Palette
import com.tihon.shtorka.ui.PanelHost
import com.tihon.shtorka.ui.StatusRowView
import com.tihon.shtorka.ui.clamp01
import com.tihon.shtorka.ui.dp
import com.tihon.shtorka.ui.rubber
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Пункт управления в раскладке iOS 18:
 *
 *   [ связь 2×2 ][ музыка 2×2 ]
 *   [🔒][⧉] [☀][🔊]
 *   [ фокус 2×1 ] [  ][  ]
 *   [🔦][⏱][🧮][📷]
 *   [🔋][◐][⏺][⏰]   ← дополнительный ряд (можно выключить)
 */
@SuppressLint("ViewConstructor")
class ControlCenterView(ctx: Context, private val host: PanelHost) : FrameLayout(ctx) {

    val status = StatusRowView(ctx)
    private val plus = GlyphButton(ctx, R.drawable.g_plus, 0.62f, 0xD9FFFFFF.toInt())
    private val power = GlyphButton(ctx, R.drawable.g_power, 0.56f, 0xD9FFFFFF.toInt())
    private val grid = GridLayer(ctx)
    private val layer = ExpandLayer(ctx)

    val connectivity = ConnectivityModule(ctx).at(0, 0, 2, 2)
    val media = MediaModule(ctx).at(2, 0, 2, 2)
    val rotation = circle(R.drawable.g_rotation_lock, Palette.RED).at(0, 2)
    val mirroring = circle(R.drawable.g_mirroring, Palette.BLACK).at(1, 2)
    val focus = FocusModule(ctx).at(0, 3, 2, 1)
    val brightness = SliderModule(ctx, SliderModule.Kind.BRIGHTNESS).at(2, 2, 1, 2)
    val volume = SliderModule(ctx, SliderModule.Kind.VOLUME).at(3, 2, 1, 2)
    val torch = circle(R.drawable.g_flashlight, Palette.BLUE).at(0, 4)
    val timer = circle(R.drawable.g_timer, Palette.BLACK).at(1, 4)
    val calculator = circle(R.drawable.g_calculator, Palette.BLACK).at(2, 4)
    val camera = circle(R.drawable.g_camera, Palette.BLACK).at(3, 4)
    val saver = circle(R.drawable.g_low_power, Palette.ORANGE).at(0, 5)
    val dark = circle(R.drawable.g_dark_mode, Palette.BLACK).at(1, 5)
    val record = circle(R.drawable.g_record, Palette.RED).at(2, 5)
    val alarm = circle(R.drawable.g_alarm, Palette.BLACK).at(3, 5)
    private val extra = listOf(saver, dark, record, alarm)
    private val modules: List<ModuleView> = listOf(
        connectivity, media, rotation, mirroring, focus, brightness, volume,
        torch, timer, calculator, camera, saver, dark, record, alarm,
    )

    // геометрия сетки
    private var unit = 0f
    private var gap = 0f
    private var margin = 0f
    private var gridTop = 0f

    private var progress = 0f
    private var card: MorphCard? = null
    private var source: ModuleView? = null
    private val expandSpring = Spring(0f) { applyExpand(it) }
    val isExpanded get() = card != null
    private var askedWriteSettings = false

    private val busListener: () -> Unit = { if (visibility == VISIBLE) refresh() }
    private val mediaListener: () -> Unit = {
        if (visibility == VISIBLE) {
            media.bind(MediaRepo.now)
            (card?.content as? Refreshable)?.refresh()
        }
    }

    init {
        clipChildren = false
        addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(status)
        addView(plus)
        addView(power)
        addView(layer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        layer.visibility = GONE
        modules.forEach { grid.addView(it) }
        wire()
        applyPrefs()
    }

    private fun circle(icon: Int, glyphOn: Int) =
        CircleToggle(context, icon, Palette.WHITE, glyphOn, offFill = Palette.GLASS, glyphFraction = 0.40f, rim = true)

    private fun <T : ModuleView> T.at(c: Int, r: Int, cs: Int = 1, rs: Int = 1): T = apply {
        col = c; row = r; colSpan = cs; rowSpan = rs
    }

    fun applyPrefs() {
        val v = if (Prefs.extraRow) VISIBLE else GONE
        extra.forEach { it.visibility = v }
        requestLayout()
    }

    private fun wire() {
        val ctx = context
        with(connectivity) {
            airplane.onTap = { airplane.userToggle(); Actions.airplane() }
            cellular.onTap = { cellular.userToggle(); Actions.data() }
            wifi.onTap = { wifi.userToggle(); Actions.wifi(ctx) }
            bluetooth.onTap = { bluetooth.userToggle(); Actions.bluetooth(ctx) }
            (circles + this).forEach { it.onLongPress = { expandConnectivity() } }
        }
        media.onTap = { expandMedia() }
        media.onLongPress = { expandMedia() }
        media.output.onTap = { Actions.bluetoothSettings() }

        rotation.onTap = { rotation.userToggle(); Actions.rotation(ctx) }
        mirroring.onTap = { Actions.mirroring() }
        focus.onTap = { focus.set(!Controls.dndOn(ctx)); Actions.dnd(ctx) }
        focus.onLongPress = { Actions.dndSettings() }

        brightness.onChange = { v -> setBrightness(v) }
        brightness.onExpand = { expandSlider(brightness) }
        volume.onChange = { v -> Controls.setVolume(ctx, v) }
        volume.onExpand = { expandSlider(volume) }

        torch.onTap = { torch.userToggle(); Actions.torch() }
        torch.onLongPress = { if (Torch.supportsLevels) expandSlider(torch) }
        timer.onTap = { Actions.timer() }
        calculator.onTap = { Actions.calculator() }
        camera.onTap = { Actions.camera() }
        saver.onTap = { saver.userToggle(); Actions.powerSave() }
        dark.onTap = { dark.userToggle(); Actions.darkMode() }
        record.onTap = { Actions.record() }
        alarm.onTap = { Actions.alarm() }

        plus.onTap = { Hub.service?.launch(Intent(ctx, MainActivity::class.java)) }
        power.onTap = {
            host.close(false)
            Hub.service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
        }
    }

    private fun setBrightness(v: Float) {
        if (Controls.setBrightness(context, v)) return
        if (!askedWriteSettings) {
            askedWriteSettings = true
            Toast.makeText(context, "Разреши «Изменение системных настроек» — для яркости", Toast.LENGTH_LONG).show()
            Hub.service?.launch(Launch.writeSettings(context))
        }
    }

    /** Перечитать все состояния. */
    fun refresh() {
        val c = context
        status.refresh()
        val air = Controls.airplaneOn(c)
        connectivity.airplane.setFromSystem(air)
        connectivity.cellular.setFromSystem(Controls.dataOn(c) && !air && Controls.hasSim(c))
        connectivity.wifi.setFromSystem(Controls.wifiOn(c))
        connectivity.bluetooth.setFromSystem(Controls.btOn(c))
        rotation.setFromSystem(Controls.rotationLocked(c))
        focus.set(Controls.dndOn(c), animate = isShown)
        brightness.setValueExternal(Controls.brightness(c))
        volume.setValueExternal(Controls.volume(c))
        torch.setFromSystem(Torch.on)
        saver.setFromSystem(Controls.powerSave(c))
        dark.setFromSystem(Controls.darkMode(c))
        media.bind(MediaRepo.now)
        (card?.content as? Refreshable)?.refresh()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Bus.add(busListener)
        MediaRepo.add(mediaListener)
    }

    override fun onDetachedFromWindow() {
        Bus.remove(busListener)
        MediaRepo.remove(mediaListener)
        super.onDetachedFromWindow()
    }

    // ---------------- раскладка ----------------

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec).toFloat()
        val h = MeasureSpec.getSize(heightMeasureSpec).toFloat()
        val sb = host.statusBarHeight.toFloat()
        val rows = if (Prefs.extraRow) 6 else 5
        val m0 = w * 0.075f
        val g0 = w * 0.04f
        val u0 = (w - m0 * 2 - g0 * 3) / 4
        gridTop = sb + dp(58)
        val avail = h - gridTop - host.navBarHeight - dp(20)
        val uH = (avail - g0 * (rows - 1)) / rows
        unit = max(dp(40), min(u0, uH))
        gap = g0 * (unit / u0).coerceAtMost(1f)
        margin = (w - unit * 4 - gap * 3) / 2
        status.sidePadding = margin - dp(2)
        status.notchLeft = host.notch.left.toFloat()
        status.notchRight = host.notch.right.toFloat()

        modules.forEach { m ->
            val mw = (unit * m.colSpan + gap * (m.colSpan - 1)).roundToInt()
            val mh = (unit * m.rowSpan + gap * (m.rowSpan - 1)).roundToInt()
            m.cornerRadius = when {
                m.colSpan == 2 && m.rowSpan == 2 -> unit * 0.46f
                m.colSpan == 2 -> unit * 0.42f
                else -> unit * 0.36f
            }
            m.measure(MeasureSpec.makeMeasureSpec(mw, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(mh, MeasureSpec.EXACTLY))
        }
        val ex = { v: Int -> MeasureSpec.makeMeasureSpec(v, MeasureSpec.EXACTLY) }
        grid.measure(widthMeasureSpec, heightMeasureSpec)
        status.measure(ex(w.roundToInt()), ex(host.statusBarHeight))
        val b = dp(38).roundToInt()
        plus.measure(ex(b), ex(b))
        power.measure(ex(b), ex(b))
        layer.measure(widthMeasureSpec, heightMeasureSpec)
        setMeasuredDimension(w.roundToInt(), h.roundToInt())
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val w = r - l
        grid.layout(0, 0, w, b - t)
        status.layout(0, 0, w, status.measuredHeight)
        val by = (host.statusBarHeight + dp(10)).roundToInt()
        val bs = plus.measuredWidth
        val x0 = (margin - dp(8)).roundToInt()
        plus.layout(x0, by, x0 + bs, by + bs)
        power.layout(w - x0 - bs, by, w - x0, by + bs)
        layer.layout(0, 0, w, b - t)
    }

    /** Модули раскладываются по клеткам. */
    private inner class GridLayer(ctx: Context) : ViewGroup(ctx) {
        init { clipChildren = false; clipToPadding = false }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            for (i in 0 until childCount) {
                val m = getChildAt(i) as ModuleView
                val x = (margin + m.col * (unit + gap)).roundToInt()
                val y = (gridTop + m.row * (unit + gap)).roundToInt()
                m.layout(x, y, x + m.measuredWidth, y + m.measuredHeight)
            }
        }
    }

    // ---------------- открытие ----------------

    /**
     * p: 0 — закрыт, 1 — открыт, >1 — перетянут вниз (резина).
     * Ряды появляются чуть по очереди и «спускаются» сверху, как в iOS 18.
     */
    fun applyProgress(p: Float) {
        progress = p
        val over = max(0f, p - 1f)
        status.alpha = clamp01(p * 1.6f - 0.2f)
        val buttonsA = clamp01((p - 0.1f) / 0.6f)
        plus.alpha = buttonsA
        power.alpha = buttonsA
        val drop = dp(46)
        modules.forEach { m ->
            val delay = m.row * 0.05f
            val q = clamp01((p - delay) / (1f - delay))
            val e = 1f - (1f - q) * (1f - q)
            val alpha = clamp01(q * 1.5f - 0.15f)
            val scale = 0.84f + 0.16f * e
            val ty = -(1f - e) * (drop + m.top * 0.16f) + over * dp(64) * (0.55f + 0.12f * m.row)
            m.setOpenTransform(scale, ty, alpha)
        }
    }

    fun reset() {
        collapse(false)
        askedWriteSettings = false
    }

    // ---------------- раскрытие модулей ----------------

    internal fun expandConnectivity() {
        val w = gridWidth()
        val content = ExpandedConnectivity(context)
        content.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        expand(connectivity, content, w, content.measuredHeight, glassCard = true, anchor = null, fading = null, toRadius = unit * 0.5f)
    }

    internal fun expandMedia() {
        val w = gridWidth()
        val content = ExpandedMedia(context)
        expand(media, content, w, content.preferredHeight(), glassCard = true, anchor = null, fading = null, toRadius = unit * 0.5f)
    }

    internal fun expandSlider(src: ModuleView) {
        val kind = when (src) {
            brightness -> SliderModule.Kind.BRIGHTNESS
            volume -> SliderModule.Kind.VOLUME
            else -> SliderModule.Kind.TORCH
        }
        val w = gridWidth()
        val content = ExpandedSlider(context, kind)
        content.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        val h = content.measuredHeight
        content.layout(0, 0, w, h)
        expand(src, content, w, h, glassCard = false, anchor = RectF(content.anchor), fading = content.fading, toRadius = 0f)
    }

    private fun gridWidth() = (unit * 4 + gap * 3).roundToInt()

    private fun expand(
        src: ModuleView, content: View, w: Int, h: Int,
        glassCard: Boolean, anchor: RectF?, fading: List<View>?, toRadius: Float,
    ) {
        if (card != null || progress < 0.9f) return
        val from = RectF(src.left.toFloat(), src.top.toFloat(), src.right.toFloat(), src.bottom.toFloat())
        // Модуль внутри блока связи: переводим координаты в систему сетки.
        var p = src.parent
        while (p is View && p !== grid) {
            from.offset(p.left.toFloat(), p.top.toFloat())
            p = p.parent
        }
        val left = (width - w) / 2f
        val top = ((height - h) / 2f).coerceAtLeast(host.statusBarHeight + dp(30))
        val to = RectF(left, top, left + w, top + h)
        val target = if (src.parent is ConnectivityModule) src.parent as ModuleView else src
        if (target !== src) {
            from.set(target.left.toFloat(), target.top.toFloat(), target.right.toFloat(), target.bottom.toFloat())
        }
        val c = MorphCard(context, content, from, to, anchor, target.radius(), toRadius, glassCard, fading)
        layer.addView(c, LayoutParams(w, h).apply { leftMargin = left.roundToInt(); topMargin = top.roundToInt() })
        layer.visibility = VISIBLE
        card = c
        source = target
        target.hiddenForExpand = true
        expandSpring.snap(0f)
        expandSpring.animateTo(1f, stiffness = 340f, damping = 0.76f)
    }

    fun collapse(animated: Boolean = true) {
        if (card == null) return
        if (!animated) { finishCollapse(); return }
        expandSpring.animateTo(0f, stiffness = 480f, damping = 0.9f) { canceled -> if (!canceled) finishCollapse() }
    }

    private fun finishCollapse() {
        expandSpring.cancel()
        layer.removeAllViews()
        layer.visibility = GONE
        source?.hiddenForExpand = false
        card = null
        source = null
        applyExpand(0f)
    }

    private fun applyExpand(m: Float) {
        card?.morph = m
        val k = clamp01(m)
        grid.alpha = 1f - 0.9f * k
        status.alpha = (1f - 0.5f * k) * clamp01(progress * 1.6f - 0.2f)
        val s = 1f - 0.05f * k
        grid.pivotX = width / 2f
        grid.pivotY = height / 2f
        grid.scaleX = s
        grid.scaleY = s
        val ui = 1f - 0.6f * k
        plus.alpha = ui * clamp01((progress - 0.1f) / 0.6f)
        power.alpha = plus.alpha
        if (Build.VERSION.SDK_INT >= 31) {
            val r = dp(18) * k
            grid.setRenderEffect(if (r > 0.5f) RenderEffect.createBlurEffect(r, r, Shader.TileMode.DECAL) else null)
        }
    }

    /** Касание мимо модулей: сворачивает раскрытый модуль или закрывает шторку. */
    fun onEmptyTap() {
        if (isExpanded) collapse() else host.close(true)
    }

    /** Слой раскрытой карточки: тап мимо неё сворачивает. */
    private inner class ExpandLayer(ctx: Context) : FrameLayout(ctx) {
        private var downX = 0f
        private var downY = 0f

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = e.x; downY = e.y }
                MotionEvent.ACTION_UP -> {
                    if (kotlin.math.abs(e.x - downX) < dp(12) && kotlin.math.abs(e.y - downY) < dp(12)) collapse()
                }
            }
            return true
        }
    }

    @Suppress("unused")
    private fun rubberOver(x: Float) = rubber(x, 0.25f)
}
