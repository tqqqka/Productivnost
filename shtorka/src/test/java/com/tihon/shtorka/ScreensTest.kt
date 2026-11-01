package com.tihon.shtorka

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Looper
import android.view.View
import com.tihon.shtorka.service.ShadeService
import com.tihon.shtorka.system.Blur
import com.tihon.shtorka.system.NotifRepo
import com.tihon.shtorka.ui.PanelHost
import com.tihon.shtorka.ui.island.IslandController
import com.tihon.shtorka.ui.island.IslandView
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.time.Duration

/**
 * Рисует экраны шторки в PNG (build/shots) — посмотреть вёрстку без телефона.
 * Размер как у Galaxy A26: 1080×2340, 420 dpi.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi", application = ShtorkaApp::class)
class ScreensTest {
    private val out = File(System.getProperty("shots.dir") ?: "build/shots").apply { mkdirs() }
    private val w = 1080
    private val h = 2340

    private fun idle(ms: Long = 1500) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    /** Что-то похожее на рабочий стол: яркий градиент и «иконки». */
    private fun wallpaper(): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(0xFF1E3C72.toInt(), 0xFF8E2DE2.toInt(), 0xFFF27121.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = null
        val colors = intArrayOf(0xFF34C759.toInt(), 0xFF0A84FF.toInt(), 0xFFFF9F0A.toInt(), 0xFFFF375F.toInt(), Color.WHITE, 0xFF5E5CE6.toInt())
        for (row in 0 until 5) for (col in 0 until 4) {
            p.color = colors[(row * 4 + col) % colors.size]
            val x = 80f + col * 250f
            val y = 360f + row * 300f
            c.drawRoundRect(RectF(x, y, x + 170f, y + 170f), 44f, 44f, p)
        }
        // строка состояния Samsung
        p.color = Color.WHITE
        p.textSize = 40f
        c.drawText("18:42", 50f, 70f, p)
        c.drawCircle(w / 2f, 40f, 24f, Paint().apply { color = Color.BLACK })
        return b
    }

    private fun blurred(src: Bitmap): Bitmap {
        val small = Bitmap.createScaledBitmap(src, 160, 160 * h / w, true).copy(Bitmap.Config.ARGB_8888, true)
        Blur.apply(small, 7)
        return small
    }

    private fun layout(v: View) {
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, w, h)
    }

    private fun shot(v: View, name: String, bg: Bitmap?) {
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, w, h)
        idle(50)
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        if (bg != null) c.drawBitmap(bg, 0f, 0f, null)
        v.draw(c)
        FileOutputStream(File(out, "$name.png")).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun host(): PanelHost {
        val svc = Robolectric.setupService(ShadeService::class.java)
        return PanelHost(svc)
    }

    @Test
    fun controlCenter() {
        val wall = wallpaper()
        val host = host()
        val root = host.root
        root.show(PanelHost.Mode.CC)
        root.backdrop.setBlurred(blurred(wall))
        root.applyProgress(PanelHost.Mode.CC, 1f)
        idle()
        shot(root, "cc_open", wall)

        root.applyProgress(PanelHost.Mode.CC, 0.4f)
        shot(root, "cc_opening_40", wall)
        root.applyProgress(PanelHost.Mode.CC, 0.75f)
        shot(root, "cc_opening_75", wall)
        root.applyProgress(PanelHost.Mode.CC, 1.15f)
        shot(root, "cc_overpull", wall)

        root.applyProgress(PanelHost.Mode.CC, 1f)
        root.cc.connectivity.wifi.setActive(true, false)
        root.cc.connectivity.bluetooth.setActive(true, false)
        root.cc.connectivity.cellular.setActive(true, false)
        root.cc.torch.setActive(true, false)
        root.cc.rotation.setActive(true, false)
        root.cc.focus.set(true, false)
        shot(root, "cc_toggled", wall)

        root.cc.expandConnectivity()
        idle(3000)
        shot(root, "cc_expanded_connectivity", wall)
        root.cc.collapse(false)

        root.cc.expandSlider(root.cc.brightness)
        idle(3000)
        shot(root, "cc_expanded_brightness", wall)
        root.cc.collapse(false)

        root.cc.expandMedia()
        idle(3000)
        shot(root, "cc_expanded_media", wall)
    }

    @Test
    fun notificationCenter() {
        val wall = wallpaper()
        val now = System.currentTimeMillis()
        val pkg = "com.tihon.shtorka"
        fun item(k: String, p: String, app: String, t: String, x: String, ago: Long, msg: Boolean = false) =
            NotifRepo.Item(k, p, app, t, x, now - ago, null, null, true, true, msg)
        NotifRepo.replaceForTest(
            listOf(
                item("1", pkg, "Telegram", "Мама", "Ты где? Ужин стынет, приходи скорее домой", 60_000, true),
                item("2", pkg, "Telegram", "Друзья", "Вася: го в субботу на каток?", 5 * 60_000, true),
                item("3", pkg, "Telegram", "Работа", "Отчёт нужен к понедельнику", 50 * 60_000, true),
                item("4", "x.y", "YouTube", "Новое видео", "Канал, на который ты подписан, выложил ролик", 2 * 3600_000L),
            ),
        )
        val host = host()
        val root = host.root
        layout(root)
        root.show(PanelHost.Mode.NC)
        root.backdrop.setBlurred(blurred(wall))
        idle()
        layout(root)
        root.applyProgress(PanelHost.Mode.NC, 1f)
        shot(root, "nc_open", wall)
        root.applyProgress(PanelHost.Mode.NC, 0.55f)
        shot(root, "nc_pulling", wall)

        root.applyProgress(PanelHost.Mode.NC, 1f)
        root.nc.setExpanded(pkg, true)
        idle()
        shot(root, "nc_group_expanded", wall)

        NotifRepo.replaceForTest(emptyList())
        root.show(PanelHost.Mode.NC)
        root.backdrop.setBlurred(blurred(wall))
        idle()
        root.applyProgress(PanelHost.Mode.NC, 1f)
        shot(root, "nc_empty", wall)
    }

    @Test
    fun island() {
        val wall = wallpaper()
        val svc = Robolectric.setupService(ShadeService::class.java)
        val ctl = IslandController(svc)
        val f = IslandController::class.java.getDeclaredField("view").apply { isAccessible = true }
        val view = f.get(ctl) as IslandView
        view.notch = android.graphics.Rect(w / 2 - 40, 0, w / 2 + 40, 78)
        view.screenW = w
        for (k in listOf(IslandView.Kind.MEDIA, IslandView.Kind.CHARGING, IslandView.Kind.RINGER, IslandView.Kind.MEDIA_EXPANDED)) {
            view.setKind(k, animate = false)
            view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, w, 900)
            idle(600)
            val b = Bitmap.createBitmap(w, 900, Bitmap.Config.ARGB_8888)
            val c = Canvas(b)
            c.drawBitmap(wall, 0f, 0f, null)
            view.draw(c)
            FileOutputStream(File(out, "island_${k.name.lowercase()}.png")).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun View.children(): List<View> =
        if (this is android.view.ViewGroup) (0 until childCount).map { getChildAt(it) } else emptyList()

    @Test
    fun settings() {
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val v = a.window.decorView
        shot(v, "settings", null)
    }
}
