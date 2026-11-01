package com.tihon.shtorka

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.os.Looper
import android.os.SystemClock
import android.view.View
import com.tihon.shtorka.service.IslandService
import com.tihon.shtorka.system.Act
import com.tihon.shtorka.system.Kind
import com.tihon.shtorka.system.Live
import com.tihon.shtorka.ui.Palette
import com.tihon.shtorka.ui.island.IslandController
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
 * Рисует состояния островка в PNG (build/shots) — посмотреть без телефона.
 * Экран как у Galaxy A26: 1080×2340, 420 dpi, капля по центру.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi", application = ShtorkaApp::class)
class IslandShotsTest {
    private val out = File(System.getProperty("shots.dir") ?: "build/shots").apply { mkdirs() }
    private val w = 1080
    private val h = 760

    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private fun wallpaper(): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), intArrayOf(0xFF1E3C72.toInt(), 0xFF8E2DE2.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = null
        p.color = Color.WHITE
        p.textSize = 40f
        c.drawText("19:40", 50f, 64f, p)
        c.drawText("85%", 940f, 64f, p)
        // капля
        p.color = Color.BLACK
        c.drawCircle(w / 2f, 40f, 26f, p)
        c.drawRect(w / 2f - 26f, 0f, w / 2f + 26f, 40f, p)
        val cols = intArrayOf(0xFF34C759.toInt(), 0xFF0A84FF.toInt(), 0xFFFF9F0A.toInt(), 0xFFFF375F.toInt())
        for (i in 0 until 4) {
            p.color = cols[i]
            c.drawRoundRect(RectF(80f + i * 250f, 520f, 250f + i * 250f, 690f), 44f, 44f, p)
        }
        return b
    }

    private fun art(color: Int): Bitmap {
        val b = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 200f, 200f, color, 0xFF1C1C1E.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, 200f, 200f, p)
        return b
    }

    @Test
    fun states() {
        val wall = wallpaper()
        val svc = Robolectric.setupService(IslandService::class.java)
        val ctl = IslandController(svc)
        val v = ctl.view
        v.notch = Rect(w / 2 - 34, 0, w / 2 + 34, 80)
        v.screenW = w
        val now = SystemClock.elapsedRealtime()
        val music = Live(Kind.MUSIC, "music", title = "Песня", text = "Исполнитель", art = art(0xFFFF375F.toInt()), accent = 0xFFFF6B8A.toInt(), playing = true)
        val timer = Live(Kind.TIMER, "t", title = "Таймер", text = "Паста", accent = Palette.ORANGE, chronoBase = now + 4 * 60_000 + 59_000, countDown = true,
            actions = listOf(Act("Пауза", null), Act("Отмена", null)))
        val call = Live(Kind.CALL_INCOMING, "c", title = "Мама", accent = Palette.GREEN,
            actions = listOf(Act("Отклонить", null, Act.Role.DECLINE), Act("Ответить", null, Act.Role.ACCEPT)))
        val ongoing = Live(Kind.CALL, "c2", title = "Мама", text = "мобильный", accent = Palette.GREEN, chronoBase = now - 42_000,
            actions = listOf(Act("Завершить", null, Act.Role.DECLINE)))
        val rec = Live(Kind.RECORDING, "r", title = "Запись экрана", accent = Palette.RED, chronoBase = now - 12_000)
        val nav = Live(Kind.NAVIGATION, "n", title = "300 м", text = "Поверните направо на ул. Ленина", value = "300 м")
        val dl = Live(Kind.PROGRESS, "d", title = "Загрузка", text = "video.mp4", progress = 0.62f, accent = Palette.BLUE, value = "62%")
        val banner = Live(Kind.BANNER, "b", title = "Мама", text = "Ты где? Ужин стынет, приходи скорее домой", value = "Сообщения")
        val cases = listOf(
            "music" to Triple(music, null as Live?, false),
            "music_timer" to Triple(timer, music, false),
            "timer_expanded" to Triple(timer, null, true),
            "call_incoming" to Triple(call, null, true),
            "call_ongoing" to Triple(ongoing, null, false),
            "call_ongoing_expanded" to Triple(ongoing, null, true),
            "recording" to Triple(rec, null, false),
            "navigation" to Triple(nav, null, false),
            "download" to Triple(dl, music, false),
            "banner" to Triple(banner, null, true),
            "charging" to Triple(Live(Kind.CHARGING, "ch", level = 85, accent = Palette.GREEN), null, false),
            "low_battery" to Triple(Live(Kind.LOW_BATTERY, "lb", level = 10, accent = Palette.RED), null, false),
            "unlock" to Triple(Live(Kind.UNLOCK, "u"), null, false),
            "headphones" to Triple(Live(Kind.HEADPHONES, "hp", title = "Galaxy Buds", level = 80), null, false),
            "ringer" to Triple(Live(Kind.RINGER, "rg"), null, false),
        )
        for ((name, c) in cases) {
            v.render(null, null, false, animate = false)
            v.render(c.first, c.second, c.third, animate = false)
            v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
            v.layout(0, 0, w, h)
            idle(1200)
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val cv = Canvas(b)
            cv.drawBitmap(wall, 0f, 0f, null)
            v.draw(cv)
            FileOutputStream(File(out, "island_$name.png")).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    /** Кадры анимации: капля → музыка → раскрытый таймер → обратно. */
    @Test
    fun animation() {
        val wall = wallpaper()
        val svc = Robolectric.setupService(IslandService::class.java)
        val ctl = IslandController(svc)
        val v = ctl.view
        v.notch = Rect(w / 2 - 34, 0, w / 2 + 34, 80)
        v.screenW = w
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, w, h)
        val music = Live(Kind.MUSIC, "music", title = "Песня", art = art(0xFFFF375F.toInt()), accent = 0xFFFF6B8A.toInt(), playing = true)
        val timer = Live(Kind.TIMER, "t", title = "Таймер", text = "Паста", accent = Palette.ORANGE,
            chronoBase = SystemClock.elapsedRealtime() + 300_000, countDown = true, actions = listOf(Act("Пауза", null), Act("Отмена", null)))
        val strip = Bitmap.createBitmap(w, 6 * 3 * 130, Bitmap.Config.ARGB_8888)
        val sc = Canvas(strip)
        var row = 0
        fun frames(step: Long, n: Int) {
            repeat(n) {
                idle(step)
                v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
                v.layout(0, 0, w, h)
                val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val cv = Canvas(b)
                cv.drawBitmap(wall, 0f, 0f, null)
                v.draw(cv)
                sc.drawBitmap(b, Rect(0, 0, w, 130), Rect(0, row * 130, w, row * 130 + 130), null)
                row++
            }
        }
        v.render(music, null, false, animate = true); frames(40, 6)
        v.render(timer, music, false, animate = true); frames(40, 6)
        v.render(music, null, false, animate = true); frames(40, 6)
        FileOutputStream(File(out, "anim_strip.png")).use { strip.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun settings() {
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val v = a.window.decorView
        v.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, 1080, 2340)
        idle(100)
        val b = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(b))
        FileOutputStream(File(out, "settings.png")).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
