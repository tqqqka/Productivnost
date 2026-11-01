package com.tihon.shtorka.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.roundToInt

/**
 * Фон шторки. Для Пункта управления — размытый снимок, проявляющийся вместе
 * с жестом. Для Центра уведомлений — «занавес»: размытие видно только выше
 * нижнего края листа, который тянется пальцем.
 */
class BackdropView(ctx: Context) : View(ctx) {
    private var bitmap: Bitmap? = null
    private var loaded = 0f
    private var loadAnim: ValueAnimator? = null

    /** 0..1 — прозрачность в режиме Пункта управления. */
    var progress = 0f
        set(v) { field = v; invalidate() }

    /** ≥ 0 — режим листа: рисуем только выше этой линии. */
    var sheetBottom = -1f
        set(v) { field = v; invalidate() }

    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val dim = Paint()
    private val dst = RectF()

    init {
        // Насыщенность выше, яркость ниже — так выглядит материал iOS.
        val cm = ColorMatrix().apply { setSaturation(1.55f) }
        val dark = ColorMatrix(
            floatArrayOf(
                0.66f, 0f, 0f, 0f, 4f,
                0f, 0.66f, 0f, 0f, 4f,
                0f, 0f, 0.66f, 0f, 6f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        cm.postConcat(dark)
        bmpPaint.colorFilter = ColorMatrixColorFilter(cm)
    }

    fun setBlurred(b: Bitmap?) {
        loadAnim?.cancel()
        bitmap = b
        if (b == null) {
            loaded = 0f
            invalidate()
            return
        }
        loadAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 180
            interpolator = DecelerateInterpolator()
            addUpdateListener { loaded = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    fun clear() {
        loadAnim?.cancel()
        bitmap = null
        loaded = 0f
        progress = 0f
        sheetBottom = -1f
    }

    override fun onDraw(c: Canvas) {
        val sheet = sheetBottom >= 0f
        val p = if (sheet) 1f else clamp01(progress)
        if (p <= 0.001f) return
        val bottom = if (sheet) sheetBottom.coerceAtMost(height.toFloat()) else height.toFloat()
        if (bottom <= 0f) return
        val save = c.save()
        if (sheet) c.clipRect(0f, 0f, width.toFloat(), bottom)
        val b = bitmap
        if (b != null && loaded > 0f) {
            bmpPaint.alpha = (255 * p * loaded).roundToInt()
            dst.set(0f, 0f, width.toFloat(), height.toFloat())
            c.drawBitmap(b, null, dst, bmpPaint)
        }
        // Пока снимка нет (или его нельзя сделать) — просто затемнение.
        val dimAlpha = p * lerp(if (sheet) 0.86f else 0.66f, if (sheet) 0.10f else 0.08f, loaded)
        dim.color = withAlpha(Palette.BLACK, dimAlpha)
        c.drawRect(0f, 0f, width.toFloat(), bottom, dim)
        c.restoreToCount(save)
    }
}
