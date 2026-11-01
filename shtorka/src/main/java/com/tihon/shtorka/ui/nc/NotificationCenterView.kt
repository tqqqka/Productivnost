package com.tihon.shtorka.ui.nc

import android.animation.LayoutTransition
import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.tihon.shtorka.R
import com.tihon.shtorka.core.Bus
import com.tihon.shtorka.core.Hub
import com.tihon.shtorka.system.MediaRepo
import com.tihon.shtorka.system.NotifRepo
import com.tihon.shtorka.system.Torch
import com.tihon.shtorka.ui.Fonts
import com.tihon.shtorka.ui.Glass
import com.tihon.shtorka.ui.GlyphButton
import com.tihon.shtorka.ui.MediaPanel
import com.tihon.shtorka.ui.Palette
import com.tihon.shtorka.ui.PanelHost
import com.tihon.shtorka.ui.StatusRowView
import com.tihon.shtorka.ui.cc.Actions
import com.tihon.shtorka.ui.cc.CircleToggle
import com.tihon.shtorka.ui.clamp01
import com.tihon.shtorka.ui.dp
import com.tihon.shtorka.ui.dpi
import com.tihon.shtorka.ui.text
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Центр уведомлений как экран блокировки iPhone: дата и большие часы,
 * «Исполняется», уведомления стопками по приложениям, внизу фонарик и камера.
 * Весь лист опускается за пальцем, как занавес.
 */
@SuppressLint("ViewConstructor")
class NotificationCenterView(ctx: Context, private val host: PanelHost) : FrameLayout(ctx) {
    private val status = StatusRowView(ctx)
    private val sheet = FrameLayout(ctx)
    private val scroll = ScrollView(ctx)
    private val content = LinearLayout(ctx)
    private val date: TextView = ctx.text(19f, Fonts.semibold, 0xE6FFFFFF.toInt(), gravity = Gravity.CENTER_HORIZONTAL)
    private val clock: TextView = ctx.text(96f, Fonts.bold, 0xF2FFFFFF.toInt(), gravity = Gravity.CENTER_HORIZONTAL, tracking = -0.035f)
    private val media = MediaCard(ctx)
    private val header = LinearLayout(ctx)
    private val list = LinearLayout(ctx)
    private val empty: TextView = ctx.text(15f, Fonts.medium, Palette.TEXT_SECONDARY, gravity = Gravity.CENTER_HORIZONTAL)
    private val torch = CircleToggle(ctx, R.drawable.g_flashlight, Palette.WHITE, Palette.BLACK, 0x40000000, 0.42f, rim = true)
    private val camera = CircleToggle(ctx, R.drawable.g_camera, Palette.WHITE, Palette.BLACK, 0x40000000, 0.42f, rim = true)
    private val homeBar = HomeBar(ctx)

    private val groups = LinkedHashMap<String, NotifGroup>()
    private val expanded = HashSet<String>()
    private var sheetOpen = false

    private val clockTick = object : Runnable {
        override fun run() {
            updateClock()
            val now = System.currentTimeMillis()
            postDelayed(this, 60_000 - now % 60_000 + 50)
        }
    }

    private val busListener: () -> Unit = {
        if (visibility == VISIBLE) {
            status.refresh()
            torch.setFromSystem(Torch.on)
        }
    }
    private val notifListener: () -> Unit = { if (visibility == VISIBLE) rebuild(animate = sheetOpen) }
    private val mediaListener: () -> Unit = { if (visibility == VISIBLE) bindMedia() }

    init {
        clipChildren = false
        addView(sheet, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(status, LayoutParams(LayoutParams.MATCH_PARENT, host.statusBarHeight))

        scroll.isFillViewport = true
        scroll.isVerticalScrollBarEnabled = false
        scroll.clipToPadding = false
        scroll.setPadding(0, host.statusBarHeight, 0, host.navBarHeight + dpi(118))
        sheet.addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        content.orientation = LinearLayout.VERTICAL
        content.clipChildren = false
        val side = dpi(10)
        content.setPadding(side, 0, side, 0)
        scroll.addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        content.addView(date, lp().apply { topMargin = dpi(30) })
        content.addView(clock, lp().apply { topMargin = dpi(2) })
        content.addView(View(ctx), LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dpi(28), 1f))
        content.addView(media, lp().apply { bottomMargin = dpi(14) })

        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        val title = ctx.text(20f, Fonts.bold).apply { text = "Центр уведомлений" }
        header.addView(title, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        val clearAll = GlyphButton(ctx, R.drawable.g_close, 0.5f, Palette.WHITE).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(0x33FFFFFF)
            }
            onTap = { dismiss(NotifRepo.items) }
        }
        header.addView(clearAll, LinearLayout.LayoutParams(dpi(30), dpi(30)))
        header.setPadding(dpi(6), 0, dpi(2), dpi(10))
        content.addView(header, lp())

        list.orientation = LinearLayout.VERTICAL
        list.clipChildren = false
        content.addView(list, lp())
        empty.text = "Нет старых уведомлений"
        content.addView(empty, lp().apply { topMargin = dpi(6); bottomMargin = dpi(6) })

        val b = dpi(52)
        val bottom = host.navBarHeight + dpi(30)
        sheet.addView(torch, LayoutParams(b, b, Gravity.BOTTOM or Gravity.START).apply { setMargins(dpi(46), 0, 0, bottom) })
        sheet.addView(camera, LayoutParams(b, b, Gravity.BOTTOM or Gravity.END).apply { setMargins(0, 0, dpi(46), bottom) })
        sheet.addView(homeBar, LayoutParams(dpi(140), dpi(5), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dpi(9)
        })

