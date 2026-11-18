package com.example

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TripStatisticsSummary(
    val sessionId: String,
    val sessionLabel: String,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val durationSec: Double,
    val sampleCount: Long,
    val averageSpeedKmh: Float,
    val maxSpeedKmh: Float,
    val estimatedDistanceKm: Double,
    val minAltitudeM: Float,
    val maxAltitudeM: Float,
    val maxAltitudeChangeM: Float,
    val totalElevationGainM: Float,
    val netAltitudeChangeM: Float,
    val altitudeSource: String,
    val pressureMinHpa: Float,
    val pressureMaxHpa: Float,
    val totalRoadNoiseExposureDbSec: Double,
    val totalNoiseEnergyDosage: Double,
    val noiseExposureIndexPct: Float,
    val equivalentNoiseDbfs: Float,
    val peakNoiseDbfs: Float,
    val elevatedNoiseDurationSec: Double,
    val hasAudioSamples: Boolean,
    val averageRoadScore: Int,
    val worstSurfaceClass: String,
    val peakVibrationRms: Float,
    val impactCount: Int,
    val peakLateralG: Float,
    val peakLongitudinalG: Float,
    val obd2ConnectedDuringTrip: Boolean,
    val peakRpm: Int,
    val averageRpm: Int,
    val maxCoolantTempC: Int,
    val averageStreamHz: Float,
)

/**
 * Aggregates live telemetry frames during a session and computes final trip statistics
 * (average speed, maximum altitude change, and total road noise exposure) when the session ends.
 */
class TripStatisticsService {

    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()

    private val _latestSummary = MutableStateFlow<TripStatisticsSummary?>(null)
    val latestSummary: StateFlow<TripStatisticsSummary?> = _latestSummary.asStateFlow()

    private val _lastCompletedSummary = MutableStateFlow<TripStatisticsSummary?>(null)
    val lastCompletedSummary: StateFlow<TripStatisticsSummary?> = _lastCompletedSummary.asStateFlow()

    private var activeSessionId: String = TelemetrySnapshot.DEFAULT_DEVICE_ID
    private var activeSessionLabel: String = "UDP Telemetry Session"
    private var startedAtMs: Long = 0L
    private var lastSampleTimeMs: Long = 0L
    private var sampleCount: Long = 0L

    // Speed & Distance accumulators
    private var speedSumKmh: Double = 0.0
    private var weightedSpeedSumKmh: Double = 0.0
    private var weightedDurationSec: Double = 0.0
    private var prevSpeedKmh: Float = 0f
    private var hasPrevSpeed: Boolean = false
    private var maxSpeedKmh: Float = 0f
    private var estimatedDistanceKm: Double = 0.0

    // Barometer altitude accumulators (5 Hz TYPE_PRESSURE)
    private var baroSampleCount: Long = 0L
    private var baroFirstAltM: Float = 0f
    private var baroLastAltM: Float = 0f
    private var baroMinAltM: Float = Float.POSITIVE_INFINITY
    private var baroMaxAltM: Float = Float.NEGATIVE_INFINITY
    private var baroGainM: Float = 0f
    private var baroPrevAltM: Float = 0f
    private var hasBaroPrevAlt: Boolean = false
    private var pressureMinHpa: Float = Float.POSITIVE_INFINITY
    private var pressureMaxHpa: Float = Float.NEGATIVE_INFINITY

    // GNSS altitude accumulators (fallback when barometer is unavailable)
    private var gpsAltSampleCount: Long = 0L
    private var gpsFirstAltM: Float = 0f
    private var gpsLastAltM: Float = 0f
    private var gpsMinAltM: Float = Float.POSITIVE_INFINITY
    private var gpsMaxAltM: Float = Float.NEGATIVE_INFINITY
    private var gpsGainM: Float = 0f
    private var gpsPrevAltM: Float = 0f
    private var hasGpsPrevAlt: Boolean = false

    // Acoustic road noise exposure accumulators (AudioRecord RMS dBFS)
    private var audioSampleCount: Long = 0L
    private var audioDurationSec: Double = 0.0
    private var prevAudioTimeMs: Long = 0L
    private var prevAudioDbfs: Float = NOISE_FLOOR_DBFS
    private var hasPrevAudioSample: Boolean = false
    private var totalNoiseExposureDbSec: Double = 0.0
    private var totalNoiseEnergyDosage: Double = 0.0
    private var peakNoiseDbfs: Float = NOISE_FLOOR_DBFS
    private var elevatedNoiseDurationSec: Double = 0.0

