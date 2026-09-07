package com.tihon.productivnost.core

import kotlinx.serialization.Serializable

/**
 * Слепок всего прогресса. Формат намеренно простой и версионированный:
 * файл, сохранённый сегодня, должен читаться будущими версиями приложения.
 */
@Serializable
data class Backup(
    val app: String = APP_TAG,
    val version: Int = FORMAT_VERSION,
    val exportedAt: Long = 0L,
    val settings: Settings = Settings(),
    val garden: List<Plant> = emptyList(),
    val tasks: List<HabitTask> = emptyList(),
    val limits: List<AppLimit> = emptyList(),
    val usage: UsageState = UsageState(),
) {
    companion object {
        const val APP_TAG = "productivnost"
        const val FORMAT_VERSION = 1
    }
}
