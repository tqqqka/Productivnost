package com.tihon.shtorka.service

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.tihon.shtorka.core.Hub
import com.tihon.shtorka.system.LiveRepo
import com.tihon.shtorka.system.MediaRepo

/**
 * Источник живых активностей (звонки, таймеры, запись, навигация, загрузки),
 * всплывающих уведомлений и ключ к сессиям музыки.
 */
class NotifListener : NotificationListenerService() {
    /** Чтобы обновление того же уведомления не всплывало заново. */
    private val seen = HashMap<String, Int>()

    override fun onListenerConnected() {
        Hub.listener = this
        val all = runCatching { activeNotifications }.getOrNull() ?: emptyArray()
        all.forEach { seen[it.key] = signature(it) }
        LiveRepo.setAll(this, all)
        MediaRepo.start(applicationContext)
    }

    override fun onListenerDisconnected() {
        if (Hub.listener === this) Hub.listener = null
        runCatching { requestRebind(ComponentName(this, NotifListener::class.java)) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        LiveRepo.posted(this, sbn)
        maybeBanner(sbn, rankingMap)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = onNotificationPosted(sbn, null)

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        seen.remove(sbn.key)
        LiveRepo.removed(sbn.key)
    }

    private fun signature(sbn: StatusBarNotification): Int {
        val ex = sbn.notification.extras
        return (ex?.getCharSequence(Notification.EXTRA_TITLE)?.toString() + "|" + ex?.getCharSequence(Notification.EXTRA_TEXT)).hashCode()
    }

    private fun maybeBanner(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        val sig = signature(sbn)
        val prev = seen.put(sbn.key, sig)
        val n = sbn.notification
        if (prev != null && (prev == sig || n.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)) return
        // Только то, что и так всплыло бы: важность «со звуком» и не заглушено «Не беспокоить».
        val r = Ranking()
        if (rankingMap != null && rankingMap.getRanking(sbn.key, r)) {
            if (r.importance < NotificationManager.IMPORTANCE_DEFAULT || !r.matchesInterruptionFilter()) return
        }
        val l = LiveRepo.banner(this, sbn) ?: return
        Hub.service?.island?.onBanner(l)
    }
}
