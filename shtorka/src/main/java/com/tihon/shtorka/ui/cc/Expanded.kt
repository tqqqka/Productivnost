package com.tihon.shtorka.ui.cc

import android.content.Context
import android.graphics.RectF
import android.media.AudioManager
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.tihon.shtorka.R
import com.tihon.shtorka.system.Controls
import com.tihon.shtorka.system.Torch
import com.tihon.shtorka.ui.Fonts
import com.tihon.shtorka.ui.Palette
import com.tihon.shtorka.ui.dp
import com.tihon.shtorka.ui.text
import kotlin.math.max
import kotlin.math.roundToInt

/** Содержимое раскрытого модуля, которое умеет обновляться из системы. */
interface Refreshable {
    fun refresh()
}

private fun Context.caption(t: String, size: Float, face: android.graphics.Typeface, color: Int) =
    text(size, face, color, lines = 2, gravity = Gravity.CENTER_HORIZONTAL).apply { text = t }

/** Раскрытый блок связи: шесть кнопок с подписями. */
class ExpandedConnectivity(ctx: Context) : FrameLayout(ctx), Refreshable {
    private class Item(val toggle: CircleToggle, val title: TextView, val sub: TextView)

    private val airplane = item(R.drawable.g_airplane, Palette.ORANGE, "Авиарежим")
    private val cellular = item(R.drawable.g_cellular, Palette.GREEN, "Сотовые данные")
    private val wifi = item(R.drawable.g_wifi, Palette.BLUE, "Wi-Fi")
    private val bluetooth = item(R.drawable.g_bluetooth, Palette.BLUE, "Bluetooth")
    private val hotspot = item(R.drawable.g_hotspot, Palette.GREEN, "Режим модема")
    private val location = item(R.drawable.g_location, Palette.BLUE, "Геолокация")
    private val items = listOf(airplane, cellular, wifi, bluetooth, hotspot, location)

    init {
        airplane.toggle.onTap = { airplane.toggle.userToggle(); Actions.airplane() }
        cellular.toggle.onTap = { cellular.toggle.userToggle(); Actions.data() }
        wifi.toggle.onTap = { wifi.toggle.userToggle(); Actions.wifi(context) }
        wifi.toggle.onLongPress = { Actions.wifiSettings() }
        bluetooth.toggle.onTap = { bluetooth.toggle.userToggle(); Actions.bluetooth(context) }
        bluetooth.toggle.onLongPress = { Actions.bluetoothSettings() }
        hotspot.toggle.onTap = { hotspot.toggle.userToggle(); Actions.hotspot() }
        location.toggle.onTap = { location.toggle.userToggle(); Actions.location() }
        refresh()
    }

    private fun item(icon: Int, color: Int, title: String): Item {
        val t = CircleToggle(context, icon, color, glyphFraction = 0.42f)
        val a = context.caption(title, 13.5f, Fonts.semibold, Palette.WHITE)
        val b = context.caption("", 12.5f, Fonts.medium, Palette.TEXT_SECONDARY)
        addView(t); addView(a); addView(b)
        return Item(t, a, b)
    }

    override fun refresh() {
        val c = context
        fun onOff(on: Boolean) = if (on) "Вкл." else "Выкл."
        val air = Controls.airplaneOn(c)
        airplane.toggle.setFromSystem(air)
        airplane.sub.text = onOff(air)
        val sim = Controls.hasSim(c)
        val data = Controls.dataOn(c) && sim && !air
        cellular.toggle.setFromSystem(data)
        cellular.sub.text = if (!sim) "Нет SIM" else onOff(data)
        val w = Controls.wifiOn(c)
        wifi.toggle.setFromSystem(w)
        wifi.sub.text = if (!w) "Выкл." else Controls.wifiName(c) ?: if (Controls.wifiConnected(c)) "Подключено" else "Вкл."
        val b = Controls.btOn(c)
        bluetooth.toggle.setFromSystem(b)
        bluetooth.sub.text = onOff(b)
        val h = Controls.hotspotOn(c)
        hotspot.toggle.setFromSystem(h)
        hotspot.sub.text = if (h) "Вкл." else "Не обнаруживается"
        val l = Controls.locationOn(c)
        location.toggle.setFromSystem(l)
        location.sub.text = onOff(l)
    }

