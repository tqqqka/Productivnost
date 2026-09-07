package com.tihon.productivnost.service

import android.content.Context
import com.tihon.productivnost.core.FocusSession
import com.tihon.productivnost.core.PlantKind
import com.tihon.productivnost.core.SessionOutcome
import com.tihon.productivnost.core.SessionPhase
import com.tihon.productivnost.core.Sessions
import com.tihon.productivnost.core.Store
import com.tihon.productivnost.core.StudySession
import com.tihon.productivnost.util.Fmt

/**
 * Единая точка управления сессиями: и экран, и сервис ходят сюда,
 * поэтому итог сессии всегда считается одинаково.
 */
object SessionController {

    fun startFocus(context: Context, minutes: Int, species: Int) {
        val now = System.currentTimeMillis()
        Store.setOutcome(null)
        Store.setStudy(null)
        Store.setFocus(
            FocusSession(
                startedAt = now,
                durationMs = minutes * 60_000L,
                trees = Sessions.treesFor(minutes),
                seed = Sessions.newSeed(),
                species = species,
            ),
        )
        Store.updateSettings { it.copy(focusMinutes = minutes) }
        ProductivityService.start(context)
    }

    /** Досрочный отказ — росток засыхает так же, как при заходе в чужое приложение. */
    fun giveUpFocus(context: Context) {
        val s = Store.focus.value ?: return
        val now = System.currentTimeMillis()
        Store.setFocus(null)
        Store.setOutcome(
            SessionOutcome(
                success = false,
                kind = PlantKind.FOCUS,
                plants = 0,
                minutes = ((now - s.startedAt) / 60_000L).toInt(),
                reason = "Сессия остановлена вручную — росток не успел вырасти",
                seed = s.seed, species = s.species, at = now,
            ),
        )
        Sfx.play(context, Sfx.WITHER, 0.5f)
    }

    fun startStudy(context: Context, workMinutes: Int, breakMinutes: Int) {
        val now = System.currentTimeMillis()
        Store.setOutcome(null)
        Store.setFocus(null)
        Store.setStudy(
            StudySession(
                startedAt = now,
                phase = SessionPhase.WORK,
                phaseStartedAt = now,
                completedBlocks = 0,
                workMs = workMinutes * 60_000L,
                breakMs = breakMinutes * 60_000L,
                seed = Sessions.newSeed(),
            ),
        )
        Store.updateSettings { it.copy(studyWorkMinutes = workMinutes, studyBreakMinutes = breakMinutes) }
        ProductivityService.start(context)
    }

    /** Завершение учёбы: по дереву за каждый полностью отработанный блок. */
    fun finishStudy(context: Context) {
        val s = Store.study.value ?: return
        val now = System.currentTimeMillis()
        Store.setStudy(null)
        val blocks = s.completedBlocks
        val minutes = (s.workMs / 60_000L).toInt()
        if (blocks > 0) {
            Store.addPlants(Sessions.plants(blocks, PlantKind.STUDY, s.seed, 0, minutes, now))
            Store.setOutcome(
                SessionOutcome(
                    success = true, kind = PlantKind.STUDY, plants = blocks,
                    minutes = blocks * minutes,
                    reason = "${Fmt.trees(blocks)} учёбы — по одному за каждые $minutes минут работы",
                    seed = s.seed, species = 0, at = now,
                ),
            )
            Sfx.play(context, Sfx.GROWN, 0.75f)
        } else {
            Store.setOutcome(
                SessionOutcome(
                    success = false, kind = PlantKind.STUDY, plants = 0, minutes = 0,
                    reason = "Ни один блок по $minutes минут не был закончен",
                    at = now,
                ),
            )
            Sfx.play(context, Sfx.WITHER, 0.5f)
        }
    }

    fun finishFocusNow(context: Context) {
        val s = Store.focus.value ?: return
        val now = System.currentTimeMillis()
        val minutes = (s.durationMs / 60_000L).toInt()
        Store.setFocus(null)
        val plants = Sessions.plants(s.trees, PlantKind.FOCUS, s.seed, s.species, 30, now)
        Store.addPlants(plants)
        Store.setOutcome(
            SessionOutcome(
                success = true, kind = PlantKind.FOCUS, plants = plants.size, minutes = minutes,
                reason = "Ты продержался ${Fmt.duration(minutes)} без телефона",
                seed = s.seed, species = s.species, at = now,
            ),
        )
        Sfx.play(context, Sfx.GROWN, 0.75f)
    }
}