    // Road intelligence, G-force & OBD2 accumulators
    private var roadScoreSum: Long = 0L
    private var worstSurfaceRank: Int = 0
    private var worstSurfaceClass: String = "SMOOTH"
    private var peakVibrationRms: Float = 0f
    private var impactCount: Int = 0
    private var prevImpactDetected: Boolean = false
    private var peakLateralG: Float = 0f
    private var peakLongitudinalG: Float = 0f

    private var obd2ConnectedDuringTrip: Boolean = false
    private var obd2SampleCount: Long = 0L
    private var rpmSum: Long = 0L
    private var peakRpm: Int = 0
    private var maxCoolantTempC: Int = Int.MIN_VALUE
    private var streamHzSum: Double = 0.0

    @Synchronized
    fun startSession(
        sessionId: String = TelemetrySnapshot.DEFAULT_DEVICE_ID,
        sessionLabel: String = "UDP Telemetry Session",
        startTimeMs: Long = System.currentTimeMillis(),
    ) {
        resetAccumulators()
        activeSessionId = sessionId.ifBlank { TelemetrySnapshot.DEFAULT_DEVICE_ID }
        activeSessionLabel = sessionLabel.ifBlank { "UDP Telemetry Session" }
        startedAtMs = startTimeMs
        _isSessionActive.value = true
        _latestSummary.value = null
    }

