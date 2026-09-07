package com.tihon.productivnost.util

import java.util.Locale

object Fmt {

    fun clock(ms: Long): String {
        val total = (ms.coerceAtLeast(0L) + 999) / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%02d:%02d", m, s)
    }

    fun minutes(ms: Long): String {
        val m = ms / 60_000
        return when {
            m >= 60 && m % 60 == 0L -> "${m / 60} ч"
            m >= 60 -> "${m / 60} ч ${m % 60} мин"
            else -> "$m мин"
        }
    }

    fun duration(minutes: Int): String = when {
        minutes >= 60 && minutes % 60 == 0 -> "${minutes / 60} ч"
        minutes >= 60 -> "${minutes / 60} ч ${minutes % 60} мин"
        else -> "$minutes мин"
    }

    fun timeOfDay(minuteOfDay: Int): String =
        String.format(Locale.US, "%02d:%02d", minuteOfDay / 60, minuteOfDay % 60)

    fun plural(n: Int, one: String, few: String, many: String): String {
        val mod10 = n % 10
        val mod100 = n % 100
        return when {
            mod10 == 1 && mod100 != 11 -> one
            mod10 in 2..4 && mod100 !in 12..14 -> few
            else -> many
        }
    }

    fun trees(n: Int) = "$n ${plural(n, "дерево", "дерева", "деревьев")}"

    fun bytes(b: Long): String {
        if (b < 1024) return "$b Б"
        val kb = b / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.0f КБ", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.US, "%.1f МБ", mb)
        return String.format(Locale.US, "%.2f ГБ", mb / 1024.0)
    }
}