    private val pad get() = dp(26).roundToInt()
    private val d get() = dp(60).roundToInt()
    private val rowGap get() = dp(20).roundToInt()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val colW = (w - pad * 2) / 2 - dp(6).roundToInt()
        val ex = MeasureSpec.makeMeasureSpec(d, MeasureSpec.EXACTLY)
        val tw = MeasureSpec.makeMeasureSpec(colW, MeasureSpec.EXACTLY)
        val un = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        var rowH = 0
        items.forEach {
            it.toggle.measure(ex, ex)
            it.title.measure(tw, un)
            it.sub.measure(tw, un)
            rowH = max(rowH, d + dp(9).roundToInt() + it.title.measuredHeight + dp(2).roundToInt() + it.sub.measuredHeight)
        }
        this.rowH = rowH
        val h = pad * 2 + rowH * 3 + rowGap * 2
        setMeasuredDimension(w, if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) MeasureSpec.getSize(heightMeasureSpec) else h)
    }

    private var rowH = 0

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val w = r - l
        items.forEachIndexed { i, it ->
            val cx = if (i % 2 == 0) w / 4 + pad / 2 else w * 3 / 4 - pad / 2
            var y = pad + (i / 2) * (rowH + rowGap)
            it.toggle.layout(cx - d / 2, y, cx + d / 2, y + d)
            y += d + dp(9).roundToInt()
            val tw = it.title.measuredWidth
            it.title.layout(cx - tw / 2, y, cx + tw / 2, y + it.title.measuredHeight)
            y += it.title.measuredHeight + dp(2).roundToInt()
            it.sub.layout(cx - tw / 2, y, cx + tw / 2, y + it.sub.measuredHeight)
        }
    }
}

/** Большой ползунок яркости/громкости/фонарика и кнопки под ним. */
class ExpandedSlider(ctx: Context, kind: SliderModule.Kind) : FrameLayout(ctx), Refreshable {
    val slider = SliderModule(ctx, kind, big = true)
    private val buttons = ArrayList<Pair<CircleToggle, TextView>>()
    private val title: TextView = ctx.text(15f, Fonts.semibold, Palette.TEXT_SECONDARY, gravity = Gravity.CENTER_HORIZONTAL)
    private var dark: CircleToggle? = null
    private var eye: CircleToggle? = null
    private var auto: CircleToggle? = null
    private var ringer: CircleToggle? = null
    val fading: List<View> get() = buttons.flatMap { listOf(it.first, it.second) } + title

    init {
        addView(slider)
        addView(title)
        when (kind) {
            SliderModule.Kind.BRIGHTNESS -> {
                title.text = "Яркость"
                slider.onChange = { v -> Controls.setBrightness(context, v) }
                dark = button(R.drawable.g_dark_mode, Palette.WHITE, Palette.BLACK, "Тёмная\nтема") { dark?.userToggle(); Actions.darkMode() }
                eye = button(R.drawable.g_eye_comfort, Palette.WHITE, Palette.ORANGE, "Комфорт\nдля глаз") { eye?.userToggle(); Actions.eyeComfort() }
                auto = button(R.drawable.g_auto_brightness, Palette.WHITE, Palette.BLACK, "Авто-\nяркость") { auto?.userToggle(); Actions.autoBrightness(context) }
            }
            SliderModule.Kind.VOLUME -> {
                title.text = "Громкость"
                slider.onChange = { v -> Controls.setVolume(context, v) }
                ringer = button(R.drawable.g_bell, Palette.WHITE, Palette.RED, "Звонок") { Actions.ringer(context) }
            }
            SliderModule.Kind.TORCH -> {
                title.text = "Фонарик"
                slider.onChange = { v -> Torch.setFraction(v) }
            }
        }
        refresh()
    }

