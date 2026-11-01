package com.tihon.shtorka.service

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.tihon.shtorka.core.Hub

/** Проверки доступов и самовключение службы после загрузки. */
object A11y {
    fun component(ctx: Context) = ComponentName(ctx, IslandService::class.java)

    fun enabled(ctx: Context): Boolean {
        val s = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = component(ctx)
        return s.split(':').any { ComponentName.unflattenFromString(it.trim()) == me }
    }

    val running get() = Hub.service != null

    fun canSelfEnable(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /**
     * Включает службу сама, если через adb выдано WRITE_SECURE_SETTINGS.
     * Нужно на случай, когда система или «чистильщик» выключили службу.
     */
    fun tryEnable(ctx: Context): Boolean {
        if (!canSelfEnable(ctx)) return false
        return runCatching {
            val cr = ctx.contentResolver
            val me = component(ctx).flattenToString()
            val cur = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            val list = cur.split(':').map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
            if (list.none { ComponentName.unflattenFromString(it) == component(ctx) }) list += me
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, list.joinToString(":"))
            Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        }.getOrDefault(false)
    }

    fun notificationAccess(ctx: Context) =
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
}
