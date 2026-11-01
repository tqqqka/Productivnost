package com.tihon.shtorka.system

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import androidx.annotation.RequiresApi
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * Размытый фон, как у iPhone. Служба делает снимок экрана в момент касания,
 * он уменьшается до ~160 px по ширине и размывается — потом при отрисовке
 * растягивается с билинейной фильтрацией, что даёт мягкое «стекло» почти даром.
 */
class Backdrop(private val svc: AccessibilityService) {
    private val exec = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var pending = false
    private var waiting: ((Bitmap?) -> Unit)? = null

    fun capture(cb: (Bitmap?) -> Unit) {
        if (Build.VERSION.SDK_INT < 30) { cb(null); return }
        waiting = cb
        if (pending) return
        pending = true
        try {
            shoot()
        } catch (_: Throwable) {
            finish(null)
        }
    }

    @RequiresApi(30)
    private fun shoot() {
        svc.takeScreenshot(
            Display.DEFAULT_DISPLAY, exec,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    val out = try { process(result) } catch (_: Throwable) { null }
                    main.post { finish(out) }
                }

                override fun onFailure(errorCode: Int) {
                    main.post { finish(null) }
                }
            },
        )
    }

    private fun finish(b: Bitmap?) {
        pending = false
        val cb = waiting
        waiting = null
        cb?.invoke(b)
    }

    @RequiresApi(30)
    private fun process(r: AccessibilityService.ScreenshotResult): Bitmap? {
        val hb = r.hardwareBuffer
        try {
            val hw = Bitmap.wrapHardwareBuffer(hb, r.colorSpace) ?: return null
            val soft = hw.copy(Bitmap.Config.ARGB_8888, false) ?: return null
            hw.recycle()
            val w = 160
            val h = (soft.height * w / soft.width.toFloat()).roundToInt().coerceAtLeast(1)
            val small = Bitmap.createScaledBitmap(soft, w, h, true)
            if (small !== soft) soft.recycle()
            val m = if (small.isMutable) small else small.copy(Bitmap.Config.ARGB_8888, true)
            Blur.apply(m, 7)
            return m
        } finally {
            hb.close()
        }
    }
}

/** Трёхпроходное блочное размытие ≈ гауссово. На 160×350 занимает миллисекунды. */
object Blur {
    fun apply(bmp: Bitmap, radius: Int) {
        val w = bmp.width
        val h = bmp.height
        val a = IntArray(w * h)
        val b = IntArray(w * h)
        bmp.getPixels(a, 0, w, 0, 0, w, h)
        repeat(3) {
            horizontal(a, b, w, h, radius)
            vertical(b, a, w, h, radius)
        }
        bmp.setPixels(a, 0, w, 0, 0, w, h)
    }

    private fun horizontal(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        for (y in 0 until h) {
            val row = y * w
            var rs = 0; var gs = 0; var bs = 0
            for (i in -r..r) {
                val p = src[row + i.coerceIn(0, w - 1)]
                rs += (p shr 16) and 0xFF; gs += (p shr 8) and 0xFF; bs += p and 0xFF
            }
            for (x in 0 until w) {
                dst[row + x] = (0xFF shl 24) or ((rs / div) shl 16) or ((gs / div) shl 8) or (bs / div)
                val add = src[row + (x + r + 1).coerceAtMost(w - 1)]
                val rem = src[row + (x - r).coerceAtLeast(0)]
                rs += ((add shr 16) and 0xFF) - ((rem shr 16) and 0xFF)
                gs += ((add shr 8) and 0xFF) - ((rem shr 8) and 0xFF)
                bs += (add and 0xFF) - (rem and 0xFF)
            }
        }
    }

    private fun vertical(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        for (x in 0 until w) {
            var rs = 0; var gs = 0; var bs = 0
            for (i in -r..r) {
                val p = src[i.coerceIn(0, h - 1) * w + x]
                rs += (p shr 16) and 0xFF; gs += (p shr 8) and 0xFF; bs += p and 0xFF
            }
            for (y in 0 until h) {
                dst[y * w + x] = (0xFF shl 24) or ((rs / div) shl 16) or ((gs / div) shl 8) or (bs / div)
                val add = src[(y + r + 1).coerceAtMost(h - 1) * w + x]
                val rem = src[(y - r).coerceAtLeast(0) * w + x]
                rs += ((add shr 16) and 0xFF) - ((rem shr 16) and 0xFF)
                gs += ((add shr 8) and 0xFF) - ((rem shr 8) and 0xFF)
                bs += (add and 0xFF) - (rem and 0xFF)
            }
        }
    }
}
