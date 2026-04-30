# PARC Fitness Timer — Android App: Required Changes

**Document purpose:** Exact, exhaustive specification of every change required to bring the Android app into full parity with the current ESP32 firmware. Feed this document directly to Gemini to implement all changes. Every section states *what* is wrong, *why* it is wrong, and *exactly* what the correct implementation must be.

---

## 0. Executive summary of gaps

The app was built against an earlier firmware version. The firmware has since received:

1. **Stopwatch mode** (mode index 6) — entirely missing from the app
2. **Expanded `settings_get` response** — now includes `disp` and `conn` objects; app only handles `dmap` and `bz`
3. **Three new commands** — `display_save`, `conn_set`, and `mode` (cycle mode)
4. **New typed responses** — `disp_saved`, `sound_saved`, `dmap_saved`, `restarting`
5. **Bluetooth SPP transport** — entire new connection layer; app has no BT code
6. **Connection mode management** — BOTH_AUTO / WIFI_ONLY / BT_ONLY / BOTH_ALWAYS
7. **Display behaviour settings** — colon mode, per-mode 3-2-1 countdown control
8. **`pre` field handling** — PRE_START state should show only the countdown digit (3/2/1), all other digits blank
9. **`TRANSITION` state handling** — state 4 must be treated as RUNNING for UI purposes
10. **`mm` leading zero** — minutes field is already zero-suppressed by firmware; app must not re-add leading zero on D2

---

## 1. Data models

### 1.1 `TimerMode` enum — add STOPWATCH

**Current (wrong):**
```kotlin
enum class TimerMode(val id: Int, val displayName: String) {
    AMRAP(0, "AMRAP"),
    FOR_TIME(1, "For Time"),
    EMOM(2, "EMOM"),
    TABATA(3, "Tabata"),
    INTERVAL(4, "Interval"),
    COUNTDOWN(5, "Countdown")
}
```

**Required:**
```kotlin
enum class TimerMode(val id: Int, val displayName: String) {
    AMRAP(0, "AMRAP"),
    FOR_TIME(1, "For Time"),
    EMOM(2, "EMOM"),
    TABATA(3, "Tabata"),
    INTERVAL(4, "Interval"),
    COUNTDOWN(5, "Countdown"),
    STOPWATCH(6, "Stopwatch");          // ADD THIS

    companion object {
        fun fromId(id: Int) = entries.firstOrNull { it.id == id } ?: AMRAP
    }
}
```

### 1.2 `TimerState` enum — add TRANSITION

**Required:**
```kotlin
enum class TimerState(val id: Int) {
    IDLE(0),
    PRE_START(1),
    RUNNING(2),
    PAUSED(3),
    TRANSITION(4),   // ADD — treat same as RUNNING in UI; timer is between phases
    DONE(5);

    companion object {
        fun fromId(id: Int) = entries.firstOrNull { it.id == id } ?: IDLE
    }
}
```

### 1.3 `DeviceSettings` data class — add `disp` and `conn`

**Current (wrong):**
```kotlin
data class DeviceSettings(
    val dmap: List<Int> = listOf(0,1,2,3,4,5),
    val buzzerVol: Int = 8,
    val buzzerEvents: Int = 0x3F,
    val buzzerModes: Int = 0x3F
)
```

**Required:**
```kotlin
data class DeviceSettings(
    // Existing
    val dmap: List<Int> = listOf(0, 1, 2, 3, 4, 5),
    val buzzerVol: Int = 8,
    val buzzerEvents: Int = 0x3F,
    val buzzerModes: Int = 0x3F,
    // NEW — display behaviour
    val colonMode: Int = 2,          // 0=always on  1=always off  2=blink idle/pause  3=always blink
    val countdown321: Int = 0x3F,    // bitmask — bit N = mode N shows 3-2-1 countdown
    // NEW — connection
    val connMode: Int = 0,           // 0=BOTH_AUTO  1=WIFI_ONLY  2=BT_ONLY  3=BOTH_ALWAYS
    val btName: String = "GymTimer",
    val btPin: String = ""           // empty = no PIN
)
```

### 1.4 `TimerUiState` — add `pre` field

The `pre` field (value 3, 2, or 1) is broadcast during `PRE_START` state. The display must show only this digit on D3, all other digits blank.