        torch.onTap = { torch.userToggle(); Torch.toggle() }
        camera.onTap = { Actions.camera() }
        media.panel.onOpenApp = { Hub.service?.let { if (MediaRepo.open(it)) host.close(true) } }
        media.panel.onOutput = { Actions.bluetoothSettings() }
    }

    private fun lp() = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Bus.add(busListener)
        NotifRepo.add(notifListener)
        MediaRepo.add(mediaListener)
    }

    override fun onDetachedFromWindow() {
        Bus.remove(busListener)
        NotifRepo.remove(notifListener)
        MediaRepo.remove(mediaListener)
        removeCallbacks(clockTick)
        super.onDetachedFromWindow()
    }

    fun refresh() {
        status.refresh()
        status.notchLeft = host.notch.left.toFloat()
        status.sidePadding = dp(30)
        torch.setFromSystem(Torch.on)
        removeCallbacks(clockTick)
        clockTick.run()
        bindMedia()
        rebuild(animate = false)
        scroll.scrollTo(0, 0)
    }

    private fun updateClock() {
        val now = Date()
        clock.text = SimpleDateFormat("H:mm", Locale.getDefault()).format(now)
        val d = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now)
        date.text = d.replaceFirstChar { it.titlecase(Locale.getDefault()) }
    }

    private fun bindMedia() {
        val n = MediaRepo.now
        media.visibility = if (n != null) VISIBLE else GONE
        media.panel.bind(n)
    }

    fun isExpanded(pkg: String) = pkg in expanded

    fun setExpanded(pkg: String, on: Boolean) {
        if (on) expanded += pkg else expanded -= pkg
        rebuild(animate = true)
    }

    private fun rebuild(animate: Boolean) {
        val trans = if (animate) LayoutTransition().apply {
            enableTransitionType(LayoutTransition.CHANGING)
            setDuration(260)
        } else null
        list.layoutTransition = trans
        content.layoutTransition = trans

        val byPkg = LinkedHashMap<String, MutableList<NotifRepo.Item>>()
        NotifRepo.items.forEach { byPkg.getOrPut(it.pkg) { ArrayList() } += it }
        expanded.retainAll(byPkg.keys)

        groups.keys.filter { it !in byPkg }.forEach { pkg -> groups.remove(pkg)?.let { list.removeView(it) } }
        var i = 0
        byPkg.forEach { (pkg, items) ->
            val g = groups.getOrPut(pkg) { NotifGroup(context, pkg, this) }
            if (g.layoutTransition == null && animate) g.layoutTransition = LayoutTransition().apply {
                enableTransitionType(LayoutTransition.CHANGING)
                setDuration(240)
            }
            if (!animate) g.layoutTransition = null
            g.bind(items)
            if (list.indexOfChild(g) != i) {
                if (g.parent != null) list.removeView(g)
                list.addView(g, i, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dpi(10)
                })
            }
            i++
        }
        val any = byPkg.isNotEmpty()
        header.visibility = if (any) VISIBLE else GONE
        empty.visibility = if (any) GONE else VISIBLE
    }

    fun open(item: NotifRepo.Item) {
        val svc = Hub.service ?: return
        val pi = item.intent
        if (pi != null) {
            runCatching {
                if (Build.VERSION.SDK_INT >= 34) {
                    val o = ActivityOptions.makeBasic()
                        .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    pi.send(svc, 0, null, null, null, null, o.toBundle())
                } else {
                    pi.send()
                }
            }
            if (item.autoCancel) Hub.listener?.dismiss(listOf(item.key))
        } else {
            com.tihon.shtorka.system.Launch.appLaunch(svc, item.pkg)?.let { com.tihon.shtorka.system.Launch.start(svc, it) }
        }
        host.close(true)
    }

    fun dismiss(items: List<NotifRepo.Item>) {
        val keys = items.filter { it.clearable }.map { it.key }
        Hub.listener?.dismiss(keys)
    }

    fun canScrollDown() = scroll.canScrollVertically(1)

    fun isBusy() = groups.values.any { it.swiping }

    /** p — прогресс, bottom — нижний край листа в пикселях. */
    fun applyProgress(p: Float, bottom: Float) {
        sheetOpen = p >= 0.99f
        sheet.translationY = bottom - height
        val sb = host.statusBarHeight.toFloat()
        status.alpha = clamp01((bottom - sb * 0.6f) / sb)
    }

    fun reset() {
        expanded.clear()
        removeCallbacks(clockTick)
        scroll.scrollTo(0, 0)
        sheetOpen = false
    }

    /** Карточка «Исполняется». */
    class MediaCard(ctx: Context) : FrameLayout(ctx) {
        val panel = MediaPanel(ctx, withVolume = false)
        private val glass = Glass(ctx)
        private val r = RectF()

        init {
            setWillNotDraw(false)
            addView(panel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }

        override fun onDraw(c: Canvas) {
            r.set(0f, 0f, width.toFloat(), height.toFloat())
            glass.draw(c, r, dp(24), CARD_FILL, 0.8f)
        }
    }

    /** Полоска «домой» внизу листа. */
    class HomeBar(ctx: Context) : View(ctx) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE6FFFFFF.toInt() }
        override fun onDraw(c: Canvas) {
            c.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), height / 2f, height / 2f, p)
        }
    }
}
