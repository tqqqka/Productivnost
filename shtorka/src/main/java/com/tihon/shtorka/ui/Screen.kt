package com.tihon.shtorka.ui

import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
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
