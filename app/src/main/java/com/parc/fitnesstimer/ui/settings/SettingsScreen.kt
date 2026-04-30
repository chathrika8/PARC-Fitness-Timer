package com.parc.fitnesstimer.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parc.fitnesstimer.data.model.DeviceSettings
import com.parc.fitnesstimer.ui.theme.AccentGreen
import com.parc.fitnesstimer.ui.theme.AccentRed
import com.parc.fitnesstimer.ui.theme.BorderSubtle
import com.parc.fitnesstimer.ui.theme.SurfaceCard
import com.parc.fitnesstimer.ui.theme.SurfaceElevated
import com.parc.fitnesstimer.ui.theme.TextPrimary
import com.parc.fitnesstimer.ui.theme.TextSecondary
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val ui      by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // File picker for OTA
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val name = uri.lastPathSegment ?: "firmware.bin"
            viewModel.onFileSelected(uri, name, context.contentResolver)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        SectionHeader("DEVICE SETTINGS", modifier = Modifier.padding(top = 8.dp))

        // ══════════════════════════════════════════════════════════════════════
        // SECTION 1 — Digit Display Order
        // ══════════════════════════════════════════════════════════════════════
        SettingCard {
            SectionTitle("Display Digit Order")
            Text(
                "Long-press and drag to reorder physical display positions.",
                color    = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            DigitReorderPanel(
                dmap    = ui.dmap,
                onSwap  = viewModel::swapDigits
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick  = viewModel::saveDmap,
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.buttonColors(containerColor = AccentRed)
            ) { Text("Save Display Order") }
        }

        // ══════════════════════════════════════════════════════════════════════
        // SECTION 2 — Sound Settings
        // ══════════════════════════════════════════════════════════════════════
        SettingCard {
            SectionTitle("Sound Settings")

            // Volume slider
            Text("Master Volume: ${ui.buzzConfig.vol}", color = TextSecondary, fontSize = 13.sp)
            Slider(
                value         = ui.buzzConfig.vol.toFloat(),
                onValueChange = { viewModel.onVolumeChanged(it.roundToInt()) },
                valueRange    = 1f..10f,
                steps         = 8,
                colors        = SliderDefaults.colors(
                    thumbColor       = AccentRed,
                    activeTrackColor = AccentRed
                )
            )

            HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(vertical = 8.dp))
            Text("Events", color = TextSecondary, fontSize = 12.sp,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))

            SoundToggle("3-2-1 Pip",           ui.buzzConfig.pip321,           viewModel::onPip321Changed)
            SoundToggle("GO tone",              ui.buzzConfig.goTone,           viewModel::onGoToneChanged)
            SoundToggle("Phase transition",     ui.buzzConfig.phaseTransition,  viewModel::onPhaseTransitionChanged)
            SoundToggle("Workout done",         ui.buzzConfig.workoutDone,      viewModel::onWorkoutDoneChanged)
            SoundToggle("Last 10 s warning",    ui.buzzConfig.last10sWarning,   viewModel::onLast10sChanged)
            SoundToggle("Pause click",          ui.buzzConfig.pauseClick,       viewModel::onPauseClickChanged)

            Spacer(Modifier.height(8.dp))
            Button(
                onClick  = viewModel::saveSound,
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.buttonColors(containerColor = AccentRed)
            ) { Text("Save Sound Settings") }
        }

        // ══════════════════════════════════════════════════════════════════════
        // SECTION 3 — Display Settings
        // ══════════════════════════════════════════════════════════════════════
        SettingCard {
            SectionTitle("Display Settings")
            
            Text("Colon behavior", color = TextSecondary, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                listOf("Off", "Solid", "Blink", "Alt").forEachIndexed { index, label ->
                    OutlinedButton(
                        onClick = { viewModel.onColonModeChanged(index) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (ui.colonMode == index) AccentRed.copy(alpha = 0.2f) else SurfaceCard,
                            contentColor = if (ui.colonMode == index) AccentRed else TextSecondary
                        ),
                        border = BorderStroke(1.dp, if (ui.colonMode == index) AccentRed else BorderSubtle)
                    ) { Text(label, fontSize = 11.sp) }
                }
            }
            
            Spacer(Modifier.height(8.dp))
            
            Text("Pre-start Countdown", color = TextSecondary, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                listOf("10s", "3s").forEachIndexed { index, label ->
                    val value = if (index == 0) 10 else 3
                    val isSelected = ui.c321Mode == index
                    OutlinedButton(
                        onClick = { viewModel.onC321ModeChanged(index) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isSelected) AccentRed.copy(alpha = 0.2f) else SurfaceCard,
                            contentColor = if (isSelected) AccentRed else TextSecondary
                        ),
                        border = BorderStroke(1.dp, if (isSelected) AccentRed else BorderSubtle)
                    ) { Text(label, fontSize = 12.sp) }
                }
            }
            
            Spacer(Modifier.height(8.dp))
            Button(
                onClick  = viewModel::saveDisplaySettings,
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.buttonColors(containerColor = AccentRed)
            ) { Text("Save Display Settings") }
        }

        // ══════════════════════════════════════════════════════════════════════
        // SECTION 4 — Connection Settings
        // ══════════════════════════════════════════════════════════════════════
        SettingCard {
            SectionTitle("Connection Settings")

            Text("Connection Mode", color = TextSecondary, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                listOf("AP", "Client", "BT", "BT+WiFi").forEachIndexed { index, label ->
                    OutlinedButton(
                        onClick = { viewModel.onConnModeChanged(index) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (ui.connMode == index) AccentRed.copy(alpha = 0.2f) else SurfaceCard,
                            contentColor = if (ui.connMode == index) AccentRed else TextSecondary
                        ),
                        border = BorderStroke(1.dp, if (ui.connMode == index) AccentRed else BorderSubtle),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    ) { Text(label, fontSize = 10.sp, maxLines = 1) }
                }
            }
            
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value         = ui.btNameInput,
                onValueChange = viewModel::onBtNameChanged,
                label         = { Text("Bluetooth Name") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
                colors        = fieldColors()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value         = ui.btPinInput,
                onValueChange = viewModel::onBtPinChanged,
                label         = { Text("Bluetooth PIN") },
                singleLine    = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                modifier      = Modifier.fillMaxWidth(),
                colors        = fieldColors()
            )
            
            Spacer(Modifier.height(12.dp))
            Button(
                onClick  = viewModel::onApplyConnSet,
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.buttonColors(containerColor = AccentRed)
            ) { Text("Save Connection Mode") }
            
            HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(vertical = 12.dp))
            
            Text("WiFi Client Credentials", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))

            OutlinedTextField(
                value         = ui.ssidInput,
                onValueChange = viewModel::onSsidChanged,
                label         = { Text("Client SSID") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
                colors        = fieldColors()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value               = ui.passInput,
                onValueChange       = viewModel::onPassChanged,
                label               = { Text("Password (blank = open)") },
                singleLine          = true,
                visualTransformation = if (ui.showPass) VisualTransformation.None
                                       else PasswordVisualTransformation(),
                trailingIcon        = {
                    IconButton(onClick = viewModel::onShowPassToggled) {
                        Icon(
                            if (ui.showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle password",
                            tint = TextSecondary
                        )
                    }
                },
                keyboardOptions     = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction    = ImeAction.Done
                ),
                modifier            = Modifier.fillMaxWidth(),
                colors              = fieldColors()
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick  = viewModel::onApplyWifi,
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.buttonColors(containerColor = AccentRed)
            ) { Text("Save WiFi Credentials") }
        }

        // ══════════════════════════════════════════════════════════════════════
        // SECTION 4 — Firmware Update
        // ══════════════════════════════════════════════════════════════════════
        SettingCard {
            SectionTitle("Firmware OTA Update")

            when (val ota = ui.otaState) {
                is OtaUiState.Idle -> {
                    Button(
                        onClick  = { filePicker.launch("application/octet-stream") },
                        modifier = Modifier.fillMaxWidth(),
                        colors   = ButtonDefaults.buttonColors(containerColor = SurfaceElevated)
                    ) { Text("Select .bin File", color = TextPrimary) }
                }
                is OtaUiState.FileSelected -> {
                    Text("Selected: ${ota.name}", color = TextSecondary, fontSize = 12.sp)
                    Text("Size: ${ota.size / 1024} KB", color = TextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick  = viewModel::resetOtaState,
                            modifier = Modifier.weight(1f),
                            border   = BorderStroke(1.dp, BorderSubtle)
                        ) { Text("Cancel", color = TextSecondary) }
                        Button(
                            onClick  = viewModel::startOtaUpload,
                            modifier = Modifier.weight(1f),
                            colors   = ButtonDefaults.buttonColors(containerColor = AccentRed)
                        ) { Text("Upload") }
                    }
                }
                is OtaUiState.WaitingForReady -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color       = AccentRed
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Waiting for device…", color = TextSecondary, fontSize = 13.sp)
                    }
                }
                is OtaUiState.Uploading -> {
                    Text(
                        "Uploading — ${(ota.progress * 100).roundToInt()}% (${ota.written / 1024} / ${ota.total / 1024} KB)",
                        color    = TextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress      = { ota.progress },
                        modifier      = Modifier.fillMaxWidth(),
                        color         = AccentRed,
                        trackColor    = BorderSubtle
                    )
                }
                is OtaUiState.Done -> {
                    Text("✓ Upload complete! Device is restarting…",
                        color = AccentGreen, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = viewModel::resetOtaState,
                        border  = BorderStroke(1.dp, BorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Done", color = TextSecondary) }
                }
                is OtaUiState.Error -> {
                    Text("Error: ${ota.message}", color = AccentRed, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = viewModel::resetOtaState,
                        border  = BorderStroke(1.dp, BorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Try Again", color = TextSecondary) }
                }
            }
        }

        // ══════════════════════════════════════════════════════════════════════
        // SECTION 5 — Danger zone
        // ══════════════════════════════════════════════════════════════════════
        SettingCard {
            SectionTitle("Device Control")
            OutlinedButton(
                onClick  = viewModel::onRestartDevice,
                modifier = Modifier.fillMaxWidth(),
                border   = BorderStroke(1.dp, AccentRed.copy(alpha = 0.5f)),
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = AccentRed)
            ) { Text("Restart Device") }
        }
    }
}

