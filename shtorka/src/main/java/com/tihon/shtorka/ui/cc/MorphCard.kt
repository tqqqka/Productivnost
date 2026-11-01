package com.tihon.shtorka.ui.cc

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.tihon.shtorka.ui.Glass
import com.tihon.shtorka.ui.clamp01
import com.tihon.shtorka.ui.lerp

/**
 * Раскрытый модуль. Карточка «вырастает» из маленького модуля: при morph=0
 * её якорная область совпадает с исходным модулем, при morph=1 — стоит на месте.
 * Пружина может перелететь 1 — тогда карточка чуть-чуть больше и возвращается.
 */
@SuppressLint("ViewConstructor")
class MorphCard(
    ctx: Context,
    val content: View,
    private val from: RectF,
    private val to: RectF,
    /** Область внутри content, которая превращается из модуля (null — вся карточка). */
    private val anchor: RectF?,
    private val fromRadius: Float,
    private val toRadius: Float,
    private val glassCard: Boolean,
    /** Что проявляется по ходу раскрытия (если null — всё содержимое). */
    private val fading: List<View>?,
) : FrameLayout(ctx) {
    private val glass = Glass(ctx)
    private val cur = RectF()
    private val path = Path()

    var morph = 0f
        set(v) {
            field = v
            val a = clamp01((v - 0.3f) / 0.55f)
            if (fading == null) content.alpha = a else fading.forEach { it.alpha = a }
            invalidate()
        }

    init {
        setWillNotDraw(false)
        clipChildren = false
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    override fun dispatchDraw(c: Canvas) {
        val m = morph
        val w = width.toFloat()
        val h = height.toFloat()
        val fl = from.left - to.left
        val ft = from.top - to.top
        if (glassCard) {
            cur.set(lerp(fl, 0f, m), lerp(ft, 0f, m), lerp(fl + from.width(), w, m), lerp(ft + from.height(), h, m))
            val r = lerp(fromRadius, toRadius, clamp01(m))
            glass.draw(c, cur, r, 0x3DFFFFFF, 1f)
            val save = c.save()
            path.reset()
            path.addRoundRect(cur, r, r, Path.Direction.CW)
            c.clipPath(path)
            val s = cur.width() / w
            c.translate(cur.left, cur.top)
            c.scale(s, s)
            super.dispatchDraw(c)
            c.restoreToCount(save)
        } else {
            val a = anchor ?: RectF(0f, 0f, w, h)
            cur.set(lerp(fl, a.left, m), lerp(ft, a.top, m), lerp(fl + from.width(), a.right, m), lerp(ft + from.height(), a.bottom, m))
            val sx = cur.width() / a.width()
            val sy = cur.height() / a.height()
            val save = c.save()
            c.translate(cur.left - a.left * sx, cur.top - a.top * sy)
            c.scale(sx, sy)
            super.dispatchDraw(c)
            c.restoreToCount(save)
        }
    }

    /** Пока карточка в движении — касания не пропускаем внутрь. */
    override fun onInterceptTouchEvent(ev: MotionEvent) = morph < 0.97f

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent) = glassCard
}