**Add to existing state data class:**
```kotlin
data class TimerUiState(
    val state: TimerState = TimerState.IDLE,
    val mode: TimerMode = TimerMode.AMRAP,
    val mm: Int = 0,
    val ss: Int = 0,
    val round: Int = 0,
    val phase: Int = 0,       // 0=WORK 1=REST
    val pre: Int = 3,         // ADD — 3/2/1 during PRE_START
    val colon: Boolean = true,
    val work: Int = 720,
    val rest: Int = 60,
    val rounds: Int = 8,
    val mname: String = "AMRAP"
)
```

### 1.5 New `ConnMode` enum

```kotlin
enum class ConnMode(val id: Int, val label: String, val description: String) {
    BOTH_AUTO(0, "Auto (first wins)",
        "Both start. First transport to connect disables the other. Reconnect re-enables both."),
    WIFI_ONLY(1, "WiFi only",
        "Bluetooth never starts. Web dashboard always available."),
    BT_ONLY(2, "Bluetooth only",
        "WiFi AP never starts. Control via this app only."),
    BOTH_ALWAYS(3, "Both always",
        "WiFi and Bluetooth always active. Multiple clients can connect simultaneously.");

    companion object {
        fun fromId(id: Int) = entries.firstOrNull { it.id == id } ?: BOTH_AUTO
    }
}
```

### 1.6 New `ColonMode` enum

```kotlin
enum class ColonMode(val id: Int, val label: String) {
    ALWAYS_ON(0, "Always on"),
    ALWAYS_OFF(1, "Always off"),
    BLINK_IDLE_PAUSE(2, "Blink when idle / paused"),
    ALWAYS_BLINK(3, "Always blink");

    companion object {
        fun fromId(id: Int) = entries.firstOrNull { it.id == id } ?: BLINK_IDLE_PAUSE
    }
}
```

---

## 2. JSON parsing

### 2.1 `settings_get` response parser

The firmware now returns a richer payload. The parser must handle the new fields without breaking on older firmware (treat missing fields as defaults).

**Firmware response (current):**
```json
{
  "type": "settings",
  "dmap": [0, 1, 2, 3, 4, 5],
  "bz": { "vol": 8, "ev": 63, "modes": 63 },
  "disp": { "colon": 2, "c321": 63 },
  "conn": { "mode": 0, "btName": "GymTimer", "btPin": "" }
}
```

**Required parsing logic (Kotlin/kotlinx.serialization or manual JSON):**
```kotlin
fun parseSettings(json: JsonObject): DeviceSettings {
    val bz   = json["bz"]?.jsonObject
    val disp = json["disp"]?.jsonObject
    val conn = json["conn"]?.jsonObject
    return DeviceSettings(
        dmap          = json["dmap"]?.jsonArray?.map { it.jsonPrimitive.int }
                          ?: listOf(0,1,2,3,4,5),
        buzzerVol     = bz?.get("vol")?.jsonPrimitive?.int    ?: 8,
        buzzerEvents  = bz?.get("ev")?.jsonPrimitive?.int     ?: 0x3F,
        buzzerModes   = bz?.get("modes")?.jsonPrimitive?.int  ?: 0x3F,
        colonMode     = disp?.get("colon")?.jsonPrimitive?.int ?: 2,
        countdown321  = disp?.get("c321")?.jsonPrimitive?.int  ?: 0x3F,
        connMode      = conn?.get("mode")?.jsonPrimitive?.int  ?: 0,
        btName        = conn?.get("btName")?.jsonPrimitive?.content ?: "GymTimer",
        btPin         = conn?.get("btPin")?.jsonPrimitive?.content  ?: ""
    )
}
```

### 2.2 New typed response keys to handle

The message router must handle these additional keys in the top-level JSON object:

| Key | When received | Action |
|---|---|---|
| `disp_saved` | After `display_save` command | Show success feedback; no data change |
| `sound_saved` | After `sound_save` command | Show success feedback; no data change |
| `dmap_saved` | After `dmap_save` command | Show success feedback; no data change |
| `restarting` | After `wifi_set`, `conn_set`, or `restart` | Show "Device restarting, reconnecting…" UI state; begin reconnect loop |

**Add to message router:**
```kotlin
"disp_saved"  -> uiEvent(UiEvent.ShowToast("Display settings saved"))
"sound_saved" -> uiEvent(UiEvent.ShowToast("Sound settings saved"))
"dmap_saved"  -> uiEvent(UiEvent.ShowToast("Digit map saved"))
"restarting"  -> handleRestarting()   // close WS, show reconnect UI, start retry loop
```

