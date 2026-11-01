package com.tihon.shtorka.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import com.tihon.shtorka.core.Prefs
import com.tihon.shtorka.core.Spring
import com.tihon.shtorka.service.ShadeService
import com.tihon.shtorka.ui.cc.ControlCenterView
import com.tihon.shtorka.ui.nc.NotificationCenterView
import kotlin.math.abs

fun overlayParams(w: Int, h: Int): WindowManager.LayoutParams =
    WindowManager.LayoutParams(
        w, h,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = android.view.Gravity.TOP or android.view.Gravity.START
        if (Build.VERSION.SDK_INT >= 30) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= 28) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

/**
 * Хозяин шторки: невидимая полоска-ловушка у верхнего края и полноэкранное
 * окно с Пунктом управления и Центром уведомлений. Справа от капли — Пункт
 * управления, слева — Центр уведомлений, как у iPhone.
 */
class PanelHost(val svc: ShadeService) {
    enum class Mode { NONE, CC, NC }

    private val wm = svc.getSystemService(WindowManager::class.java)

    var statusBarHeight = 0; private set
    var navBarHeight = 0; private set
    var notch = Rect(); private set
    var screenW = 0; private set
    var screenH = 0; private set

    var mode = Mode.NONE; private set
    var progress = 0f; private set
    var isOpen = false; private set

    private val spring = Spring(0f) { apply(it) }
    private var attached = false
    private var freshPull = false

    init { measureScreen() }

    val root = PanelRoot(svc, this)
    private val trigger = TriggerView(svc, this)
    private val rootLp = overlayParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        .apply { title = "Shtorka panel" }
    private val triggerLp = overlayParams(WindowManager.LayoutParams.MATCH_PARENT, statusBarHeight)
        .apply { title = "Shtorka edge" }

    private fun measureScreen() {
        val s = Screen.size(svc)
        screenW = s.x
        screenH = s.y
        notch = Screen.notch(svc)
        statusBarHeight = Screen.statusBar(svc)
        navBarHeight = Screen.navBar(svc)
    }

    fun attach() {
        if (attached) return
        root.visibility = View.INVISIBLE
        runCatching {
            wm.addView(trigger, triggerLp)
            wm.addView(root, rootLp)
            attached = true
        }
        applyPrefs()
    }

    fun detach() {
        if (!attached) return
        spring.cancel()
        runCatching { wm.removeViewImmediate(trigger) }
        runCatching { wm.removeViewImmediate(root) }
        attached = false
    }

    fun applyPrefs() {
        if (!attached) return
        val cc = Prefs.controlCenter
        val nc = Prefs.notificationCenter
        val split = notch.centerX()
        trigger.visibility = if (cc || nc) View.VISIBLE else View.GONE
        when {
            cc && nc -> { triggerLp.x = 0; triggerLp.width = screenW }
            cc -> { triggerLp.x = split; triggerLp.width = screenW - split }
            nc -> { triggerLp.x = 0; triggerLp.width = split }
        }
        triggerLp.height = statusBarHeight
        runCatching { wm.updateViewLayout(trigger, triggerLp) }
        root.applyPrefs()
    }

    fun onConfigChanged() {
        close(false)
        measureScreen()
        applyPrefs()
        root.requestLayout()
    }

    private fun modeAt(rawX: Float): Mode? {
        val right = rawX >= notch.centerX()
        return when {
            right && Prefs.controlCenter -> Mode.CC
            !right && Prefs.notificationCenter -> Mode.NC
            Prefs.controlCenter && !Prefs.notificationCenter -> Mode.CC
            Prefs.notificationCenter && !Prefs.controlCenter -> Mode.NC
            else -> null
        }
    }

    // ---------- жест от верхнего края ----------

    fun pullBegin(rawX: Float): Boolean {
        val m = modeAt(rawX) ?: return false
        spring.cancel()
        if (mode != m) {
            if (mode != Mode.NONE) root.reset()
            mode = m
            root.show(m)
        }
        freshPull = progress < 0.01f
        root.visibility = View.VISIBLE
        if (freshPull) captureBackdrop(m)
        return true
    }

    fun pullMove(rawY: Float, dy: Float) {
        val p = when (mode) {
            Mode.CC -> mapCC(dy / ccDistance())
            Mode.NC -> clamp01(rawY / screenH)
            Mode.NONE -> return
        }
        spring.snap(p)
    }

    fun pullEnd(velocityY: Float, dragged: Boolean) {
        if (mode == Mode.NONE) return
        if (!dragged) {
            if (progress < 0.01f) hideNow() else settle(0f)
            return
        }
        settle(velocityY)
    }

