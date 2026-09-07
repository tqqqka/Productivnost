package com.tihon.productivnost.util

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppInfo(
    val pkg: String,
    val label: String,
    val system: Boolean,
)

object Apps {

    @Volatile
    private var cache: List<AppInfo>? = null

    suspend fun launchable(context: Context, force: Boolean = false): List<AppInfo> = withContext(Dispatchers.IO) {
        cache?.takeIf { !force }?.let { return@withContext it }
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = pm.queryIntentActivities(intent, 0)
            .asSequence()
            .mapNotNull { it.activityInfo?.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .map {
                AppInfo(
                    pkg = it.packageName,
                    label = runCatching { pm.getApplicationLabel(it).toString() }.getOrDefault(it.packageName),
                    system = (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
        cache = list
        list
    }

    fun icon(context: Context, pkg: String): Drawable? = runCatching {
        context.packageManager.getApplicationIcon(pkg)
    }.getOrNull()

    fun label(context: Context, pkg: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    /** Лаунчеры, звонилка и системный UI никогда не считаются «запрещёнными». */
    fun neutralPackages(context: Context): Set<String> {
        val pm = context.packageManager
        val out = HashSet<String>()
        out += context.packageName
        out += "com.android.systemui"
        out += "android"
        runCatching {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            pm.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)
                .forEach { it.activityInfo?.packageName?.let(out::add) }
        }
        runCatching {
            val dial = Intent(Intent.ACTION_DIAL)
            pm.queryIntentActivities(dial, PackageManager.MATCH_DEFAULT_ONLY)
                .forEach { it.activityInfo?.packageName?.let(out::add) }
        }
        runCatching {
            pm.queryIntentActivities(Intent("android.settings.SETTINGS"), 0)
                .forEach { it.activityInfo?.packageName?.let(out::add) }
        }
        return out
    }

    /**
     * Текущее приложение на переднем плане по данным «Доступа к статистике».
     *
     * События приходят только в момент переключения, поэтому при долгом сидении
     * в одном приложении окно оказывается пустым — тогда падаем на снимок
     * статистики и берём приложение с самым свежим временем использования.
     */
    fun foregroundPackage(context: Context, windowMs: Long = 60_000L): String? {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val now = System.currentTimeMillis()
        return runCatching {
            val events = usm.queryEvents(now - windowMs, now + 1_000)
            val e = android.app.usage.UsageEvents.Event()
            var pkg: String? = null
            var ts = 0L
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                val resumed = e.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND ||
                    e.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED
                if (resumed && e.timeStamp >= ts) {
                    ts = e.timeStamp
                    pkg = e.packageName
                }
            }
            pkg
        }.getOrNull() ?: lastUsedPackage(context)
    }

    private fun lastUsedPackage(context: Context): String? {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val now = System.currentTimeMillis()
        return runCatching {
            usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - 4 * 60 * 60_000L, now)
                ?.maxByOrNull { it.lastTimeUsed }
                ?.takeIf { now - it.lastTimeUsed < 5 * 60_000L }
                ?.packageName
        }.getOrNull()
    }
}