### 2.3 `pre` field in state broadcast

During `PRE_START` state (state=1), the `pre` field holds the current countdown digit (3, 2, or 1).

**Required display logic:**
```kotlin
fun buildDisplayDigits(state: TimerUiState): DisplayDigits {
    if (state.state == TimerState.PRE_START) {
        // All digits blank except D3 (red, minutes-units position)
        return DisplayDigits(
            d0 = null, d1 = null,   // blue section — blank
            d2 = null, d3 = state.pre,  // red minutes — show countdown digit
            d4 = null, d5 = null,   // red seconds — blank
            colon = false
        )
    }
    // ... normal display logic
}
```

### 2.4 `TRANSITION` state (state=4)

TRANSITION is a 300ms hold between phases. The display shows 0:00 and the colon holds on. The app must not show "Done" or "Idle" UI during this state.

**Required:** in all UI state checks, treat `TRANSITION` identically to `RUNNING`:
```kotlin
val isActive = state == TimerState.RUNNING || state == TimerState.TRANSITION
```

This applies to: button label logic, phase badge visibility, colour scheme, colon behaviour.

---

## 3. Commands to add

### 3.1 `display_save` command

Sent when user saves display behaviour settings.

```kotlin
fun saveDisplaySettings(colonMode: Int, countdown321: Int) {
    sendCommand("""{"cmd":"display_save","colon":$colonMode,"c321":$countdown321}""")
}
```

### 3.2 `conn_set` command

Sent when user saves connection mode settings. Device restarts on receipt.

```kotlin
fun saveConnectionSettings(mode: Int, btName: String, btPin: String) {
    val escapedName = btName.replace("\"", "\\\"")
    val escapedPin  = btPin.replace("\"", "\\\"")
    sendCommand("""{"cmd":"conn_set","mode":$mode,"btName":"$escapedName","btPin":"$escapedPin"}""")
}
```

### 3.3 `mode` command (cycle mode)

The firmware supports cycling through modes via button. The app can also trigger this:

```kotlin
fun cycleMode() {
    sendCommand("""{"cmd":"mode"}""")
}
```

---

## 4. Bluetooth SPP transport

This is the largest new feature. The app must support connecting to the device via Bluetooth Classic (SPP) as an alternative to WiFi. Both transports use **identical JSON protocol** — the BT layer is a pure transport swap.

### 4.1 Required permissions — `AndroidManifest.xml`

```xml
<!-- Bluetooth Classic — required for SPP -->
<uses-permission android:name="android.permission.BLUETOOTH"
    android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN"
    android:maxSdkVersion="30" />
<!-- Android 12+ -->
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
```

**Runtime permission handling (Android 12+):** Request `BLUETOOTH_CONNECT` and `BLUETOOTH_SCAN` at runtime before any BT operation. Use `ActivityResultContracts.RequestMultiplePermissions`.

### 4.2 `TimerConnection` interface

Extract the existing WS logic behind an interface so both transports share the same ViewModel contract:

```kotlin
interface TimerConnection {
    val state: StateFlow<ConnectionState>
    fun connect()
    fun disconnect()
    fun send(json: String)
    fun sendBinary(bytes: ByteArray)  // OTA — WS uses binary frames; BT uses raw bytes on same socket
    val messages: SharedFlow<String>   // incoming JSON lines
}

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED }
```

### 4.3 `WifiTimerConnection` (rename/refactor existing class)

The existing OkHttp WebSocket class implements `TimerConnection`. Rename it and make it implement the interface. No protocol changes needed.

### 4.4 `BluetoothTimerConnection` (new class)