    private fun captureBackdrop(m: Mode) {
        svc.backdrop.capture { b -> if (mode == m) root.backdrop.setBlurred(b) }
    }

    private fun ccDistance() = screenH * 0.36f
    private fun mapCC(raw: Float) = if (raw <= 1f) raw.coerceAtLeast(0f) else 1f + rubber(raw - 1f, 0.22f)

    // ---------- перетаскивание открытой панели ----------

    private var dragFrom = 0f

    fun dragBegin() {
        spring.cancel()
        dragFrom = progress.coerceAtMost(1f)
    }

    fun dragMove(dy: Float) {
        val p = when (mode) {
            Mode.CC -> mapCC(dragFrom + dy / ccDistance())
            Mode.NC -> clamp01(dragFrom + dy / screenH)
            Mode.NONE -> return
        }
        spring.snap(p)
    }

    fun dragEnd(velocityY: Float) = settle(velocityY)

    private fun settle(velocityY: Float) {
        val dist = if (mode == Mode.CC) ccDistance() else screenH.toFloat()
        val v = velocityY / dist
        val open = when {
            v > 1.1f -> true
            v < -1.1f -> false
            else -> progress > 0.4f
        }
        if (open) openTo(v) else closeTo(v)
    }

    private fun openTo(v: Float) {
        isOpen = true
        setFocusable(true)
        if (mode == Mode.CC) spring.animateTo(1f, v, stiffness = 340f, damping = 0.74f)
        else spring.animateTo(1f, v, stiffness = 300f, damping = 0.9f)
    }

    private fun closeTo(v: Float) {
        isOpen = false
        spring.animateTo(0f, v, stiffness = 460f, damping = 1f) { canceled -> if (!canceled) hideNow() }
    }

    /** Свайп двумя пальцами — бросаем свою шторку и открываем системную Samsung. */
    fun openSystemShade() {
        if (mode != Mode.NONE) hideNow()
        svc.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
    }

    /** Открыть программно (кнопка «Проверить» в настройках). */
    fun open(m: Mode) {
        if (!attached) return
        if (mode != m) {
            if (mode != Mode.NONE) hideNow()
            mode = m
            root.show(m)
        }
        root.visibility = View.VISIBLE
        if (progress < 0.01f) captureBackdrop(m)
        openTo(0f)
    }

    fun close(animated: Boolean = true) {
        if (mode == Mode.NONE) return
        if (!animated) hideNow() else closeTo(0f)
    }

    private fun apply(p: Float) {
        progress = p
        root.applyProgress(mode, p)
    }

    private fun hideNow() {
        spring.cancel()
        progress = 0f
        root.applyProgress(mode, 0f)
        root.reset()
        root.visibility = View.INVISIBLE
        setFocusable(false)
        mode = Mode.NONE
        isOpen = false
    }

    private fun setFocusable(f: Boolean) {
        if (!attached) return
        val flags = if (f) rootLp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        else rootLp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        if (flags == rootLp.flags) return
        rootLp.flags = flags
        runCatching { wm.updateViewLayout(root, rootLp) }
    }

    /** Окно потеряло фокус — например, жест «домой». Закрываемся, если это не мы жмём плитку. */
    fun onFocusLost() {
        if (!isOpen) return
        root.postDelayed({
            if (isOpen && !svc.qs.busy && !root.hasWindowFocus()) close(true)
        }, 350)
    }
}

/** Полоска у верхнего края: ловит свайп вниз. */
@SuppressLint("ViewConstructor")
class TriggerView(ctx: Context, private val host: PanelHost) : View(ctx) {
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private var downY = 0f
    private var active = false
    private var dragged = false
    private var vt: VelocityTracker? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val raw = MotionEvent.obtain(e).apply { setLocation(e.rawX, e.rawY) }
        try {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    vt?.recycle()
                    vt = VelocityTracker.obtain().also { it.addMovement(raw) }
                    downY = e.rawY
                    dragged = false
                    active = host.pullBegin(e.rawX)
                }
                MotionEvent.ACTION_POINTER_DOWN -> if (active) {
                    active = false
                    vt?.recycle()
                    vt = null
                    host.openSystemShade()
                }
                MotionEvent.ACTION_MOVE -> if (active) {
                    vt?.addMovement(raw)
                    if (!dragged && e.rawY - downY > slop) dragged = true
                    if (dragged) host.pullMove(e.rawY, e.rawY - downY)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (active) {
                    vt?.addMovement(raw)
                    vt?.computeCurrentVelocity(1000)
                    val vy = if (e.actionMasked == MotionEvent.ACTION_UP) vt?.yVelocity ?: 0f else 0f
                    active = false
                    host.pullEnd(vy, dragged)
                    vt?.recycle()
                    vt = null
                }
            }
        } finally {
            raw.recycle()
        }
        return true
    }
}

