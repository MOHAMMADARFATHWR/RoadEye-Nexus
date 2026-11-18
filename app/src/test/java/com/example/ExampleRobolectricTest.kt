package com.example

import android.content.ComponentName
import android.content.Context
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.core.app.ApplicationProvider
import com.example.ui.theme.RoadEyeNexusTheme
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `app name matches RoadEye Nexus`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("RoadEye Nexus", appName)
    }

    @Test
    fun `main activity declares Theme_RoadEyeNexus_Starting splash theme`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val activityInfo = context.packageManager.getActivityInfo(
            ComponentName(context, MainActivity::class.java),
            0,
        )
        assertEquals(R.style.Theme_RoadEyeNexus_Starting, activityInfo.themeResource)
    }

    @Test
    fun `telemetry snapshot serializes exact required v2 json schema with seq and crc32`() {
        val holder = TelemetryStateHolder()
        holder.setMetadata(
            deviceId = "RoadEye_Nexus_Node01",
            streamHz = 20,
            thermalStatus = "NOMINAL",
        )
        holder.setSensorAvailability(
            SensorAvailabilityState(
                accel = true,
                gyro = true,
                mag = true,
                gravity = true,
                linearAccel = true,
                rotationVector = true,
                barometer = true,
                light = true,
                proximity = true,
                audio = true,
            ),
        )
        holder.updateAccelAndOrientation(0.12f, 9.81f, -0.45f, 1.5f, -0.8f, 182.4f)
        holder.updateGyro(0.01f, -0.02f, 0.05f)
        holder.updateMag(25.1f, -12.3f, 45.0f)
        holder.updateGravity(0.05f, 9.80f, 0.11f)
        holder.updateLinearAccelAndRoad(
            x = 0.22f,
            y = -0.14f,
            z = 0.85f,
            roadIntelligence = RoadIntelligenceState(
                conditionScore = 88,
                surfaceClass = "SMOOTH",
                vibrationRms = 0.885f,
                impactDetected = false,
            ),
        )
        holder.updateRotationVector(0.012f, -0.008f, 0.707f, 0.707f)
        holder.updateBarometer(pressureHpa = 910.25f, relAltitudeM = 895.4f)
        holder.updateAmbientLight(lux = 1240.5f, condition = "DAYLIGHT")
        holder.updateProximity(distanceCm = 5.0f, isNear = false)
        holder.updateAudio(
            AudioTelemetryState(
                active = true,
                rmsDbfs = -34.2f,
                peakAmp = 0.125f,
                zcrHz = 420.5f,
            ),
        )
        holder.updateDeviceHealth(
            DeviceHealthState(
                batteryPct = 82,
                batteryTempC = 33.4f,
                charging = true,
                wifiRssiDbm = -54,
                wifiLinkMbps = 433,
            ),
        )
        holder.updateGps(
            lat = 13.1340,
            lng = 77.9620,
            altitude = 920.0,
            speedMps = 14.5f,
            bearing = 182.4f,
            accuracyM = 4.2f,
            hasFix = true,
        )
        holder.updateObd2 {
            it.copy(
                connected = true,
                protocol = "ISO 15765-4 CAN",
                speedKmh = 52,
                rpm = 2400,
                throttlePos = 18.5f,
                coolantTempC = 88,
                engineLoad = 34.2f,
                intakeTempC = 29,
                mafGps = 14.25f,
                timingAdvanceDeg = 12.5f,
                fuelLevelPct = 68.0f,
                dtcs = listOf("P0300"),
            )
        }

        val json = holder.snapshot(1731800000.123, seq = 42L).toJsonObject()
        assertEquals("RoadEye_Nexus_Node01", json.getString("device_id"))
        assertEquals(42L, json.getLong("seq"))
        assertEquals(1731800000.123, json.getDouble("timestamp"), 0.001)
        assertEquals(20, json.getInt("stream_hz"))
        assertEquals("NOMINAL", json.getString("thermal_status"))
        assertTrue(json.getString("crc32").length == 8)

        val sensors = json.getJSONObject("sensors")
        assertEquals(9.81, sensors.getJSONObject("accel").getDouble("y"), 0.01)
        assertEquals(-0.02, sensors.getJSONObject("gyro").getDouble("y"), 0.001)
        assertEquals(25.1, sensors.getJSONObject("mag").getDouble("x"), 0.01)
        assertEquals(9.80, sensors.getJSONObject("gravity").getDouble("y"), 0.01)
        assertEquals(0.85, sensors.getJSONObject("linear_accel").getDouble("z"), 0.01)
        assertEquals(0.707, sensors.getJSONObject("rotation_vector").getDouble("w"), 0.001)
        assertEquals(1.5, sensors.getJSONObject("orientation").getDouble("pitch_deg"), 0.01)

        val baro = sensors.getJSONObject("barometer")
        assertEquals(910.25, baro.getDouble("pressure_hpa"), 0.01)
        assertEquals(895.4, baro.getDouble("rel_altitude_m"), 0.1)

        val light = sensors.getJSONObject("ambient_light")
        assertEquals(1240.5, light.getDouble("lux"), 0.1)
        assertEquals("DAYLIGHT", light.getString("condition"))

        val prox = sensors.getJSONObject("proximity")
        assertEquals(5.0, prox.getDouble("distance_cm"), 0.1)
        assertFalse(prox.getBoolean("is_near"))

        val audio = sensors.getJSONObject("audio")
        assertTrue(audio.getBoolean("active"))
        assertEquals(-34.2, audio.getDouble("rms_dbfs"), 0.1)
        assertEquals(0.125, audio.getDouble("peak_amp"), 0.001)
        assertEquals(420.5, audio.getDouble("zcr_hz"), 0.1)

        val avail = sensors.getJSONObject("availability")
        assertTrue(avail.getBoolean("gravity"))
        assertTrue(avail.getBoolean("linear_accel"))
        assertTrue(avail.getBoolean("rotation_vector"))
        assertTrue(avail.getBoolean("barometer"))
        assertTrue(avail.getBoolean("light"))
        assertTrue(avail.getBoolean("proximity"))
        assertTrue(avail.getBoolean("audio"))

        val health = json.getJSONObject("device_health")
        assertEquals(82, health.getInt("battery_pct"))
        assertEquals(33.4, health.getDouble("battery_temp_c"), 0.1)
        assertTrue(health.getBoolean("charging"))
        assertEquals(-54, health.getInt("wifi_rssi_dbm"))
        assertEquals(433, health.getInt("wifi_link_mbps"))

        val road = json.getJSONObject("road_intelligence")
        assertEquals(88, road.getInt("condition_score"))
        assertEquals("SMOOTH", road.getString("surface_class"))
        assertEquals(0.885, road.getDouble("vibration_rms"), 0.001)
        assertFalse(road.getBoolean("impact_detected"))

        val gps = sensors.getJSONObject("gps")
        assertEquals(13.1340, gps.getDouble("lat"), 0.0001)
        assertEquals(77.9620, gps.getDouble("lng"), 0.0001)
        assertEquals(14.5, gps.getDouble("speed_mps"), 0.01)
        assertEquals(182.4, gps.getDouble("bearing"), 0.01)
        assertEquals(4.2, gps.getDouble("accuracy_m"), 0.1)
        assertTrue(gps.getBoolean("has_fix"))

        val obd2 = json.getJSONObject("obd2")
        assertTrue(obd2.getBoolean("connected"))
        assertEquals("ISO 15765-4 CAN", obd2.getString("protocol"))
        assertEquals(52, obd2.getInt("speed_kmh"))
        assertEquals(2400, obd2.getInt("rpm"))
        assertEquals(18.5, obd2.getDouble("throttle_pos"), 0.1)
        assertEquals(88, obd2.getInt("coolant_temp_c"))
        assertEquals(34.2, obd2.getDouble("engine_load"), 0.1)
        assertEquals(29, obd2.getInt("intake_temp_c"))
        assertEquals(14.25, obd2.getDouble("maf_gps"), 0.01)
        assertEquals(12.5, obd2.getDouble("timing_advance_deg"), 0.1)
        assertEquals(68.0, obd2.getDouble("fuel_level_pct"), 0.1)
        assertEquals(1, obd2.getInt("dtc_count"))
        assertEquals("P0300", obd2.getJSONArray("dtcs").getString(0))
    }

    @Test
    fun `sensorTelemetryData serializes and round trips IMU and GNSS UDP payload`() {
        val data = SensorTelemetryData(
            timestamp = 1731800123456L,
            accelX = 0.25f,
            accelY = 9.79f,
            accelZ = -0.31f,
            gyroX = 0.012f,
            gyroY = -0.004f,
            gyroZ = 0.089f,
            lat = 12.9716,
            lon = 77.5946,
            alt = 918.5,
            speed = 18.2f,
            atmosphericPressureHpa = 912.45f,
            audioRmsDbfs = -31.8f,
            deviceId = "RoadEye_Nexus_Node02",
            seq = 77L,
            streamHz = 50,
            hasFix = true,
            availability = SensorAvailabilityState(accel = true, gyro = true, mag = true, barometer = true, audio = true),
        )

        val payloadBytes = SensorTelemetryJsonSerializer.toUdpPayloadBytes(data)
        val rawJson = payloadBytes.toString(Charsets.UTF_8)
        val decoded = SensorTelemetryJsonSerializer.fromJson(rawJson)

        assertEquals(1731800123456L, decoded.timestamp)
        assertEquals(0.25f, decoded.accelX, 0.01f)
        assertEquals(9.79f, decoded.accelY, 0.01f)
        assertEquals(-0.31f, decoded.accelZ, 0.01f)
        assertEquals(0.012f, decoded.gyroX, 0.001f)
        assertEquals(-0.004f, decoded.gyroY, 0.001f)
        assertEquals(0.089f, decoded.gyroZ, 0.001f)
        assertEquals(12.9716, decoded.lat, 0.0001)
        assertEquals(77.5946, decoded.lon, 0.0001)
        assertEquals(918.5, decoded.alt, 0.1)
        assertEquals(18.2f, decoded.speed, 0.01f)
        assertEquals(912.45f, decoded.atmosphericPressureHpa, 0.01f)
        assertEquals(-31.8f, decoded.audioRmsDbfs, 0.1f)
        assertEquals("RoadEye_Nexus_Node02", decoded.deviceId)
        assertEquals(77L, decoded.seq)
        assertEquals(50, decoded.streamHz)
        assertTrue(decoded.hasFix)
        assertTrue(decoded.availability.accel)
        assertTrue(decoded.availability.barometer)
        assertTrue(decoded.availability.audio)
    }

    @Test
    fun `udpTelemetryClient transmits SensorTelemetryData over DatagramSocket`() = runBlocking {
        DatagramSocket(0, InetAddress.getByName("127.0.0.1")).use { receiver ->
            receiver.soTimeout = 3000
            val client = UdpTelemetryClient(
                initialTargetIp = "127.0.0.1",
                initialTargetPort = receiver.localPort,
            )
            try {
                val sample = SensorTelemetryData(
                    timestamp = 1731800200000L,
                    accelX = 0.14f,
                    accelY = 9.81f,
                    accelZ = -0.22f,
                    gyroX = 0.005f,
                    gyroY = -0.011f,
                    gyroZ = 0.042f,
                    lat = 13.0827,
                    lon = 80.2707,
                    alt = 16.5,
                    speed = 12.3f,
                    seq = 9L,
                )

                client.sendAsync(sample).join()

                val buffer = ByteArray(4096)
                val packet = DatagramPacket(buffer, buffer.size)
                receiver.receive(packet)

                val receivedJson = String(packet.data, 0, packet.length, Charsets.UTF_8)
                val decoded = SensorTelemetryJsonSerializer.fromJson(receivedJson)

                assertEquals(1731800200000L, decoded.timestamp)
                assertEquals(9.81f, decoded.accelY, 0.01f)
                assertEquals(0.042f, decoded.gyroZ, 0.001f)
                assertEquals(13.0827, decoded.lat, 0.0001)
                assertEquals(80.2707, decoded.lon, 0.0001)
                assertEquals(12.3f, decoded.speed, 0.01f)
                assertEquals(1L, client.stats.value.packetsSent)
                assertNull(client.stats.value.lastError)
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `viewModel lifecycle observer toggles SensorTelemetryManager collection state`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val controller = TelemetrySessionController.getInstance(app)
        val viewModel = RoadEyeViewModel(app)
        val owner = object : androidx.lifecycle.LifecycleOwner {
            override val lifecycle = androidx.lifecycle.LifecycleRegistry(this)
        }

        viewModel.onStart(owner)
        assertTrue(controller.sensorManager.isCollecting.value)

        viewModel.onStop(owner)
        assertFalse(controller.sensorManager.isCollecting.value)
    }

    @Test
    fun `compose dashboard renders stream health summary and transitions tabs`() {
        var currentMode by mutableStateOf(HudMode.COCKPIT)
        val sampleState = CockpitUiState(
            hudMode = HudMode.COCKPIT,
            isCollectingSensors = true,
            streamerStatus = StreamerStatus(
                isStreaming = true,
                packetsSent = 120L,
                sequenceNumber = 120L,
                lastPacketBytes = 842,
                measuredHz = 20.0f,
                configuredHz = 20,
                effectiveHz = 20,
                targetEndpoint = "192.168.43.100:5005",
            ),
            telemetry = TelemetrySnapshot(
                sensors = SensorsState(
                    accel = Vector3State(0.5f, 9.8f, 0.2f),
                    gps = GpsState(lat = 13.0, lng = 80.2, speedMps = 12.5f, accuracyM = 4.0f, hasFix = true),
                ),
                roadIntelligence = RoadIntelligenceState(conditionScore = 88, surfaceClass = "SMOOTH", vibrationRms = 0.72f),
                obd2 = Obd2State(connected = true, rpm = 2150, speedKmh = 48),
            ),
        )

        composeRule.setContent {
            RoadEyeNexusTheme {
                androidx.compose.foundation.layout.Column {
                    CockpitHeaderBanner(
                        streamerStatus = sampleState.streamerStatus,
                        recordingStatus = sampleState.recordingStatus,
                        deviceId = sampleState.settings.deviceId,
                        hudMode = currentMode,
                        onHudModeSelected = { currentMode = it },
                    )
                    StreamHealthSummaryCard(uiState = sampleState)
                }
            }
        }

        composeRule.onNodeWithTag("stream_health_summary_card").assertIsDisplayed()
        composeRule.onNodeWithTag("stream_health_status_row").assertIsDisplayed()
        composeRule.onNodeWithTag("stream_health_active_channels_label").assertIsDisplayed()
        composeRule.onNodeWithTag("hud_mode_race").performClick()
        assertEquals(HudMode.RACE, currentMode)
        composeRule.onNodeWithTag("hud_mode_settings").performClick()
        assertEquals(HudMode.SETTINGS, currentMode)
    }

    @Test
    fun `streamHealthSummaryCard status bar stays single line without vertical wrapping on narrow screen`() {
        val narrowState = CockpitUiState(
            isCollectingSensors = true,
            settings = TelemetrySettings(streamRateHz = 50),
            streamerStatus = StreamerStatus(
                isStreaming = false,
                effectiveHz = 20,
                thermalStatus = "NOMINAL",
            ),
            telemetry = TelemetrySnapshot(
                sensors = SensorsState(
                    availability = SensorAvailabilityState(accel = true),
                ),
                deviceHealth = DeviceHealthState(batteryPct = 56),
            ),
        )

        composeRule.setContent {
            RoadEyeNexusTheme {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier.width(320.dp),
                ) {
                    StreamHealthSummaryCard(uiState = narrowState)
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("50HZ · 56% BAT · NOMINAL").assertIsDisplayed()
        composeRule.onNodeWithText("1/4 ACTIVE").assertIsDisplayed()
        val containerBounds = composeRule.onNodeWithTag("stream_health_status_bar_container").getBoundsInRoot()
        val rowBounds = composeRule.onNodeWithTag("stream_health_status_row").getBoundsInRoot()
        val throughputBounds = composeRule.onNodeWithTag("stream_health_throughput_label").getBoundsInRoot()
        val activeBounds = composeRule.onNodeWithTag("stream_health_active_channels_label").getBoundsInRoot()
        val throughputWidth = throughputBounds.right - throughputBounds.left
        assertTrue("Expected compact single-line status row, got ${rowBounds.height}", rowBounds.height <= 40.dp)
        assertTrue("Expected single-line active channels label, got ${activeBounds.height}", activeBounds.height <= 40.dp)
        assertTrue(
            "Expected throughput label ($throughputWidth) to take remaining width >= 150.dp",
            throughputWidth >= 150.dp,
        )
        assertTrue(
            "Expected labels side-by-side without overlap: ${throughputBounds.right} <= ${activeBounds.left}",
            throughputBounds.right <= activeBounds.left,
        )
        assertTrue(
            "Expected active channels label clipped/aligned within container: ${activeBounds.right} <= ${containerBounds.right}",
            activeBounds.right <= containerBounds.right,
        )
    }

    @Test
    fun `statusBarContainer auto-collapses overflow drawer on narrow screen and expands scrollable hidden indicators`() {
        var containerWidth by mutableStateOf(320.dp)
        val state = CockpitUiState(
            isCollectingSensors = true,
            settings = TelemetrySettings(streamRateHz = 50),
            streamerStatus = StreamerStatus(
                isStreaming = true,
                effectiveHz = 50,
                measuredHz = 49.8f,
                thermalStatus = "NOMINAL",
            ),
            telemetry = TelemetrySnapshot(
                sensors = SensorsState(
                    barometer = BarometerState(pressureHpa = 910.4f),
                    audio = AudioTelemetryState(active = true, rmsDbfs = -31.2f),
                    availability = SensorAvailabilityState(accel = true, barometer = true, audio = true),
                ),
                deviceHealth = DeviceHealthState(batteryPct = 78, batteryTempC = 34.0f, wifiRssiDbm = -54),
                roadIntelligence = RoadIntelligenceState(surfaceClass = "SMOOTH"),
            ),
        )

        composeRule.setContent {
            RoadEyeNexusTheme {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier.requiredWidth(containerWidth),
                ) {
                    StreamHealthSummaryCard(uiState = state)
                }
            }
        }
        composeRule.waitForIdle()

        // Narrow screen: overflow drawer starts auto-collapsed with toggle pill visible
        composeRule.onNodeWithTag("status_bar_overflow_toggle").assertIsDisplayed()
        composeRule.onNodeWithTag("status_bar_overflow_drawer").assertDoesNotExist()

        // Expanding reveals the scrollable overflow drawer with hidden status indicators
        composeRule.onNodeWithTag("status_bar_overflow_toggle").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("status_bar_overflow_drawer").assertIsDisplayed()
        composeRule.onNodeWithTag("status_bar_overflow_scroller").assertIsDisplayed()
        composeRule.onNodeWithTag("status_indicator_rec").assertIsDisplayed()

        // Collapsing hides the drawer again
        composeRule.onNodeWithTag("status_bar_overflow_toggle").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("status_bar_overflow_drawer").assertDoesNotExist()

        // Expanding again, then widening container enough for all chips to fit inline auto-collapses drawer & removes toggle
        composeRule.onNodeWithTag("status_bar_overflow_toggle").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("status_bar_overflow_drawer").assertIsDisplayed()

        containerWidth = 900.dp
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("status_bar_overflow_drawer").assertDoesNotExist()
        composeRule.onNodeWithTag("status_bar_overflow_toggle").assertDoesNotExist()
        composeRule.onNodeWithTag("status_indicator_rec").assertExists()
    }

    @Test
    fun `statusBarContainer horizontal drag gesture scrolls overflowed status indicators`() {
        val state = CockpitUiState(
            isCollectingSensors = true,
            settings = TelemetrySettings(streamRateHz = 50),
            streamerStatus = StreamerStatus(
                isStreaming = true,
                effectiveHz = 50,
                measuredHz = 49.8f,
                thermalStatus = "NOMINAL",
            ),
            telemetry = TelemetrySnapshot(
                sensors = SensorsState(
                    barometer = BarometerState(pressureHpa = 910.4f),
                    audio = AudioTelemetryState(active = true, rmsDbfs = -31.2f),
                    availability = SensorAvailabilityState(accel = true, barometer = true, audio = true),
                ),
                deviceHealth = DeviceHealthState(batteryPct = 78, batteryTempC = 34.0f, wifiRssiDbm = -54),
                roadIntelligence = RoadIntelligenceState(surfaceClass = "SMOOTH"),
            ),
        )

        composeRule.setContent {
            RoadEyeNexusTheme {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier.width(200.dp),
                ) {
                    StreamHealthSummaryCard(uiState = state)
                }
            }
        }
        composeRule.waitForIdle()

        val initialRecBounds = composeRule.onNodeWithTag("scroll_status_indicator_rec").getUnclippedBoundsInRoot()
        composeRule.onNodeWithTag("stream_health_status_bar_container").performTouchInput {
            swipeLeft(startX = right * 0.85f, endX = left + 10f)
        }
        composeRule.waitForIdle()

        val scrolledRecBounds = composeRule.onNodeWithTag("scroll_status_indicator_rec").getUnclippedBoundsInRoot()
        assertTrue(
            "Expected horizontal drag on container to scroll overflowed indicator leftward: ${scrolledRecBounds.left} < ${initialRecBounds.left}",
            scrolledRecBounds.left < initialRecBounds.left,
        )

        composeRule.onNodeWithTag("status_bar_inline_scroller").performTouchInput {
            swipeRight(startX = left + 10f, endX = right * 0.85f)
        }
        composeRule.waitForIdle()

        val restoredRecBounds = composeRule.onNodeWithTag("scroll_status_indicator_rec").getUnclippedBoundsInRoot()
        assertTrue(
            "Expected horizontal swipeRight to scroll indicator back rightward: ${restoredRecBounds.left} > ${scrolledRecBounds.left}",
            restoredRecBounds.left > scrolledRecBounds.left,
        )
    }

    @Test
    fun `tripSummaryOverlay displays scrollable trip statistics and dismisses cleanly`() {
        var dismissed = false
        val summary = TripStatisticsSummary(
            sessionId = "RoadEye_Node01",
            sessionLabel = "UDP 20Hz -> 192.168.43.100:5005",
            startedAtMs = 1_731_800_000_000L,
            endedAtMs = 1_731_800_180_000L,
            durationSec = 180.0,
            sampleCount = 3600L,
            averageSpeedKmh = 64.5f,
            maxSpeedKmh = 98.2f,
            estimatedDistanceKm = 3.23,
            minAltitudeM = 890.0f,
            maxAltitudeM = 932.5f,
            maxAltitudeChangeM = 42.5f,
            totalElevationGainM = 48.0f,
            netAltitudeChangeM = 18.0f,
            altitudeSource = "Barometer (5 Hz)",
            pressureMinHpa = 906.2f,
            pressureMaxHpa = 911.4f,
            totalRoadNoiseExposureDbSec = 9420.0,
            totalNoiseEnergyDosage = 0.24,
            noiseExposureIndexPct = 40.0f,
            equivalentNoiseDbfs = -28.4f,
            peakNoiseDbfs = -14.2f,
            elevatedNoiseDurationSec = 72.0,
            hasAudioSamples = true,
            averageRoadScore = 84,
            worstSurfaceClass = "MODERATE",
            peakVibrationRms = 2.65f,
            impactCount = 2,
            peakLateralG = 0.52f,
            peakLongitudinalG = 0.41f,
            obd2ConnectedDuringTrip = true,
            peakRpm = 4200,
            averageRpm = 2350,
            maxCoolantTempC = 91,
            averageStreamHz = 20.0f,
        )

        var exportJsonClicked = false
        composeRule.setContent {
            RoadEyeNexusTheme {
                TripSummaryOverlay(
                    summary = summary,
                    canExportRecording = false,
                    lastExportedFileName = "roadeye_trip_stats_test.json",
                    onDismiss = { dismissed = true },
                    onExportRecording = {},
                    onExportStatsJson = { exportJsonClicked = true },
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("trip_summary_overlay").assertIsDisplayed()
        composeRule.onNodeWithTag("trip_summary_scroll_container").assertIsDisplayed()
        composeRule.onNodeWithText("64.5 km/h").assertIsDisplayed()
        composeRule.onNodeWithText("42.5 m").assertIsDisplayed()
        composeRule.onNodeWithTag("trip_kpi_noise_exposure").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("9420 dB·s").assertIsDisplayed()
        composeRule.onNodeWithTag("trip_summary_noise_section").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("trip_summary_road_section").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("trip_summary_exported_badge").assertIsDisplayed()
        composeRule.onNodeWithTag("trip_summary_export_json_button").performClick()
        assertTrue(exportJsonClicked)
        composeRule.onNodeWithTag("dismiss_trip_summary_button").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun `tripStatisticsExportRepository persists summary in DataStore and exports shareable JSON file`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = TripStatisticsExportRepository(app)
        val summary = TripStatisticsSummary(
            sessionId = "RoadEye_DS_Test",
            sessionLabel = "UDP 20Hz -> 192.168.1.50:5005",
            startedAtMs = 1_731_810_000_000L,
            endedAtMs = 1_731_810_120_000L,
            durationSec = 120.0,
            sampleCount = 2400L,
            averageSpeedKmh = 55.2f,
            maxSpeedKmh = 84.6f,
            estimatedDistanceKm = 1.84,
            minAltitudeM = 400.0f,
            maxAltitudeM = 436.8f,
            maxAltitudeChangeM = 36.8f,
            totalElevationGainM = 40.0f,
            netAltitudeChangeM = 14.5f,
            altitudeSource = "Barometer (5 Hz)",
            pressureMinHpa = 960.1f,
            pressureMaxHpa = 964.4f,
            totalRoadNoiseExposureDbSec = 5120.5,
            totalNoiseEnergyDosage = 0.145,
            noiseExposureIndexPct = 24.2f,
            equivalentNoiseDbfs = -29.8f,
            peakNoiseDbfs = -16.4f,
            elevatedNoiseDurationSec = 42.0,
            hasAudioSamples = true,
            averageRoadScore = 87,
            worstSurfaceClass = "MODERATE",
            peakVibrationRms = 2.15f,
            impactCount = 1,
            peakLateralG = 0.44f,
            peakLongitudinalG = 0.38f,
            obd2ConnectedDuringTrip = true,
            peakRpm = 3800,
            averageRpm = 2100,
            maxCoolantTempC = 89,
            averageStreamHz = 20.0f,
        )

        repo.saveCompletedSession(summary)
        val storedState = repo.exportStateFlow.first()
        assertNotNull(storedState.latestSummary)
        assertEquals("RoadEye_DS_Test", storedState.latestSummary!!.sessionId)
        assertEquals(55.2f, storedState.latestSummary!!.averageSpeedKmh, 0.05f)

        // Export reading from DataStore (null summary triggers DataStore read)
        val exported = repo.exportSummaryToJsonFile(null)
        assertNotNull(exported)
        assertTrue(exported!!.file.exists())
        assertTrue(exported.fileName.endsWith(".json"))
        assertTrue(exported.byteSize > 100L)
        assertEquals("content", exported.shareUri.scheme)
        assertTrue(exported.shareUri.toString().contains("telemetry_exports"))

        val parsed = TripStatisticsJsonSerializer.fromJson(exported.file.readText())
        assertEquals("RoadEye_DS_Test", parsed.sessionId)
        assertEquals(36.8f, parsed.maxAltitudeChangeM, 0.05f)
        assertEquals(5120.5, parsed.totalRoadNoiseExposureDbSec, 0.1)

        val updatedState = repo.exportStateFlow.first()
        assertEquals(exported.fileName, updatedState.lastExportedFileName)
        assertTrue(updatedState.lastExportedBytes > 0L)
    }

    @Test
    fun `controller stopStreaming computes end of session trip summary`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val controller = TelemetrySessionController.getInstance(app)

        controller.stateHolder.updateBarometer(pressureHpa = 912.0f, relAltitudeM = 880.0f)
        controller.stateHolder.updateAudio(AudioTelemetryState(active = true, rmsDbfs = -29.5f))
        controller.startStreaming("127.0.0.1", 5005, "RoadEye_Trip_Test", 20)
        assertTrue(controller.tripStatisticsService.isSessionActive.value)

        controller.tripStatisticsService.recordSnapshot(controller.stateHolder.snapshot())
        controller.stateHolder.updateBarometer(pressureHpa = 910.0f, relAltitudeM = 898.0f)
        controller.tripStatisticsService.recordSnapshot(controller.stateHolder.snapshot())

        controller.stopStreaming()

        val summary = controller.tripStatisticsService.latestSummary.value
        assertTrue(summary != null)
        assertEquals(18.0f, summary!!.maxAltitudeChangeM, 0.1f)
        controller.dismissTripSummary()
        assertNull(controller.tripStatisticsService.latestSummary.value)
        assertTrue(controller.tripStatisticsService.lastCompletedSummary.value != null)
    }
}
