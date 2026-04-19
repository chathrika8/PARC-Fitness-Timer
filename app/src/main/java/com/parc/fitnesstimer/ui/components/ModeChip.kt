package com.parc.fitnesstimer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parc.fitnesstimer.domain.TimerMode
import com.parc.fitnesstimer.ui.theme.AccentRed
import com.parc.fitnesstimer.ui.theme.BorderSubtle
import com.parc.fitnesstimer.ui.theme.SurfaceCard
import com.parc.fitnesstimer.ui.theme.TextPrimary
import com.parc.fitnesstimer.ui.theme.TextSecondary

/**
 * Horizontally-scrollable row of mode selection chips.
 * The active chip is filled with [AccentRed]; others are outlined.
 */
@Composable
fun ModeChipRow(
    selectedMode: TimerMode,
    onModeSelected: (TimerMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TimerMode.entries.forEach { mode ->
            ModeChip(
                label    = mode.displayName,
                selected = mode == selectedMode,
                onClick  = { onModeSelected(mode) }
            )
        }
    }
}

@Composable
private fun ModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bgColor     = if (selected) AccentRed else SurfaceCard
    val textColor   = if (selected) TextPrimary else TextSecondary
    val borderColor = if (selected) AccentRed else BorderSubtle

    Surface(
        onClick      = onClick,
        shape        = RoundedCornerShape(50),
        color        = bgColor,
        border       = BorderStroke(1.dp, borderColor)
    ) {
        Text(
            text       = label,
            fontSize   = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color      = textColor,
            modifier   = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}