/** Полноэкранное окно шторки. Ловит свайп для закрытия, «Назад» и тап мимо. */
@SuppressLint("ViewConstructor")
class PanelRoot(ctx: Context, private val host: PanelHost) : FrameLayout(ctx) {
    val backdrop = BackdropView(ctx)
    val cc = ControlCenterView(ctx, host)
    val nc = NotificationCenterView(ctx, host)

    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var originY = 0f
    private var decided = false
    private var dragging = false
    private var vt: VelocityTracker? = null

    init {
        clipChildren = false
        addView(backdrop, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(cc, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(nc, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        cc.visibility = GONE
        nc.visibility = GONE
        isFocusable = true
        isFocusableInTouchMode = true
    }

    fun show(m: PanelHost.Mode) {
        backdrop.clear()
        backdrop.sheetBottom = if (m == PanelHost.Mode.NC) 0f else -1f
        cc.visibility = if (m == PanelHost.Mode.CC) VISIBLE else GONE
        nc.visibility = if (m == PanelHost.Mode.NC) VISIBLE else GONE
        when (m) {
            PanelHost.Mode.CC -> { cc.refresh(); cc.applyProgress(0f) }
            PanelHost.Mode.NC -> { nc.refresh(); nc.applyProgress(0f, 0f) }
            PanelHost.Mode.NONE -> Unit
        }
    }

    fun applyProgress(m: PanelHost.Mode, p: Float) {
        when (m) {
            PanelHost.Mode.CC -> { backdrop.progress = p; cc.applyProgress(p) }
            PanelHost.Mode.NC -> {
                val bottom = clamp01(p) * height
                backdrop.sheetBottom = bottom
                nc.applyProgress(p, bottom)
            }
            PanelHost.Mode.NONE -> Unit
        }
    }

    fun reset() {
        cc.reset()
        nc.reset()
        backdrop.clear()
        cc.visibility = GONE
        nc.visibility = GONE
    }

    fun applyPrefs() = cc.applyPrefs()

    private fun canDrag(dy: Float): Boolean = when (host.mode) {
        PanelHost.Mode.CC -> !cc.isExpanded
        PanelHost.Mode.NC -> dy < 0 && !nc.canScrollDown() && !nc.isBusy()
        PanelHost.Mode.NONE -> false
    }

    override fun dispatchTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN) {
            vt?.recycle()
            vt = VelocityTracker.obtain()
        }
        vt?.addMovement(e)
        return super.dispatchTouchEvent(e)
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        if (host.mode == PanelHost.Mode.NONE) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x; downY = e.y
                decided = false
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (decided) return dragging
                val dx = e.x - downX
                val dy = e.y - downY
                if (abs(dy) > slop && abs(dy) > abs(dx) * 1.1f) {
                    decided = true
                    if (canDrag(dy)) { startDrag(e); return true }
                } else if (abs(dx) > slop) {
                    decided = true
                }
            }
        }
        return false
    }

    private fun startDrag(e: MotionEvent) {
        dragging = true
        originY = e.y
        host.dragBegin()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (host.mode == PanelHost.Mode.NONE) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x; downY = e.y
                decided = false
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && !decided) {
                    val dx = e.x - downX
                    val dy = e.y - downY
                    if (abs(dy) > slop && abs(dy) > abs(dx)) {
                        decided = true
                        if (canDrag(dy)) startDrag(e)
                    } else if (abs(dx) > slop) {
                        decided = true
                    }
                }
                if (dragging) host.dragMove(e.y - originY)
            }
            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    vt?.computeCurrentVelocity(1000)
                    host.dragEnd(vt?.yVelocity ?: 0f)
                } else if (!decided && host.mode == PanelHost.Mode.CC) {
                    cc.onEmptyTap()
                }
                dragging = false
            }
            MotionEvent.ACTION_CANCEL -> {
                if (dragging) host.dragEnd(0f)
                dragging = false
            }
        }
        return true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK && host.mode != PanelHost.Mode.NONE) {
            if (event.action == KeyEvent.ACTION_UP) {
                if (cc.isExpanded) cc.collapse() else host.close(true)
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) host.onFocusLost()
    }
}
