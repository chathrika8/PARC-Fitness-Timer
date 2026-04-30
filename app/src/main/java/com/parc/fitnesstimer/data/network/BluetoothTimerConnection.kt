package com.parc.fitnesstimer.data.network

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.Context
import com.parc.fitnesstimer.domain.ConnectionState
import dagger.hilt.android.qualifiers.ApplicationContext
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
import kotlinx.coroutines.withContext
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
    private var currentDeviceName: String? = null
    private var backoffIndex = 0

    private val backoffMs = listOf(1_500L, 3_000L, 5_000L, 7_500L, 10_000L)

    override fun connect(urlOrAddress: String) {
        reconnectJob?.cancel()
        currentDeviceName = urlOrAddress
        connectInternal()
    }

    @SuppressLint("MissingPermission")
    private fun connectInternal() {
        _connectionState.value = ConnectionState.CONNECTING
        scope.launch {
            val deviceName = currentDeviceName ?: return@launch
            try {
                val adapter = BluetoothAdapter.getDefaultAdapter()
                if (adapter == null || !adapter.isEnabled) {
                    _connectionState.value = ConnectionState.DISCONNECTED
                    return@launch
                }

                val device = adapter.bondedDevices.firstOrNull { it.name == deviceName }
                if (device == null) {
                    // Device not paired, UI should handle this
                    _connectionState.value = ConnectionState.DISCONNECTED
                    return@launch
                }

                val btSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                btSocket.connect()
                
                socket = btSocket
                backoffIndex = 0
                _connectionState.value = ConnectionState.CONNECTED
                startReader()
            } catch (e: Exception) {
                socket?.close()
                socket = null
                _connectionState.value = ConnectionState.DISCONNECTED
                scheduleReconnect()
            }
        }
    }

    override fun disconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        readerJob?.cancel()
        readerJob = null
        try {
            socket?.close()
        } catch (e: IOException) {
            // Ignore
        }
        socket = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    override fun sendText(text: String): Boolean {
        val out = socket?.outputStream ?: return false
        return try {
            out.write((text + "\n").toByteArray(Charsets.UTF_8))
            true
        } catch (e: IOException) {
            false
        }
    }

    override fun sendBinary(bytes: ByteString): Boolean {
        val out = socket?.outputStream ?: return false
        return try {
            out.write(bytes.toByteArray())
            true
        } catch (e: IOException) {
            false
        }
    }

    private fun startReader() {
        val inStream = socket?.inputStream ?: return
        readerJob = scope.launch(Dispatchers.IO) {
            val reader = inStream.bufferedReader(Charsets.UTF_8)
            try {
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    line?.let { _textFrames.emit(it) }
                }
            } catch (e: IOException) {
                // Connection lost
            } finally {
                _connectionState.value = ConnectionState.DISCONNECTED
                scheduleReconnect()
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
            connectInternal()
        }
    }
}
