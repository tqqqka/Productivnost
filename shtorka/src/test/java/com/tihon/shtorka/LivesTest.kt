package com.tihon.shtorka

import android.app.Notification
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import com.tihon.shtorka.system.Kind
import com.tihon.shtorka.system.LiveRepo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Разбор уведомлений часов, навигации и загрузок в живые активности. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ShtorkaApp::class)
class LivesTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val clock = "com.sec.android.app.clockpackage"

    private fun sbn(pkg: String, n: Notification, id: Int = 1) =
        @Suppress("DEPRECATION") StatusBarNotification(pkg, pkg, id, null, 10000, 0, 0, n, Process.myUserHandle(), System.currentTimeMillis())

    private fun builder() = Notification.Builder(ctx, "x").setSmallIcon(android.R.drawable.ic_dialog_info)

    @Test
    fun timerWithCountdownChronometer() {
        val end = System.currentTimeMillis() + 299_000
        val n = builder().setContentTitle("Таймер").setOngoing(true).setWhen(end)
            .setUsesChronometer(true).setChronometerCountDown(true).build()
        val l = LiveRepo.classify(ctx, sbn(clock, n))!!
        assertEquals(Kind.TIMER, l.kind)
        assertTrue(l.countDown)
        val left = l.chronoBase - SystemClock.elapsedRealtime()
        assertTrue("осталось $left", left in 290_000..300_000)
    }

    @Test
    fun timerWithFutureWhenOnly() {
        // Флага секундомера нет, но «время уведомления» — момент окончания.
        val n = builder().setContentTitle("Таймер").setOngoing(true).setWhen(System.currentTimeMillis() + 60_000).build()
        val l = LiveRepo.classify(ctx, sbn(clock, n))!!
        assertEquals(Kind.TIMER, l.kind)
        assertTrue(l.chronoBase > SystemClock.elapsedRealtime())
        assertTrue(l.countDown)
    }

    @Test
    fun timerWithTimeInText() {
        val n = builder().setContentTitle("Таймер").setContentText("Осталось 04:59").setOngoing(true).build()
        val l = LiveRepo.classify(ctx, sbn(clock, n))!!
        assertEquals(Kind.TIMER, l.kind)
        assertEquals("04:59", l.value)
    }

    @Test
    fun timerFromVendorExtras() {
        // Время окончания в своём ключе, как у Samsung.
        val n = builder().setContentTitle("Таймер").setOngoing(true).build()
        n.extras.putLong("samsung.timerEndTime", System.currentTimeMillis() + 120_000)
        val l = LiveRepo.classify(ctx, sbn(clock, n))!!
        assertEquals(Kind.TIMER, l.kind)
        assertTrue(l.chronoBase - SystemClock.elapsedRealtime() in 110_000..121_000)
    }

    @Test
    fun groupSummaryIsSkipped() {
        val n = builder().setContentTitle("Таймер").setOngoing(true).setGroup("g").setGroupSummary(true).build()
        assertNull(LiveRepo.classify(ctx, sbn(clock, n)))
    }

    @Test
    fun stopwatchCountsUp() {
        val n = builder().setContentTitle("Секундомер").setOngoing(true).setWhen(System.currentTimeMillis() - 30_000)
            .setUsesChronometer(true).build()
        val l = LiveRepo.classify(ctx, sbn(clock, n))!!
        assertEquals(Kind.STOPWATCH, l.kind)
        assertTrue(!l.countDown)
    }

    @Test
    fun navigationDistanceAndEta() {
        val n = builder().setContentTitle("300 м").setContentText("Поверните направо на ул. Ленина · 12 мин").setOngoing(true)
            .setCategory(Notification.CATEGORY_NAVIGATION).build()
        val l = LiveRepo.classify(ctx, sbn("ru.yandex.yandexnavi", n))!!
        assertEquals(Kind.NAVIGATION, l.kind)
        assertEquals("300 м", l.value)
        assertEquals("12 мин", l.eta)
    }

    @Test
    fun navigationKilometresWithoutSpace() {
        val n = builder().setContentTitle("1,2км").setContentText("Прямо").setOngoing(true).build()
        val l = LiveRepo.classify(ctx, sbn("com.google.android.apps.maps", n))!!
        assertEquals("1,2 км", l.value)
    }

    @Test
    fun downloadFinishedByNewNotification() {
        var done = false
        LiveRepo.onFinished = { done = true }
        val p = builder().setContentTitle("video.mp4").setOngoing(true).setProgress(100, 40, false).build()
        LiveRepo.posted(ctx, sbn("com.android.chrome", p, 5))
        LiveRepo.removed(sbn("com.android.chrome", p, 5).key)
        val fin = builder().setContentTitle("Загрузка завершена").setContentText("video.mp4").build()
        LiveRepo.posted(ctx, sbn("com.android.chrome", fin, 6))
        assertTrue(done)
        LiveRepo.onFinished = null
    }

    @Suppress("unused")
    private fun unused() = Bundle()
}
