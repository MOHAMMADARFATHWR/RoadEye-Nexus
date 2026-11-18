package com.example

import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.CRC32
import kotlin.math.roundToLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class Vector3State(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
)

data class QuaternionState(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
    val w: Float = 1f,
)

data class OrientationState(
    val pitchDeg: Float = 0f,
    val rollDeg: Float = 0f,
    val yawDeg: Float = 0f,
)

data class BarometerState(
    val pressureHpa: Float = 0f,
    val relAltitudeM: Float = 0f,
)

data class AmbientLightState(
    val lux: Float = 0f,
    val condition: String = "UNKNOWN",
)

data class ProximityState(
    val distanceCm: Float = 0f,
    val isNear: Boolean = false,
)

data class AudioTelemetryState(
    val active: Boolean = false,
    val rmsDbfs: Float = -90f,
    val peakAmp: Float = 0f,
    val zcrHz: Float = 0f,
)

data class DeviceHealthState(
    val batteryPct: Int = 100,
    val batteryTempC: Float = 25f,
    val charging: Boolean = false,
    val wifiRssiDbm: Int = -127,
    val wifiLinkMbps: Int = 0,
)

data class RoadIntelligenceState(
    val conditionScore: Int = 100,
    val surfaceClass: String = "SMOOTH",
    val vibrationRms: Float = 0f,
    val impactDetected: Boolean = false,
)

data class SensorAvailabilityState(
    val accel: Boolean = false,
    val gyro: Boolean = false,
    val mag: Boolean = false,
    val gravity: Boolean = false,
    val linearAccel: Boolean = false,
    val rotationVector: Boolean = false,
    val barometer: Boolean = false,
    val light: Boolean = false,
    val proximity: Boolean = false,
    val audio: Boolean = false,
)

data class GpsState(
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val altitude: Double = 0.0,
    val speedMps: Float = 0f,
    val bearing: Float = 0f,
    val accuracyM: Float = 0f,
    val hasFix: Boolean = false,
)

data class SensorsState(
    val accel: Vector3State = Vector3State(),
    val gyro: Vector3State = Vector3State(),
    val mag: Vector3State = Vector3State(),
    val gravity: Vector3State = Vector3State(),
    val linearAccel: Vector3State = Vector3State(),
    val rotationVector: QuaternionState = QuaternionState(),
    val orientation: OrientationState = OrientationState(),
    val barometer: BarometerState = BarometerState(),
    val ambientLight: AmbientLightState = AmbientLightState(),
    val proximity: ProximityState = ProximityState(),
    val audio: AudioTelemetryState = AudioTelemetryState(),
    val gps: GpsState = GpsState(),
    val availability: SensorAvailabilityState = SensorAvailabilityState(),
)

enum class Obd2ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    POLLING,
    RECONNECTING,
    ERROR,
}

data class Obd2State(
    val connected: Boolean = false,
    val status: Obd2ConnectionStatus = Obd2ConnectionStatus.DISCONNECTED,
    val deviceName: String = "",
    val deviceAddress: String = "",
    val protocol: String = "AUTO",
    val speedKmh: Int = 0,
    val rpm: Int = 0,
    val throttlePos: Float = 0f,
    val coolantTempC: Int = 0,
    val engineLoad: Float = 0f,
    val intakeTempC: Int = 0,
    val mafGps: Float = 0f,
    val timingAdvanceDeg: Float = 0f,
    val fuelLevelPct: Float = 0f,
    val dtcs: List<String> = emptyList(),
    val lastError: String? = null,
)

