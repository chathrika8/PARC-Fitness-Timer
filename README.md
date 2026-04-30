# PARC Fitness Timer

An Android fitness timer application built with Kotlin and Jetpack Compose. This application acts as a remote control and real-time display for a custom ESP32-based hardware timer over a local Wi-Fi network.

---

## 🔌 Technical Specification & WebSocket Protocol

If you are building the hardware from the ground up or writing your own client, this section contains everything you need to interface with the timer.

### Connection Details
- **Protocol**: WebSocket (`ws://`)
- **Default ESP32 AP IP**: `192.168.4.1`
- **Default SSID**: `GymTimer`
- **Port**: `81`
- **Connection URL**: `ws://<ESP_IP>:81`

The server does not use TLS, so cleartext traffic is permitted on the local network. The app maintains a persistent connection with an exponential backoff strategy (1.5s to 10s) upon failure.

---

### Commands (App ➔ ESP32)

All commands are sent as plain text JSON frames, with the exception of OTA binary chunks.

#### 1. Playback Controls
- **Start/Resume**: `{"cmd":"start"}`
- **Pause**: `{"cmd":"pause"}`
- **Reset**: `{"cmd":"reset"}`
- **Increment Round (Manual)**: `{"cmd":"rinc"}`

#### 2. Configuration
- **Set Timer Config**: `{"cmd":"cfg", "mode":<int>, "work":<int>, "rest":<int>, "rounds":<int>}`
  *See Enums section below for mode mappings.*

#### 3. Presets Management
- **Get Presets**: `{"cmd":"presets_get"}`
- **Save Preset**: `{"cmd":"preset_save", "slot":<int>, "name":"<string>"}`
- **Load Preset**: `{"cmd":"preset_load", "slot":<int>}`
- **Delete Preset**: `{"cmd":"preset_del", "slot":<int>}`

#### 4. Device Settings
- **Get Settings**: `{"cmd":"settings_get"}`
- **Save Display Map (Digit Wiring)**: `{"cmd":"dmap_save", "map":[0,1,2,3,4,5]}`
- **Save Sound Settings**: `{"cmd":"sound_save", "vol":<int>, "ev":<int>, "modes":<int>}`
  - `vol`: 1-10
  - `ev`: Bitmask (bit 0=3-2-1 pip, 1=GO tone, 2=phase transition, 3=workout done, 4=last-10s warning, 5=pause click)
  - `modes`: Bitmask defining which timer modes produce sound.

#### 5. System
- **Set Wi-Fi Credentials**: `{"cmd":"wifi_set", "ssid":"<string>", "pass":"<string>"}`
- **Restart Device**: `{"cmd":"restart"}`

#### 6. OTA Firmware Updates
1. Send Init: `{"cmd":"ota_begin", "size":<total_bytes>}`
2. Wait for ESP to broadcast `{"ota_ready": true}`
3. Send Firmware: Stream raw firmware binary in chunks using **Binary WebSocket Frames** (opcode `0x02`). *Do not use text frames.*

---

### Broadcasts & Responses (ESP32 ➔ App)

The ESP32 broadcasts its state continuously and replies to specific fetch commands.

#### 1. Live State Broadcast (Every 100ms)
The hardware broadcasts the exact timer state 10 times a second.
```json
{
  "state": 0,
  "mode": 0,
  "mname": "AMRAP",
  "mm": 0,
  "ss": 0,
  "round": 0,
  "phase": 0,
  "pre": 3,
  "colon": true,
  "work": 720,
  "rest": 60,
  "rounds": 8
}
```

#### 2. Typed Responses (Polled)
Triggered by `presets_get` or `settings_get`. Identified by the `type` key.

**Presets List:**
```json
{
  "type": "presets",
  "list": [
    {
      "slot": 0,
      "name": "Morning HIIT",
      "mode": 3,
      "work": 20,
      "rest": 10,
      "rounds": 8
    }
  ]
}
```

**Device Settings:**
```json
{
  "type": "settings",
  "dmap": [0, 1, 2, 3, 4, 5],
  "bz": {
    "vol": 8,
    "ev": 63,
    "modes": 63
  }
}
```

#### 3. OTA Events
- **Ready to receive**: `{"ota_ready": true}`
- **Progress update**: `{"ota_prog": <bytes_written>, "ota_total": <total_bytes>}`
- **Success**: `{"ota_done": true}`
- **Error**: `{"ota_err": "Error message"}`
- **Rebooting**: `{"restarting": true}`

---

### Enumerations Reference

**`state` (Run State):**
- `0`: Idle
- `1`: Pre-start (Get Ready countdown)
- `2`: Running
- `3`: Paused
- `4`: Transition
- `5`: Done

**`mode` (Timer Mode):**
- `0`: AMRAP
- `1`: FOR TIME
- `2`: EMOM
- `3`: TABATA
- `4`: INTERVAL
- `5`: COUNTDOWN
- `6`: STOPWATCH

**`phase`:**
- `0`: Work
- `1`: Rest

---

## 🛠 App Architecture & UI Stack
- **Language**: 100% Kotlin.
- **UI Framework**: Jetpack Compose.
- **Architecture**: MVVM with Unidirectional Data Flow using `StateFlow` and `SharedFlow`.
- **Dependency Injection**: Hilt.
- **Networking**: OkHttp for WebSocket connection handling.
- **Digital LED Replica**: The app dynamically computes a 6-digit 7-segment LED display replica. It intelligently colors the round counters in blue and the minute/second digits in red, mimicking the physical hardware.

## Requirements
- Minimum SDK: 26
- Target SDK: 34
- Android Studio Ladybug or newer.

## Building and Running
1. Open the project in Android Studio.
2. Wait for Gradle sync to complete.
3. Build and Run the `app` configuration on your emulator or physical device.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
