package com.tihon.shtorka.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tihon.shtorka.MainActivity
import com.tihon.shtorka.R

/**
 * Включённую службу специальных возможностей система запускает сама при
 * каждой загрузке — так островок работает сразу после включения телефона.
 * Ресивер страхует: если службу кто-то выключил, он включит её обратно
 * (при выданном через adb разрешении) или хотя бы напомнит уведомлением.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (A11y.enabled(ctx)) return
        if (A11y.tryEnable(ctx)) return
        remind(ctx)
    }

    private fun remind(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("state", "Состояние островка", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val pi = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(ctx, "state")
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Островок выключен")
            .setContentText("Нажми, чтобы снова включить службу")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(1, n) }
    }
}
