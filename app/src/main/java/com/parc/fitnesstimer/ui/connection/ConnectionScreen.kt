package com.parc.fitnesstimer.ui.connection

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        // Only attempt to connect if BLUETOOTH_CONNECT was actually granted.
        // Calling into the BT stack without it throws SecurityException on
        // Android 12+ and leaves the user staring at a useless spinner.
        val granted = grants[Manifest.permission.BLUETOOTH_CONNECT] == true
        if (granted) {
            viewModel.onConnectBluetoothTapped()
        } else {
            viewModel.onBluetoothPermissionDenied()
        }
    }

    LaunchedEffect(ui.wsConnected) {
        if (ui.wsConnected) onConnected()
    }

    val isLoading = ui.wifiState is WifiUiState.Connecting

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            TabRow(selectedTabIndex = ui.selectedTransport) {
                Tab(
                    selected = ui.selectedTransport == 0,
                    onClick = { viewModel.onTransportSelected(0) },
                    text = { Text("WiFi") },
                    icon = { Icon(Icons.Default.Wifi, contentDescription = null) }
                )
                Tab(
                    selected = ui.selectedTransport == 1,
                    onClick = { viewModel.onTransportSelected(1) },
                    text = { Text("Bluetooth") },
                    icon = { Icon(Icons.Default.Bluetooth, contentDescription = null) }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
            ) {
                if (ui.selectedTransport == 0) {
                    WifiConnectionView(ui, viewModel, isLoading)
                } else {
                    BluetoothConnectionView(ui, viewModel, isLoading) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.BLUETOOTH_CONNECT,
                                    Manifest.permission.BLUETOOTH_SCAN
                                )
                            )
                        } else {
                            viewModel.onConnectBluetoothTapped()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WifiConnectionView(ui: ConnectionUiState, viewModel: ConnectionViewModel, isLoading: Boolean) {
    if (isLoading) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 16.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = AccentRed)
            Spacer(Modifier.height(8.dp))
            Text("Looking for GymTimer...", color = TextSecondary, fontSize = 13.sp)
        }
    } else {
        Text("If you are already connected to the network, just tap below to open the app.", color = TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)

        Button(
            onClick = viewModel::onConnectIpTapped,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
        ) {
            Text("Connect to Device (${ui.manualIp})", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(8.dp))
        Divider(color = BorderSubtle)
        Spacer(Modifier.height(8.dp))

        Text("Having trouble? Force Android to switch your WiFi network:", color = TextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            OutlinedButton(
                onClick = viewModel::onConnectWifiTapped,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, AccentRed),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRed)
            ) {
                Text("Force Connect to ${ui.ssid}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Text("Open Settings \u2192 WiFi, then connect to \"${ui.ssid}\".", color = TextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
        
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = ui.manualIp,
            onValueChange = viewModel::onManualIpChanged,
            label = { Text("Device IP Address") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentRed, unfocusedBorderColor = BorderSubtle, focusedLabelColor = AccentRed)
        )
    }

    ConnectionStatusMessage(ui.wifiState)
}

@Composable
fun BluetoothConnectionView(ui: ConnectionUiState, viewModel: ConnectionViewModel, isLoading: Boolean, onConnectClick: () -> Unit) {
    if (isLoading) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 16.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = AccentRed)
            Spacer(Modifier.height(8.dp))
            Text("Looking for Bluetooth device...", color = TextSecondary, fontSize = 13.sp)
        }
    } else {
        Text("Connect using paired Bluetooth device.", color = TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)

        OutlinedTextField(
            value = ui.btDeviceName,
            onValueChange = viewModel::onBtDeviceNameChanged,
            label = { Text("Bluetooth Device Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentRed, unfocusedBorderColor = BorderSubtle, focusedLabelColor = AccentRed)
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onConnectClick,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
        ) {
            Text("Connect via Bluetooth", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }

    ConnectionStatusMessage(ui.wifiState)
}

@Composable
fun ConnectionStatusMessage(state: WifiUiState) {
    when (state) {
        is WifiUiState.Error -> Text(state.message, color = AccentRed, fontSize = 12.sp, textAlign = TextAlign.Center)
        is WifiUiState.ManualRequired -> Text("Automatic WiFi connection requires Android 10+. Please connect manually using Settings.", color = TextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
        is WifiUiState.Success -> Text("Connected!", color = AccentGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        else -> {}
    }
}
