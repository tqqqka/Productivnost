package com.tihon.shtorka.ui

import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import android.view.WindowManager
import kotlin.math.max
import kotlin.math.roundToInt

/** Размеры экрана, строки состояния и капли камеры. */
object Screen {
    fun display(ctx: Context): Display =
        ctx.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)

    @Suppress("DEPRECATION")
    fun size(ctx: Context): Point = Point().also { display(ctx).getRealSize(it) }

    fun rotation(ctx: Context): Int = display(ctx).rotation

    private fun dimen(ctx: Context, name: String): Int {
        val id = ctx.resources.getIdentifier(name, "dimen", "android")
        return if (id > 0) ctx.resources.getDimensionPixelSize(id) else 0
    }

    fun statusBar(ctx: Context): Int {
        val sb = dimen(ctx, "status_bar_height").takeIf { it > 0 } ?: ctx.dp(28).roundToInt()
        return max(sb, notch(ctx).bottom)
    }

    fun navBar(ctx: Context): Int = dimen(ctx, "navigation_bar_height")

    /**
     * Прямоугольник капли сверху. У Galaxy A26 это каплевидный вырез по центру.
     * Если выреза нет — «виртуальная» капля по центру, чтобы островок всё равно жил.
     */
    fun notch(ctx: Context): Rect {
        val w = size(ctx).x
        if (Build.VERSION.SDK_INT >= 29) {
            val c = runCatching { display(ctx).cutout }.getOrNull()
            val r = c?.boundingRectTop
            if (r != null && !r.isEmpty) return Rect(r)
        }
        val nw = ctx.dp(30).roundToInt()
        val nh = ctx.dp(24).roundToInt()
        return Rect(w / 2 - nw / 2, 0, w / 2 + nw / 2, nh)
    }

    fun hasRealNotch(ctx: Context): Boolean =
        Build.VERSION.SDK_INT >= 29 && runCatching { display(ctx).cutout?.boundingRectTop?.isEmpty == false }.getOrDefault(false)
}

/** Окно поверх всего, включая строку состояния: доступно только службе спец. возможностей. */
fun overlayParams(w: Int, h: Int): WindowManager.LayoutParams =
    WindowManager.LayoutParams(
        w, h,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        android.graphics.PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = android.view.Gravity.TOP or android.view.Gravity.START
        if (Build.VERSION.SDK_INT >= 30) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= 28) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }
