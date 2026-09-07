package com.tihon.productivnost.core

import kotlinx.serialization.Serializable

/** Вид растения. */
@Serializable
enum class PlantKind { FOCUS, STUDY }

/**
 * Растение в саду. Хранится вечно.
 * [seed] определяет форму — одна и та же семечка всегда даёт одно и то же дерево.
 */
@Serializable
data class Plant(
    val id: Long,
    val seed: Long,
    val kind: PlantKind,
    val species: Int,
    val plantedAt: Long,
    val minutes: Int,
)

@Serializable
enum class TaskRepeat { ONCE, DAILY, EVERY_OTHER_DAY }

/** Разовое / повторяющееся напоминание с привязкой ко времени дня. */
@Serializable
data class HabitTask(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: String,
    val repeat: TaskRepeat,
    val minuteOfDay: Int,
    val enabled: Boolean = true,
    /** Последний день (epochDay), когда напоминание сработало. */
    val lastFiredDay: Long = -1L,
    /** День отсчёта чётности для «через день». */
    val anchorDay: Long = 0L,
    /** Разовая задача уже выполнена. */
    val done: Boolean = false,
) {
    fun dueToday(epochDay: Long): Boolean = when (repeat) {
        TaskRepeat.ONCE -> !done
        TaskRepeat.DAILY -> true
        TaskRepeat.EVERY_OTHER_DAY -> ((epochDay - anchorDay) % 2L + 2L) % 2L == 0L
    }
}

@Serializable
enum class SessionPhase { WORK, BREAK }

/** Сессия «дерево от зависимости». */
@Serializable
data class FocusSession(
    val startedAt: Long,
    val durationMs: Long,
    val trees: Int,
    val seed: Long,
    val species: Int,
    val kind: PlantKind = PlantKind.FOCUS,
)

/** Сессия учёбы: помодоро 25/5 с блокировкой во время работы. */
@Serializable
data class StudySession(
    val startedAt: Long,
    val phase: SessionPhase,
    val phaseStartedAt: Long,
    val completedBlocks: Int,
    val workMs: Long,
    val breakMs: Long,
    val seed: Long,
)

/** Итог завершившейся сессии — показывается пользователю один раз. */
@Serializable
data class SessionOutcome(
    val success: Boolean,
    val kind: PlantKind,
    val plants: Int,
    val minutes: Int,
    val reason: String = "",
    val blamePackage: String? = null,
    val seed: Long = 0L,
    val species: Int = 0,
    val at: Long = 0L,
)

/** Дневной лимит для приложения; продлевается отжиманиями. */
@Serializable
data class AppLimit(
    val pkg: String,
    val dailyMinutes: Int,
)

@Serializable
data class Settings(
    val remindersEnabled: Boolean = true,
    val intervalMinutes: Int = 30,
    val onlyWhenUsingPhone: Boolean = true,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val sleepEnabled: Boolean = true,
    val sleepFromMinute: Int = 21 * 60,
    val sleepToMinute: Int = 22 * 60,
    val sleepEveryMinutes: Int = 10,
    val allowedApps: Set<String> = emptySet(),
    val focusMinutes: Int = 30,
    val studyWorkMinutes: Int = 25,
    val studyBreakMinutes: Int = 5,
    val minutesPerPushup: Double = 2.5,
    val strictBlocking: Boolean = true,
)

/** Учёт экранного времени за текущий день. */
@Serializable
data class UsageState(
    val day: Long = 0L,
    val perApp: Map<String, Long> = emptyMap(),
    val bonusMs: Map<String, Long> = emptyMap(),
    val activeUseMs: Long = 0L,
    val pushupsToday: Int = 0,
    /** Сколько интервальных напоминаний уже показано за сегодня. */
    val remindersFired: Int = 0,
    /** Последний отработанный «слот» ночных напоминаний. */
    val lastSleepSlot: Int = -1,
)

/** Тексты «интервальных» напоминаний. */
data class IntervalReminder(val title: String, val body: String, val icon: String)

object Reminders {
    val interval = listOf(
        IntervalReminder("Попей воды", "Стакан воды — и голова снова ясная", "💧"),
        IntervalReminder("Оторвись от телефона", "Посмотри вдаль 20 секунд, глаза скажут спасибо", "👀"),
        IntervalReminder("Разомнись", "Встань, потянись, покрути плечами", "🤸"),
        IntervalReminder("Пойди погуляй", "Пять минут на воздухе стоят часа в ленте", "🌿"),
        IntervalReminder("Выпрями спину", "Плечи назад, подбородок вверх", "🧘"),
        IntervalReminder("Глубокий вдох", "Четыре счёта вдох, шесть — выдох", "🌬️"),
    )
}
