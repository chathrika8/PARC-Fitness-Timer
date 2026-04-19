package com.parc.fitnesstimer.ui.timer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parc.fitnesstimer.data.model.DisplayDigits
import com.parc.fitnesstimer.domain.ConnectionState
import com.parc.fitnesstimer.domain.TimerMode
import com.parc.fitnesstimer.domain.TimerRunState
import com.parc.fitnesstimer.ui.components.ConfigPanel
import com.parc.fitnesstimer.ui.components.ConnectionDot
import com.parc.fitnesstimer.ui.components.ModeChipRow
import com.parc.fitnesstimer.ui.components.PhaseBar
import com.parc.fitnesstimer.ui.components.SixDigitDisplay
import com.parc.fitnesstimer.ui.theme.AccentBlue
import com.parc.fitnesstimer.ui.theme.AccentGreen
import com.parc.fitnesstimer.ui.theme.AccentRed
import com.parc.fitnesstimer.ui.theme.BackgroundDeep
import com.parc.fitnesstimer.ui.theme.BorderSubtle
import com.parc.fitnesstimer.ui.theme.SurfaceCard
import com.parc.fitnesstimer.ui.theme.TextPrimary
import com.parc.fitnesstimer.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@Composable
fun TimerScreen(
    onNavigateToConnection: () -> Unit,
    viewModel: TimerViewModel = hiltViewModel()
) {
    val ui        by viewModel.uiState.collectAsStateWithLifecycle()
    val connState by viewModel.connectionState.collectAsStateWithLifecycle()

    // ── Client-side colon blink ───────────────────────────────────────────────
    var colonVisible by remember { mutableStateOf(true) }
    LaunchedEffect(ui.runState) {
        // Only blink locally when IDLE or PAUSED; otherwise server drives it.
        while (ui.runState == TimerRunState.IDLE || ui.runState == TimerRunState.PAUSED) {
            delay(500)
            colonVisible = !colonVisible
        }
        // When running/PRE_START, keep colon solid (overridden by server value)
        colonVisible = true
    }

    // ── DONE state flash (4 × 600 ms) ────────────────────────────────────────
    var flashOn by remember { mutableStateOf(true) }
    LaunchedEffect(ui.runState) {
        if (ui.runState == TimerRunState.DONE) {
            repeat(8) { // 4 complete cycles
                delay(300)
                flashOn = !flashOn
            }
            flashOn = true
        }
    }

    val digits = viewModel.computeDigits(colonVisible)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // ── Top bar ───────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("PARC TIMER", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp, color = TextSecondary)
            ConnectionDot(state = connState)
        }

        // ── Disconnected banner ───────────────────────────────────────────────
        if (connState == ConnectionState.DISCONNECTED) {
            Surface(
                color = SurfaceCard,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, AccentRed.copy(alpha = 0.3f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Not connected to timer", color = TextSecondary, fontSize = 13.sp)
                    TextButton(onClick = onNavigateToConnection) {
                        Text("Connect", color = AccentRed, fontSize = 13.sp,
                            fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── 6-digit display ───────────────────────────────────────────────────
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            SixDigitDisplay(
                digits    = digits,
                digitWidth = 40.dp,
                flashOn   = flashOn
            )
        }

        Spacer(Modifier.height(8.dp))

        // ── Phase badge ───────────────────────────────────────────────────────
        if (ui.showPhaseBar) {
            PhaseBar(phase = ui.currentPhase)
            Spacer(Modifier.height(8.dp))
        }

        // ── PRE_START countdown label ─────────────────────────────────────────
        if (ui.isPreStart) {
            Text(
                text = "GET READY",
                fontSize = 12.sp, letterSpacing = 3.sp,
                color = TextSecondary, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
        }

        Spacer(Modifier.height(16.dp))

        // ── Mode chips ────────────────────────────────────────────────────────
        ModeChipRow(
            selectedMode    = ui.configMode,
            onModeSelected  = { viewModel.onModeChipTapped(it) }
        )

        // ── Config panel ──────────────────────────────────────────────────────
        ConfigPanel(
            mode          = ui.configMode,
            workSecs      = ui.configWorkSecs,
            restSecs      = ui.configRestSecs,
            rounds        = ui.configRounds,
            onWorkSecsChange = viewModel::onWorkSecsChange,
            onRestSecsChange = viewModel::onRestSecsChange,
            onRoundsChange   = viewModel::onRoundsChange,
            modifier      = Modifier.padding(horizontal = 4.dp)
        )

        Spacer(Modifier.height(8.dp))

        // ── Apply + Save row ──────────────────────────────────────────────────
        if (ui.configMode.hasConfig) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = viewModel::onApplyConfig,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRed),
                    border = BorderStroke(1.dp, AccentRed)
                ) { Text("Apply", fontSize = 13.sp) }

                OutlinedButton(
                    onClick = viewModel::onSavePresetTapped,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                    border = BorderStroke(1.dp, BorderSubtle)
                ) { Text("Save Preset", fontSize = 13.sp) }
            }
            Spacer(Modifier.height(8.dp))
        }

        // ── Bottom controls ───────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Primary START / PAUSE / RESUME / DONE button
            val startLabel = when (ui.runState) {
                TimerRunState.IDLE       -> "START"
                TimerRunState.PRE_START  -> "CANCEL"
                TimerRunState.RUNNING,
                TimerRunState.TRANSITION -> "PAUSE"
                TimerRunState.PAUSED     -> "RESUME"
                TimerRunState.DONE       -> "RESTART"
            }
            val startColor = when (ui.runState) {
                TimerRunState.RUNNING,
                TimerRunState.TRANSITION -> AccentBlue
                TimerRunState.DONE       -> AccentGreen
                else                     -> AccentRed
            }

            Button(
                onClick  = viewModel::onStartPause,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = startColor)
            ) {
                Text(startLabel, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick  = viewModel::onReset,
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                    border   = BorderStroke(1.dp, BorderSubtle),
                    shape    = RoundedCornerShape(10.dp)
                ) { Text("RESET", fontSize = 14.sp, letterSpacing = 1.sp) }

                if (ui.showRoundButton) {
                    Button(
                        onClick  = viewModel::onRoundInc,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape    = RoundedCornerShape(10.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                    ) { Text("ROUND +1", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }

    // ── Mode switch confirmation dialog ───────────────────────────────────────
    if (ui.showModeConfirmDialog && ui.pendingMode != null) {
        AlertDialog(
            onDismissRequest = viewModel::onModeConfirmDismissed,
            title   = { Text("Switch mode?") },
            text    = { Text("Switch to ${ui.pendingMode!!.displayName}? Current workout will reset.") },
            confirmButton = {
                TextButton(onClick = viewModel::onModeConfirmAccepted) {
                    Text("Switch", color = AccentRed)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onModeConfirmDismissed) {
                    Text("Cancel")
                }
            },
            containerColor = SurfaceCard
        )
    }

    // ── Preset name dialog ────────────────────────────────────────────────────
    if (ui.showPresetNameDialog) {
        var presetName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = viewModel::onPresetNameDialogDismissed,
            title   = { Text("Save Preset") },
            text    = {
                OutlinedTextField(
                    value         = presetName,
                    onValueChange = { presetName = it },
                    label         = { Text("Preset name") },
                    singleLine    = true,
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = AccentRed,
                        unfocusedBorderColor = BorderSubtle
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick  = { viewModel.onPresetSaveConfirmed(presetName) },
                    enabled  = presetName.isNotBlank()
                ) { Text("Save", color = AccentRed) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onPresetNameDialogDismissed) {
                    Text("Cancel")
                }
            },
            containerColor = SurfaceCard
        )
    }
}
