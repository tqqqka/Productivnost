package com.tihon.productivnost.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.delay

/** Текущее время, обновляемое с заданным периодом — для живых таймеров. */
@Composable
fun rememberNow(periodMs: Long = 500L, enabled: Boolean = true): Long {
    val now by produceState(System.currentTimeMillis(), periodMs, enabled) {
        while (enabled) {
            value = System.currentTimeMillis()
            delay(periodMs)
        }
        value = System.currentTimeMillis()
    }
    return now
}
