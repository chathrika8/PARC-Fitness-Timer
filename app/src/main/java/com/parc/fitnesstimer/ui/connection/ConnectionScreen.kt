package com.parc.fitnesstimer.ui.connection

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parc.fitnesstimer.ui.theme.AccentGreen
import com.parc.fitnesstimer.ui.theme.AccentRed
import com.parc.fitnesstimer.ui.theme.BorderSubtle
import com.parc.fitnesstimer.ui.theme.TextPrimary
import com.parc.fitnesstimer.ui.theme.TextSecondary

@Composable
fun ConnectionScreen(
    onConnected: () -> Unit,
    viewModel: ConnectionViewModel = hiltViewModel()
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()

    // Navigate away as soon as WebSocket connects
    LaunchedEffect(ui.wsConnected) {
        if (ui.wsConnected) onConnected()
    }

    val isLoading = ui.wifiState is WifiUiState.Connecting

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
        ) {
            Icon(
                Icons.Default.Wifi,
                contentDescription = null,
                tint     = AccentRed,
                modifier = Modifier.size(56.dp)
            )

            Text(
                "Connect to Timer",
                fontSize   = 22.sp,
                fontWeight = FontWeight.Bold,
                color      = TextPrimary
            )

            Text(
                "Connect your phone to the GymTimer WiFi network, or enter the device IP address below.",
                color     = TextSecondary,
                fontSize  = 13.sp,
                textAlign = TextAlign.Center
            )

            // ── WiFi AP connect button (API 29+) ──────────────────────────────
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Button(
                    onClick  = viewModel::onConnectWifiTapped,
                    enabled  = !isLoading,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape    = RoundedCornerShape(3.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = AccentRed)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = TextPrimary
                        )
                    } else {
                        Text(
                            "Connect to ${ui.ssid} WiFi",
                            fontSize   = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                Text(
                    "Open Settings \u2192 WiFi, then connect to \"${ui.ssid}\", then return here.",
                    color     = TextSecondary,
                    fontSize  = 12.sp,
                    textAlign = TextAlign.Center
                )
            }

            // ── Manual IP fallback ─────────────────────────────────────────────
            HorizontalDivider(color = BorderSubtle)

            Text("Or enter IP address manually", color = TextSecondary, fontSize = 12.sp)

            OutlinedTextField(
                value         = ui.manualIp,
                onValueChange = viewModel::onManualIpChanged,
                label         = { Text("Device IP Address") },
                singleLine    = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction    = ImeAction.Done
                ),
                modifier = Modifier.fillMaxWidth(),
                colors   = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = AccentRed,
                    unfocusedBorderColor = BorderSubtle,
                    focusedLabelColor    = AccentRed
                )
            )

            OutlinedButton(
                onClick  = viewModel::onConnectIpTapped,
                enabled  = !isLoading,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape    = RoundedCornerShape(3.dp),
                border   = BorderStroke(1.dp, AccentRed),
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = AccentRed)
            ) {
                Text("Connect", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }

            // ── Error / status message ─────────────────────────────────────────
            when (val ws = ui.wifiState) {
                is WifiUiState.Error -> Text(
                    ws.message,
                    color    = AccentRed,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                is WifiUiState.ManualRequired -> Text(
                    "Automatic WiFi connection requires Android 10+. Please connect manually using Settings.",
                    color    = TextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                is WifiUiState.Success -> Text(
                    "Connected!",
                    color    = AccentGreen,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                else -> {}
            }
        }
    }
}
