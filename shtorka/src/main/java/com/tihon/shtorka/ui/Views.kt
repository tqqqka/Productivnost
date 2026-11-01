package com.tihon.shtorka.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.content.ContextCompat
import com.tihon.shtorka.R
import com.tihon.shtorka.core.Spring
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Значок без подложки: кнопки плеера, «+», питание. При нажатии тускнеет и сжимается. */
@SuppressLint("ViewConstructor")
class GlyphButton(ctx: Context, icon: Int, private val fraction: Float = 0.6f, color: Int = Palette.WHITE) : View(ctx) {
    var icon: Drawable = ContextCompat.getDrawable(ctx, icon)!!.mutate().also { it.setTint(color) }
        private set
    var onTap: (() -> Unit)? = null
    private var color = color
    private var downX = 0f
    private var downY = 0f
    private var tracking = false
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private val press = Spring(1f, 0.001f) { v ->
        scaleX = v; scaleY = v
        alpha = if (isEnabled) 0.35f + 0.65f * clamp01((v - 0.86f) / 0.14f) else 0.35f
    }

    fun setIcon(res: Int) {
        icon = ContextCompat.getDrawable(context, res)!!.mutate().also { it.setTint(color) }
        invalidate()
    }

    fun setColor(c: Int) {
        color = c
        icon.setTint(c)
        invalidate()
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        alpha = if (enabled) 1f else 0.35f
    }

    override fun onDraw(c: Canvas) {
        val s = min(width, height) * fraction
        val l = (width - s) / 2f
        val t = (height - s) / 2f
        icon.setBounds(l.toInt(), t.toInt(), (l + s).toInt(), (t + s).toInt())
        icon.draw(c)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x; downY = e.y; tracking = true
                press.animateTo(0.86f, stiffness = 1200f, damping = 1f)
            }
            MotionEvent.ACTION_MOVE -> if (tracking && (abs(e.x - downX) > slop * 2 || abs(e.y - downY) > slop * 2)) {
                tracking = false
                press.animateTo(1f, stiffness = 600f, damping = 0.6f)
            }
            MotionEvent.ACTION_UP -> {
                press.animateTo(1f, stiffness = 600f, damping = 0.6f)
                if (tracking) {
                    tracking = false
                    Haptics.tick(this)
                    onTap?.invoke()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                tracking = false
                press.animateTo(1f, stiffness = 600f, damping = 0.6f)
            }
        }
        return true
    }
}

/** Обложка альбома или иконка с круглёнными углами (center-crop). */
class ArtView(ctx: Context) : View(ctx) {
    var radiusFraction = 0.18f
    var circle = false
    private var bmp: Bitmap? = null
    private var drawable: Drawable? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val holder = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF }
    private var note = ContextCompat.getDrawable(ctx, R.drawable.g_music)!!.mutate().also { it.setTint(0x99FFFFFF.toInt()) }

    /** Значок-заглушка, пока нет картинки (по умолчанию — нота). */
    fun setPlaceholder(res: Int) {
        note = ContextCompat.getDrawable(context, res)!!.mutate().also { it.setTint(0x99FFFFFF.toInt()) }
        invalidate()
    }
    private val m = Matrix()
    private val r = RectF()

    fun setBitmap(b: Bitmap?) {
        if (b === bmp && drawable == null) return
        bmp = b
        drawable = null
        paint.shader = b?.let { BitmapShader(it, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
        invalidate()
    }

    fun setDrawable(d: Drawable?) {
        bmp = null
        paint.shader = null
        drawable = d
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        r.set(0f, 0f, w, h)
        val rad = if (circle) min(w, h) / 2 else min(w, h) * radiusFraction
        val b = bmp
        val d = drawable
        when {
            b != null -> {
                val k = max(w / b.width, h / b.height)
                m.setScale(k, k)
                m.postTranslate((w - b.width * k) / 2, (h - b.height * k) / 2)
                paint.shader.setLocalMatrix(m)
                c.drawRoundRect(r, rad, rad, paint)
            }
            d != null -> {
                val save = c.save()
                val p = android.graphics.Path().apply { addRoundRect(r, rad, rad, android.graphics.Path.Direction.CW) }
                c.clipPath(p)
                d.setBounds(0, 0, width, height)
                d.draw(c)
                c.restoreToCount(save)
            }
            else -> {
                c.drawRoundRect(r, rad, rad, holder)
                val s = min(w, h) * 0.5f
                note.setBounds(((w - s) / 2).toInt(), ((h - s) / 2).toInt(), ((w + s) / 2).toInt(), ((h + s) / 2).toInt())
                note.draw(c)
            }
        }
    }
}
