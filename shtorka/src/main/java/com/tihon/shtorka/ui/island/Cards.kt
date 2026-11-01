package com.tihon.shtorka.ui.island

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Chronometer
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.tihon.shtorka.R
import com.tihon.shtorka.core.Spring
import com.tihon.shtorka.system.Act
import com.tihon.shtorka.system.Kind
import com.tihon.shtorka.system.Live
import com.tihon.shtorka.system.MediaRepo
import com.tihon.shtorka.ui.ArtView
import com.tihon.shtorka.ui.Fonts
import com.tihon.shtorka.ui.Haptics
import com.tihon.shtorka.ui.MediaPanel
import com.tihon.shtorka.ui.Palette
import com.tihon.shtorka.ui.dpi
import com.tihon.shtorka.ui.text
import com.tihon.shtorka.ui.withAlpha
import kotlin.math.min

/** Содержимое раскрытого островка. */
abstract class IslandCard(ctx: Context) : FrameLayout(ctx) {
    var onAction: ((Act) -> Unit)? = null
    abstract fun bind(l: Live)

    fun preferredHeight(width: Int): Int {
        measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        return measuredHeight
    }
}

/** Значок в кружке цвета активности: таймер, трубка, наушники. */
class GlyphCircle(ctx: Context) : View(ctx) {
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private var icon: android.graphics.drawable.Drawable? = null
    var solid = false

    fun set(res: Int, color: Int) {
        icon = ContextCompat.getDrawable(context, res)!!.mutate().also { it.setTint(if (solid) Palette.WHITE else color) }
        bg.color = if (solid) color else withAlpha(color, 0.22f)
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val d = min(width, height).toFloat()
        c.drawCircle(width / 2f, height / 2f, d / 2, bg)
        val s = d * 0.5f
        icon?.setBounds(((width - s) / 2).toInt(), ((height - s) / 2).toInt(), ((width + s) / 2).toInt(), ((height + s) / 2).toInt())
        icon?.draw(c)
    }
}

/** Круглая кнопка звонка: красная «сбросить», зелёная «ответить». */
@SuppressLint("ViewConstructor")
class RoundButton(ctx: Context) : View(ctx) {
    private val glyph = GlyphCircle(ctx).apply { solid = true }
    var onTap: (() -> Unit)? = null
    private val press = Spring(1f, 0.001f) { scaleX = it; scaleY = it }

    fun set(res: Int, color: Int) = glyph.set(res, color)

    override fun onDraw(c: Canvas) {
        glyph.layout(0, 0, width, height)
        glyph.draw(c)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> press.animateTo(0.86f, stiffness = 1200f, damping = 1f)
            MotionEvent.ACTION_UP -> {
                press.animateTo(1f, stiffness = 600f, damping = 0.55f)
                if (e.x in 0f..width.toFloat() && e.y in 0f..height.toFloat()) {
                    Haptics.click(this)
                    onTap?.invoke()
                }
            }
            MotionEvent.ACTION_CANCEL -> press.animateTo(1f, stiffness = 600f, damping = 0.55f)
        }
        return true
    }
}

private fun Context.chrono(sizeSp: Float, color: Int) = Chronometer(this).apply {
    setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    typeface = Fonts.semibold
    setTextColor(color)
    fontFeatureSettings = "tnum"
    includeFontPadding = false
}

internal fun Chronometer.bindTo(l: Live) {
    if (l.chronoBase == 0L) { stop(); visibility = View.GONE; return }
    visibility = View.VISIBLE
    base = l.chronoBase
    isCountDown = l.countDown
    start()
}

private fun pill(color: Int) = GradientDrawable().apply { cornerRadius = 999f; setColor(color) }

/** Музыка — тот же плеер, что раньше. */
class MusicCard(ctx: Context) : IslandCard(ctx) {
    val panel = MediaPanel(ctx, withVolume = false)
    init { addView(panel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)) }
    override fun bind(l: Live) = panel.bind(MediaRepo.now)
}

