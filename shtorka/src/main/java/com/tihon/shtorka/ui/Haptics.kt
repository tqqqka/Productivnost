package com.tihon.shtorka.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import com.tihon.shtorka.core.Prefs

/** Тактильные отклики в духе Taptic Engine. */
object Haptics {
    /** Лёгкий щелчок: переключатель, упор слайдера. */
    fun tick(v: View) = perform(v, if (Build.VERSION.SDK_INT >= 27) HapticFeedbackConstants.KEYBOARD_PRESS else HapticFeedbackConstants.VIRTUAL_KEY)

    /** Отчётливый клик: нажатие кнопки. */
    fun click(v: View) = perform(v, HapticFeedbackConstants.CONTEXT_CLICK)

    /** Долгое нажатие: раскрытие модуля. */
    fun heavy(v: View) = perform(v, HapticFeedbackConstants.LONG_PRESS)

    /** Граница: слайдер упёрся в край. */
    fun edge(v: View) = perform(v, if (Build.VERSION.SDK_INT >= 27) HapticFeedbackConstants.TEXT_HANDLE_MOVE else HapticFeedbackConstants.CLOCK_TICK)

    private fun perform(v: View, c: Int) {
        if (!Prefs.haptics) return
        v.performHapticFeedback(c)
    }
}