```kotlin
class BluetoothTimerConnection(
    private val context: Context,
    private val deviceName: String = "GymTimer"
) : TimerConnection {

    // SPP UUID — standard Serial Port Profile
    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private var socket: BluetoothSocket? = null
    private var readerJob: Job? = null
    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 64)

    override val state: StateFlow<ConnectionState> = _state
    override val messages: SharedFlow<String> = _messages

    override fun connect() {
        // 1. Get BluetoothAdapter — show "enable Bluetooth" dialog if off
        // 2. Find bonded device matching deviceName
        //    If not found: show "Pair GymTimer in Bluetooth settings" instruction screen
        // 3. socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
        // 4. socket.connect()  — runs on IO dispatcher
        // 5. On success: _state = CONNECTED, launch readerJob
        // 6. On failure: exponential backoff (1.5s → 10s cap), retry
    }

    override fun send(json: String) {
        // Write UTF-8 bytes + newline to socket.outputStream
        socket?.outputStream?.write((json + "\n").toByteArray(Charsets.UTF_8))
    }

    override fun sendBinary(bytes: ByteArray) {
        // For OTA: raw bytes directly to outputStream (no framing — BT is a raw stream)
        socket?.outputStream?.write(bytes)
    }

    private fun startReader() {
        readerJob = scope.launch(Dispatchers.IO) {
            val reader = socket!!.inputStream.bufferedReader(Charsets.UTF_8)
            try {
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    line?.let { _messages.emit(it) }
                }
            } catch (e: IOException) {
                _state.value = ConnectionState.DISCONNECTED
                reconnect()  // exponential backoff
            }
        }
    }

    override fun disconnect() {
        readerJob?.cancel()
        socket?.close()
        _state.value = ConnectionState.DISCONNECTED
    }
}
```

**Critical detail — finding the paired device:**
```kotlin
val adapter = BluetoothAdapter.getDefaultAdapter()
val device = adapter.bondedDevices.firstOrNull { it.name == deviceName }
    ?: throw DeviceNotPairedException("$deviceName not found in paired devices. " +
       "Go to Bluetooth settings and pair it first.")
```

If the device is not paired, show an instruction screen: *"Pair 'GymTimer' in your phone's Bluetooth settings, then return here."* Do not attempt to initiate pairing programmatically — Android requires the user to do this from system settings.

### 4.5 OTA over BT

BT OTA uses the same `ota_begin` JSON text command then raw binary bytes — no WebSocket framing. The `sendBinary` method writes directly to the output stream.

```kotlin
// In OTA ViewModel:
suspend fun flashFirmware(bytes: ByteArray) {
    connection.send("""{"cmd":"ota_begin","size":${bytes.size}}""")
    // Wait for {"ota_ready":true} via messages flow
    val CHUNK = 4096
    var offset = 0
    while (offset < bytes.size) {
        val end = minOf(offset + CHUNK, bytes.size)
        connection.sendBinary(bytes.copyOfRange(offset, end))
        offset = end
        // Progress comes back as {"ota_prog":N,"ota_total":M} via messages flow
    }
}
```

### 4.6 Connection selection UI

Add a screen (or bottom sheet) shown when the app launches and no connection is active:

```
┌────────────────────────────────┐
│  Connect to GymTimer           │
│                                │
│  [  WiFi  ]  [ Bluetooth ]     │
│                                │
│  WiFi: connects to 192.168.4.1 │
│  BT:   uses paired "GymTimer"  │
└────────────────────────────────┘
```

- WiFi tab: shows IP input field (default 192.168.4.1), Connect button, and WiFi network connection helper
- BT tab: shows paired device status, "Open Bluetooth Settings" button if not paired, Connect button

Store the last-used transport and IP in `DataStore` and attempt auto-connect on launch.

### 4.7 `TimerRepository` — dual transport

```kotlin
class TimerRepository @Inject constructor(
    private val wifiConnection: WifiTimerConnection,
    private val btConnection: BluetoothTimerConnection,
    private val prefs: DataStore<Preferences>
) {
    private var activeConnection: TimerConnection = wifiConnection

    fun useWifi() { activeConnection = wifiConnection }
    fun useBluetooth() { activeConnection = btConnection }

    fun connect() = activeConnection.connect()
    fun send(json: String) = activeConnection.send(json)
    // etc.
}
```

---

## 5. New settings screens

### 5.1 Display Settings screen

New section in the existing Device/Settings screen (or a dedicated sub-screen).

**Colon behaviour** — radio group (single select):
```
○ Always on
○ Always off
● Blink when idle / paused    ← default
○ Always blink
```
Maps to `colonMode` integer (0–3). On save: `sendCommand({"cmd":"display_save","colon":<value>,"c321":<c321Value>})`