/** Таймер, секундомер, запись, навигация, загрузка, наушники, зарядка. */
class LiveCard(ctx: Context) : IslandCard(ctx) {
    private val art = ArtView(ctx).apply { radiusFraction = 0.24f }
    private val glyph = GlyphCircle(ctx)
    private val title: TextView = ctx.text(17f, Fonts.semibold)
    private val sub: TextView = ctx.text(14f, Fonts.medium, Palette.TEXT_SECONDARY, lines = 2)
    private val bigTime = ctx.chrono(30f, Palette.ORANGE)
    private val bigText: TextView = ctx.text(26f, Fonts.semibold, Palette.WHITE)
    private val ring = ProgressRing(ctx)
    private val bar = com.tihon.shtorka.ui.HBar(ctx).apply { interactive = false }
    private val acts = LinearLayout(ctx)

    init {
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val iconBox = FrameLayout(ctx)
        iconBox.addView(art, LayoutParams(dpi(46), dpi(46)))
        iconBox.addView(glyph, LayoutParams(dpi(46), dpi(46)))
        row.addView(iconBox, LinearLayout.LayoutParams(dpi(46), dpi(46)))
        val texts = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(title)
        texts.addView(sub, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(2) })
        row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dpi(12); marginEnd = dpi(8) })
        row.addView(bigTime)
        row.addView(bigText, LinearLayout.LayoutParams(-2, -2))
        row.addView(ring, LinearLayout.LayoutParams(dpi(38), dpi(38)))
        col.addView(row)
        col.addView(bar, LinearLayout.LayoutParams(-1, dpi(20)).apply { topMargin = dpi(10) })
        acts.orientation = LinearLayout.HORIZONTAL
        col.addView(acts, LinearLayout.LayoutParams(-1, dpi(42)).apply { topMargin = dpi(12) })
        col.setPadding(dpi(20), dpi(10), dpi(20), dpi(18))
        addView(col, LayoutParams(-1, -2))
    }

    override fun bind(l: Live) {
        val glyphRes = when (l.kind) {
            Kind.TIMER, Kind.STOPWATCH -> R.drawable.g_timer
            Kind.CALL -> R.drawable.g_call
            Kind.HEADPHONES -> R.drawable.g_headphones
            Kind.CHARGING, Kind.LOW_BATTERY -> R.drawable.g_bolt
            Kind.RECORDING -> R.drawable.g_record
            else -> 0
        }
        if (glyphRes != 0) {
            glyph.visibility = VISIBLE; art.visibility = GONE
            glyph.set(glyphRes, l.accent)
        } else {
            glyph.visibility = GONE; art.visibility = VISIBLE
            art.setDrawable(l.largeIcon ?: l.appIcon)
        }
        title.text = l.title
        sub.text = l.text
        sub.visibility = if (l.text.isBlank()) GONE else VISIBLE
        bigTime.setTextColor(l.accent)
        bigTime.bindTo(l)
        val showText = l.chronoBase == 0L && !l.value.isNullOrBlank() && l.kind != Kind.NAVIGATION && l.kind != Kind.PROGRESS
        bigText.visibility = if (showText) VISIBLE else GONE
        bigText.text = l.value
        bigText.setTextColor(l.accent)
        val ringOn = l.kind == Kind.HEADPHONES && l.level >= 0
        ring.visibility = if (ringOn) VISIBLE else GONE
        ring.progress = l.level / 100f
        ring.color = l.accent
        bar.visibility = if (l.progress >= 0f && l.kind != Kind.HEADPHONES) VISIBLE else GONE
        bar.set(l.progress.coerceAtLeast(0f))
        acts.removeAllViews()
        acts.visibility = if (l.actions.isEmpty()) GONE else VISIBLE
        l.actions.take(3).forEachIndexed { i, a ->
            val b = context.text(14f, Fonts.semibold, gravity = Gravity.CENTER).apply {
                text = a.title
                background = pill(
                    when (a.role) {
                        Act.Role.DECLINE -> withAlpha(Palette.RED, 0.9f)
                        Act.Role.ACCEPT -> withAlpha(Palette.GREEN, 0.9f)
                        else -> 0x33FFFFFF
                    },
                )
                setOnClickListener { Haptics.click(this); onAction?.invoke(a) }
            }
            acts.addView(b, LinearLayout.LayoutParams(0, -1, 1f).apply { if (i > 0) marginStart = dpi(8) })
        }
    }
}