    @Synchronized
    fun recordSnapshot(
        snapshot: TelemetrySnapshot,
        sampleTimeMs: Long = resolveTimestampMs(snapshot),
    ) {
        if (!_isSessionActive.value) return

        val effectiveTimeMs = maxOf(sampleTimeMs, startedAtMs)
        val dtSec = if (lastSampleTimeMs > 0L && effectiveTimeMs >= lastSampleTimeMs) {
            ((effectiveTimeMs - lastSampleTimeMs) / 1000.0).coerceAtMost(MAX_INTERVAL_SEC)
        } else {
            0.0
        }

        sampleCount++
        streamHzSum += snapshot.streamHz.coerceAtLeast(1)

        // 1. Speed & distance
        val instantSpeedKmh = if (snapshot.obd2.connected) {
            snapshot.obd2.speedKmh.toFloat().coerceAtLeast(0f)
        } else {
            (snapshot.sensors.gps.speedMps * 3.6f).coerceAtLeast(0f)
        }
        speedSumKmh += instantSpeedKmh
        if (instantSpeedKmh > maxSpeedKmh) {
            maxSpeedKmh = instantSpeedKmh
        }
        if (hasPrevSpeed && dtSec > 0.0) {
            val avgSegmentSpeed = (prevSpeedKmh + instantSpeedKmh) / 2.0
            weightedSpeedSumKmh += avgSegmentSpeed * dtSec
            weightedDurationSec += dtSec
            estimatedDistanceKm += (avgSegmentSpeed * dtSec) / 3600.0
        }
        prevSpeedKmh = instantSpeedKmh
        hasPrevSpeed = true

        // 2. Barometer & GNSS altitude tracking
        val baro = snapshot.sensors.barometer
        if (baro.pressureHpa > 0f || snapshot.sensors.availability.barometer) {
            if (baro.pressureHpa > 0f) {
                if (baro.pressureHpa < pressureMinHpa) pressureMinHpa = baro.pressureHpa
                if (baro.pressureHpa > pressureMaxHpa) pressureMaxHpa = baro.pressureHpa
            }
            val baroAlt = baro.relAltitudeM
            if (baro.pressureHpa > 0f || baroAlt != 0f) {
                baroSampleCount++
                if (baroSampleCount == 1L) baroFirstAltM = baroAlt
                baroLastAltM = baroAlt
                if (baroAlt < baroMinAltM) baroMinAltM = baroAlt
                if (baroAlt > baroMaxAltM) baroMaxAltM = baroAlt
                if (hasBaroPrevAlt) {
                    val delta = baroAlt - baroPrevAltM
                    if (delta > ALTITUDE_GAIN_NOISE_THRESHOLD_M) {
                        baroGainM += delta
                    }
                }
                baroPrevAltM = baroAlt
                hasBaroPrevAlt = true
            }
        }

        val gps = snapshot.sensors.gps
        if (gps.hasFix || gps.altitude != 0.0) {
            val gpsAlt = gps.altitude.toFloat()
            gpsAltSampleCount++
            if (gpsAltSampleCount == 1L) gpsFirstAltM = gpsAlt
            gpsLastAltM = gpsAlt
            if (gpsAlt < gpsMinAltM) gpsMinAltM = gpsAlt
            if (gpsAlt > gpsMaxAltM) gpsMaxAltM = gpsAlt
            if (hasGpsPrevAlt) {
                val delta = gpsAlt - gpsPrevAltM
                if (delta > ALTITUDE_GAIN_NOISE_THRESHOLD_M) {
                    gpsGainM += delta
                }
            }
            gpsPrevAltM = gpsAlt
            hasGpsPrevAlt = true
        }

        // 3. Road noise exposure (AudioRecord RMS dBFS)
        val audio = snapshot.sensors.audio
        val hasAudioReading = audio.active || audio.rmsDbfs > (NOISE_FLOOR_DBFS + 0.1f)
        if (hasAudioReading) {
            val dbfs = audio.rmsDbfs.coerceIn(NOISE_FLOOR_DBFS, 0f)
            audioSampleCount++
            if (dbfs > peakNoiseDbfs) {
                peakNoiseDbfs = dbfs
            }
            if (hasPrevAudioSample && prevAudioTimeMs > 0L && effectiveTimeMs >= prevAudioTimeMs) {
                val audioDt = ((effectiveTimeMs - prevAudioTimeMs) / 1000.0).coerceAtMost(MAX_INTERVAL_SEC)
                if (audioDt > 0.0) {
                    val avgDb = (prevAudioDbfs + dbfs) / 2.0
                    val aboveFloorDb = (avgDb - NOISE_FLOOR_DBFS).coerceAtLeast(0.0)
                    val energyAvg = (10.0.pow(prevAudioDbfs / 10.0) + 10.0.pow(dbfs / 10.0)) / 2.0
                    totalNoiseExposureDbSec += aboveFloorDb * audioDt
                    totalNoiseEnergyDosage += energyAvg * audioDt
                    audioDurationSec += audioDt
                    if (avgDb >= ELEVATED_NOISE_THRESHOLD_DBFS) {
                        elevatedNoiseDurationSec += audioDt
                    }
                }
            }
            prevAudioDbfs = dbfs
            prevAudioTimeMs = effectiveTimeMs
            hasPrevAudioSample = true
        }

        // 4. Road intelligence & G-force dynamics
        val road = snapshot.roadIntelligence
        roadScoreSum += road.conditionScore.coerceIn(0, 100)
        if (road.vibrationRms > peakVibrationRms) {
            peakVibrationRms = road.vibrationRms
        }
        val surfaceRank = surfaceSeverityRank(road.surfaceClass)
        if (surfaceRank >= worstSurfaceRank) {
            worstSurfaceRank = surfaceRank
            worstSurfaceClass = road.surfaceClass
        }
        if (road.impactDetected && !prevImpactDetected) {
            impactCount++
        }
        prevImpactDetected = road.impactDetected

        val latG = abs(snapshot.sensors.accel.x / GRAVITY_MPS2)
        val lonG = abs(snapshot.sensors.accel.y / GRAVITY_MPS2)
        if (latG > peakLateralG) peakLateralG = latG
        if (lonG > peakLongitudinalG) peakLongitudinalG = lonG

        // 5. OBD2 powertrain metrics
        val obd2 = snapshot.obd2
        if (obd2.connected) {
            obd2ConnectedDuringTrip = true
            obd2SampleCount++
            rpmSum += obd2.rpm.coerceAtLeast(0)
            if (obd2.rpm > peakRpm) peakRpm = obd2.rpm
            if (obd2.coolantTempC > maxCoolantTempC) maxCoolantTempC = obd2.coolantTempC
        }

        lastSampleTimeMs = effectiveTimeMs
    }

