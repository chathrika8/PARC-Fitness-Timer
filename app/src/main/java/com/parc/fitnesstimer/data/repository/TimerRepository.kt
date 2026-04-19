package com.parc.fitnesstimer.data.repository

import com.parc.fitnesstimer.data.model.DeviceSettings
import com.parc.fitnesstimer.data.model.OtaEvent
import com.parc.fitnesstimer.data.model.Preset
import com.parc.fitnesstimer.data.model.PresetsResponse
import com.parc.fitnesstimer.data.model.TimerStateDto
import com.parc.fitnesstimer.data.network.WebSocketClient
import com.parc.fitnesstimer.data.prefs.AppPreferences
import com.parc.fitnesstimer.domain.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.ByteString
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central hub that owns the WebSocket connection and all data flows.
 *
 * Frame routing logic:
 *  - Frames containing a "type" key → typed response (presets / settings)
 *  - Frames containing "ota_*" keys → [otaEvents] flow
 *  - All other frames → [timerState] flow (server broadcast)
 */
@Singleton
class TimerRepository @Inject constructor(
    private val wsClient: WebSocketClient,
    private val prefs: AppPreferences,
    private val json: Json
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ── Exposed flows ─────────────────────────────────────────────────────────

    val connectionState: StateFlow<ConnectionState> = wsClient.connectionState

    private val _timerState = MutableStateFlow(TimerStateDto())
    val timerState: StateFlow<TimerStateDto> = _timerState.asStateFlow()

    private val _presets = MutableSharedFlow<List<Preset>>(replay = 1)
    val presets: SharedFlow<List<Preset>> = _presets.asSharedFlow()

    private val _settings = MutableSharedFlow<DeviceSettings>(replay = 1)
    val settings: SharedFlow<DeviceSettings> = _settings.asSharedFlow()

    /**
     * OTA events use [replay=1] so the SettingsViewModel can always see the
     * last emitted event even if it wasn't actively collecting at that moment.
     */
    private val _otaEvents = MutableSharedFlow<OtaEvent>(replay = 1, extraBufferCapacity = 32)
    val otaEvents: SharedFlow<OtaEvent> = _otaEvents.asSharedFlow()

    // ── Initialisation ────────────────────────────────────────────────────────

    init {
        scope.launch {
            wsClient.textFrames.collect { text -> handleTextFrame(text) }
        }
    }

    // ── Connection management ─────────────────────────────────────────────────

    fun connect(ip: String) {
        val url = prefs.buildWsUrl(ip)
        scope.launch { prefs.saveLastIp(ip) }
        wsClient.connect(url)
    }

    fun disconnect() = wsClient.disconnect()

    // ── Commands ──────────────────────────────────────────────────────────────

    fun sendStart()  = wsClient.sendText("""{"cmd":"start"}""")
    fun sendPause()  = wsClient.sendText("""{"cmd":"pause"}""")
    fun sendReset()  = wsClient.sendText("""{"cmd":"reset"}""")
    fun sendRinc()   = wsClient.sendText("""{"cmd":"rinc"}""")

    fun sendConfig(mode: Int, work: Int, rest: Int, rounds: Int) =
        wsClient.sendText("""{"cmd":"cfg","mode":$mode,"work":$work,"rest":$rest,"rounds":$rounds}""")

    fun sendPresetsGet() = wsClient.sendText("""{"cmd":"presets_get"}""")

    fun sendPresetSave(slot: Int, name: String) =
        wsClient.sendText("""{"cmd":"preset_save","slot":$slot,"name":"${name.replace("\"", "\\\"")}"}""")

    fun sendPresetLoad(slot: Int) =
        wsClient.sendText("""{"cmd":"preset_load","slot":$slot}""")

    fun sendPresetDel(slot: Int) =
        wsClient.sendText("""{"cmd":"preset_del","slot":$slot}""")

    fun sendSettingsGet() = wsClient.sendText("""{"cmd":"settings_get"}""")

    fun sendDmapSave(map: List<Int>) =
        wsClient.sendText("""{"cmd":"dmap_save","map":[${map.joinToString(",")}]}""")

    fun sendSoundSave(vol: Int, ev: Int, modes: Int) =
        wsClient.sendText("""{"cmd":"sound_save","vol":$vol,"ev":$ev,"modes":$modes}""")

    fun sendWifiSet(ssid: String, pass: String) {
        val escapedSsid = ssid.replace("\"", "\\\"")
        val escapedPass = pass.replace("\"", "\\\"")
        wsClient.sendText("""{"cmd":"wifi_set","ssid":"$escapedSsid","pass":"$escapedPass"}""")
    }

    fun sendRestart() = wsClient.sendText("""{"cmd":"restart"}""")

    fun sendOtaBegin(size: Int) =
        wsClient.sendText("""{"cmd":"ota_begin","size":$size}""")

    /**
     * Send a raw binary WebSocket frame containing OTA data.
     * Uses OkHttp's [WebSocket.send(ByteString)] which produces opcode 0x02 (binary).
     */
    fun sendOtaChunk(bytes: ByteString): Boolean = wsClient.sendBinary(bytes)

    // ── Frame parsing ─────────────────────────────────────────────────────────

    private fun handleTextFrame(text: String) {
        try {
            val root: JsonObject = json.parseToJsonElement(text).jsonObject

            when {
                root.containsKey("type") -> handleTypedResponse(root)
                root.containsKey("ota_ready") -> {
                    val ready = root["ota_ready"]?.jsonPrimitive?.booleanOrNull ?: false
                    if (ready) _otaEvents.tryEmit(OtaEvent.Ready)
                }
                root.containsKey("ota_prog") -> {
                    val written = root["ota_prog"]?.jsonPrimitive?.int ?: 0
                    val total   = root["ota_total"]?.jsonPrimitive?.int ?: 0
                    _otaEvents.tryEmit(OtaEvent.Progress(written, total))
                }
                root.containsKey("ota_done") -> {
                    _otaEvents.tryEmit(OtaEvent.Done)
                }
                root.containsKey("ota_err") -> {
                    val msg = root["ota_err"]?.jsonPrimitive?.contentOrNull ?: "Unknown OTA error"
                    _otaEvents.tryEmit(OtaEvent.Error(msg))
                }
                root.containsKey("restarting") -> {
                    _otaEvents.tryEmit(OtaEvent.Restarting)
                }
                root.containsKey("dmap_saved") || root.containsKey("sound_saved") -> {
                    // Acknowledged — no action needed beyond what the ViewModel tracks.
                }
                else -> {
                    // Standard 100 ms state broadcast
                    val state = json.decodeFromString<TimerStateDto>(text)
                    _timerState.value = state
                }
            }
        } catch (e: Exception) {
            // Malformed frame — silently ignore. Production builds don't log PII.
        }
    }

    private fun handleTypedResponse(root: JsonObject) {
        when (root["type"]?.jsonPrimitive?.contentOrNull) {
            "presets" -> {
                try {
                    val response = json.decodeFromString<PresetsResponse>(root.toString())
                    _presets.tryEmit(response.list)
                } catch (_: Exception) {}
            }
            "settings" -> {
                try {
                    val settings = json.decodeFromString<DeviceSettings>(root.toString())
                    _settings.tryEmit(settings)
                } catch (_: Exception) {}
            }
        }
    }
}
