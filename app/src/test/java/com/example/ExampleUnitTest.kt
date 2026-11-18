package com.example

import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `parsePidResponse computes vehicle speed 010D`() {
        val result = Obd2PidParser.parsePidResponse("010D", "410D34\r\n>")
        assertEquals(PidParseResult.VehicleSpeed(52), result)
    }

    @Test
    fun `parsePidResponse computes engine rpm 010C`() {
        val result = Obd2PidParser.parsePidResponse("010C", "41 0C 25 80\r>")
        assertEquals(PidParseResult.EngineRpm(2400), result)
    }

    @Test
    fun `parsePidResponse computes throttle position 0111`() {
        val result = Obd2PidParser.parsePidResponse("0111", "411180\r")
        assertTrue(result is PidParseResult.ThrottlePosition)
        assertEquals((100f * 128) / 255f, (result as PidParseResult.ThrottlePosition).percent, 0.01f)
    }

    @Test
    fun `parsePidResponse computes coolant temp 0105`() {
        val result = Obd2PidParser.parsePidResponse("0105", "410580\r")
        assertEquals(PidParseResult.CoolantTemp(88), result)
    }

    @Test
    fun `parsePidResponse computes engine load 0104`() {
        val result = Obd2PidParser.parsePidResponse("0104", "410457\r")
        assertTrue(result is PidParseResult.EngineLoad)
        assertEquals((100f * 87) / 255f, (result as PidParseResult.EngineLoad).percent, 0.01f)
    }

    @Test
    fun `parsePidResponse computes intake temp MAF timing and fuel level`() {
        assertEquals(PidParseResult.IntakeAirTemp(25), Obd2PidParser.parsePidResponse("010F", "410F41\r"))

        val maf = Obd2PidParser.parsePidResponse("0110", "411004B0\r")
        assertTrue(maf is PidParseResult.MafAirFlow)
        assertEquals(12.0f, (maf as PidParseResult.MafAirFlow).gramsPerSec, 0.01f)

        val timing = Obd2PidParser.parsePidResponse("010E", "410E98\r")
        assertTrue(timing is PidParseResult.TimingAdvance)
        assertEquals(12.0f, (timing as PidParseResult.TimingAdvance).degrees, 0.01f)

        val fuel = Obd2PidParser.parsePidResponse("012F", "412F80\r")
        assertTrue(fuel is PidParseResult.FuelLevel)
        assertEquals((100f * 128) / 255f, (fuel as PidParseResult.FuelLevel).percent, 0.01f)
    }

    @Test
    fun `parseDtcResponse decodes standard SAE J2012 trouble codes`() {
        // 0300 -> P0300, 4120 -> C0120, 8010 -> B0010, C101 -> U0101, 0000 -> ignored
        val codes = Obd2PidParser.parseDtcResponse("43 0300 4120 8010 C101 0000\r>")
        assertEquals(listOf("P0300", "C0120", "B0010", "U0101"), codes)
    }

    @Test
    fun `parsePidResponse detects NO DATA and UNABLE TO CONNECT errors`() {
        assertEquals(
            PidParseResult.Error("NO DATA"),
            Obd2PidParser.parsePidResponse("010C", "NO DATA\r"),
        )
        assertEquals(
            PidParseResult.Error("UNABLE TO CONNECT"),
            Obd2PidParser.parsePidResponse("010C", "UNABLE TO CONNECT\r"),
        )
    }

    @Test
    fun `roadConditionAnalyzer classifies smooth rough and impact anomaly`() {
        val analyzer = RoadConditionAnalyzer()

        val smooth = analyzer.analyzeSample(
            linAx = 0.05f,
            linAy = -0.04f,
            linAz = 0.08f,
            gyroMagRad = 0.02f,
            speedMps = 12f,
            audioRmsDbfs = -45f,
            audioActive = true,
        )
        assertEquals("SMOOTH", smooth.surfaceClass)
        assertFalse(smooth.impactDetected)
        assertTrue(smooth.conditionScore >= 80)

        var latest = smooth
        repeat(20) {
            latest = analyzer.analyzeSample(
                linAx = 2.4f,
                linAy = 2.0f,
                linAz = 3.2f,
                gyroMagRad = 0.6f,
                speedMps = 15f,
                audioRmsDbfs = -16f,
                audioActive = true,
            )
        }
        assertEquals("ROUGH", latest.surfaceClass)
        assertFalse(latest.impactDetected)
        assertTrue(latest.conditionScore < 55)
        assertTrue(latest.vibrationRms > 3.0f)

        val impact = analyzer.analyzeSample(
            linAx = 0.2f,
            linAy = 0.3f,
            linAz = 8.4f,
            gyroMagRad = 0.5f,
            speedMps = 0.2f,
            audioRmsDbfs = -20f,
            audioActive = true,
        )
        assertTrue(impact.impactDetected)
        assertEquals("IMPACT_ANOMALY", impact.surfaceClass)
    }

    @Test
    fun `extractAcousticFeatures computes silence and 1khz tone accurately`() {
        val silence = ShortArray(1600)
        val silenceFeatures = AudioTelemetryManager.extractAcousticFeatures(silence, silence.size, 16000)
        assertTrue(silenceFeatures.active)
        assertEquals(-90f, silenceFeatures.rmsDbfs, 0.01f)
        assertEquals(0f, silenceFeatures.peakAmp, 0.001f)
        assertEquals(0f, silenceFeatures.zcrHz, 0.01f)

        val tone = ShortArray(1600) { i ->
            (sin(2.0 * PI * 1000.0 * i / 16000.0) * 16384.0).toInt().toShort()
        }
        val toneFeatures = AudioTelemetryManager.extractAcousticFeatures(tone, tone.size, 16000)
        assertTrue(toneFeatures.active)
        assertEquals(-9.03f, toneFeatures.rmsDbfs, 0.3f)
        assertEquals(0.5f, toneFeatures.peakAmp, 0.02f)
        assertEquals(1000f, toneFeatures.zcrHz, 25f)
    }

    @Test
    fun `udpTelemetryClient defaults to port 5005 and updates target endpoint`() {
        assertEquals(5005, UdpTelemetryClient.DEFAULT_PORT)
        val client = UdpTelemetryClient()
        try {
            assertEquals(5005, client.stats.value.targetPort)
            client.setTarget("192.168.1.42", 5005)
            assertEquals("192.168.1.42", client.stats.value.targetIp)
            assertEquals(5005, client.stats.value.targetPort)
        } finally {
            client.close()
        }
    }

    @Test
    fun `tripStatisticsService calculates average speed max altitude change and road noise exposure`() {
        val service = TripStatisticsService()
        val t0 = 1_000_000L
        service.startSession(sessionId = "RoadEye_Node01", sessionLabel = "UDP 20Hz", startTimeMs = t0)
        assertTrue(service.isSessionActive.value)

        service.recordSnapshot(
            TelemetrySnapshot(
                deviceId = "RoadEye_Node01",
                seq = 1L,
                timestampSec = t0 / 1000.0,
                streamHz = 20,
                sensors = SensorsState(
                    barometer = BarometerState(pressureHpa = 910.0f, relAltitudeM = 900.0f),
                    audio = AudioTelemetryState(active = true, rmsDbfs = -30.0f),
                    gps = GpsState(speedMps = 10.0f, hasFix = true),
                    availability = SensorAvailabilityState(barometer = true, audio = true),
                ),
                roadIntelligence = RoadIntelligenceState(
                    conditionScore = 90,
                    surfaceClass = "SMOOTH",
                    vibrationRms = 0.6f,
                    impactDetected = false,
                ),
                obd2 = Obd2State(connected = true, speedKmh = 36, rpm = 2000, coolantTempC = 85),
            ),
            sampleTimeMs = t0,
        )

        service.recordSnapshot(
            TelemetrySnapshot(
                deviceId = "RoadEye_Node01",
                seq = 2L,
                timestampSec = (t0 + 5_000L) / 1000.0,
                streamHz = 20,
                sensors = SensorsState(
                    barometer = BarometerState(pressureHpa = 905.0f, relAltitudeM = 945.0f),
                    audio = AudioTelemetryState(active = true, rmsDbfs = -20.0f),
                    gps = GpsState(speedMps = 20.0f, hasFix = true),
                    availability = SensorAvailabilityState(barometer = true, audio = true),
                ),
                roadIntelligence = RoadIntelligenceState(
                    conditionScore = 50,
                    surfaceClass = "ROUGH",
                    vibrationRms = 4.2f,
                    impactDetected = true,
                ),
                obd2 = Obd2State(connected = true, speedKmh = 72, rpm = 3500, coolantTempC = 92),
            ),
            sampleTimeMs = t0 + 5_000L,
        )

        service.recordSnapshot(
            TelemetrySnapshot(
                deviceId = "RoadEye_Node01",
                seq = 3L,
                timestampSec = (t0 + 10_000L) / 1000.0,
                streamHz = 20,
                sensors = SensorsState(
                    barometer = BarometerState(pressureHpa = 908.0f, relAltitudeM = 920.0f),
                    audio = AudioTelemetryState(active = true, rmsDbfs = -40.0f),
                    gps = GpsState(speedMps = 15.0f, hasFix = true),
                    availability = SensorAvailabilityState(barometer = true, audio = true),
                ),
                roadIntelligence = RoadIntelligenceState(
                    conditionScore = 85,
                    surfaceClass = "MODERATE",
                    vibrationRms = 1.4f,
                    impactDetected = false,
                ),
                obd2 = Obd2State(connected = true, speedKmh = 54, rpm = 2600, coolantTempC = 90),
            ),
            sampleTimeMs = t0 + 10_000L,
        )

        val summary = service.endSession(endTimeMs = t0 + 10_000L)
        assertFalse(service.isSessionActive.value)
        assertEquals(3L, summary.sampleCount)
        assertEquals(10.0, summary.durationSec, 0.01)
        assertEquals(58.5f, summary.averageSpeedKmh, 0.05f)
        assertEquals(72.0f, summary.maxSpeedKmh, 0.05f)
        assertEquals(45.0f, summary.maxAltitudeChangeM, 0.05f)
        assertEquals(45.0f, summary.totalElevationGainM, 0.05f)
        assertEquals(20.0f, summary.netAltitudeChangeM, 0.05f)
        assertEquals("Barometer (5 Hz)", summary.altitudeSource)
        assertEquals(625.0, summary.totalRoadNoiseExposureDbSec, 0.5)
        assertEquals(-20.0f, summary.peakNoiseDbfs, 0.05f)
        assertEquals(10.0, summary.elevatedNoiseDurationSec, 0.05)
        assertTrue(summary.hasAudioSamples)
        assertEquals(1, summary.impactCount)
        assertEquals("ROUGH", summary.worstSurfaceClass)
        assertEquals(3500, summary.peakRpm)
        assertEquals(92, summary.maxCoolantTempC)
    }

    @Test
    fun `tripStatisticsService uses GNSS altitude fallback and handles inactive audio`() {
        val service = TripStatisticsService()
        val t0 = 2_000_000L
        service.startSession(startTimeMs = t0)

        service.recordSnapshot(
            TelemetrySnapshot(
                timestampSec = t0 / 1000.0,
                sensors = SensorsState(
                    gps = GpsState(altitude = 120.0, speedMps = 10.0f, hasFix = true),
                    audio = AudioTelemetryState(active = false, rmsDbfs = -90f),
                ),
            ),
            sampleTimeMs = t0,
        )
        service.recordSnapshot(
            TelemetrySnapshot(
                timestampSec = (t0 + 4_000L) / 1000.0,
                sensors = SensorsState(
                    gps = GpsState(altitude = 152.5, speedMps = 10.0f, hasFix = true),
                    audio = AudioTelemetryState(active = false, rmsDbfs = -90f),
                ),
            ),
            sampleTimeMs = t0 + 4_000L,
        )

        val summary = service.endSession(endTimeMs = t0 + 4_000L)
        assertEquals(36.0f, summary.averageSpeedKmh, 0.05f)
        assertEquals(32.5f, summary.maxAltitudeChangeM, 0.05f)
        assertEquals("GNSS Altitude", summary.altitudeSource)
        assertFalse(summary.hasAudioSamples)
        assertEquals(0.0, summary.totalRoadNoiseExposureDbSec, 0.001)
    }
}
