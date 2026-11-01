package com.tihon.shtorka.service

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.tihon.shtorka.core.Bus
import com.tihon.shtorka.core.Hub
import com.tihon.shtorka.system.MediaRepo
import com.tihon.shtorka.system.NotifRepo

/** Источник уведомлений для Центра уведомлений и ключ к сессиям музыки. */
class NotifListener : NotificationListenerService() {

    override fun onListenerConnected() {
        Hub.listener = this
        val all = runCatching { activeNotifications }.getOrNull() ?: emptyArray()
        NotifRepo.setAll(this, all)
        MediaRepo.start(applicationContext)
        Bus.fire()
    }

    override fun onListenerDisconnected() {
        if (Hub.listener === this) Hub.listener = null
        runCatching { requestRebind(ComponentName(this, NotifListener::class.java)) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = NotifRepo.posted(this, sbn)

    override fun onNotificationRemoved(sbn: StatusBarNotification) = NotifRepo.removed(sbn.key)

    override fun onInterruptionFilterChanged(interruptionFilter: Int) = Bus.fire()

    fun dismiss(keys: Collection<String>) {
        keys.forEach { runCatching { cancelNotification(it) } }
    }

    fun dismissAll() {
        runCatching { cancelAllNotifications() }
    }
}
