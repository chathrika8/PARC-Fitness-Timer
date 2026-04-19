package com.parc.fitnesstimer.data.network

import com.parc.fitnesstimer.domain.ConnectionState
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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin OkHttp WebSocket wrapper.
 *
 * Responsibilities:
 *  - Open / close the WebSocket connection
 *  - Expose incoming text frames as a SharedFlow
 *  - Expose connection state as a StateFlow
 *  - Implement exponential back-off reconnection on failure
 *  - Provide [sendText] for JSON commands and [sendBinary] for OTA chunks
 *
 * IMPORTANT: Binary OTA chunks MUST use [sendBinary] (OkHttp binary frame).
 *            Using [sendText] for raw bytes will corrupt the data.
 */
@Singleton
class WebSocketClient @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ── Connection state ──────────────────────────────────────────────────────

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // ── Incoming frames ───────────────────────────────────────────────────────

    /** All text frames from the server. Subscribers must not block. */
    private val _textFrames = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val textFrames: SharedFlow<String> = _textFrames.asSharedFlow()

    // ── Internal state ────────────────────────────────────────────────────────

    @Volatile private var webSocket: WebSocket? = null
    @Volatile private var currentUrl: String? = null
    private var reconnectJob: Job? = null
    private var backoffIndex = 0

    /** Backoff ladder: 1.5 s → 3 s → 5 s → 7.5 s → 10 s (capped). */
    private val backoffMs = listOf(1_500L, 3_000L, 5_000L, 7_500L, 10_000L)

    // ── OkHttp listener ───────────────────────────────────────────────────────

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            this@WebSocketClient.webSocket = webSocket
            backoffIndex = 0
            _connectionState.value = ConnectionState.CONNECTED
            reconnectJob?.cancel()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            _textFrames.tryEmit(text)
        }

        // Binary frames from server are not expected per protocol, but silently ignored.
        override fun onMessage(webSocket: WebSocket, bytes: ByteString) = Unit

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            this@WebSocketClient.webSocket = null
            _connectionState.value = ConnectionState.DISCONNECTED
            scheduleReconnect()
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            this@WebSocketClient.webSocket = null
            _connectionState.value = ConnectionState.DISCONNECTED
            // 1000 = normal closure initiated by us; do not reconnect.
            if (code != 1000) scheduleReconnect()
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun connect(url: String) {
        reconnectJob?.cancel()
        currentUrl = url
        _connectionState.value = ConnectionState.CONNECTING
        val request = Request.Builder().url(url).build()
        okHttpClient.newWebSocket(request, listener)
    }

    fun disconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        currentUrl = null
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    /**
     * Send a JSON text command to the server.
     * @return true if the frame was enqueued; false if not connected.
     */
    fun sendText(json: String): Boolean = webSocket?.send(json) ?: false

    /**
     * Send a raw binary frame — used exclusively for OTA firmware chunks.
     * OkHttp sends this as a WebSocket binary frame (opcode 0x02).
     * @return true if the frame was enqueued; false if not connected.
     */
    fun sendBinary(bytes: ByteString): Boolean = webSocket?.send(bytes) ?: false

    // ── Reconnect logic ───────────────────────────────────────────────────────

    private fun scheduleReconnect() {
        val url = currentUrl ?: return
        reconnectJob?.cancel()
        _connectionState.value = ConnectionState.RECONNECTING

        reconnectJob = scope.launch {
            val delayMs = backoffMs.getOrElse(backoffIndex) { backoffMs.last() }
            if (backoffIndex < backoffMs.lastIndex) backoffIndex++
            delay(delayMs)
            _connectionState.value = ConnectionState.CONNECTING
            val request = Request.Builder().url(url).build()
            okHttpClient.newWebSocket(request, listener)
        }
    }
}
