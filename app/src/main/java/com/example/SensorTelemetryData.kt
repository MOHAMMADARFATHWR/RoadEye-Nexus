package com.example

import java.util.Locale
import java.util.zip.CRC32
import kotlin.math.roundToLong
import org.json.JSONObject

/**
 * High-frequency IMU and GNSS telemetry packet data class prepared for UDP network transmission.
 */
data class SensorTelemetryData(
    val timestamp: Long = System.currentTimeMillis(),
    val accelX: Float = 0f,
    val accelY: Float = 0f,
    val accelZ: Float = 0f,
    val gyroX: Float = 0f,
    val gyroY: Float = 0f,
    val gyroZ: Float = 0f,
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val alt: Double = 0.0,
    val speed: Float = 0f,
    val atmosphericPressureHpa: Float = 0f,
    val audioRmsDbfs: Float = -90f,
    val deviceId: String = TelemetrySnapshot.DEFAULT_DEVICE_ID,
    val seq: Long = 0L,
    val streamHz: Int = 20,
    val mag: Vector3State = Vector3State(),
    val gravity: Vector3State = Vector3State(),
    val linearAccel: Vector3State = Vector3State(),
    val rotationVector: QuaternionState = QuaternionState(),
    val orientation: OrientationState = OrientationState(),
    val barometer: BarometerState = BarometerState(),
    val ambientLight: AmbientLightState = AmbientLightState(),
    val proximity: ProximityState = ProximityState(),
    val audio: AudioTelemetryState = AudioTelemetryState(),
    val bearing: Float = 0f,
    val accuracyM: Float = 0f,
    val hasFix: Boolean = false,
    val availability: SensorAvailabilityState = SensorAvailabilityState(),
) {
    val timestampSec: Double
        get() = timestamp / 1000.0

    companion object {
        fun fromSnapshot(snapshot: TelemetrySnapshot): SensorTelemetryData {
            val s = snapshot.sensors
            return SensorTelemetryData(
                timestamp = (snapshot.timestampSec * 1000.0).roundToLong(),
                accelX = s.accel.x,
                accelY = s.accel.y,
                accelZ = s.accel.z,
                gyroX = s.gyro.x,
                gyroY = s.gyro.y,
                gyroZ = s.gyro.z,
                lat = s.gps.lat,
                lon = s.gps.lng,
                alt = s.gps.altitude,
                speed = s.gps.speedMps,
                atmosphericPressureHpa = s.barometer.pressureHpa,
                audioRmsDbfs = s.audio.rmsDbfs,
                deviceId = snapshot.deviceId,
                seq = snapshot.seq,
                streamHz = snapshot.streamHz,
                mag = s.mag,
                gravity = s.gravity,
                linearAccel = s.linearAccel,
                rotationVector = s.rotationVector,
                orientation = s.orientation,
                barometer = s.barometer,
                ambientLight = s.ambientLight,
                proximity = s.proximity,
                audio = s.audio,
                bearing = s.gps.bearing,
                accuracyM = s.gps.accuracyM,
                hasFix = s.gps.hasFix,
                availability = s.availability,
            )
        }
    }
}

/**
 * Zero-dependency org.json serializer and parser for [SensorTelemetryData] UDP payloads.
 */
object SensorTelemetryJsonSerializer {

