package com.tihon.productivnost.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/** Минимальный владелец жизненного цикла для Compose внутри окна WindowManager. */
private class OverlayOwner : SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val controller = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry get() = controller.savedStateRegistry
    override val viewModelStore = ViewModelStore()

    fun start() {
        controller.performAttach()
        controller.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun stop() {
        registry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}

/**
 * Показывает Compose-экран поверх всех приложений.
 * Одновременно живёт максимум одно окно каждого «слота».
 */
class OverlayHost(private val context: Context) {

    enum class Slot { REMINDER, BLOCK }

    private data class Window(val view: View, val owner: OverlayOwner)

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val windows = HashMap<Slot, Window>()

    fun isShowing(slot: Slot) = windows.containsKey(slot)

    fun show(
        slot: Slot,
        focusable: Boolean = true,
        onBack: (() -> Unit)? = null,
        content: @Composable () -> Unit,
    ) {
        hide(slot)
        val owner = OverlayOwner()
        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent(content)
            isFocusableInTouchMode = focusable
            if (focusable) {
                setOnKeyListener { _, keyCode, event ->
                    if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                        onBack?.invoke(); true
                    } else false
                }
            }
        }
        owner.start()

        var flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
        if (!focusable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }

        runCatching {
            wm.addView(view, params)
            windows[slot] = Window(view, owner)
            if (focusable) view.requestFocus()
        }.onFailure { owner.stop() }
    }

    fun hide(slot: Slot) {
        val w = windows.remove(slot) ?: return
        runCatching { wm.removeViewImmediate(w.view) }
        w.owner.stop()
    }

    fun hideAll() = Slot.entries.forEach { hide(it) }
}