data class TelemetrySnapshot(
    val deviceId: String = DEFAULT_DEVICE_ID,
    val seq: Long = 0L,
    val timestampSec: Double = 0.0,
    val streamHz: Int = 20,
    val thermalStatus: String = "NOMINAL",
    val sensors: SensorsState = SensorsState(),
    val deviceHealth: DeviceHealthState = DeviceHealthState(),
    val roadIntelligence: RoadIntelligenceState = RoadIntelligenceState(),
    val obd2: Obd2State = Obd2State(),
) {
    fun toJsonObject(): JSONObject {
        val accelJson = vec3Json(sensors.accel, 3)
        val gyroJson = vec3Json(sensors.gyro, 4)
        val magJson = vec3Json(sensors.mag, 2)
        val gravityJson = vec3Json(sensors.gravity, 3)
        val linearAccelJson = vec3Json(sensors.linearAccel, 3)

        val rotVecJson = JSONObject()
            .put("x", roundFloat(sensors.rotationVector.x, 4))
            .put("y", roundFloat(sensors.rotationVector.y, 4))
            .put("z", roundFloat(sensors.rotationVector.z, 4))
            .put("w", roundFloat(sensors.rotationVector.w, 4))

        val orientationJson = JSONObject()
            .put("pitch_deg", roundFloat(sensors.orientation.pitchDeg, 2))
            .put("roll_deg", roundFloat(sensors.orientation.rollDeg, 2))
            .put("yaw_deg", roundFloat(sensors.orientation.yawDeg, 2))

        val barometerJson = JSONObject()
            .put("pressure_hpa", roundFloat(sensors.barometer.pressureHpa, 2))
            .put("rel_altitude_m", roundFloat(sensors.barometer.relAltitudeM, 2))

        val lightJson = JSONObject()
            .put("lux", roundFloat(sensors.ambientLight.lux, 1))
            .put("condition", sensors.ambientLight.condition)

        val proximityJson = JSONObject()
            .put("distance_cm", roundFloat(sensors.proximity.distanceCm, 1))
            .put("is_near", sensors.proximity.isNear)

        val audioJson = JSONObject()
            .put("active", sensors.audio.active)
            .put("rms_dbfs", roundFloat(sensors.audio.rmsDbfs, 1))
            .put("peak_amp", roundFloat(sensors.audio.peakAmp, 3))
            .put("zcr_hz", roundFloat(sensors.audio.zcrHz, 1))

        val gpsJson = JSONObject()
            .put("lat", finiteDouble(sensors.gps.lat))
            .put("lng", finiteDouble(sensors.gps.lng))
            .put("altitude_m", roundDouble(sensors.gps.altitude, 2))
            .put("speed_mps", roundFloat(sensors.gps.speedMps, 2))
            .put("bearing", roundFloat(sensors.gps.bearing, 2))
            .put("accuracy_m", roundFloat(sensors.gps.accuracyM, 1))
            .put("has_fix", sensors.gps.hasFix)

        val availabilityJson = JSONObject()
            .put("accel", sensors.availability.accel)
            .put("gyro", sensors.availability.gyro)
            .put("mag", sensors.availability.mag)
            .put("gravity", sensors.availability.gravity)
            .put("linear_accel", sensors.availability.linearAccel)
            .put("rotation_vector", sensors.availability.rotationVector)
            .put("barometer", sensors.availability.barometer)
            .put("light", sensors.availability.light)
            .put("proximity", sensors.availability.proximity)
            .put("audio", sensors.availability.audio)

        val sensorsJson = JSONObject()
            .put("accel", accelJson)
            .put("gyro", gyroJson)
            .put("mag", magJson)
            .put("gravity", gravityJson)
            .put("linear_accel", linearAccelJson)
            .put("rotation_vector", rotVecJson)
            .put("orientation", orientationJson)
            .put("barometer", barometerJson)
            .put("ambient_light", lightJson)
            .put("proximity", proximityJson)
            .put("audio", audioJson)
            .put("gps", gpsJson)
            .put("availability", availabilityJson)

        val deviceHealthJson = JSONObject()
            .put("battery_pct", deviceHealth.batteryPct)
            .put("battery_temp_c", roundFloat(deviceHealth.batteryTempC, 1))
            .put("charging", deviceHealth.charging)
            .put("wifi_rssi_dbm", deviceHealth.wifiRssiDbm)
            .put("wifi_link_mbps", deviceHealth.wifiLinkMbps)

        val roadIntelJson = JSONObject()
            .put("condition_score", roadIntelligence.conditionScore)
            .put("surface_class", roadIntelligence.surfaceClass)
            .put("vibration_rms", roundFloat(roadIntelligence.vibrationRms, 3))
            .put("impact_detected", roadIntelligence.impactDetected)

        val dtcArray = JSONArray()
        obd2.dtcs.forEach { dtcArray.put(it) }

        val obd2Json = JSONObject()
            .put("connected", obd2.connected)
            .put("protocol", obd2.protocol)
            .put("speed_kmh", obd2.speedKmh)
            .put("rpm", obd2.rpm)
            .put("throttle_pos", roundFloat(obd2.throttlePos, 1))
            .put("coolant_temp_c", obd2.coolantTempC)
            .put("engine_load", roundFloat(obd2.engineLoad, 1))
            .put("intake_temp_c", obd2.intakeTempC)
            .put("maf_gps", roundFloat(obd2.mafGps, 2))
            .put("timing_advance_deg", roundFloat(obd2.timingAdvanceDeg, 1))
            .put("fuel_level_pct", roundFloat(obd2.fuelLevelPct, 1))
            .put("dtc_count", obd2.dtcs.size)
            .put("dtcs", dtcArray)

        val coreObject = JSONObject()
            .put("device_id", deviceId)
            .put("seq", seq)
            .put("timestamp", roundDouble(timestampSec, 3))
            .put("stream_hz", streamHz)
            .put("thermal_status", thermalStatus)
            .put("sensors", sensorsJson)
            .put("device_health", deviceHealthJson)
            .put("road_intelligence", roadIntelJson)
            .put("obd2", obd2Json)

        val crc32 = computeCrc32Hex(coreObject.toString())
        return coreObject.put("crc32", crc32)
    }

    fun toJsonString(): String = toJsonObject().toString()

    fun toSensorTelemetryData(): SensorTelemetryData = SensorTelemetryData.fromSnapshot(this)

    companion object {
        const val DEFAULT_DEVICE_ID = "RoadEye_Nexus_Node01"

        private fun vec3Json(v: Vector3State, decimals: Int): JSONObject {
            return JSONObject()
                .put("x", roundFloat(v.x, decimals))
                .put("y", roundFloat(v.y, decimals))
                .put("z", roundFloat(v.z, decimals))
        }

        fun computeCrc32Hex(payload: String): String {
            val crc = CRC32()
            crc.update(payload.toByteArray(Charsets.UTF_8))
            return String.format(Locale.US, "%08X", crc.value)
        }

        private fun finiteDouble(value: Double): Double = if (value.isFinite()) value else 0.0

        private fun roundFloat(value: Float, decimals: Int): Double {
            if (!value.isFinite()) return 0.0
            val factor = when (decimals) {
                1 -> 10.0
                2 -> 100.0
                3 -> 1000.0
                4 -> 10000.0
                else -> 100.0
            }
            return (value.toDouble() * factor).roundToLong() / factor
        }

        private fun roundDouble(value: Double, decimals: Int): Double {
            if (!value.isFinite()) return 0.0
            val factor = when (decimals) {
                1 -> 10.0
                2 -> 100.0
                3 -> 1000.0
                else -> 1000.0
            }
            return (value * factor).roundToLong() / factor
        }
    }
}

