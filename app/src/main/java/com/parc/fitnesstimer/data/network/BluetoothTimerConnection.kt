package com.parc.fitnesstimer.data.network

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.parc.fitnesstimer.domain.ConnectionState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okio.ByteString
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BluetoothTimerConnection @Inject constructor(
    @ApplicationContext private val context: Context
) : TimerConnection {

    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _textFrames = MutableSharedFlow<String>(extraBufferCapacity = 64)
    override val textFrames: SharedFlow<String> = _textFrames.asSharedFlow()

    @Volatile private var socket: BluetoothSocket? = null
    private var readerJob: Job? = null
    private var reconnectJob: Job? = null
    @Volatile private var currentDeviceName: String? = null
    private var backoffIndex = 0

    private val backoffMs = listOf(1_500L, 3_000L, 5_000L, 7_500L, 10_000L)

    override fun connect(urlOrAddress: String) {
        reconnectJob?.cancel()
        currentDeviceName = urlOrAddress
        backoffIndex = 0
        connectInternal()
    }

    private fun connectInternal() {
        _connectionState.value = ConnectionState.CONNECTING
        scope.launch {
            val deviceName = currentDeviceName ?: return@launch

            // Android 12+ requires runtime BLUETOOTH_CONNECT before any
            // adapter / device call. Calling without it throws SecurityException.
            if (!hasBluetoothConnectPermission()) {
                _connectionState.value = ConnectionState.DISCONNECTED
                return@launch
            }

            try {
                val adapter = bluetoothAdapter()
                if (adapter == null || !adapter.isEnabled) {
                    _connectionState.value = ConnectionState.DISCONNECTED
                    return@launch
                }

                val device = adapter.bondedDevices?.firstOrNull { it.name == deviceName }
                if (device == null) {
                    // Device not paired — UI should prompt the user to pair.
                    _connectionState.value = ConnectionState.DISCONNECTED
                    return@launch
                }

                val btSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                btSocket.connect()

                socket = btSocket
                backoffIndex = 0
                _connectionState.value = ConnectionState.CONNECTED
                startReader(btSocket)
            } catch (e: SecurityException) {
                _connectionState.value = ConnectionState.DISCONNECTED
            } catch (e: IOException) {
                closeSocketQuietly()
                _connectionState.value = ConnectionState.DISCONNECTED
                scheduleReconnect()
            }
        }
    }

    override fun disconnect() {
        // Clear name FIRST so the reader's finally block doesn't trigger
        // a reconnect after we cancel its job.
        currentDeviceName = null
        reconnectJob?.cancel()
        reconnectJob = null
        readerJob?.cancel()
        readerJob = null
        closeSocketQuietly()
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    override fun sendText(text: String): Boolean {
        val out = socket?.outputStream ?: return false
        return try {
            out.write((text + "\n").toByteArray(Charsets.UTF_8))
            out.flush()
            true
        } catch (e: IOException) {
            false
        }
    }

    override fun sendBinary(bytes: ByteString): Boolean {
        val out = socket?.outputStream ?: return false
        return try {
            out.write(bytes.toByteArray())
            out.flush()
            true
        } catch (e: IOException) {
            false
        }
    }

    private fun startReader(activeSocket: BluetoothSocket) {
        readerJob = scope.launch(Dispatchers.IO) {
            try {
                activeSocket.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        line?.let { _textFrames.emit(it) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                // Connection lost — fall through to reconnect logic below.
            } finally {
                _connectionState.value = ConnectionState.DISCONNECTED
                // Only reconnect if disconnect() hasn't been called.
                if (currentDeviceName != null) scheduleReconnect()
            }
        }
    }

    private fun scheduleReconnect() {
        if (currentDeviceName == null) return
        reconnectJob?.cancel()
        _connectionState.value = ConnectionState.RECONNECTING

        reconnectJob = scope.launch {
            val delayMs = backoffMs.getOrElse(backoffIndex) { backoffMs.last() }
            if (backoffIndex < backoffMs.lastIndex) backoffIndex++
            delay(delayMs)
            if (currentDeviceName != null) connectInternal()
        }
    }

    private fun bluetoothAdapter(): BluetoothAdapter? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        } else {
            @Suppress("DEPRECATION")
            BluetoothAdapter.getDefaultAdapter()
        }

    private fun hasBluetoothConnectPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Pre-API 31 the install-time BLUETOOTH permission is sufficient.
            true
        }

    private fun closeSocketQuietly() {
        try { socket?.close() } catch (_: IOException) { /* best-effort */ }
        socket = null
    }
}
