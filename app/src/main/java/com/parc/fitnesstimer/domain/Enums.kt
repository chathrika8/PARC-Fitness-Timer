package com.parc.fitnesstimer.domain

/**
 * Maps to the "state" field in the server JSON broadcast.
 * Values match protocol exactly — do NOT reorder.
 */
enum class TimerRunState(val value: Int, val displayName: String) {
    IDLE(0, "Idle"),
    PRE_START(1, "Get Ready"),
    RUNNING(2, "Running"),
    PAUSED(3, "Paused"),
    TRANSITION(4, "Transition"),
    DONE(5, "Done");

    companion object {
        fun fromInt(v: Int): TimerRunState =
            entries.firstOrNull { it.value == v } ?: IDLE
    }
}

/**
 * Maps to the "mode" field in the server JSON broadcast.
 * Values match protocol exactly — do NOT reorder.
 */
enum class TimerMode(val value: Int, val displayName: String) {
    AMRAP(0, "AMRAP"),
    FOR_TIME(1, "FOR TIME"),
    EMOM(2, "EMOM"),
    TABATA(3, "TABATA"),
    INTERVAL(4, "INTERVAL"),
    COUNTDOWN(5, "COUNTDOWN"),
    STOPWATCH(6, "STOPWATCH");

    /** Default work seconds when switching to this mode. */
    val defaultWorkSecs: Int
        get() = when (this) {
            AMRAP -> 1200        // 20 min AMRAP
            FOR_TIME -> 0       // No cap
            EMOM -> 60          // 1 min per round
            TABATA -> 20        // 20s work
            INTERVAL -> 180     // 3 min work
            COUNTDOWN -> 600    // 10 min countdown
            STOPWATCH -> 0      // N/A
        }

    /** Default rest seconds when switching to this mode. */
    val defaultRestSecs: Int
        get() = when (this) {
            TABATA -> 10
            INTERVAL -> 60
            else -> 0
        }

    /** Default round count when switching to this mode. */
    val defaultRounds: Int
        get() = when (this) {
            EMOM -> 10
            TABATA -> 8
            INTERVAL -> 5
            else -> 0
        }

    /** Whether this mode shows the Round +1 button. */
    val hasManualRound: Boolean
        get() = this == AMRAP || this == FOR_TIME

    /** Whether this mode shows the config panel. */
    val hasConfig: Boolean
        get() = this != STOPWATCH

    companion object {
        fun fromInt(v: Int): TimerMode =
            entries.firstOrNull { it.value == v } ?: AMRAP
    }
}

/**
 * Maps to the "phase" field (work=0, rest=1).
 */
enum class TimerPhase(val value: Int, val displayName: String) {
    WORK(0, "WORK"),
    REST(1, "REST");

    companion object {
        fun fromInt(v: Int): TimerPhase =
            if (v == 0) WORK else REST
    }
}

/**
 * WebSocket connection states used in the UI.
 */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING
}

/**
 * Transport mode configuration (WiFi vs BT).
 */
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

/**
 * Colon behavior settings.
 */
enum class ColonMode(val id: Int, val label: String) {
    ALWAYS_ON(0, "Always on"),
    ALWAYS_OFF(1, "Always off"),
    BLINK_IDLE_PAUSE(2, "Blink when idle / paused"),
    ALWAYS_BLINK(3, "Always blink");

    companion object {
        fun fromId(id: Int) = entries.firstOrNull { it.id == id } ?: BLINK_IDLE_PAUSE
    }
}
