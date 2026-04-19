package com.parc.fitnesstimer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parc.fitnesstimer.domain.TimerMode
import com.parc.fitnesstimer.ui.theme.AccentRed
import com.parc.fitnesstimer.ui.theme.BorderSubtle
import com.parc.fitnesstimer.ui.theme.SurfaceCard
import com.parc.fitnesstimer.ui.theme.TextPrimary
import com.parc.fitnesstimer.ui.theme.TextSecondary

/**
 * Configuration panel showing mode-appropriate spinners.
 * All state lives in the parent ViewModel; this composable is stateless.
 *
 * @param workSecs   Work duration in seconds
 * @param restSecs   Rest duration in seconds
 * @param rounds     Number of rounds
 */
@Composable
fun ConfigPanel(
    mode: TimerMode,
    workSecs: Int,
    restSecs: Int,
    rounds: Int,
    onWorkSecsChange: (Int) -> Unit,
    onRestSecsChange: (Int) -> Unit,
    onRoundsChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = mode.hasConfig,
        enter   = expandVertically(),
        exit    = shrinkVertically()
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (mode) {
                TimerMode.AMRAP,
                TimerMode.COUNTDOWN -> {
                    DurationSpinner(
                        label      = if (mode == TimerMode.AMRAP) "Work Duration" else "Duration",
                        seconds    = workSecs,
                        onChanged  = onWorkSecsChange,
                        showMinutes = true,
                        min        = 60,
                        max        = 5999,
                        step       = 60
                    )
                }
                TimerMode.FOR_TIME -> {
                    Column {
                        DurationSpinner(
                            label      = "Time Cap",
                            seconds    = workSecs,
                            onChanged  = onWorkSecsChange,
                            showMinutes = true,
                            min        = 0,
                            max        = 5999,
                            step       = 60
                        )
                        if (workSecs == 0) {
                            Text(
                                text  = "No time cap",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                            )
                        }
                    }
                }
                TimerMode.EMOM -> {
                    DurationSpinner(
                        label     = "Round Duration",
                        seconds   = workSecs,
                        onChanged = onWorkSecsChange,
                        min       = 10,
                        max       = 600
                    )
                    IntSpinner(
                        label    = "Rounds",
                        value    = rounds,
                        onChanged = onRoundsChange,
                        min      = 1,
                        max      = 99
                    )
                }
                TimerMode.TABATA -> {
                    DurationSpinner(
                        label     = "Work",
                        seconds   = workSecs,
                        onChanged = onWorkSecsChange,
                        min       = 5,
                        max       = 300
                    )
                    DurationSpinner(
                        label     = "Rest",
                        seconds   = restSecs,
                        onChanged = onRestSecsChange,
                        min       = 5,
                        max       = 300
                    )
                    IntSpinner(
                        label    = "Rounds",
                        value    = rounds,
                        onChanged = onRoundsChange,
                        min      = 1,
                        max      = 99
                    )
                }
                TimerMode.INTERVAL -> {
                    DurationSpinner(
                        label      = "Work",
                        seconds    = workSecs,
                        onChanged  = onWorkSecsChange,
                        showMinutes = true,
                        min        = 10,
                        max        = 3600,
                        step       = 30
                    )
                    DurationSpinner(
                        label      = "Rest",
                        seconds    = restSecs,
                        onChanged  = onRestSecsChange,
                        showMinutes = true,
                        min        = 10,
                        max        = 3600,
                        step       = 30
                    )
                    IntSpinner(
                        label    = "Rounds",
                        value    = rounds,
                        onChanged = onRoundsChange,
                        min      = 1,
                        max      = 99
                    )
                }
                TimerMode.STOPWATCH -> { /* No config */ }
            }
        }
    }
}

// ── Sub-components ────────────────────────────────────────────────────────────

/**
 * Spinner for a duration in seconds, optionally displaying it as M:SS.
 */
@Composable
private fun DurationSpinner(
    label: String,
    seconds: Int,
    onChanged: (Int) -> Unit,
    showMinutes: Boolean = false,
    min: Int = 0,
    max: Int = 5999,
    step: Int = if (showMinutes) 60 else 1,
    modifier: Modifier = Modifier
) {
    val display = if (showMinutes) {
        "${seconds / 60}m ${seconds % 60}s"
    } else {
        "${seconds}s"
    }

    SpinnerRow(
        label    = label,
        display  = display,
        onDec    = { onChanged((seconds - step).coerceAtLeast(min)) },
        onInc    = { onChanged((seconds + step).coerceAtMost(max)) },
        modifier = modifier
    )
}

/**
 * Spinner for a plain integer (rounds, etc.).
 */
@Composable
private fun IntSpinner(
    label: String,
    value: Int,
    onChanged: (Int) -> Unit,
    min: Int = 1,
    max: Int = 99,
    modifier: Modifier = Modifier
) {
    SpinnerRow(
        label   = label,
        display = value.toString(),
        onDec   = { onChanged((value - 1).coerceAtLeast(min)) },
        onInc   = { onChanged((value + 1).coerceAtMost(max)) },
        modifier = modifier
    )
}

@Composable
private fun SpinnerRow(
    label: String,
    display: String,
    onDec: () -> Unit,
    onInc: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color  = SurfaceCard,
        shape  = RoundedCornerShape(3.dp),
        tonalElevation = 0.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text     = label,
                color    = TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = onDec, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease",
                    tint = TextPrimary, modifier = Modifier.size(18.dp))
            }

            Text(
                text       = display,
                color      = TextPrimary,
                fontSize   = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign  = TextAlign.Center,
                modifier   = Modifier.width(72.dp)
            )

            IconButton(onClick = onInc, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Add, contentDescription = "Increase",
                    tint = AccentRed, modifier = Modifier.size(18.dp))
            }
        }
    }
}