**3-2-1 countdown per mode** — toggle list:
```
AMRAP          [toggle ON]
For Time       [toggle ON]
EMOM           [toggle ON]
Tabata         [toggle ON]
Interval       [toggle ON]
Countdown      [toggle ON]
(Stopwatch always OFF — firmware ignores bit 6)
```
Maps to `countdown321` bitmask (bit 0 = AMRAP, bit 1 = FOR TIME, …, bit 5 = COUNTDOWN).
On toggle: update local bitmask. On save: send with colonMode together in one `display_save` command.

**Save button** fires `display_save`. Show snackbar "Display settings saved" on `disp_saved` response.

### 5.2 Connection Settings screen

New section in Device/Settings.

**Connection mode** — radio group:
```
● Auto (first wins)
  Both transports start. First to connect disables the other. Reconnect re-enables both.
○ WiFi only
  Bluetooth never starts.
○ Bluetooth only
  WiFi AP never starts.
○ Both always
  Both always active. Multiple clients can connect.
```

**Bluetooth device name:**
```
[  GymTimer          ]
```

**Bluetooth PIN:**
```
[  (empty = no PIN)  ]
Numeric keyboard, max 8 digits
```

**Apply & Restart button** — sends `conn_set` command. Immediately shows reconnect UI since device restarts. After restart, auto-reconnect using the saved transport preference.

**Important:** After sending `conn_set`:
- If new mode is `BT_ONLY` (2), switch active transport to BT and reconnect
- If new mode is `WIFI_ONLY` (1), switch active transport to WiFi and reconnect
- If `BOTH_AUTO` or `BOTH_ALWAYS`, keep current transport

### 5.3 Sound Settings — existing screen updates

The existing sound settings screen is correct for `buzzerVol`, `buzzerEvents`, `buzzerModes`. Confirm the save button sends `sound_save` and handles `sound_saved` response with snackbar confirmation.

**Verify the event bitmask labels match firmware:**
| Bit | Event | Label in UI |
|---|---|---|
| 0 | `BUZZ_321` | 3-2-1 countdown pips |
| 1 | `BUZZ_GO` | GO tone (start) |
| 2 | `BUZZ_INTERVAL` | Phase transition |
| 3 | `BUZZ_DONE` | Workout complete |
| 4 | `BUZZ_WARNING` | Last 10s warning |
| 5 | `BUZZ_PAUSE` | Pause / resume click |

### 5.4 WiFi settings — handle `restarting` response

Current implementation likely does not handle `{"restarting":true}`. After `wifi_set`:
1. Show "Device restarting, reconnecting…" in the connection status area
2. Close the WebSocket immediately (the device is restarting, the connection will drop)
3. Start the reconnect loop with a 3-second initial delay (device takes ~2s to reboot)
4. Same handling must apply after `conn_set` and `restart` commands

---

## 6. Virtual display corrections

### 6.1 Stopwatch mode (mode 6) — display behaviour

Stopwatch has no round counter. D0 and D1 must be blank. D2D3:D4D5 shows elapsed time counting up from 0:00.

```kotlin
val showRound = when (state.mode) {
    TimerMode.COUNTDOWN, TimerMode.STOPWATCH -> false
    else -> true
}
```

### 6.2 Leading zero suppression — match firmware exactly

The firmware already suppresses leading zeros:
- **D0** (round tens): blank when `round < 10`
- **D2** (minutes tens): blank when `mm < 10`
- **D2 and D3 both blank** when `mm == 0` (sub-60s phases — only seconds shown)

The app must not re-add suppressed zeros. Map `mm` and `ss` directly:

```kotlin
val minutesTens  = if (state.mm >= 10) state.mm / 10 else null   // null = blank
val minutesUnits = if (state.mm > 0)   state.mm % 10 else null   // null when mm=0 — only seconds shown
val secondsTens  = state.ss / 10   // always shown (can be 0)
val secondsUnits = state.ss % 10   // always shown
```

Wait — `minutesUnits` logic: when `mm == 0`, the firmware blanks both D2 and D3. When `mm > 0`, D2 is blank if tens is 0, D3 shows units. Exact mapping:

```kotlin
// mm=0  → D2=blank, D3=blank (only seconds shown)
// mm=5  → D2=blank, D3=5
// mm=12 → D2=1,     D3=2
val d2: Int? = if (state.mm >= 10) state.mm / 10 else null
val d3: Int? = if (state.mm > 0)   state.mm % 10 else null
val d4: Int  = state.ss / 10   // never blank
val d5: Int  = state.ss % 10   // never blank
```