class TelemetryStateHolder(
    deviceId: String = TelemetrySnapshot.DEFAULT_DEVICE_ID,
) {
    private val latestState = AtomicReference(TelemetrySnapshot(deviceId = deviceId))
    private val _stateFlow = MutableStateFlow(latestState.get())
    val stateFlow: StateFlow<TelemetrySnapshot> = _stateFlow.asStateFlow()

    fun setMetadata(deviceId: String, streamHz: Int, thermalStatus: String) {
        updateState { current ->
            current.copy(
                deviceId = deviceId,
                streamHz = streamHz,
                thermalStatus = thermalStatus,
            )
        }
    }

    fun setSensorAvailability(availability: SensorAvailabilityState) {
        updateState { current ->
            current.copy(
                sensors = current.sensors.copy(availability = availability),
            )
        }
    }

    fun updateAudioAvailability(available: Boolean) {
        updateState { current ->
            current.copy(
                sensors = current.sensors.copy(
                    availability = current.sensors.availability.copy(audio = available),
                ),
            )
        }
    }

    fun updateAccelAndOrientation(
        x: Float,
        y: Float,
        z: Float,
        pitchDeg: Float,
        rollDeg: Float,
        yawDeg: Float,
    ) {
        updateState { current ->
            current.copy(
                sensors = current.sensors.copy(
                    accel = Vector3State(x = x, y = y, z = z),
                    orientation = OrientationState(
                        pitchDeg = pitchDeg,
                        rollDeg = rollDeg,
                        yawDeg = yawDeg,
                    ),
                ),
            )
        }
    }

    fun updateGyro(x: Float, y: Float, z: Float) {
        updateState { current ->
            current.copy(
                sensors = current.sensors.copy(
                    gyro = Vector3State(x = x, y = y, z = z),
                ),
            )
        }
    }

    fun updateMag(x: Float, y: Float, z: Float) {
        updateState { current ->
            current.copy(
                sensors = current.sensors.copy(
                    mag = Vector3State(x = x, y = y, z = z),
                ),
            )
        }
    }

    fun updateGravity(x: Float, y: Float, z: Float) {
        updateState { current ->
            current.copy(
                sensors = current.sensors.copy(
                    gravity = Vector3State(x = x, y = y, z = z),
                ),
            )
        }
    }

    fun updateLinearAccelAndRoad(
        x: Float,
        y: Float,
        z: Float,
        roadIntelligence: RoadIntelligenceState,
    ) {
        updateState { current ->
            current.copy(
                sensors = current.sensors.copy(
                    linearAccel = Vector3State(x = x, y = y, z = z),
                ),
                roadIntelligence = roadIntelligence,
            )
        }
    }

    fun updateRotationVector(x: Float, y: Float, z: Float, w: Float) {
        updateState { current ->
            current.copy(
                sensors = current.sensors.copy(
                    rotationVector = QuaternionState(x = x, y = y, z = z, w = w),
                ),
            )
        }
    }

    fun updateBarometer(pressureHpa: Float, relAltitudeM: Float) {
        updateState { current ->
            current.copy(
                sensors = current.sensors.copy(
                    barometer = BarometerState(
                        pressureHpa = pressureHpa,
                        relAltitudeM = relAltitudeM,
                    ),
                ),
            )
        }
    }

    fun updateAmbientLight(lux: Float, condition: String) {
        updateState { current ->
            current.copy(
                sensors = current.sensors.copy(
                    ambientLight = AmbientLightState(lux = lux, condition = condition),
                ),
            )
        }
    }

    fun updateProximity(distanceCm: Float, isNear: Boolean) {
        updateState { current ->
            current.copy(
                sensors = current.sensors.copy(
                    proximity = ProximityState(distanceCm = distanceCm, isNear = isNear),
                ),
            )
        }
    }

    fun updateAudio(audio: AudioTelemetryState) {
        updateState { current ->
            current.copy(
                sensors = current.sensors.copy(audio = audio),
            )
        }
    }

    fun updateDeviceHealth(deviceHealth: DeviceHealthState) {
        updateState { current ->
            current.copy(deviceHealth = deviceHealth)
        }
    }

    fun updateGps(
        lat: Double,
        lng: Double,
        altitude: Double,
        speedMps: Float,
        bearing: Float,
        accuracyM: Float,
        hasFix: Boolean = true,
    ) {
        updateState { current ->
            current.copy(
                sensors = current.sensors.copy(
                    gps = GpsState(
                        lat = lat,
                        lng = lng,
                        altitude = altitude,
                        speedMps = speedMps,
                        bearing = bearing,
                        accuracyM = accuracyM,
                        hasFix = hasFix,
                    ),
                ),
            )
        }
    }

    fun updateObd2(transform: (Obd2State) -> Obd2State) {
        updateState { current ->
            current.copy(obd2 = transform(current.obd2))
        }
    }

    fun snapshot(
        timestampSec: Double = System.currentTimeMillis() / 1000.0,
        seq: Long = latestState.get().seq,
    ): TelemetrySnapshot {
        return latestState.get().copy(timestampSec = timestampSec, seq = seq)
    }

    private fun updateState(transform: (TelemetrySnapshot) -> TelemetrySnapshot) {
        val updated = latestState.updateAndGet(transform)
        _stateFlow.value = updated
    }
}