    fun toJsonObject(data: SensorTelemetryData): JSONObject {
        val accelJson = JSONObject()
            .put("x", roundFloat(data.accelX, 3))
            .put("y", roundFloat(data.accelY, 3))
            .put("z", roundFloat(data.accelZ, 3))

        val gyroJson = JSONObject()
            .put("x", roundFloat(data.gyroX, 4))
            .put("y", roundFloat(data.gyroY, 4))
            .put("z", roundFloat(data.gyroZ, 4))

        val gnssJson = JSONObject()
            .put("lat", finiteDouble(data.lat))
            .put("lon", finiteDouble(data.lon))
            .put("alt", roundDouble(data.alt, 2))
            .put("speed", roundFloat(data.speed, 2))
            .put("bearing", roundFloat(data.bearing, 2))
            .put("accuracy_m", roundFloat(data.accuracyM, 1))
            .put("has_fix", data.hasFix)

        val rotVecJson = JSONObject()
            .put("x", roundFloat(data.rotationVector.x, 4))
            .put("y", roundFloat(data.rotationVector.y, 4))
            .put("z", roundFloat(data.rotationVector.z, 4))
            .put("w", roundFloat(data.rotationVector.w, 4))

        val orientationJson = JSONObject()
            .put("pitch_deg", roundFloat(data.orientation.pitchDeg, 2))
            .put("roll_deg", roundFloat(data.orientation.rollDeg, 2))
            .put("yaw_deg", roundFloat(data.orientation.yawDeg, 2))

        val pressureHpa = if (data.atmosphericPressureHpa != 0f) {
            data.atmosphericPressureHpa
        } else {
            data.barometer.pressureHpa
        }
        val barometerJson = JSONObject()
            .put("pressure_hpa", roundFloat(pressureHpa, 2))
            .put("rel_altitude_m", roundFloat(data.barometer.relAltitudeM, 2))

        val lightJson = JSONObject()
            .put("lux", roundFloat(data.ambientLight.lux, 1))
            .put("condition", data.ambientLight.condition)

        val proximityJson = JSONObject()
            .put("distance_cm", roundFloat(data.proximity.distanceCm, 1))
            .put("is_near", data.proximity.isNear)

        val rmsDbfs = if (data.audioRmsDbfs != -90f || data.audio.rmsDbfs == -90f) {
            data.audioRmsDbfs
        } else {
            data.audio.rmsDbfs
        }
        val audioJson = JSONObject()
            .put("active", data.audio.active)
            .put("rms_dbfs", roundFloat(rmsDbfs, 1))
            .put("peak_amp", roundFloat(data.audio.peakAmp, 3))
            .put("zcr_hz", roundFloat(data.audio.zcrHz, 1))

        val availabilityJson = JSONObject()
            .put("accel", data.availability.accel)
            .put("gyro", data.availability.gyro)
            .put("mag", data.availability.mag)
            .put("gravity", data.availability.gravity)
            .put("linear_accel", data.availability.linearAccel)
            .put("rotation_vector", data.availability.rotationVector)
            .put("barometer", data.availability.barometer)
            .put("light", data.availability.light)
            .put("proximity", data.availability.proximity)
            .put("audio", data.availability.audio)

        val root = JSONObject()
            .put("device_id", data.deviceId)
            .put("seq", data.seq)
            .put("timestamp", data.timestamp)
            .put("timestamp_sec", roundDouble(data.timestampSec, 3))
            .put("stream_hz", data.streamHz)
            .put("atmospheric_pressure_hpa", roundFloat(pressureHpa, 2))
            .put("audio_rms_dbfs", roundFloat(rmsDbfs, 1))
            .put("accel", accelJson)
            .put("gyro", gyroJson)
            .put("gnss", gnssJson)
            .put("mag", vec3Json(data.mag, 2))
            .put("gravity", vec3Json(data.gravity, 3))
            .put("linear_accel", vec3Json(data.linearAccel, 3))
            .put("rotation_vector", rotVecJson)
            .put("orientation", orientationJson)
            .put("barometer", barometerJson)
            .put("ambient_light", lightJson)
            .put("proximity", proximityJson)
            .put("audio", audioJson)
            .put("availability", availabilityJson)

        return root.put("crc32", computeCrc32Hex(root.toString()))
    }

    fun toJsonString(data: SensorTelemetryData): String = toJsonObject(data).toString()

    fun toUdpPayloadBytes(data: SensorTelemetryData): ByteArray =
        toJsonString(data).toByteArray(Charsets.UTF_8)