### 6.3 PRE_START display

During `state == PRE_START`, the firmware shows only the `pre` digit (3, 2, or 1) on D3. All other digits are blank, colon is off.

```kotlin
if (state.state == TimerState.PRE_START) {
    // D0=blank, D1=blank (blue)
    // D2=blank, D3=pre value (red)
    // colon=off
    // D4=blank, D5=blank (red)
}
```

The `pre` field counts down: on first tick it's 3, then 2, then 1, then RUNNING begins. The UI should show this animated countdown clearly — consider a large centred number overlay on the virtual display rather than showing it only in the tiny D3 segment.

### 6.4 Colon blink logic

The `colon` field in the state broadcast is `true` when running/transition/done, `false` when idle/paused. When `colon` is `false`, the app should blink the colon client-side at 500ms interval (same as web dashboard). When `colon` is `true`, show solid.

```kotlin
// In a LaunchedEffect observing timerState:
LaunchedEffect(timerState.colon) {
    if (!timerState.colon) {
        while (true) {
            colonVisible = !colonVisible
            delay(500)
        }
    } else {
        colonVisible = true
    }
}
```

---

## 7. Timer configuration — Stopwatch has no config

The config panel must hide entirely for Stopwatch mode:

```kotlin
val showConfigPanel = state.mode != TimerMode.STOPWATCH
```

Stopwatch has no `work`, `rest`, or `rounds` — sending a `cfg` command for Stopwatch is harmless but pointless. The "Apply Config" and "Save as Preset" buttons should also be hidden.

---

## 8. Timer mode behaviour — app-side changes

### 8.1 AMRAP and For Time — show Round +1 button

```kotlin
val showRoundInc = state.mode == TimerMode.AMRAP || state.mode == TimerMode.FOR_TIME
```

### 8.2 Mode switch confirmation while running

If the user taps a different mode chip while `state == RUNNING || PAUSED || TRANSITION`:
```kotlin
showDialog(
    title = "Switch to ${newMode.displayName}?",
    message = "A workout is in progress. This will reset the timer.",
    confirm = { sendCommand("""{"cmd":"cfg","mode":${newMode.id},...}""") }
)
```

### 8.3 EMOM config — no minutes field

For EMOM mode, the config form shows only `work` (round duration in seconds) and `rounds`. No minutes shorthand for EMOM — the firmware removed it deliberately.

### 8.4 Sensible defaults per mode when switching

When the user selects a mode, pre-populate the config form with these defaults (send via `cfg` command when mode tab is tapped):

| Mode | work | rest | rounds |
|---|---|---|---|
| AMRAP | 720 | 0 | 0 |
| For Time | 0 | 0 | 0 |
| EMOM | 60 | 0 | 10 |
| Tabata | 20 | 10 | 8 |
| Interval | 60 | 30 | 5 |
| Countdown | 600 | 0 | 0 |
| Stopwatch | 0 | 0 | 0 |

---

## 9. Digit remapping UI

The app spec mentions `dmap_save` but the README doesn't describe whether a remapping UI is implemented. If not, it must be added to the Device settings screen.

The physical display has 6 digits in fixed positions. The chip wiring may not match the logical order (this was the bug that caused seconds to appear in the blue section). The remapping allows fixing this without hardware changes.

**Required UI:**
- 6 tiles displayed in a row representing physical positions P0–P5
- Each tile shows which logical role it currently maps to:
  - Role labels: "R×10", "R×1", "M×10", "M×1", "S×10", "S×1" (Round tens/units, Minute tens/units, Second tens/units)
  - Colour: blue for R×10, R×1; red for M×10, M×1, S×10, S×1
- Tap to select a tile, tap another to swap (same drag-to-reorder logic as web dashboard)
- Colon separator shown between position 3 and 4 (fixed hardware position)
- Reset to default [0,1,2,3,4,5] button
- Save button → sends `{"cmd":"dmap_save","map":[p0,p1,p2,p3,p4,p5]}`
- Show snackbar "Digit map saved" on `dmap_saved` response

---

## 10. `build.gradle` / dependency additions

```kotlin
// Bluetooth — no new dependency needed (uses Android SDK classes)

// If not already present, ensure these are included:
implementation("androidx.datastore:datastore-preferences:1.0.0")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

// File picker for OTA (if not already implemented):
// No additional dep needed — use ActivityResultContracts.GetContent with "application/octet-stream"
```

