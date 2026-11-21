package com.slm.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode {
    SYSTEM,
    DARK,
    LIGHT,
}

enum class TransportMode {
    BLUETOOTH,
    WIFI,
}

enum class MouseButtonsPosition {
    TOP,
    BOTTOM,
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val transportMode: TransportMode = TransportMode.WIFI,
    val mouseButtonsPosition: MouseButtonsPosition = MouseButtonsPosition.BOTTOM,
    val pointerSensitivity: Float = 1.4f,
    val scrollSensitivity: Float = 1.2f,
    val pointerAcceleration: Boolean = true,
    val naturalScroll: Boolean = false,
    val pollingRateHz: Int = 125,
    val hapticFeedback: Boolean = true,
    val leftHandedMode: Boolean = false,
    val autoConnect: Boolean = true,
    val permissionsOnboardingDone: Boolean = false,
    val lastConnectedAddress: String? = null,
    val lastConnectedName: String? = null,
    val lastWifiHostIp: String? = null,
    val lastWifiHostName: String? = null,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "bt_mouse_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val TRANSPORT_MODE = stringPreferencesKey("transport_mode")
        val MOUSE_BUTTONS_POSITION = stringPreferencesKey("mouse_buttons_position")
        val POINTER_SENSITIVITY = floatPreferencesKey("pointer_sensitivity")
        val SCROLL_SENSITIVITY = floatPreferencesKey("scroll_sensitivity")
        val POINTER_ACCELERATION = booleanPreferencesKey("pointer_acceleration")
        val NATURAL_SCROLL = booleanPreferencesKey("natural_scroll")
        val POLLING_RATE_HZ = intPreferencesKey("polling_rate_hz")
        val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
        val LEFT_HANDED_MODE = booleanPreferencesKey("left_handed_mode")
        val AUTO_CONNECT = booleanPreferencesKey("auto_connect")
        val PERMISSIONS_ONBOARDING_DONE = booleanPreferencesKey("permissions_onboarding_done")
        val LAST_ADDRESS = stringPreferencesKey("last_connected_address")
        val LAST_NAME = stringPreferencesKey("last_connected_name")
        val LAST_WIFI_IP = stringPreferencesKey("last_wifi_host_ip")
        val LAST_WIFI_NAME = stringPreferencesKey("last_wifi_host_name")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val themeStr = prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name
        val themeMode = runCatching { ThemeMode.valueOf(themeStr) }.getOrDefault(ThemeMode.SYSTEM)
        val transportStr = prefs[Keys.TRANSPORT_MODE] ?: TransportMode.WIFI.name
        val transportMode = runCatching { TransportMode.valueOf(transportStr) }.getOrDefault(TransportMode.WIFI)
        val buttonsPosStr = prefs[Keys.MOUSE_BUTTONS_POSITION] ?: MouseButtonsPosition.BOTTOM.name
        val buttonsPosition = runCatching { MouseButtonsPosition.valueOf(buttonsPosStr) }.getOrDefault(MouseButtonsPosition.BOTTOM)
        val polling = prefs[Keys.POLLING_RATE_HZ] ?: 125
        val validPolling = if (polling in listOf(60, 125, 250)) polling else 125

        AppSettings(
            themeMode = themeMode,
            transportMode = transportMode,
            mouseButtonsPosition = buttonsPosition,
            pointerSensitivity = (prefs[Keys.POINTER_SENSITIVITY] ?: 1.4f).coerceIn(0.4f, 3.5f),
            scrollSensitivity = (prefs[Keys.SCROLL_SENSITIVITY] ?: 1.2f).coerceIn(0.4f, 3.0f),
            pointerAcceleration = prefs[Keys.POINTER_ACCELERATION] ?: true,
            naturalScroll = prefs[Keys.NATURAL_SCROLL] ?: false,
            pollingRateHz = validPolling,
            hapticFeedback = prefs[Keys.HAPTIC_FEEDBACK] ?: true,
            leftHandedMode = prefs[Keys.LEFT_HANDED_MODE] ?: false,
            autoConnect = prefs[Keys.AUTO_CONNECT] ?: true,
            permissionsOnboardingDone = prefs[Keys.PERMISSIONS_ONBOARDING_DONE] ?: false,
            lastConnectedAddress = prefs[Keys.LAST_ADDRESS],
            lastConnectedName = prefs[Keys.LAST_NAME],
            lastWifiHostIp = prefs[Keys.LAST_WIFI_IP],
            lastWifiHostName = prefs[Keys.LAST_WIFI_NAME],
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setTransportMode(mode: TransportMode) {
        context.dataStore.edit { it[Keys.TRANSPORT_MODE] = mode.name }
    }

    suspend fun setMouseButtonsPosition(position: MouseButtonsPosition) {
        context.dataStore.edit { it[Keys.MOUSE_BUTTONS_POSITION] = position.name }
    }

    suspend fun setPointerSensitivity(value: Float) {
        context.dataStore.edit { it[Keys.POINTER_SENSITIVITY] = value.coerceIn(0.4f, 3.5f) }
    }

    suspend fun setScrollSensitivity(value: Float) {
        context.dataStore.edit { it[Keys.SCROLL_SENSITIVITY] = value.coerceIn(0.4f, 3.0f) }
    }

    suspend fun setPointerAcceleration(enabled: Boolean) {
        context.dataStore.edit { it[Keys.POINTER_ACCELERATION] = enabled }
    }

    suspend fun setNaturalScroll(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NATURAL_SCROLL] = enabled }
    }

    suspend fun setPollingRateHz(hz: Int) {
        context.dataStore.edit { it[Keys.POLLING_RATE_HZ] = hz }
    }

    suspend fun setHapticFeedback(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HAPTIC_FEEDBACK] = enabled }
    }

    suspend fun setLeftHandedMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.LEFT_HANDED_MODE] = enabled }
    }

    suspend fun setAutoConnect(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_CONNECT] = enabled }
    }

    suspend fun setPermissionsOnboardingDone(done: Boolean) {
        context.dataStore.edit { it[Keys.PERMISSIONS_ONBOARDING_DONE] = done }
    }

    suspend fun setLastConnectedDevice(address: String?, name: String?) {
        context.dataStore.edit {
            if (address != null) it[Keys.LAST_ADDRESS] = address else it.remove(Keys.LAST_ADDRESS)
            if (name != null) it[Keys.LAST_NAME] = name else it.remove(Keys.LAST_NAME)
        }
    }

    suspend fun setLastWifiHost(ip: String?, name: String?) {
        context.dataStore.edit {
            if (ip != null) it[Keys.LAST_WIFI_IP] = ip else it.remove(Keys.LAST_WIFI_IP)
            if (name != null) it[Keys.LAST_WIFI_NAME] = name else it.remove(Keys.LAST_WIFI_NAME)
        }
    }
}
