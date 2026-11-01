package com.tihon.shtorka.ui.cc

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.tihon.shtorka.core.Spring
import com.tihon.shtorka.ui.Glass
import com.tihon.shtorka.ui.Haptics
import com.tihon.shtorka.ui.Palette
import com.tihon.shtorka.ui.blend
import kotlin.math.abs
import kotlin.math.min

/**
 * Основа модуля Пункта управления: стеклянная плашка, пружинное нажатие,
 * касание и долгое нажатие (раскрытие, как Haptic Touch).
 */
open class ModuleView(ctx: Context) : FrameLayout(ctx) {
    protected val glass = Glass(ctx)
    protected val box = RectF()

    var cornerRadius = 0f
    var circle = false
    var platter = true
    var platterColor = Palette.GLASS
        set(v) { field = v; invalidate() }
    var rimAlpha = 1f
        set(v) { field = v; invalidate() }

    /** Место в сетке 4 колонки. */
    var col = 0; var row = 0; var colSpan = 1; var rowSpan = 1

    var onTap: (() -> Unit)? = null
    var onLongPress: (() -> Unit)? = null

    var hiddenForExpand = false
        set(v) { field = v; applyAlpha() }

    private var openScale = 1f
    private var openAlpha = 1f
    private var pressScale = 1f
    private val press = Spring(1f, 0.0005f) { pressScale = it; applyScale() }

    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var tracking = false
    private var longFired = false
    private val longRunnable = Runnable {
        if (tracking) {
            longFired = true
            tracking = false
            press.animateTo(1.04f, stiffness = 700f, damping = 0.7f) {
                press.animateTo(1f, stiffness = 500f, damping = 0.8f)
            }
            Haptics.heavy(this)
            onLongPress?.invoke()
        }
    }

    init {
        setWillNotDraw(false)
        clipChildren = false
        clipToPadding = false
    }

    /** Положение при открытии/закрытии шторки. */
    fun setOpenTransform(scale: Float, ty: Float, alpha: Float) {
        openScale = scale
        openAlpha = alpha
        translationY = ty
        applyScale()
        applyAlpha()
    }

    private fun applyScale() {
        val s = openScale * pressScale
        scaleX = s
        scaleY = s
    }

    private fun applyAlpha() {
        alpha = if (hiddenForExpand) 0f else openAlpha
    }

    fun radius(): Float = if (circle) min(width, height) / 2f else cornerRadius

    override fun onDraw(c: Canvas) {
        if (!platter) return
        box.set(0f, 0f, width.toFloat(), height.toFloat())
        glass.draw(c, box, radius(), platterColor, rimAlpha)
    }

    protected fun pressDown() = press.animateTo(0.94f, stiffness = 1100f, damping = 1f)
    protected fun pressUp() = press.animateTo(1f, stiffness = 520f, damping = 0.58f)

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (onTap == null && onLongPress == null) return super.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x; downY = e.y
                tracking = true
                longFired = false
                pressDown()
                if (onLongPress != null) postDelayed(longRunnable, LONG_PRESS_MS)
            }
            MotionEvent.ACTION_MOVE -> if (tracking && (abs(e.x - downX) > slop || abs(e.y - downY) > slop)) {
                cancelTracking()
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longRunnable)
                if (tracking && !longFired) {
                    tracking = false
                    pressUp()
                    Haptics.tick(this)
                    onTap?.invoke()
                } else if (!longFired) {
                    pressUp()
                }
                tracking = false
            }
            MotionEvent.ACTION_CANCEL -> cancelTracking()
        }
        return true
    }

    private fun cancelTracking() {
        removeCallbacks(longRunnable)
        if (tracking) pressUp()
        tracking = false
    }

    companion object {
        const val LONG_PRESS_MS = 360L
    }
}

/**
 * Круглая кнопка iOS 18: неактивная — дымчатая, активная — залита цветом
 * (или белая с цветным значком, как фонарик и блокировка поворота).
 */
open class CircleToggle(
    ctx: Context,
    icon: Int,
    private val onFill: Int,
    private val onGlyph: Int = Palette.WHITE,
    private val offFill: Int = Palette.CIRCLE_OFF,
    private val glyphFraction: Float = 0.44f,
    private val rim: Boolean = false,
) : ModuleView(ctx) {
    private var iconDrawable = ContextCompat.getDrawable(ctx, icon)!!.mutate()
    var active = false
        private set
    private var t = 0f
    private var anim: ValueAnimator? = null

    init {
        circle = true
        refreshColors()
    }

    fun setIcon(res: Int) {
        iconDrawable = ContextCompat.getDrawable(context, res)!!.mutate()
        refreshColors()
    }

    private var holdUntil = 0L

    /** Нажатие пользователя: сразу перекрашиваем и пару секунд не слушаем систему, пока она переключается. */
    fun userToggle(): Boolean {
        val nv = !active
        setActive(nv)
        holdUntil = android.os.SystemClock.uptimeMillis() + 2600
        return nv
    }

    /** Состояние из системы — игнорируется, пока действует [userToggle]. */
    fun setFromSystem(on: Boolean) {
        if (android.os.SystemClock.uptimeMillis() < holdUntil) return
        setActive(on)
    }

    fun setActive(on: Boolean, animate: Boolean = true) {
        if (on == active && anim?.isRunning != true) {
            if ((t == 1f) == on) return
        }
        active = on
        anim?.cancel()
        val target = if (on) 1f else 0f
        if (!animate || !isAttachedToWindow || !isShown) {
            t = target
            refreshColors()
            return
        }
        anim = ValueAnimator.ofFloat(t, target).apply {
            duration = 230
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener { t = it.animatedValue as Float; refreshColors() }
            start()
        }
    }

    private fun refreshColors() {
        platterColor = blend(offFill, onFill, t)
        iconDrawable.setTint(blend(Palette.WHITE, onGlyph, t))
        rimAlpha = if (rim) 1f - t else 0f
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val s = min(width, height) * glyphFraction
        val l = (width - s) / 2f
        val tp = (height - s) / 2f
        iconDrawable.setBounds(l.toInt(), tp.toInt(), (l + s).toInt(), (tp + s).toInt())
        iconDrawable.draw(c)
    }
}
