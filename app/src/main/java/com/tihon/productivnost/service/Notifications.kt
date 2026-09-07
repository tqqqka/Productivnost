package com.tihon.productivnost.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.tihon.productivnost.MainActivity
import com.tihon.productivnost.R

object Notifications {

    const val CHANNEL_ONGOING = "ongoing"
    const val CHANNEL_REMINDER = "reminder"
    const val ID_SERVICE = 1001
    const val ID_REMINDER = 1002

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ONGOING, "Фоновая работа", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Сессии и наблюдение за экранным временем"
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDER, "Напоминания", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Вода, разминка, сон и привычки"
                enableVibration(false)
                setSound(null, null)
            },
        )
    }

    fun contentIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun ongoing(context: Context, title: String, text: String, progress: Int = -1): Notification =
        NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_leaf)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent(context))
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .apply { if (progress in 0..100) setProgress(100, progress, false) }
            .build()

    fun reminder(context: Context, title: String, text: String): Notification =
        NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_stat_leaf)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent(context))
            .setAutoCancel(true)
            .setSilent(true)
            .build()

    fun notify(context: Context, id: Int, n: Notification) {
        runCatching {
            androidx.core.app.NotificationManagerCompat.from(context).notify(id, n)
        }
    }
}
