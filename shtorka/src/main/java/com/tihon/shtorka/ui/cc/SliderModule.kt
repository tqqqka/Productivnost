package com.tihon.shtorka.ui.cc

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.tihon.shtorka.R
import com.tihon.shtorka.core.Spring
import com.tihon.shtorka.ui.Glyphs
import com.tihon.shtorka.ui.Haptics
import com.tihon.shtorka.ui.Palette
import com.tihon.shtorka.ui.dp
import com.tihon.shtorka.ui.rubber
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign

/**
 * Вертикальный ползунок яркости/громкости как в iOS: тянется относительно,
 * а не прыгает под палец; за краем резиново растягивается; значок внизу
 * двухцветный — темнеет ровно там, где его накрывает белая заливка.
 */
@SuppressLint("ViewConstructor")
class SliderModule(ctx: Context, val kind: Kind, private val big: Boolean = false) : ModuleView(ctx) {
    enum class Kind { BRIGHTNESS, VOLUME, TORCH }

    var value = 0.5f
        private set
    var onChange: ((Float) -> Unit)? = null
    var onRelease: ((Float) -> Unit)? = null
    var onExpand: (() -> Unit)? = null
    val dragging get() = isDragging

    private var isDragging = false
    private var downX = 0f
    private var downY = 0f
    private var startY = 0f
    private var startValue = 0f
    private var longFired = false
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop

    private var stretch = 0f
    private val stretchSpring = Spring(0f, 0.3f) { stretch = it; invalidate() }
    private var valueAnim: ValueAnimator? = null

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.WHITE }
    private val glyph = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clip = Path()
    private val torch = ContextCompat.getDrawable(ctx, R.drawable.g_flashlight)!!.mutate()

    private val longRunnable = Runnable {
        if (!isDragging && onExpand != null) {
            longFired = true
            Haptics.heavy(this)
            onExpand?.invoke()
        }
    }

    init {
        platter = true
    }

    /** Значение пришло из системы (кнопки громкости и т.п.). */
    fun setValueExternal(v: Float, animate: Boolean = true) {
        if (isDragging) return
        val target = v.coerceIn(0f, 1f)
        valueAnim?.cancel()
        if (!animate || !isShown || abs(target - value) < 0.002f) {
            value = target
            invalidate()
            return
        }
        valueAnim = ValueAnimator.ofFloat(value, target).apply {
            duration = 220
            interpolator = DecelerateInterpolator()
            addUpdateListener { value = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val up = max(stretch, 0f)
        val down = max(-stretch, 0f)
        val squeeze = abs(stretch) * 0.16f
        box.set(squeeze, -up, w - squeeze, h + down)
        val rad = box.width() * if (big) 0.29f else 0.36f
        glass.draw(c, box, rad, Palette.GLASS, 1f)

        clip.reset()
        clip.addRoundRect(box, rad, rad, Path.Direction.CW)
        val fillTop = box.bottom - box.height() * value
        var save = c.save()
        c.clipPath(clip)
        c.drawRect(box.left, fillTop, box.right, box.bottom, fill)
        c.restoreToCount(save)

        // Значок: белый над заливкой, серый на ней.
        val gs = box.width() * if (big) 0.26f else 0.40f
        val gcx = box.centerX()
        val gcy = box.bottom - box.width() * if (big) 0.30f else 0.50f
        save = c.save()
        c.clipRect(box.left, box.top, box.right, fillTop)
        drawGlyph(c, gcx, gcy, gs, Palette.WHITE)
        c.restoreToCount(save)
        save = c.save()
        c.clipRect(box.left, fillTop, box.right, box.bottom)
        drawGlyph(c, gcx, gcy, gs, Palette.GLYPH_ON_WHITE)
        c.restoreToCount(save)
    }

    private fun drawGlyph(c: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        glyph.color = color
        when (kind) {
            Kind.BRIGHTNESS -> Glyphs.sun(c, cx, cy, s, value, glyph)
            Kind.VOLUME -> Glyphs.speaker(c, cx, cy, s, value, glyph)
            Kind.TORCH -> {
                torch.setTint(color)
                val k = s * 1.1f
                torch.setBounds((cx - k / 2).toInt(), (cy - k / 2).toInt(), (cx + k / 2).toInt(), (cy + k / 2).toInt())
                torch.draw(c)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                downX = e.x; downY = e.y
                isDragging = false
                longFired = false
                valueAnim?.cancel()
                if (onExpand != null) postDelayed(longRunnable, LONG_PRESS_MS)
            }
            MotionEvent.ACTION_MOVE -> {
                if (longFired) return true
                if (!isDragging && abs(e.y - downY) > slop) {
                    isDragging = true
                    removeCallbacks(longRunnable)
                    startY = e.y
                    startValue = value
                }
                if (isDragging) {
                    val span = height * if (big) 1f else 1.1f
                    val raw = startValue + (startY - e.y) / span
                    val nv = raw.coerceIn(0f, 1f)
                    val over = (raw - nv) * height
                    if ((nv >= 1f && value < 1f) || (nv <= 0f && value > 0f)) Haptics.edge(this)
                    value = nv
                    stretchSpring.snap(rubber(abs(over), dp(if (big) 26 else 18)) * sign(over))
                    onChange?.invoke(nv)
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longRunnable)
                parent?.requestDisallowInterceptTouchEvent(false)
                if (isDragging) {
                    isDragging = false
                    onRelease?.invoke(value)
                }
                if (stretch != 0f) stretchSpring.animateTo(0f, stiffness = 650f, damping = 0.55f)
            }
        }
        return true
    }
}
