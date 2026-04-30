package com.parc.fitnesstimer.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Buzzer configuration from server settings response.
 *
 * @param vol  Master volume 1–10
 * @param ev   Event bitmask (bit 0 = 3-2-1 pip, 1 = GO tone, 2 = phase transition,
 *             3 = workout done, 4 = last-10s warning, 5 = pause click)
 * @param modes Mode bitmask (which modes produce any sound)
 */
@Serializable
data class BuzzConfig(
    val vol: Int = 8,
    val ev: Int = 63,
    val modes: Int = 63
) {
    // Convenience getters for each event bit
    val pip321: Boolean get() = (ev shr 0) and 1 == 1
    val goTone: Boolean get() = (ev shr 1) and 1 == 1
    val phaseTransition: Boolean get() = (ev shr 2) and 1 == 1
    val workoutDone: Boolean get() = (ev shr 3) and 1 == 1
    val last10sWarning: Boolean get() = (ev shr 4) and 1 == 1
    val pauseClick: Boolean get() = (ev shr 5) and 1 == 1

    fun withPip321(on: Boolean) = copy(ev = ev.setBit(0, on))
    fun withGoTone(on: Boolean) = copy(ev = ev.setBit(1, on))
    fun withPhaseTransition(on: Boolean) = copy(ev = ev.setBit(2, on))
    fun withWorkoutDone(on: Boolean) = copy(ev = ev.setBit(3, on))
    fun withLast10s(on: Boolean) = copy(ev = ev.setBit(4, on))
    fun withPauseClick(on: Boolean) = copy(ev = ev.setBit(5, on))

    private fun Int.setBit(bit: Int, on: Boolean): Int =
        if (on) this or (1 shl bit) else this and (1 shl bit).inv()
}

/**
 * Display configuration from server settings response.
 */
@Serializable
data class DispConfig(
    val colon: Int = 2,
    val c321: Int = 63
)

/**
 * Connection configuration from server settings response.
 */
@Serializable
data class ConnConfig(
    val mode: Int = 0,
    val btName: String = "GymTimer",
    val btPin: String = ""
)

/**
 * Full device settings response from server.
 *
 * @param dmap Physical digit position → logical digit mapping (length 6)
 * @param bz   Buzzer configuration
 * @param disp Display configuration
 * @param conn Connection configuration
 */
@Serializable
data class DeviceSettings(
    val type: String = "settings",
    val dmap: List<Int> = listOf(0, 1, 2, 3, 4, 5),
    @SerialName("bz") val bz: BuzzConfig = BuzzConfig(),
    @SerialName("disp") val disp: DispConfig = DispConfig(),
    @SerialName("conn") val conn: ConnConfig = ConnConfig()
) {
    /** Human-readable label for each logical digit position. */
    companion object {
        val DIGIT_LABELS = listOf("RR tens", "RR units", "MM tens", "MM units", "SS tens", "SS units")
    }
}

/** Sealed class covering all OTA progress events received from the server. */
sealed class OtaEvent {
    object Ready : OtaEvent()
    data class Progress(val written: Int, val total: Int) : OtaEvent()
    object Done : OtaEvent()
    data class Error(val message: String) : OtaEvent()
    object Restarting : OtaEvent()
}
