package com.tihon.shtorka.system

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.tihon.shtorka.core.Bus
import kotlin.math.roundToInt

/** Фонарик: состояние приходит от системы, так что кнопка не врёт. */
object Torch {
    var on = false; private set
    var level = 1; private set
    var maxLevel = 1; private set

    private var cm: CameraManager? = null
    private var camId: String? = null

    private val callback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId != camId) return
            on = enabled
            Bus.fire()
        }

        override fun onTorchStrengthLevelChanged(cameraId: String, newStrengthLevel: Int) {
            if (cameraId != camId) return
            level = newStrengthLevel
            Bus.fire()
        }
    }

    fun init(ctx: Context) {
        if (cm != null) return
        val manager = ctx.getSystemService(CameraManager::class.java) ?: return
        cm = manager
        runCatching {
            val ids = manager.cameraIdList
            fun flash(id: String) = manager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            fun back(id: String) = manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            camId = ids.firstOrNull { flash(it) && back(it) } ?: ids.firstOrNull { flash(it) }
            camId?.let { id ->
                if (Build.VERSION.SDK_INT >= 33) {
                    val ch = manager.getCameraCharacteristics(id)
                    maxLevel = ch.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
                    level = ch.get(CameraCharacteristics.FLASH_INFO_STRENGTH_DEFAULT_LEVEL) ?: maxLevel
                }
            }
            manager.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
        }
    }

    fun release() {
        runCatching { cm?.unregisterTorchCallback(callback) }
        cm = null
    }

    val available get() = camId != null
    val supportsLevels get() = Build.VERSION.SDK_INT >= 33 && maxLevel > 1

    fun set(enable: Boolean): Boolean {
        val m = cm ?: return false
        val id = camId ?: return false
        return runCatching { m.setTorchMode(id, enable) }.isSuccess
    }

    fun toggle() = set(!on)

    /** Яркость фонарика 0..1 (Android 13+, если камера умеет). */
    val fraction: Float get() = if (!on) 0f else if (supportsLevels) level / maxLevel.toFloat() else 1f

    fun setFraction(f: Float) {
        val m = cm ?: return
        val id = camId ?: return
        if (f <= 0.02f) { set(false); return }
        if (!supportsLevels || Build.VERSION.SDK_INT < 33) { set(true); return }
        val lvl = (f * maxLevel).roundToInt().coerceIn(1, maxLevel)
        runCatching { m.turnOnTorchWithStrengthLevel(id, lvl) }
    }
}