    @Synchronized
    fun recordSample(
        data: SensorTelemetryData,
        obd2SpeedKmh: Int = 0,
        obd2Connected: Boolean = false,
        roadScore: Int = 100,
        vibrationRms: Float = 0f,
        impactDetected: Boolean = false,
        surfaceClass: String = "SMOOTH",
        rpm: Int = 0,
        coolantTempC: Int = 0,
    ) {
        val snapshot = TelemetrySnapshot(
            deviceId = data.deviceId,
            seq = data.seq,
            timestampSec = data.timestamp / 1000.0,
            streamHz = data.streamHz,
            sensors = SensorsState(
                accel = Vector3State(data.accelX, data.accelY, data.accelZ),
                gyro = Vector3State(data.gyroX, data.gyroY, data.gyroZ),
                mag = data.mag,
                gravity = data.gravity,
                linearAccel = data.linearAccel,
                rotationVector = data.rotationVector,
                orientation = data.orientation,
                barometer = if (data.barometer.pressureHpa > 0f || data.barometer.relAltitudeM != 0f) {
                    data.barometer
                } else {
                    BarometerState(
                        pressureHpa = data.atmosphericPressureHpa,
                        relAltitudeM = data.barometer.relAltitudeM,
                    )
                },
                ambientLight = data.ambientLight,
                proximity = data.proximity,
                audio = if (data.audioRmsDbfs > NOISE_FLOOR_DBFS + 0.1f && data.audio.rmsDbfs <= NOISE_FLOOR_DBFS + 0.1f) {
                    data.audio.copy(active = true, rmsDbfs = data.audioRmsDbfs)
                } else {
                    data.audio
                },
                gps = GpsState(
                    lat = data.lat,
                    lng = data.lon,
                    altitude = data.alt,
                    speedMps = data.speed,
                    bearing = data.bearing,
                    accuracyM = data.accuracyM,
                    hasFix = data.hasFix,
                ),
                availability = data.availability,
            ),
            roadIntelligence = RoadIntelligenceState(
                conditionScore = roadScore,
                surfaceClass = surfaceClass,
                vibrationRms = vibrationRms,
                impactDetected = impactDetected,
            ),
            obd2 = Obd2State(
                connected = obd2Connected,
                speedKmh = obd2SpeedKmh,
                rpm = rpm,
                coolantTempC = coolantTempC,
            ),
        )
        recordSnapshot(snapshot, sampleTimeMs = data.timestamp)
    }