/** Звонок: входящий — «Отклонить»/«Ответить», идущий — длительность и «Завершить». */
class CallCard(ctx: Context) : IslandCard(ctx) {
    private val avatar = ArtView(ctx).apply { circle = true; setPlaceholder(R.drawable.g_call) }
    private val name: TextView = ctx.text(18f, Fonts.semibold)
    private val sub: TextView = ctx.text(14f, Fonts.medium, Palette.TEXT_SECONDARY)
    private val time = ctx.chrono(14f, Palette.GREEN)
    private val decline = RoundButton(ctx).apply { set(R.drawable.g_call_end, Palette.RED) }
    private val accept = RoundButton(ctx).apply { set(R.drawable.g_call, Palette.GREEN) }
    private var live: Live? = null

    init {
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(avatar, LinearLayout.LayoutParams(dpi(50), dpi(50)))
        val texts = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(sub)
        texts.addView(name, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(2) })
        texts.addView(time, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dpi(2) })
        row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dpi(12); marginEnd = dpi(8) })
        row.addView(decline, LinearLayout.LayoutParams(dpi(50), dpi(50)))
        row.addView(accept, LinearLayout.LayoutParams(dpi(50), dpi(50)).apply { marginStart = dpi(14) })
        row.setPadding(dpi(18), dpi(10), dpi(18), dpi(18))
        addView(row, LayoutParams(-1, -2))
        decline.onTap = { live?.actions?.firstOrNull { it.role == Act.Role.DECLINE }?.let { onAction?.invoke(it) } }
        accept.onTap = { live?.actions?.firstOrNull { it.role == Act.Role.ACCEPT }?.let { onAction?.invoke(it) } }
    }

    override fun bind(l: Live) {
        live = l
        avatar.setDrawable(l.largeIcon)
        name.text = l.title
        val incoming = l.kind == Kind.CALL_INCOMING
        sub.text = if (incoming) "Входящий вызов" else l.text.ifBlank { "Вызов" }
        time.bindTo(if (incoming) Live(Kind.CALL, "") else l)
        decline.visibility = if (l.actions.any { it.role == Act.Role.DECLINE }) VISIBLE else GONE
        accept.visibility = if (incoming && l.actions.any { it.role == Act.Role.ACCEPT }) VISIBLE else GONE
    }
}

/** Уведомление, выехавшее из островка. */
class BannerCard(ctx: Context) : IslandCard(ctx) {
    private val icon = ArtView(ctx).apply { radiusFraction = 0.24f; setPlaceholder(R.drawable.g_bell) }
    private val badge = ArtView(ctx).apply { radiusFraction = 0.24f }
    private val app: TextView = ctx.text(12.5f, Fonts.medium, Palette.TEXT_SECONDARY)
    private val title: TextView = ctx.text(15.5f, Fonts.semibold)
    private val body: TextView = ctx.text(14.5f, Fonts.regular, 0xE6FFFFFF.toInt(), lines = 2)

    init {
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val iconBox = FrameLayout(ctx).apply { clipChildren = false }
        iconBox.addView(icon, LayoutParams(dpi(42), dpi(42)))
        iconBox.addView(badge, LayoutParams(dpi(18), dpi(18), Gravity.BOTTOM or Gravity.END).apply { setMargins(0, 0, -dpi(3), -dpi(3)) })
        row.addView(iconBox, LinearLayout.LayoutParams(dpi(42), dpi(42)))
        val texts = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(app)
        texts.addView(title, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(1) })
        texts.addView(body, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(1) })
        row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dpi(12) })
        row.setPadding(dpi(18), dpi(8), dpi(18), dpi(16))
        addView(row, LayoutParams(-1, -2))
    }

    override fun bind(l: Live) {
        val avatar = l.largeIcon
        if (avatar != null) {
            icon.circle = true
            icon.setDrawable(avatar)
            badge.setDrawable(l.appIcon)
            badge.visibility = if (l.appIcon != null) VISIBLE else GONE
        } else {
            icon.circle = false
            icon.setDrawable(l.appIcon)
            badge.visibility = GONE
        }
        app.text = (l.value ?: "") + " · сейчас"
        title.text = l.title
        body.text = l.text
        body.visibility = if (l.text.isBlank()) GONE else VISIBLE
    }
}
