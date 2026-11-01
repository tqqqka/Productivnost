package com.tihon.shtorka.system

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/** Запуск приложений и PendingIntent'ов из уведомлений. */
object Launch {

    fun start(ctx: Context, intent: Intent): Boolean = try {
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: Exception) {
        false
    }

    fun appLaunch(ctx: Context, pkg: String): Intent? = ctx.packageManager.getLaunchIntentForPackage(pkg)

    fun bluetoothSettings() = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)

    /** Отправить PendingIntent; окно островка видно — Android разрешает старт экрана. */
    fun send(ctx: Context, pi: PendingIntent?): Boolean {
        if (pi == null) return false
        return runCatching {
            if (Build.VERSION.SDK_INT >= 34) {
                val o = ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                pi.send(ctx, 0, null, null, null, null, o.toBundle())
            } else {
                pi.send()
            }
        }.isSuccess
    }

    /** Открыть приложение пакета (по нажатию на островок). */
    fun openApp(ctx: Context, pkg: String, pi: PendingIntent?): Boolean =
        send(ctx, pi) || (appLaunch(ctx, pkg)?.let { start(ctx, it) } ?: false)
}
