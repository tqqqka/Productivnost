package com.tihon.productivnost.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.ZoneId

/**
 * Единое хранилище состояния приложения.
 * Живёт в одном процессе с сервисом и UI, поэтому StateFlow видны обоим.
 */
object Store {

    private lateinit var prefs: SharedPreferences
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _garden = MutableStateFlow<List<Plant>>(emptyList())
    val garden: StateFlow<List<Plant>> = _garden.asStateFlow()

    private val _tasks = MutableStateFlow<List<HabitTask>>(emptyList())
    val tasks: StateFlow<List<HabitTask>> = _tasks.asStateFlow()

    private val _focus = MutableStateFlow<FocusSession?>(null)
    val focus: StateFlow<FocusSession?> = _focus.asStateFlow()

    private val _study = MutableStateFlow<StudySession?>(null)
    val study: StateFlow<StudySession?> = _study.asStateFlow()

    private val _outcome = MutableStateFlow<SessionOutcome?>(null)
    val outcome: StateFlow<SessionOutcome?> = _outcome.asStateFlow()

    private val _limits = MutableStateFlow<List<AppLimit>>(emptyList())
    val limits: StateFlow<List<AppLimit>> = _limits.asStateFlow()

    private val _usage = MutableStateFlow(UsageState())
    val usage: StateFlow<UsageState> = _usage.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences("productivnost", Context.MODE_PRIVATE)
        _settings.value = read("settings", Settings())
        _garden.value = read("garden", emptyList())
        _tasks.value = read("tasks", defaultTasks())
        _focus.value = readNullable("focus")
        _study.value = readNullable("study")
        _outcome.value = readNullable("outcome")
        _limits.value = read("limits", emptyList())
        _usage.value = read("usage", UsageState(day = today()))
        rolloverIfNeeded()
    }

    val isReady: Boolean get() = ::prefs.isInitialized

    private inline fun <reified T> read(key: String, fallback: T): T = runCatching {
        prefs.getString(key, null)?.let { json.decodeFromString<T>(it) } ?: fallback
    }.getOrElse { fallback }

    private inline fun <reified T> readNullable(key: String): T? = runCatching {
        prefs.getString(key, null)?.let { json.decodeFromString<T>(it) }
    }.getOrNull()

    private inline fun <reified T> write(key: String, value: T?) {
        if (value == null) prefs.edit().remove(key).apply()
        else prefs.edit().putString(key, json.encodeToString(value)).apply()
    }

    fun today(): Long = LocalDate.now(ZoneId.systemDefault()).toEpochDay()

    /** Сбрасывает дневные счётчики при смене суток. */
    fun rolloverIfNeeded() {
        val d = today()
        if (_usage.value.day != d) {
            updateUsage { UsageState(day = d) }
        }
    }

    fun updateSettings(block: (Settings) -> Settings) {
        _settings.value = block(_settings.value).also { write("settings", it) }
    }

    fun addPlants(plants: List<Plant>) {
        _garden.value = (_garden.value + plants).also { write("garden", it) }
    }

    fun clearGarden() {
        _garden.value = emptyList<Plant>().also { write("garden", it) }
    }

    fun updateTasks(block: (List<HabitTask>) -> List<HabitTask>) {
        _tasks.value = block(_tasks.value).also { write("tasks", it) }
    }

    fun setFocus(session: FocusSession?) {
        _focus.value = session.also { write("focus", it) }
    }

    fun setStudy(session: StudySession?) {
        _study.value = session.also { write("study", it) }
    }

    fun setOutcome(o: SessionOutcome?) {
        _outcome.value = o.also { write("outcome", it) }
    }

    fun updateLimits(block: (List<AppLimit>) -> List<AppLimit>) {
        _limits.value = block(_limits.value).also { write("limits", it) }
    }

    fun updateUsage(block: (UsageState) -> UsageState) {
        _usage.value = block(_usage.value).also { write("usage", it) }
    }

    fun addAppUsage(pkg: String, deltaMs: Long) = updateUsage { u ->
        val cur = u.perApp[pkg] ?: 0L
        u.copy(perApp = u.perApp + (pkg to cur + deltaMs))
    }

    fun addBonus(pkg: String, ms: Long) = updateUsage { u ->
        val cur = u.bonusMs[pkg] ?: 0L
        u.copy(bonusMs = u.bonusMs + (pkg to cur + ms))
    }

    /** Сколько миллисекунд ещё разрешено приложению сегодня. `null` — лимита нет. */
    fun remainingFor(pkg: String): Long? {
        val limit = _limits.value.firstOrNull { it.pkg == pkg } ?: return null
        val u = _usage.value
        val allowed = limit.dailyMinutes * 60_000L + (u.bonusMs[pkg] ?: 0L)
        return allowed - (u.perApp[pkg] ?: 0L)
    }

    private fun defaultTasks(): List<HabitTask> = listOf(
        HabitTask(
            id = "teeth",
            title = "Почисти зубы",
            subtitle = "Две минуты перед сном",
            icon = "🪥",
            repeat = TaskRepeat.DAILY,
            minuteOfDay = 21 * 60 + 30,
        ),
        HabitTask(
            id = "shower",
            title = "Сходи помойся",
            subtitle = "Через день, ближе к вечеру",
            icon = "🚿",
            repeat = TaskRepeat.EVERY_OTHER_DAY,
            minuteOfDay = 20 * 60 + 30,
            anchorDay = LocalDate.now(ZoneId.systemDefault()).toEpochDay(),
        ),
        HabitTask(
            id = "tidy",
            title = "Разбери фото",
            subtitle = "Почисти дубликаты в галерее",
            icon = "🗂️",
            repeat = TaskRepeat.DAILY,
            minuteOfDay = 19 * 60,
            enabled = false,
        ),
    )
}
