package com.parc.fitnesstimer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parc.fitnesstimer.domain.TimerPhase
import com.parc.fitnesstimer.ui.theme.AccentBlue
import com.parc.fitnesstimer.ui.theme.AccentRed
import com.parc.fitnesstimer.ui.theme.TextPrimary

/**
 * WORK / REST phase badge shown during TABATA and INTERVAL modes.
 * Red for WORK, blue for REST.
 */
@Composable
fun PhaseBar(
    phase: TimerPhase,
    modifier: Modifier = Modifier
) {
    val bgColor = when (phase) {
        TimerPhase.WORK -> AccentRed
        TimerPhase.REST -> AccentBlue
    }
    val label = phase.displayName

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bgColor)
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        Text(
            text          = label,
            color         = TextPrimary,
            fontSize      = 13.sp,
            fontWeight    = FontWeight.Bold,
            fontFamily    = FontFamily.Monospace,
            letterSpacing = 2.sp
        )
    }
}
