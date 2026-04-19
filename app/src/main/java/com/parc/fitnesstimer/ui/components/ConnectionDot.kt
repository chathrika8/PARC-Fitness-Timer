package com.parc.fitnesstimer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parc.fitnesstimer.domain.ConnectionState
import com.parc.fitnesstimer.ui.theme.AccentGreen
import com.parc.fitnesstimer.ui.theme.AccentOrange
import com.parc.fitnesstimer.ui.theme.TextSecondary
import com.parc.fitnesstimer.ui.theme.TextDisabled

/**
 * A small coloured dot + label indicating WebSocket connection state.
 * Colour transitions are animated over 300 ms to avoid jarring flashes.
 */
@Composable
fun ConnectionDot(
    state: ConnectionState,
    modifier: Modifier = Modifier
) {
    val dotColor by animateColorAsState(
        targetValue = when (state) {
            ConnectionState.CONNECTED    -> AccentGreen
            ConnectionState.RECONNECTING -> AccentOrange
            ConnectionState.CONNECTING   -> AccentOrange.copy(alpha = 0.6f)
            ConnectionState.DISCONNECTED -> TextDisabled
        },
        animationSpec = tween(durationMillis = 300),
        label = "dot_color"
    )

    val label = when (state) {
        ConnectionState.CONNECTED    -> "Connected"
        ConnectionState.RECONNECTING -> "Reconnecting…"
        ConnectionState.CONNECTING   -> "Connecting…"
        ConnectionState.DISCONNECTED -> "Disconnected"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text  = label,
            fontSize = 11.sp,
            color = TextSecondary
        )
    }
}
