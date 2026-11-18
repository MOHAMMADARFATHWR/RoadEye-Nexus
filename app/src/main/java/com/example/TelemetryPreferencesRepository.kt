package com.example

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.telemetryDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "roadeye_nexus_settings",
)

data class TelemetrySettings(
    val deviceId: String = TelemetrySnapshot.DEFAULT_DEVICE_ID,
    val targetIp: String = UdpTelemetryStreamer.DEFAULT_IP,
    val targetPort: Int = UdpTelemetryStreamer.DEFAULT_PORT,
    val streamRateHz: Int = 20,
    val obdPollDelayMs: Long = 20L,
    val autoReconnectObd: Boolean = true,
    val lastObdDeviceName: String = "",
    val lastObdDeviceAddress: String = "",
    val pureOledBlack: Boolean = false,
    val acousticSensingEnabled: Boolean = true,
    val accelBiasX: Float = 0f,
    val accelBiasY: Float = 0f,
    val gyroBiasX: Float = 0f,
    val gyroBiasY: Float = 0f,
    val gyroBiasZ: Float = 0f,
)

class TelemetryPreferencesRepository(context: Context) {
    private val dataStore = context.applicationContext.telemetryDataStore

    val settingsFlow: Flow<TelemetrySettings> = dataStore.data.map { prefs ->
        TelemetrySettings(
            deviceId = prefs[KEY_DEVICE_ID]?.takeIf { it.isNotBlank() }
                ?: TelemetrySnapshot.DEFAULT_DEVICE_ID,
            targetIp = prefs[KEY_TARGET_IP] ?: UdpTelemetryStreamer.DEFAULT_IP,
            targetPort = prefs[KEY_TARGET_PORT] ?: UdpTelemetryStreamer.DEFAULT_PORT,
            streamRateHz = prefs[KEY_STREAM_RATE_HZ] ?: 20,
            obdPollDelayMs = prefs[KEY_OBD_POLL_DELAY_MS] ?: 20L,
            autoReconnectObd = prefs[KEY_AUTO_RECONNECT_OBD] ?: true,
            lastObdDeviceName = prefs[KEY_LAST_OBD_NAME] ?: "",
            lastObdDeviceAddress = prefs[KEY_LAST_OBD_ADDRESS] ?: "",
            pureOledBlack = prefs[KEY_PURE_OLED_BLACK] ?: false,
            acousticSensingEnabled = prefs[KEY_ACOUSTIC_SENSING_ENABLED] ?: true,
            accelBiasX = prefs[KEY_ACCEL_BIAS_X] ?: 0f,
            accelBiasY = prefs[KEY_ACCEL_BIAS_Y] ?: 0f,
            gyroBiasX = prefs[KEY_GYRO_BIAS_X] ?: 0f,
            gyroBiasY = prefs[KEY_GYRO_BIAS_Y] ?: 0f,
            gyroBiasZ = prefs[KEY_GYRO_BIAS_Z] ?: 0f,
        )
    }

    suspend fun setTargetIp(ip: String) {
        dataStore.edit { it[KEY_TARGET_IP] = ip }
    }

    suspend fun setTargetPort(port: Int) {
        dataStore.edit { it[KEY_TARGET_PORT] = port }
    }

    suspend fun setDeviceId(deviceId: String) {
        dataStore.edit {
            it[KEY_DEVICE_ID] = deviceId.trim().ifEmpty { TelemetrySnapshot.DEFAULT_DEVICE_ID }
        }
    }

    suspend fun setStreamRateHz(rateHz: Int) {
        dataStore.edit { it[KEY_STREAM_RATE_HZ] = rateHz.coerceIn(10, 50) }
    }

    suspend fun setObdPollDelayMs(delayMs: Long) {
        dataStore.edit { it[KEY_OBD_POLL_DELAY_MS] = delayMs.coerceIn(0L, 250L) }
    }

    suspend fun setAutoReconnectObd(enabled: Boolean) {
        dataStore.edit { it[KEY_AUTO_RECONNECT_OBD] = enabled }
    }

    suspend fun setLastObdDevice(name: String, address: String) {
        dataStore.edit {
            it[KEY_LAST_OBD_NAME] = name
            it[KEY_LAST_OBD_ADDRESS] = address
        }
    }

    suspend fun setPureOledBlack(enabled: Boolean) {
        dataStore.edit { it[KEY_PURE_OLED_BLACK] = enabled }
    }

    suspend fun setAcousticSensingEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_ACOUSTIC_SENSING_ENABLED] = enabled }
    }

    suspend fun setCalibrationOffsets(
        accelBiasX: Float,
        accelBiasY: Float,
        gyroBiasX: Float,
        gyroBiasY: Float,
        gyroBiasZ: Float,
    ) {
        dataStore.edit {
            it[KEY_ACCEL_BIAS_X] = accelBiasX
            it[KEY_ACCEL_BIAS_Y] = accelBiasY
            it[KEY_GYRO_BIAS_X] = gyroBiasX
            it[KEY_GYRO_BIAS_Y] = gyroBiasY
            it[KEY_GYRO_BIAS_Z] = gyroBiasZ
        }
    }

    private companion object {
        val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        val KEY_TARGET_IP = stringPreferencesKey("target_ip")
        val KEY_TARGET_PORT = intPreferencesKey("target_port")
        val KEY_STREAM_RATE_HZ = intPreferencesKey("stream_rate_hz")
        val KEY_OBD_POLL_DELAY_MS = longPreferencesKey("obd_poll_delay_ms")
        val KEY_AUTO_RECONNECT_OBD = booleanPreferencesKey("auto_reconnect_obd")
        val KEY_LAST_OBD_NAME = stringPreferencesKey("last_obd_name")
        val KEY_LAST_OBD_ADDRESS = stringPreferencesKey("last_obd_address")
        val KEY_PURE_OLED_BLACK = booleanPreferencesKey("pure_oled_black")
        val KEY_ACOUSTIC_SENSING_ENABLED = booleanPreferencesKey("acoustic_sensing_enabled")
        val KEY_ACCEL_BIAS_X = floatPreferencesKey("accel_bias_x")
        val KEY_ACCEL_BIAS_Y = floatPreferencesKey("accel_bias_y")
        val KEY_GYRO_BIAS_X = floatPreferencesKey("gyro_bias_x")
        val KEY_GYRO_BIAS_Y = floatPreferencesKey("gyro_bias_y")
        val KEY_GYRO_BIAS_Z = floatPreferencesKey("gyro_bias_z")
    }
}
