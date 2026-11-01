package com.tihon.shtorka.ui.island

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Surface
import android.view.View
import android.view.WindowManager
import com.tihon.shtorka.core.Prefs
import com.tihon.shtorka.service.ShadeService
import com.tihon.shtorka.system.Launch
import com.tihon.shtorka.system.MediaRepo
import com.tihon.shtorka.ui.Haptics
import com.tihon.shtorka.ui.Screen
import com.tihon.shtorka.ui.overlayParams
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Решает, что показывает островок: музыку, зарядку, режим звонка или ничего.
 * Окно островка маленькое и растёт только на время анимации, чтобы не
 * перехватывать касания у приложений под ним.
 */
class IslandController(private val svc: ShadeService) {
    private val wm = svc.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val view = IslandView(svc, this)
    private val lp = overlayParams(1, 1).apply {
        flags = flags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        title = "Shtorka island"
    }
    private var attached = false
    private var expanded = false
    private var transient: IslandView.Kind? = null
    private var lastPlayingAt = 0L

    private val endTransient = Runnable { transient = null; update() }
    private val mediaListener: () -> Unit = { onMedia() }
    private val pausedCheck = Runnable { update() }

    fun attach() {
        if (attached || !Prefs.island) return
        geometry()
        view.visibility = View.INVISIBLE
        val size = view.windowSizeFor(view.notch.width().toFloat(), view.notch.bottom.toFloat())
        place(size[0], size[1])
        runCatching { wm.addView(view, lp); attached = true }
        MediaRepo.add(mediaListener)
        if (MediaRepo.now?.playing == true) lastPlayingAt = SystemClock.uptimeMillis()
        update()
    }

    fun detach() {
        if (!attached) return
        MediaRepo.remove(mediaListener)
        main.removeCallbacksAndMessages(null)
        runCatching { wm.removeViewImmediate(view) }
        attached = false
        expanded = false
        transient = null
    }

    fun applyPrefs() {
        when {
            Prefs.island && !attached -> attach()
            !Prefs.island && attached -> detach()
            else -> update()
        }
    }

    fun onConfigChanged() {
        if (!attached) return
        geometry()
        expanded = false
        update(animate = false)
    }

    fun onScreenOff() {
        expanded = false
        transient = null
        main.removeCallbacks(endTransient)
        update(animate = false)
    }

    fun onCharging(plugged: Boolean) {
        if (plugged && Prefs.islandCharging) showTransient(IslandView.Kind.CHARGING, 3200)
    }

    fun onRinger() {
        if (Prefs.islandRinger) showTransient(IslandView.Kind.RINGER, 2200)
    }

    private fun showTransient(k: IslandView.Kind, ms: Long) {
        if (!attached) return
        transient = k
        main.removeCallbacks(endTransient)
        main.postDelayed(endTransient, ms)
        update()
    }

    private fun onMedia() {
        val n = MediaRepo.now
        if (n?.playing == true) lastPlayingAt = SystemClock.uptimeMillis()
        main.removeCallbacks(pausedCheck)
        if (n != null && !n.playing) main.postDelayed(pausedCheck, PAUSE_KEEP_MS + 50)
        val before = view.kind
        update()
        if (view.kind == before) view.refreshMedia()
    }

    private fun geometry() {
        view.notch = Screen.notch(svc)
        view.screenW = Screen.size(svc).x
    }

    private fun wanted(): IslandView.Kind {
        if (Screen.rotation(svc) != Surface.ROTATION_0) return IslandView.Kind.HIDDEN
        transient?.let { return it }
        val n = MediaRepo.now
        val musicOn = Prefs.islandMusic && n != null &&
            (n.playing || SystemClock.uptimeMillis() - lastPlayingAt < PAUSE_KEEP_MS)
        if (!musicOn) expanded = false
        return when {
            musicOn && expanded -> IslandView.Kind.MEDIA_EXPANDED
            musicOn -> IslandView.Kind.MEDIA
            else -> IslandView.Kind.HIDDEN
        }
    }

    fun update(animate: Boolean = true) {
        if (!attached) return
        val k = wanted()
        if (k == view.kind && view.visibility == View.VISIBLE) return
        if (k == IslandView.Kind.HIDDEN && view.kind == IslandView.Kind.HIDDEN) return
        view.setKind(k, animate)
    }

    // ---------- от вида ----------

    fun onTap() {
        when (view.kind) {
            IslandView.Kind.MEDIA -> {
                expanded = true
                Haptics.heavy(view)
                update()
            }
            IslandView.Kind.CHARGING, IslandView.Kind.RINGER -> {
                main.removeCallbacks(endTransient)
                endTransient.run()
            }
            else -> Unit
        }
    }

    fun collapse() {
        if (!expanded) return
        expanded = false
        update()
    }

    fun openMediaApp() {
        if (MediaRepo.open(svc)) collapse()
    }

    fun openOutput() {
        Launch.start(svc, Launch.bluetoothSettings())
        collapse()
    }

    fun onHidden() {
        fitWindow(0f, 0f)
    }

    /** Перед ростом — расширить окно, чтобы форма в него влезла. */
    fun growWindow(w: Float, h: Float) {
        val s = view.windowSizeFor(w, h)
        if (s[0] > lp.width || s[1] > lp.height) place(max(s[0], lp.width), max(s[1], lp.height))
    }

    /** После анимации — ужать окно до формы. */
    fun fitWindow(w: Float, h: Float) {
        val s = view.windowSizeFor(max(w, view.notch.width().toFloat()), max(h, view.notch.bottom.toFloat()))
        place(s[0], s[1])
    }

    private fun place(w: Int, h: Int) {
        lp.width = w
        lp.height = h
        lp.x = (view.notch.exactCenterX() - w / 2f).roundToInt()
        lp.y = 0
        if (attached) runCatching { wm.updateViewLayout(view, lp) }
    }

    companion object {
        /** Сколько островок держит музыку на паузе, прежде чем спрятаться. */
        const val PAUSE_KEEP_MS = 8000L
    }
}