    @Synchronized
    fun endSession(endTimeMs: Long = System.currentTimeMillis()): TripStatisticsSummary {
        val effectiveEndMs = maxOf(endTimeMs, maxOf(startedAtMs, lastSampleTimeMs))
        val durationSec = ((effectiveEndMs - startedAtMs) / 1000.0).coerceAtLeast(0.0)

        val avgSpeedKmh = when {
            weightedDurationSec > 0.0 -> (weightedSpeedSumKmh / weightedDurationSec).toFloat()
            sampleCount > 0L -> (speedSumKmh / sampleCount).toFloat()
            else -> 0f
        }

        // Select richer altitude source (prefer 5 Hz barometer when pressure/altitude samples exist)
        val baroSpan = if (baroSampleCount > 0L) (baroMaxAltM - baroMinAltM).coerceAtLeast(0f) else 0f
        val useBaro = baroSampleCount > 0L && (baroSpan > 0f || gpsAltSampleCount == 0L)
        val useGps = !useBaro && gpsAltSampleCount > 0L

        val minAltM: Float
        val maxAltM: Float
        val maxAltChangeM: Float
        val elevationGainM: Float
        val netAltChangeM: Float
        val altitudeSource: String

        when {
            useBaro -> {
                minAltM = baroMinAltM
                maxAltM = baroMaxAltM
                maxAltChangeM = (baroMaxAltM - baroMinAltM).coerceAtLeast(0f)
                elevationGainM = baroGainM
                netAltChangeM = baroLastAltM - baroFirstAltM
                altitudeSource = "Barometer (5 Hz)"
            }
            useGps -> {
                minAltM = gpsMinAltM
                maxAltM = gpsMaxAltM
                maxAltChangeM = (gpsMaxAltM - gpsMinAltM).coerceAtLeast(0f)
                elevationGainM = gpsGainM
                netAltChangeM = gpsLastAltM - gpsFirstAltM
                altitudeSource = "GNSS Altitude"
            }
            else -> {
                minAltM = 0f
                maxAltM = 0f
                maxAltChangeM = 0f
                elevationGainM = 0f
                netAltChangeM = 0f
                altitudeSource = "Unavailable"
            }
        }

        // Close out single-sample or zero-interval audio exposure cleanly using session duration
        var finalNoiseExposureDbSec = totalNoiseExposureDbSec
        var finalNoiseEnergyDosage = totalNoiseEnergyDosage
        var finalAudioDurationSec = audioDurationSec
        var finalElevatedNoiseSec = elevatedNoiseDurationSec
        if (audioSampleCount > 0L && finalAudioDurationSec <= 0.0 && durationSec > 0.0) {
            val singleDuration = durationSec
            val aboveFloor = (prevAudioDbfs - NOISE_FLOOR_DBFS).coerceAtLeast(0f).toDouble()
            finalNoiseExposureDbSec = aboveFloor * singleDuration
            finalNoiseEnergyDosage = 10.0.pow(prevAudioDbfs / 10.0) * singleDuration
            finalAudioDurationSec = singleDuration
            if (prevAudioDbfs >= ELEVATED_NOISE_THRESHOLD_DBFS) {
                finalElevatedNoiseSec = singleDuration
            }
        }

        val eqNoiseDbfs = if (finalAudioDurationSec > 0.0 && finalNoiseEnergyDosage > 0.0) {
            (10.0 * log10(finalNoiseEnergyDosage / finalAudioDurationSec)).toFloat()
                .coerceIn(NOISE_FLOOR_DBFS, 0f)
        } else if (audioSampleCount > 0L) {
            prevAudioDbfs
        } else {
            NOISE_FLOOR_DBFS
        }

        // Reference moderate road noise = -30 dBFS (energy = 10^-3) over 600s -> dosage = 0.6
        val noiseIndexPct = if (audioSampleCount > 0L) {
            ((finalNoiseEnergyDosage / REFERENCE_NOISE_DOSAGE) * 100.0).toFloat().coerceAtLeast(0f)
        } else {
            0f
        }

        val avgRoadScore = if (sampleCount > 0L) {
            (roadScoreSum / sampleCount).toInt().coerceIn(0, 100)
        } else {
            100
        }

        val avgStreamHz = when {
            durationSec > 0.0 && sampleCount > 1L -> (sampleCount / durationSec).toFloat()
            sampleCount > 0L -> (streamHzSum / sampleCount).toFloat()
            else -> 0f
        }

        val summary = TripStatisticsSummary(
            sessionId = activeSessionId,
            sessionLabel = activeSessionLabel,
            startedAtMs = startedAtMs,
            endedAtMs = effectiveEndMs,
            durationSec = durationSec,
            sampleCount = sampleCount,
            averageSpeedKmh = avgSpeedKmh,
            maxSpeedKmh = maxSpeedKmh,
            estimatedDistanceKm = estimatedDistanceKm,
            minAltitudeM = minAltM,
            maxAltitudeM = maxAltM,
            maxAltitudeChangeM = maxAltChangeM,
            totalElevationGainM = elevationGainM,
            netAltitudeChangeM = netAltChangeM,
            altitudeSource = altitudeSource,
            pressureMinHpa = if (pressureMinHpa.isFinite()) pressureMinHpa else 0f,
            pressureMaxHpa = if (pressureMaxHpa.isFinite()) pressureMaxHpa else 0f,
            totalRoadNoiseExposureDbSec = finalNoiseExposureDbSec,
            totalNoiseEnergyDosage = finalNoiseEnergyDosage,
            noiseExposureIndexPct = noiseIndexPct,
            equivalentNoiseDbfs = eqNoiseDbfs,
            peakNoiseDbfs = if (audioSampleCount > 0L) peakNoiseDbfs else NOISE_FLOOR_DBFS,
            elevatedNoiseDurationSec = finalElevatedNoiseSec,
            hasAudioSamples = audioSampleCount > 0L,
            averageRoadScore = avgRoadScore,
            worstSurfaceClass = if (sampleCount > 0L) worstSurfaceClass else "SMOOTH",
            peakVibrationRms = peakVibrationRms,
            impactCount = impactCount,
            peakLateralG = peakLateralG,
            peakLongitudinalG = peakLongitudinalG,
            obd2ConnectedDuringTrip = obd2ConnectedDuringTrip,
            peakRpm = peakRpm,
            averageRpm = if (obd2SampleCount > 0L) (rpmSum / obd2SampleCount).toInt() else 0,
            maxCoolantTempC = if (maxCoolantTempC != Int.MIN_VALUE) maxCoolantTempC else 0,
            averageStreamHz = avgStreamHz,
        )

        _isSessionActive.value = false
        _lastCompletedSummary.value = summary
        _latestSummary.value = summary
        return summary
    }

