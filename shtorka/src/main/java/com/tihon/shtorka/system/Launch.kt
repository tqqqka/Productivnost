package com.tihon.shtorka.system

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings

/** Интенты для кнопок, которые открывают приложения и панели настроек. */
object Launch {

    fun start(ctx: Context, intent: Intent): Boolean = try {
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: Exception) {
        false
    }

    fun first(ctx: Context, vararg intents: Intent?): Boolean = intents.filterNotNull().any { start(ctx, it) }

    fun wifiPanel(): Intent =
        if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_WIFI) else Intent(Settings.ACTION_WIFI_SETTINGS)

    fun dataPanel(): Intent =
        if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
        else Intent(Settings.ACTION_DATA_ROAMING_SETTINGS)

    fun bluetoothSettings() = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)

    fun writeSettings(ctx: Context) =
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}"))

    fun appLaunch(ctx: Context, pkg: String): Intent? = ctx.packageManager.getLaunchIntentForPackage(pkg)

    fun calculator(ctx: Context) = first(
        ctx,
        Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR),
        appLaunch(ctx, "com.sec.android.app.popupcalculator"),
        appLaunch(ctx, "com.google.android.calculator"),
    )

    fun camera(ctx: Context): Boolean {
        val locked = ctx.getSystemService(KeyguardManager::class.java).isKeyguardLocked
        return first(
            ctx,
            Intent(if (locked) MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE else MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            appLaunch(ctx, "com.sec.android.app.camera"),
        )
    }

    fun timer(ctx: Context) = first(
        ctx,
        Intent(AlarmClock.ACTION_SHOW_TIMERS),
        Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_SKIP_UI, false),
        appLaunch(ctx, "com.sec.android.app.clockpackage"),
    )

    fun alarms(ctx: Context) = first(
        ctx,
        Intent(AlarmClock.ACTION_SHOW_ALARMS),
        appLaunch(ctx, "com.sec.android.app.clockpackage"),
    )
}
