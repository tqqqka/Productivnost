package com.tihon.productivnost.core

import kotlin.random.Random

/** Чистая логика сессий — используется и сервисом, и экранами. */
object Sessions {

    /** Сколько деревьев даёт фокус-сессия выбранной длительности. */
    fun treesFor(minutes: Int): Int = (minutes / 30).coerceAtLeast(1)

    val focusOptions = listOf(30, 60, 90, 120)

    fun focusProgress(session: FocusSession, now: Long): Float =
        ((now - session.startedAt).toFloat() / session.durationMs.toFloat()).coerceIn(0f, 1f)

    fun focusRemaining(session: FocusSession, now: Long): Long =
        (session.startedAt + session.durationMs - now).coerceAtLeast(0L)

    fun studyPhaseRemaining(session: StudySession, now: Long): Long {
        val total = if (session.phase == SessionPhase.WORK) session.workMs else session.breakMs
        return (session.phaseStartedAt + total - now).coerceAtLeast(0L)
    }

    fun studyPhaseProgress(session: StudySession, now: Long): Float {
        val total = if (session.phase == SessionPhase.WORK) session.workMs else session.breakMs
        if (total <= 0) return 0f
        return ((now - session.phaseStartedAt).toFloat() / total).coerceIn(0f, 1f)
    }

    /** Общий прогресс текущего блока учёбы вместе с уже завершёнными. */
    fun studyGrowth(session: StudySession, now: Long): Float {
        val inBlock = if (session.phase == SessionPhase.WORK) studyPhaseProgress(session, now) else 1f
        return inBlock
    }

    fun newSeed(): Long = Random.nextLong()

    fun plants(count: Int, kind: PlantKind, baseSeed: Long, baseSpecies: Int, minutes: Int, at: Long): List<Plant> {
        val species = PlantGeneratorSpecies.count(kind)
        return (0 until count).map { i ->
            val seed = if (i == 0) baseSeed else baseSeed * 31L + i * 2654435761L
            Plant(
                id = at + i,
                seed = seed,
                kind = kind,
                species = if (i == 0) baseSpecies else ((seed ushr 13).toInt().let { if (it < 0) -it else it }) % species,
                plantedAt = at + i,
                minutes = minutes,
            )
        }
    }
}

/** Небольшая обёртка, чтобы core не зависел от пакета voxel напрямую. */
object PlantGeneratorSpecies {
    var focus: Int = 5
    var study: Int = 3
    fun count(kind: PlantKind) = if (kind == PlantKind.STUDY) study else focus
}