---

## 11. `AndroidManifest.xml` — complete permissions block

Replace/extend the existing manifest permissions:

```xml
<!-- WiFi — connecting to specific AP -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
<!-- Required on Android 10+ to connect to specific SSID programmatically -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />

<!-- Bluetooth Classic (SPP) -->
<!-- Pre-Android 12 -->
<uses-permission android:name="android.permission.BLUETOOTH"
    android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN"
    android:maxSdkVersion="30" />
<!-- Android 12+ -->
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"
    android:usesPermissionFlags="neverForLocation" />

<!-- OTA — reading .bin files from storage -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />
<!-- Android 13+ scoped storage -->
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
```

**Runtime permission request sequence:**
1. On app launch: request `ACCESS_FINE_LOCATION` (required for WiFi SSID-specific connect on Android 10+)
2. Before any BT operation: request `BLUETOOTH_CONNECT` + `BLUETOOTH_SCAN` (Android 12+)
3. Before OTA file picker: no runtime permission needed (SAF handles it)

---

## 12. Reconnect behaviour after restart

Both `wifi_set`, `conn_set`, and `restart` commands trigger `{"restarting":true}` from firmware, followed by device reboot (~2 seconds). The app must handle this correctly:

```kotlin
fun handleRestarting() {
    _connectionState.value = ConnectionState.DISCONNECTED
    showReconnectingBanner("Device restarting…")
    // Wait 3 seconds before first reconnect attempt (device boot time)
    viewModelScope.launch {
        delay(3000)
        connection.connect()  // existing exponential backoff handles subsequent retries
    }
}
```

---

## 13. Summary checklist for Gemini

Use this as the implementation task list:

- [x] Add `STOPWATCH` to `TimerMode` enum (id=6)
- [x] Add `TRANSITION` to `TimerState` enum (id=4), treat as RUNNING in all UI checks
- [x] Add `pre` field to `TimerUiState`
- [x] Add `colonMode`, `countdown321`, `connMode`, `btName`, `btPin` to `DeviceSettings`
- [x] Add `ConnMode` enum (0–3)
- [x] Add `ColonMode` enum (0–3)
- [x] Update `settings_get` response parser to extract `disp` and `conn` objects
- [x] Add handlers for `disp_saved`, `sound_saved`, `dmap_saved`, `restarting` responses
- [x] Add `display_save` command sender
- [x] Add `conn_set` command sender
- [x] Add Bluetooth manifest permissions (pre-12 and post-12)
- [x] Add runtime Bluetooth permission request flow
- [x] Create `TimerConnection` interface
- [x] Refactor existing OkHttp WS class to implement `TimerConnection`
- [x] Implement `BluetoothTimerConnection` (SPP, UUID `00001101-0000-1000-8000-00805F9B34FB`)
- [x] Implement BT device pairing detection with "go to system settings" fallback
- [x] Implement OTA over BT (raw bytes via `sendBinary`)
- [x] Add connection selection screen (WiFi vs BT, IP input, pairing status)
- [x] Update `TimerRepository` to support dual transport with `useWifi()` / `useBluetooth()`
- [x] Add Display Settings section (colon mode radio, per-mode countdown toggles)
- [x] Add Connection Settings section (mode radio, BT name input, BT PIN input)
- [x] Fix `restarting` response handling with 3s delay before reconnect
- [x] Fix virtual display: blank D2+D3 when `mm == 0`
- [x] Fix virtual display: blank D0 when round < 10, blank D2 when mm < 10
- [x] Fix virtual display: PRE_START shows only `pre` digit on D3, all others blank, colon off
- [x] Fix colon blink: client-side 500ms blink when `colon == false` from server
- [x] Hide config panel entirely for Stopwatch mode
- [x] Show Round +1 only for AMRAP and For Time
- [x] Add mode-switch confirmation dialog when timer is running
- [x] Fix EMOM config form to show only `work` and `rounds` (no minutes field)
- [x] Add sensible per-mode config defaults when switching modes
- [x] Implement digit remapping UI (6 swappable tiles, `dmap_save` command, `dmap_saved` handler)
- [x] Verify sound event bitmask labels match firmware (6 events, bits 0–5)
- [x] Persist last-used transport (WiFi/BT) and IP in DataStore
