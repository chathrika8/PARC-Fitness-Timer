package com.parc.fitnesstimer.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin DataStore wrapper that persists app-level preferences relevant to
 * WiFi connection management. All keys are typed; no raw string access.
 */
@Singleton
class AppPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        private val KEY_LAST_IP   = stringPreferencesKey("last_ip")
        private val KEY_LAST_SSID = stringPreferencesKey("last_ssid")
        private val KEY_MANUAL_IP = stringPreferencesKey("manual_ip")
        private val KEY_LAST_TRANSPORT = androidx.datastore.preferences.core.intPreferencesKey("last_transport")

        const val DEFAULT_IP   = "192.168.4.1"
        const val DEFAULT_SSID = "GymTimer"
        const val DEFAULT_PORT = 81
    }

    /** IP address of the last successfully connected device. */
    val lastIp: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_LAST_IP] ?: ""
    }

    /** SSID of the last AP the user connected to. */
    val lastSsid: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_LAST_SSID] ?: DEFAULT_SSID
    }

    /**
     * Optional user-supplied IP override (for non-standard AP configs).
     * Empty string = not set, use lastIp or default.
     */
    val manualIp: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_MANUAL_IP] ?: ""
    }

    /** Last used transport (0 = WiFi, 1 = Bluetooth). */
    val lastTransport: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_LAST_TRANSPORT] ?: 0
    }

    suspend fun saveLastIp(ip: String) {
        dataStore.edit { it[KEY_LAST_IP] = ip }
    }

    suspend fun saveLastSsid(ssid: String) {
        dataStore.edit { it[KEY_LAST_SSID] = ssid }
    }

    suspend fun saveManualIp(ip: String) {
        dataStore.edit { it[KEY_MANUAL_IP] = ip }
    }

    suspend fun saveLastTransport(transport: Int) {
        dataStore.edit { it[KEY_LAST_TRANSPORT] = transport }
    }

    suspend fun clearLastIp() {
        dataStore.edit { it.remove(KEY_LAST_IP) }
    }

    /** Build a WebSocket URL from a given IP using the fixed port 81. */
    fun buildWsUrl(ip: String): String = "ws://$ip:$DEFAULT_PORT"
}
