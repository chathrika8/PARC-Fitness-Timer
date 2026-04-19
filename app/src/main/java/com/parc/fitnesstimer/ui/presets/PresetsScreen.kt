package com.parc.fitnesstimer.ui.presets

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parc.fitnesstimer.data.model.Preset
import com.parc.fitnesstimer.ui.theme.AccentGreen
import com.parc.fitnesstimer.ui.theme.AccentRed
import com.parc.fitnesstimer.ui.theme.BorderSubtle
import com.parc.fitnesstimer.ui.theme.SurfaceCard
import com.parc.fitnesstimer.ui.theme.TextPrimary
import com.parc.fitnesstimer.ui.theme.TextSecondary

@Composable
fun PresetsScreen(
    onPresetLoaded: () -> Unit,
    viewModel: PresetsViewModel = hiltViewModel()
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Header ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("PRESETS", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp, color = TextSecondary)
            IconButton(onClick = viewModel::refresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh",
                    tint = TextSecondary)
            }
        }

        when {
            ui.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentRed)
                }
            }
            ui.presets.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.FitnessCenter, contentDescription = null,
                            tint = TextSecondary.copy(alpha = 0.4f),
                            modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("No presets saved yet.", color = TextSecondary, fontSize = 15.sp)
                        Text("Configure a workout and tap \"Save Preset\".",
                            color = TextSecondary.copy(alpha = 0.6f), fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item { Spacer(Modifier.height(4.dp)) }
                    items(ui.presets, key = { it.slot }) { preset ->
                        PresetCard(
                            preset   = preset,
                            onLoad   = {
                                viewModel.onLoadPreset(preset.slot)
                                onPresetLoaded()
                            },
                            onDelete = { viewModel.onDeleteTapped(preset.slot) }
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }

    // ── Delete confirmation dialog ─────────────────────────────────────────────
    if (ui.confirmDeleteSlot != null) {
        AlertDialog(
            onDismissRequest = viewModel::onDeleteDismissed,
            title   = { Text("Delete preset?") },
            text    = { Text("This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = viewModel::onDeleteConfirmed) {
                    Text("Delete", color = AccentRed)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDeleteDismissed) { Text("Cancel") }
            },
            containerColor = SurfaceCard
        )
    }
}

@Composable
private fun PresetCard(
    preset: Preset,
    onLoad: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color  = SurfaceCard,
        shape  = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, BorderSubtle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(preset.name, color = TextPrimary, fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold)
                Text(preset.summary, color = TextSecondary, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp))
            }
            IconButton(onClick = onLoad) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Load",
                    tint = AccentGreen)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete",
                    tint = AccentRed.copy(alpha = 0.7f))
            }
        }
    }
}