// ── Helper composables ────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        fontSize      = 11.sp,
        fontWeight    = FontWeight.Bold,
        letterSpacing = 2.sp,
        color         = TextSecondary,
        modifier      = modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        fontSize   = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color      = TextPrimary,
        modifier   = Modifier.padding(bottom = 12.dp)
    )
}

@Composable
private fun SettingCard(content: @Composable () -> Unit) {
    Surface(
        color  = SurfaceCard,
        shape  = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
private fun SoundToggle(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier             = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment    = Alignment.CenterVertically
    ) {
        Text(label, color = TextPrimary, fontSize = 14.sp)
        Switch(
            checked         = checked,
            onCheckedChange = onChecked,
            colors          = SwitchDefaults.colors(
                checkedThumbColor  = TextPrimary,
                checkedTrackColor  = AccentRed
            )
        )
    }
}

/**
 * Digit reorder panel — 6 draggable tiles.
 * Long-press + drag to reorder. Calls [onSwap](fromIndex, toIndex) on drop.
 */
@Composable
private fun DigitReorderPanel(
    dmap: List<Int>,
    onSwap: (Int, Int) -> Unit
) {
    val tileSize = 48.dp
    val spacing  = 6.dp

    // Track which tile is being dragged and its current Y offset
    var dragIndex  by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
        dmap.forEachIndexed { physicalPos, logicalRole ->
            val label = DeviceSettings.DIGIT_LABELS.getOrElse(logicalRole) { "D$logicalRole" }
            val isDragging = physicalPos == dragIndex

            Surface(
                color  = if (isDragging) AccentRed.copy(alpha = 0.15f) else SurfaceElevated,
                shape  = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, if (isDragging) AccentRed else BorderSubtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(tileSize)
                    .offset { IntOffset(0, if (isDragging) dragOffset.roundToInt() else 0) }
                    .pointerInput(physicalPos) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                dragIndex  = physicalPos
                                dragOffset = 0f
                            },
                            onDrag = { _, delta ->
                                dragOffset += delta.y
                                // Determine swap target based on how far we've dragged
                                val tilePx = (tileSize + spacing).toPx()
                                val steps  = (dragOffset / tilePx).roundToInt()
                                val target = (physicalPos + steps).coerceIn(0, dmap.lastIndex)
                                if (target != physicalPos) {
                                    onSwap(physicalPos, target)
                                    dragIndex  = target
                                    dragOffset -= steps * tilePx
                                }
                            },
                            onDragEnd = {
                                dragIndex  = -1
                                dragOffset = 0f
                            },
                            onDragCancel = {
                                dragIndex  = -1
                                dragOffset = 0f
                            }
                        )
                    }
            ) {
                Row(
                    modifier             = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment    = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("D$physicalPos", color = AccentRed, fontSize = 13.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.width(32.dp))
                    Text(label, color = TextPrimary, fontSize = 13.sp,
                        modifier = Modifier.weight(1f))
                    Icon(Icons.Default.DragHandle, contentDescription = "Drag",
                        tint = TextSecondary, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = AccentRed,
    unfocusedBorderColor = BorderSubtle,
    focusedLabelColor    = AccentRed,
    cursorColor          = AccentRed
)