    private fun button(icon: Int, fill: Int, glyph: Int, label: String, tap: () -> Unit): CircleToggle {
        val t = CircleToggle(context, icon, fill, glyph, glyphFraction = 0.44f)
        t.onTap = tap
        val l = context.caption(label, 12.5f, Fonts.medium, Palette.WHITE)
        addView(t); addView(l)
        buttons += t to l
        return t
    }

    override fun refresh() {
        val c = context
        when (slider.kind) {
            SliderModule.Kind.BRIGHTNESS -> slider.setValueExternal(Controls.brightness(c))
            SliderModule.Kind.VOLUME -> slider.setValueExternal(Controls.volume(c))
            SliderModule.Kind.TORCH -> slider.setValueExternal(Torch.fraction)
        }
        dark?.setFromSystem(Controls.darkMode(c))
        eye?.setFromSystem(false)
        auto?.setFromSystem(Controls.autoBrightness(c))
        ringer?.let { r ->
            val m = Controls.ringerMode(c)
            val silent = m != AudioManager.RINGER_MODE_NORMAL
            r.setIcon(if (m == AudioManager.RINGER_MODE_VIBRATE) R.drawable.g_vibration else if (silent) R.drawable.g_bell_off else R.drawable.g_bell)
            r.setFromSystem(silent)
            buttons.firstOrNull { it.first === r }?.second?.text = when (m) {
                AudioManager.RINGER_MODE_VIBRATE -> "Вибрация"
                AudioManager.RINGER_MODE_SILENT -> "Без звука"
                else -> "Звонок"
            }
        }
    }

    /** Где стоит большой ползунок — из этой области «вырастает» маленький. */
    val anchor = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val sw = (w * 0.36f).roundToInt()
        val sh = (sw * 2.6f).roundToInt()
        slider.measure(MeasureSpec.makeMeasureSpec(sw, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(sh, MeasureSpec.EXACTLY))
        val un = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        title.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), un)
        val d = dp(56).roundToInt()
        val lw = MeasureSpec.makeMeasureSpec((w / 3.2f).roundToInt(), MeasureSpec.EXACTLY)
        var labelH = 0
        buttons.forEach { (t, l) ->
            t.measure(MeasureSpec.makeMeasureSpec(d, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(d, MeasureSpec.EXACTLY))
            l.measure(lw, un)
            labelH = max(labelH, l.measuredHeight)
        }
        var h = title.measuredHeight + dp(16).roundToInt() + sh
        if (buttons.isNotEmpty()) h += dp(34).roundToInt() + d + dp(8).roundToInt() + labelH
        setMeasuredDimension(w, if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) MeasureSpec.getSize(heightMeasureSpec) else h)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val w = r - l
        title.layout(0, 0, w, title.measuredHeight)
        val top = title.measuredHeight + dp(16).roundToInt()
        val sw = slider.measuredWidth
        val sh = slider.measuredHeight
        val sx = (w - sw) / 2
        slider.layout(sx, top, sx + sw, top + sh)
        anchor.set(sx.toFloat(), top.toFloat(), (sx + sw).toFloat(), (top + sh).toFloat())
        val by = top + sh + dp(34).roundToInt()
        val n = buttons.size
        buttons.forEachIndexed { i, (tg, lb) ->
            val cx = if (n == 1) w / 2 else (w * (0.5f + (i - (n - 1) / 2f) * 0.29f)).roundToInt()
            val d = tg.measuredWidth
            tg.layout(cx - d / 2, by, cx + d / 2, by + d)
            val lw = lb.measuredWidth
            val ly = by + d + dp(8).roundToInt()
            lb.layout(cx - lw / 2, ly, cx + lw / 2, ly + lb.measuredHeight)
        }
    }
}

/** Раскрытый плеер. */
class ExpandedMedia(ctx: Context) : FrameLayout(ctx), Refreshable {
    val panel = com.tihon.shtorka.ui.MediaPanel(ctx, withVolume = true)

    init {
        addView(panel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        panel.onOutput = { Actions.bluetoothSettings() }
    }

    override fun refresh() {
        panel.bind(com.tihon.shtorka.system.MediaRepo.now)
    }

    fun preferredHeight() = panel.preferredHeight()
}