    fun dismissSummary() {
        _latestSummary.value = null
    }

    fun reopenLastSummary() {
        _latestSummary.value = _lastCompletedSummary.value
    }

    fun restoreLastCompletedSummary(summary: TripStatisticsSummary?) {
        if (summary != null && _lastCompletedSummary.value == null) {
            _lastCompletedSummary.value = summary
        }
    }

    private fun resolveTimestampMs(snapshot: TelemetrySnapshot): Long {
        return if (snapshot.timestampSec > 0.0) {
            (snapshot.timestampSec * 1000.0).toLong()
        } else {
            System.currentTimeMillis()
        }
    }

    private fun surfaceSeverityRank(surfaceClass: String): Int = when (surfaceClass) {
        "IMPACT_ANOMALY" -> 3
        "ROUGH" -> 2
        "MODERATE" -> 1
        else -> 0
    }

    private fun resetAccumulators() {
        lastSampleTimeMs = 0L
        sampleCount = 0L

        speedSumKmh = 0.0
        weightedSpeedSumKmh = 0.0
        weightedDurationSec = 0.0
        prevSpeedKmh = 0f
        hasPrevSpeed = false
        maxSpeedKmh = 0f
        estimatedDistanceKm = 0.0

        baroSampleCount = 0L
        baroFirstAltM = 0f
        baroLastAltM = 0f
        baroMinAltM = Float.POSITIVE_INFINITY
        baroMaxAltM = Float.NEGATIVE_INFINITY
        baroGainM = 0f
        baroPrevAltM = 0f
        hasBaroPrevAlt = false
        pressureMinHpa = Float.POSITIVE_INFINITY
        pressureMaxHpa = Float.NEGATIVE_INFINITY

        gpsAltSampleCount = 0L
        gpsFirstAltM = 0f
        gpsLastAltM = 0f
        gpsMinAltM = Float.POSITIVE_INFINITY
        gpsMaxAltM = Float.NEGATIVE_INFINITY
        gpsGainM = 0f
        gpsPrevAltM = 0f
        hasGpsPrevAlt = false

        audioSampleCount = 0L
        audioDurationSec = 0.0
        prevAudioTimeMs = 0L
        prevAudioDbfs = NOISE_FLOOR_DBFS
        hasPrevAudioSample = false
        totalNoiseExposureDbSec = 0.0
        totalNoiseEnergyDosage = 0.0
        peakNoiseDbfs = NOISE_FLOOR_DBFS
        elevatedNoiseDurationSec = 0.0

        roadScoreSum = 0L
        worstSurfaceRank = 0
        worstSurfaceClass = "SMOOTH"
        peakVibrationRms = 0f
        impactCount = 0
        prevImpactDetected = false
        peakLateralG = 0f
        peakLongitudinalG = 0f

        obd2ConnectedDuringTrip = false
        obd2SampleCount = 0L
        rpmSum = 0L
        peakRpm = 0
        maxCoolantTempC = Int.MIN_VALUE
        streamHzSum = 0.0
    }

    companion object {
        private const val GRAVITY_MPS2 = 9.80665f
        private const val NOISE_FLOOR_DBFS = -90f
        private const val ELEVATED_NOISE_THRESHOLD_DBFS = -35f
        private const val ALTITUDE_GAIN_NOISE_THRESHOLD_M = 0.05f
        private const val MAX_INTERVAL_SEC = 10.0
        private const val REFERENCE_NOISE_DOSAGE = 0.6 // ~10 min at -30 dBFS road noise
    }
}
