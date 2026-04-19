package com.parc.fitnesstimer.data.model

import com.parc.fitnesstimer.domain.TimerMode
import kotlinx.serialization.Serializable

/**
 * A single saved preset slot returned by the server.
 */
@Serializable
data class Preset(
    val slot: Int,
    val name: String,
    val mode: Int,
    val work: Int,
    val rest: Int,
    val rounds: Int
) {
    val timerMode: TimerMode get() = TimerMode.fromInt(mode)

    /** Human-readable duration summary shown in the preset list. */
    val summary: String
        get() = buildString {
            append(timerMode.displayName)
            append(" · ")
            when (timerMode) {
                TimerMode.AMRAP, TimerMode.COUNTDOWN -> {
                    val m = work / 60
                    val s = work % 60
                    if (m > 0) append("${m}m ")
                    if (s > 0 || m == 0) append("${s}s")
                }
                TimerMode.FOR_TIME -> {
                    if (work <= 0) append("No cap")
                    else {
                        val m = work / 60
                        val s = work % 60
                        if (m > 0) append("Cap ${m}m ")
                        if (s > 0) append("${s}s")
                    }
                }
                TimerMode.EMOM -> append("${work}s × ${rounds}r")
                TimerMode.TABATA -> append("${work}s on / ${rest}s off × ${rounds}r")
                TimerMode.INTERVAL -> append("${work}s / ${rest}s × ${rounds}r")
                TimerMode.STOPWATCH -> append("∞")
            }
        }
}

/**
 * Wrapper for the "presets" typed response from server.
 */
@Serializable
data class PresetsResponse(
    val type: String,
    val list: List<Preset>
)
