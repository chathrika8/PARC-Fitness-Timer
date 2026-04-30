package com.parc.fitnesstimer.data.model

import com.parc.fitnesstimer.domain.TimerMode
import com.parc.fitnesstimer.domain.TimerPhase
import com.parc.fitnesstimer.domain.TimerRunState
import kotlinx.serialization.Serializable

/**
 * Exact JSON representation broadcast by the server every 100 ms.
 * Field names match the protocol spec.
 */
@Serializable
data class TimerStateDto(
    val state: Int = 0,
    val mode: Int = 0,
    val mname: String = "AMRAP",
    val mm: Int = 0,
    val ss: Int = 0,
    val round: Int = 0,
    val phase: Int = 0,
    val pre: Int = 3,
    val colon: Boolean = true,
    val work: Int = 720,
    val rest: Int = 60,
    val rounds: Int = 8
) {
    val runState: TimerRunState get() = TimerRunState.fromInt(state)
    val timerMode: TimerMode get() = TimerMode.fromInt(mode)
    val timerPhase: TimerPhase get() = TimerPhase.fromInt(phase)
}

/**
 * Computed display values for the 6-digit LED panel.
 * null = blank digit (no segments lit).
 */
data class DisplayDigits(
    val d0: Int?,   // Round tens (blue)
    val d1: Int?,   // Round units (blue)
    val d2: Int?,   // Minute tens (red)
    val d3: Int?,   // Minute units (red)
    val d4: Int?,   // Second tens (red)
    val d5: Int?,   // Second units (red)
    val colonOn: Boolean = true
) {
    companion object {
        val BLANK = DisplayDigits(null, null, null, null, null, null, false)

        fun from(state: TimerStateDto, colonOverride: Boolean? = null): DisplayDigits {
            val colon = colonOverride ?: state.colon
            return when (state.runState) {
                TimerRunState.PRE_START -> DisplayDigits(
                    d0 = null,
                    d1 = null,
                    d2 = null,
                    d3 = state.pre,
                    d4 = null,
                    d5 = null,
                    colonOn = false
                )
                else -> {
                    val showRound = state.timerMode != TimerMode.STOPWATCH &&
                            state.timerMode != TimerMode.COUNTDOWN
                    DisplayDigits(
                        d0 = if (showRound && state.round >= 10) state.round / 10 else null,
                        d1 = if (showRound) state.round % 10 else null,
                        // Leading zero on minute tens is suppressed; the minute
                        // units digit is always shown so "0:30" doesn't render
                        // as a blank with a stray colon.
                        d2 = if (state.mm >= 10) state.mm / 10 else null,
                        d3 = state.mm % 10,
                        d4 = state.ss / 10,
                        d5 = state.ss % 10,
                        colonOn = colon
                    )
                }
            }
        }
    }
}
