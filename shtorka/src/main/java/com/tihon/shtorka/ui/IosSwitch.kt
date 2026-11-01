package com.tihon.shtorka.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import com.tihon.shtorka.core.Spring

/** Переключатель как в Настройках iOS: зелёная дорожка и белый кружок на пружине. */
class IosSwitch(ctx: Context) : View(ctx) {
    var checked = false
        private set
    var onChange: ((Boolean) -> Unit)? = null

    private var t = 0f
    private val spring = Spring(0f, 0.002f) { t = it; invalidate() }
    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumb = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.WHITE
        setShadowLayer(ctx.dp(2.5f), 0f, ctx.dp(1.5f), 0x40000000)
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun set(on: Boolean, animate: Boolean = false) {
        checked = on
        if (animate) spring.animateTo(if (on) 1f else 0f, stiffness = 500f, damping = 0.72f)
        else spring.snap(if (on) 1f else 0f)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(dpi(51), dpi(31))
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        track.color = blend(0xFF39393D.toInt(), Palette.GREEN, t)
        c.drawRoundRect(0f, 0f, w, h, h / 2, h / 2, track)
        val r = h / 2 - dp(2)
        val cx = lerp(h / 2, w - h / 2, t)
        c.drawCircle(cx, h / 2, r, thumb)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_UP) {
            set(!checked, animate = true)
            Haptics.tick(this)
            onChange?.invoke(checked)
        }
        return true
    }
}
