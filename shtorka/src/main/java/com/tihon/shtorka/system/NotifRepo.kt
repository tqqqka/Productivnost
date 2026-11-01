package com.tihon.shtorka.system

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.service.notification.StatusBarNotification
import android.util.LruCache

/** Активные уведомления в удобном для Центра уведомлений виде. */
object NotifRepo {

    class Item(
        val key: String,
        val pkg: String,
        val app: String,
        val title: String,
        val text: String,
        val time: Long,
        val largeIcon: Icon?,
        val intent: PendingIntent?,
        val clearable: Boolean,
        val autoCancel: Boolean,
        val isMessage: Boolean,
    )

    var items: List<Item> = emptyList()
        private set

    private val listeners = LinkedHashSet<() -> Unit>()
    fun add(l: () -> Unit) { listeners += l }
    fun remove(l: () -> Unit) { listeners -= l }
    private fun changed() = listeners.toList().forEach { it() }

    private val labels = LruCache<String, String>(64)
    private val icons = LruCache<String, Drawable>(48)

    fun appIcon(ctx: Context, pkg: String): Drawable? {
        icons.get(pkg)?.let { return it }
        val d = runCatching { ctx.packageManager.getApplicationIcon(pkg) }.getOrNull() ?: return null
        icons.put(pkg, d)
        return d
    }

    private fun appLabel(ctx: Context, pkg: String): String {
        labels.get(pkg)?.let { return it }
        val pm = ctx.packageManager
        val l = runCatching {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, PackageManager.GET_META_DATA)).toString()
        }.getOrDefault(pkg)
        labels.put(pkg, l)
        return l
    }

    fun setAll(ctx: Context, all: Array<StatusBarNotification>) {
        val summaries = all.filter { isSummary(it) }.map { it.groupKey }.toSet()
        val withChildren = all.filter { !isSummary(it) }.map { it.groupKey }.toSet()
        items = all.filter { !(isSummary(it) && it.groupKey in withChildren && it.groupKey in summaries) }
            .mapNotNull { convert(ctx, it) }
            .sortedByDescending { it.time }
        changed()
    }

    fun posted(ctx: Context, sbn: StatusBarNotification) {
        val item = convert(ctx, sbn)
        val rest = items.filter { it.key != sbn.key }
        items = (if (item != null && !(isSummary(sbn) && rest.any { it.pkg == sbn.packageName })) rest + item else rest)
            .sortedByDescending { it.time }
        changed()
    }

    /** Для снимков экрана в тестах. */
    internal fun replaceForTest(list: List<Item>) {
        items = list
        changed()
    }

    fun removed(key: String) {
        val rest = items.filter { it.key != key }
        if (rest.size != items.size) {
            items = rest
            changed()
        }
    }

    private fun isSummary(sbn: StatusBarNotification) =
        sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0

    private fun convert(ctx: Context, sbn: StatusBarNotification): Item? {
        if (sbn.packageName == ctx.packageName) return null
        val n = sbn.notification
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return null
        if (n.flags and Notification.FLAG_FOREGROUND_SERVICE != 0) return null
        val ex = n.extras ?: return null
        if (ex.containsKey(Notification.EXTRA_MEDIA_SESSION)) return null

        val isMessage = n.category == Notification.CATEGORY_MESSAGE ||
            ex.containsKey(Notification.EXTRA_MESSAGES)
        var title = (ex.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            ?: ex.getCharSequence(Notification.EXTRA_TITLE_BIG)
            ?: ex.getCharSequence(Notification.EXTRA_TITLE))?.toString().orEmpty()
        var text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: ex.getCharSequence(Notification.EXTRA_TEXT)
            ?: ex.getCharSequence(Notification.EXTRA_SUB_TEXT))?.toString().orEmpty()

        // MessagingStyle: показываем последнее сообщение.
        if (isMessage) {
            @Suppress("DEPRECATION")
            val msgs = ex.getParcelableArray(Notification.EXTRA_MESSAGES)
            val last = msgs?.lastOrNull() as? android.os.Bundle
            if (last != null) {
                val body = last.getCharSequence("text")?.toString()
                val sender = last.getCharSequence("sender")?.toString()
                if (!body.isNullOrBlank()) text = if (!sender.isNullOrBlank() && sender != title) "$sender: $body" else body
            }
        }
        if (title.isBlank() && text.isBlank()) return null
        val app = appLabel(ctx, sbn.packageName)
        if (title.isBlank()) { title = text; text = "" }

        return Item(
            key = sbn.key,
            pkg = sbn.packageName,
            app = app,
            title = title.trim(),
            text = text.trim(),
            time = if (n.`when` > 0) n.`when` else sbn.postTime,
            largeIcon = n.getLargeIcon(),
            intent = n.contentIntent,
            clearable = sbn.isClearable,
            autoCancel = n.flags and Notification.FLAG_AUTO_CANCEL != 0,
            isMessage = isMessage,
        )
    }
}