    fun fromJson(rawJson: String): SensorTelemetryData {
        val root = JSONObject(rawJson)
        val nested = root.optJSONObject("sensors")
        val accel = root.optJSONObject("accel") ?: nested?.optJSONObject("accel")
        val gyro = root.optJSONObject("gyro") ?: nested?.optJSONObject("gyro")
        val gnss = root.optJSONObject("gnss") ?: root.optJSONObject("gps") ?: nested?.optJSONObject("gps")
        val mag = root.optJSONObject("mag") ?: nested?.optJSONObject("mag")
        val gravity = root.optJSONObject("gravity") ?: nested?.optJSONObject("gravity")
        val linearAccel = root.optJSONObject("linear_accel") ?: nested?.optJSONObject("linear_accel")
        val rotVec = root.optJSONObject("rotation_vector") ?: nested?.optJSONObject("rotation_vector")
        val orientation = root.optJSONObject("orientation") ?: nested?.optJSONObject("orientation")
        val barometer = root.optJSONObject("barometer") ?: nested?.optJSONObject("barometer")
        val light = root.optJSONObject("ambient_light") ?: nested?.optJSONObject("ambient_light")
        val proximity = root.optJSONObject("proximity") ?: nested?.optJSONObject("proximity")
        val audio = root.optJSONObject("audio") ?: nested?.optJSONObject("audio")
        val avail = root.optJSONObject("availability") ?: nested?.optJSONObject("availability")

        val lonValue = when {
            gnss?.has("lon") == true -> gnss.optDouble("lon", 0.0)
            gnss?.has("lng") == true -> gnss.optDouble("lng", 0.0)
            else -> 0.0
        }
        val altValue = when {
            gnss?.has("alt") == true -> gnss.optDouble("alt", 0.0)
            gnss?.has("altitude_m") == true -> gnss.optDouble("altitude_m", 0.0)
            else -> 0.0
        }
        val speedValue = when {
            gnss?.has("speed") == true -> gnss.optDouble("speed", 0.0).toFloat()
            gnss?.has("speed_mps") == true -> gnss.optDouble("speed_mps", 0.0).toFloat()
            else -> 0f
        }

        val pressureParsed = when {
            root.has("atmospheric_pressure_hpa") -> root.optDouble("atmospheric_pressure_hpa", 0.0).toFloat()
            barometer != null -> barometer.optDouble("pressure_hpa", 0.0).toFloat()
            else -> 0f
        }
        val audioRmsParsed = when {
            root.has("audio_rms_dbfs") -> root.optDouble("audio_rms_dbfs", -90.0).toFloat()
            audio != null -> audio.optDouble("rms_dbfs", -90.0).toFloat()
            else -> -90f
        }

        return SensorTelemetryData(
            timestamp = root.optLong("timestamp", 0L),
            accelX = accel?.optDouble("x", 0.0)?.toFloat() ?: 0f,
            accelY = accel?.optDouble("y", 0.0)?.toFloat() ?: 0f,
            accelZ = accel?.optDouble("z", 0.0)?.toFloat() ?: 0f,
            gyroX = gyro?.optDouble("x", 0.0)?.toFloat() ?: 0f,
            gyroY = gyro?.optDouble("y", 0.0)?.toFloat() ?: 0f,
            gyroZ = gyro?.optDouble("z", 0.0)?.toFloat() ?: 0f,
            lat = gnss?.optDouble("lat", 0.0) ?: 0.0,
            lon = lonValue,
            alt = altValue,
            speed = speedValue,
            atmosphericPressureHpa = pressureParsed,
            audioRmsDbfs = audioRmsParsed,
            deviceId = root.optString("device_id", TelemetrySnapshot.DEFAULT_DEVICE_ID),
            seq = root.optLong("seq", 0L),
            streamHz = root.optInt("stream_hz", 20),
            mag = parseVec3(mag),
            gravity = parseVec3(gravity),
            linearAccel = parseVec3(linearAccel),
            rotationVector = QuaternionState(
                x = rotVec?.optDouble("x", 0.0)?.toFloat() ?: 0f,
                y = rotVec?.optDouble("y", 0.0)?.toFloat() ?: 0f,
                z = rotVec?.optDouble("z", 0.0)?.toFloat() ?: 0f,
                w = rotVec?.optDouble("w", 1.0)?.toFloat() ?: 1f,
            ),
            orientation = OrientationState(
                pitchDeg = orientation?.optDouble("pitch_deg", 0.0)?.toFloat() ?: 0f,
                rollDeg = orientation?.optDouble("roll_deg", 0.0)?.toFloat() ?: 0f,
                yawDeg = orientation?.optDouble("yaw_deg", 0.0)?.toFloat() ?: 0f,
            ),
            barometer = BarometerState(
                pressureHpa = barometer?.optDouble("pressure_hpa", 0.0)?.toFloat() ?: 0f,
                relAltitudeM = barometer?.optDouble("rel_altitude_m", 0.0)?.toFloat() ?: 0f,
            ),
            ambientLight = AmbientLightState(
                lux = light?.optDouble("lux", 0.0)?.toFloat() ?: 0f,
                condition = light?.optString("condition", "UNKNOWN") ?: "UNKNOWN",
            ),
            proximity = ProximityState(
                distanceCm = proximity?.optDouble("distance_cm", 0.0)?.toFloat() ?: 0f,
                isNear = proximity?.optBoolean("is_near", false) ?: false,
            ),
            audio = AudioTelemetryState(
                active = audio?.optBoolean("active", false) ?: false,
                rmsDbfs = audio?.optDouble("rms_dbfs", -90.0)?.toFloat() ?: -90f,
                peakAmp = audio?.optDouble("peak_amp", 0.0)?.toFloat() ?: 0f,
                zcrHz = audio?.optDouble("zcr_hz", 0.0)?.toFloat() ?: 0f,
            ),
            bearing = gnss?.optDouble("bearing", 0.0)?.toFloat() ?: 0f,
            accuracyM = gnss?.optDouble("accuracy_m", 0.0)?.toFloat() ?: 0f,
            hasFix = gnss?.optBoolean("has_fix", false) ?: false,
            availability = SensorAvailabilityState(
                accel = avail?.optBoolean("accel", false) ?: false,
                gyro = avail?.optBoolean("gyro", false) ?: false,
                mag = avail?.optBoolean("mag", false) ?: false,
                gravity = avail?.optBoolean("gravity", false) ?: false,
                linearAccel = avail?.optBoolean("linear_accel", false) ?: false,
                rotationVector = avail?.optBoolean("rotation_vector", false) ?: false,
                barometer = avail?.optBoolean("barometer", false) ?: false,
                light = avail?.optBoolean("light", false) ?: false,
                proximity = avail?.optBoolean("proximity", false) ?: false,
                audio = avail?.optBoolean("audio", false) ?: false,
            ),
        )
    }

    private fun parseVec3(obj: JSONObject?): Vector3State {
        if (obj == null) return Vector3State()
        return Vector3State(
            x = obj.optDouble("x", 0.0).toFloat(),
            y = obj.optDouble("y", 0.0).toFloat(),
            z = obj.optDouble("z", 0.0).toFloat(),
        )
    }

    private fun vec3Json(v: Vector3State, decimals: Int): JSONObject {
        return JSONObject()
            .put("x", roundFloat(v.x, decimals))
            .put("y", roundFloat(v.y, decimals))
            .put("z", roundFloat(v.z, decimals))
    }

    private fun computeCrc32Hex(payload: String): String {
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
