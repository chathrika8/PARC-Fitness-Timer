package com.parc.fitnesstimer.data.repository

import com.parc.fitnesstimer.data.model.DeviceSettings
import com.parc.fitnesstimer.data.model.OtaEvent
import com.parc.fitnesstimer.data.model.Preset
import com.parc.fitnesstimer.data.model.PresetsResponse
import com.parc.fitnesstimer.data.model.TimerStateDto
import com.parc.fitnesstimer.data.network.BluetoothTimerConnection
import com.parc.fitnesstimer.data.network.TimerConnection
import com.parc.fitnesstimer.data.network.WifiTimerConnection
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
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
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
    private val wifiConnection: WifiTimerConnection,
    private val btConnection: BluetoothTimerConnection,
    private val prefs: AppPreferences,
    private val json: Json
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var activeConnection: TimerConnection = wifiConnection

    // ── Exposed flows ─────────────────────────────────────────────────────────

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

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

    private val _commandAcks = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val commandAcks: SharedFlow<String> = _commandAcks.asSharedFlow()

    // ── Initialisation ────────────────────────────────────────────────────────

    init {
        scope.launch {
            wifiConnection.textFrames.collect { text -> handleTextFrame(text) }
        }
        scope.launch {
            btConnection.textFrames.collect { text -> handleTextFrame(text) }
        }
        scope.launch {
            wifiConnection.connectionState.collect { state ->
                if (activeConnection == wifiConnection) _connectionState.value = state
            }
        }
        scope.launch {
            btConnection.connectionState.collect { state ->
                if (activeConnection == btConnection) _connectionState.value = state
            }
        }
    }

    // ── Connection management ─────────────────────────────────────────────────

    fun useWifi() {
        if (activeConnection === wifiConnection) return
        // Tear down the previous transport so two sockets aren't held open.
        btConnection.disconnect()
        activeConnection = wifiConnection
        _connectionState.value = wifiConnection.connectionState.value
    }

    fun useBluetooth() {
        if (activeConnection === btConnection) return
        wifiConnection.disconnect()
        activeConnection = btConnection
        _connectionState.value = btConnection.connectionState.value
    }

    fun connectWifi(ip: String) {
        useWifi()
        val url = prefs.buildWsUrl(ip)
        scope.launch { prefs.saveLastIp(ip) }
        activeConnection.connect(url)
    }

    fun connectBluetooth(deviceName: String) {
        useBluetooth()
        activeConnection.connect(deviceName)
    }

    fun disconnect() = activeConnection.disconnect()

    // ── Commands ──────────────────────────────────────────────────────────────
    //
    // All command frames are built via kotlinx.serialization rather than string
    // templating so user-supplied values (preset names, SSIDs, passwords, BT
    // names/PINs) are correctly JSON-escaped — including backslashes, control
    // characters, and embedded quotes.

    private fun send(json: JsonObject): Boolean =
        activeConnection.sendText(json.toString())

    private inline fun cmd(name: String, build: JsonObjectBuilder.() -> Unit = {}): JsonObject =
        buildJsonObject {
            put("cmd", name)
            build()
        }

    fun sendStart()  = send(cmd("start"))
    fun sendPause()  = send(cmd("pause"))
    fun sendReset()  = send(cmd("reset"))
    fun sendRinc()   = send(cmd("rinc"))

    fun sendConfig(mode: Int, work: Int, rest: Int, rounds: Int) =
        send(cmd("cfg") {
            put("mode", mode)
            put("work", work)
            put("rest", rest)
            put("rounds", rounds)
        })

    fun sendPresetsGet() = send(cmd("presets_get"))

    fun sendPresetSave(slot: Int, name: String) =
        send(cmd("preset_save") {
            put("slot", slot)
            put("name", name)
        })

    fun sendPresetLoad(slot: Int) =
        send(cmd("preset_load") { put("slot", slot) })

    fun sendPresetDel(slot: Int) =
        send(cmd("preset_del") { put("slot", slot) })

    fun sendSettingsGet() = send(cmd("settings_get"))

    fun sendDmapSave(map: List<Int>) =
        send(cmd("dmap_save") {
            put("map", buildJsonArray { map.forEach { add(JsonPrimitive(it)) } })
        })

    fun sendSoundSave(vol: Int, ev: Int, modes: Int) =
        send(cmd("sound_save") {
            put("vol", vol)
            put("ev", ev)
            put("modes", modes)
        })

    fun sendDisplaySave(colonMode: Int, countdown321: Int) =
        send(cmd("display_save") {
            put("colon", colonMode)
            put("c321", countdown321)
        })

    fun sendConnSet(mode: Int, btName: String, btPin: String) =
        send(cmd("conn_set") {
            put("mode", mode)
            put("btName", btName)
            put("btPin", btPin)
        })

    fun sendWifiSet(ssid: String, pass: String) =
        send(cmd("wifi_set") {
            put("ssid", ssid)
            put("pass", pass)
        })

    fun sendRestart() = send(cmd("restart"))

    fun sendOtaBegin(size: Int) =
        send(cmd("ota_begin") { put("size", size) })

    /**
     * Send a raw binary WebSocket frame containing OTA data.
     * Uses OkHttp's [WebSocket.send(ByteString)] which produces opcode 0x02 (binary).
     */
    fun sendOtaChunk(bytes: ByteString): Boolean = activeConnection.sendBinary(bytes)

    // ── Frame parsing ─────────────────────────────────────────────────────────

    private fun handleTextFrame(text: String) {
        val root: JsonObject = try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            // Not a JSON object — frame is malformed or non-JSON. Drop it.
            return
        }

        try {
            when {
                root.containsKey("type") -> handleTypedResponse(root)
                root.containsKey("ota_ready") -> {
                    val ready = root["ota_ready"]?.jsonPrimitive?.booleanOrNull ?: false
                    if (ready) _otaEvents.tryEmit(OtaEvent.Ready)
                }
                root.containsKey("ota_prog") -> {
                    val written = root["ota_prog"]?.jsonPrimitive?.intOrNull ?: 0
                    val total   = root["ota_total"]?.jsonPrimitive?.intOrNull ?: 0
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
                    _commandAcks.tryEmit("restarting")
                }
                root.containsKey("dmap_saved") -> _commandAcks.tryEmit("dmap_saved")
                root.containsKey("sound_saved") -> _commandAcks.tryEmit("sound_saved")
                root.containsKey("disp_saved")  -> _commandAcks.tryEmit("disp_saved")
                else -> {
                    // Standard 100 ms state broadcast — decode directly from the
                    // already-parsed JsonObject instead of re-parsing the raw text.
                    val state = json.decodeFromJsonElement(TimerStateDto.serializer(), root)
                    _timerState.value = state
                }
            }
        } catch (e: Exception) {
            // Malformed payload — drop it. Avoid logging PII (SSIDs, etc.) in production.
        }
    }

    private fun handleTypedResponse(root: JsonObject) {
        when (root["type"]?.jsonPrimitive?.contentOrNull) {
            "presets" -> {
                try {
                    val response = json.decodeFromJsonElement(PresetsResponse.serializer(), root)
                    _presets.tryEmit(response.list)
                } catch (_: Exception) { /* ignore malformed */ }
            }
            "settings" -> {
                try {
                    val settings = json.decodeFromJsonElement(DeviceSettings.serializer(), root)
                    _settings.tryEmit(settings)
                } catch (_: Exception) { /* ignore malformed */ }
            }
        }
    }
}
