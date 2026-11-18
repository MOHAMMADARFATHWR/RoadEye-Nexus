package com.example

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TelemetrySessionController private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val preferencesRepository = TelemetryPreferencesRepository(appContext)
    val stateHolder = TelemetryStateHolder()
    val audioManager = AudioTelemetryManager(appContext, stateHolder)
    val sensorManager = SensorTelemetryManager(
        context = appContext,
        stateHolder = stateHolder,
        audioTelemetryManager = audioManager,
    )
    val deviceDiagnosticsManager = DeviceDiagnosticsManager(appContext, stateHolder)
    val obd2Manager = Obd2BluetoothManager(appContext, stateHolder)
    val sessionRecorder = SessionRecorder(appContext)
    val tripStatisticsService = TripStatisticsService()
    val tripExportRepository = TripStatisticsExportRepository(appContext)
    val udpStreamer = UdpTelemetryStreamer(
        context = appContext,
        stateHolder = stateHolder,
        sessionRecorder = sessionRecorder,
        onSnapshotSampled = { snapshot, sampleTimeMs ->
            tripStatisticsService.recordSnapshot(snapshot, sampleTimeMs)
        },
    )

    private val _isCalibrating = MutableStateFlow(false)
    val isCalibrating: StateFlow<Boolean> = _isCalibrating.asStateFlow()

    @Volatile
    private var isSensorsActive = false

    @Volatile
    private var acousticSensingEnabled = true

    @Volatile
    private var recordingStatsJob: Job? = null

    init {
        stateHolder.updateAudioAvailability(audioManager.hasAudioPermission())
        controllerScope.launch {
            val exportState = tripExportRepository.exportStateFlow.first()
            tripStatisticsService.restoreLastCompletedSummary(exportState.latestSummary)
        }
        controllerScope.launch {
            preferencesRepository.settingsFlow.collect { settings ->
                acousticSensingEnabled = settings.acousticSensingEnabled
                sensorManager.setCalibrationOffsets(
                    accelBiasX = settings.accelBiasX,
                    accelBiasY = settings.accelBiasY,
                    gyroBiasX = settings.gyroBiasX,
                    gyroBiasY = settings.gyroBiasY,
                    gyroBiasZ = settings.gyroBiasZ,
                )
                obd2Manager.configure(
                    autoReconnect = settings.autoReconnectObd,
                    pollDelayMs = settings.obdPollDelayMs,
                )
                stateHolder.setMetadata(
                    deviceId = settings.deviceId,
                    streamHz = settings.streamRateHz,
                    thermalStatus = udpStreamer.status.value.thermalStatus,
                )
                syncAudioCaptureState()
            }
        }
    }

    fun startSensors() {
        isSensorsActive = true
        sensorManager.start()
        deviceDiagnosticsManager.start()
        syncAudioCaptureState()
    }

    fun stopSensors() {
        if (!udpStreamer.status.value.isStreaming) {
            isSensorsActive = false
            sensorManager.stop()
            audioManager.stop()
            deviceDiagnosticsManager.stop()
        }
    }

    fun onPermissionsUpdated() {
        sensorManager.startLocationUpdates()
        syncAudioCaptureState()
    }

    private fun syncAudioCaptureState() {
        val permitted = audioManager.hasAudioPermission()
        stateHolder.updateAudioAvailability(permitted)
        if (isSensorsActive && acousticSensingEnabled && permitted) {
            audioManager.start()
        } else {
            audioManager.stop()
        }
    }

    fun startStreaming(targetIp: String, targetPort: Int, deviceId: String, rateHz: Int) {
        recordingStatsJob?.cancel()
        recordingStatsJob = null
        tripStatisticsService.startSession(
            sessionId = deviceId,
            sessionLabel = "UDP ${rateHz}Hz → $targetIp:$targetPort",
        )
        sensorManager.setHighRateMode(rateHz >= 50)
        startSensors()
        udpStreamer.start(
            targetIp = targetIp,
            targetPort = targetPort,
            deviceId = deviceId,
            rateHz = rateHz,
        )
        TelemetryForegroundService.start(
            context = appContext,
            endpoint = "$targetIp:$targetPort",
            rateHz = rateHz,
        )
    }

    fun stopStreaming() {
        udpStreamer.stop()
        sensorManager.setHighRateMode(false)
        TelemetryForegroundService.stop(appContext)
        if (tripStatisticsService.isSessionActive.value) {
            val summary = tripStatisticsService.endSession()
            controllerScope.launch {
                tripExportRepository.saveCompletedSession(summary)
            }
        }
    }

    fun connectObd2(device: PairedBluetoothDevice) {
        controllerScope.launch {
            preferencesRepository.setLastObdDevice(device.name, device.address)
        }
        obd2Manager.connectAndPoll(
            deviceAddress = device.address,
            deviceName = device.name,
        )
    }

    fun quickConnectLastObd2() {
        controllerScope.launch {
            val settings = preferencesRepository.settingsFlow.first()
            if (settings.lastObdDeviceAddress.isNotBlank()) {
                obd2Manager.connectAndPoll(
                    deviceAddress = settings.lastObdDeviceAddress,
                    deviceName = settings.lastObdDeviceName.ifBlank { "ELM327 Adapter" },
                )
            }
        }
    }

    fun disconnectObd2() {
        obd2Manager.disconnect(clearSelectedDevice = false)
    }

    fun scanDtcs() {
        obd2Manager.requestDtcScan()
    }

    fun toggleRecording() {
        if (sessionRecorder.status.value.isRecording) {
            sessionRecorder.stopSession()
            recordingStatsJob?.cancel()
            recordingStatsJob = null
            if (!udpStreamer.status.value.isStreaming && tripStatisticsService.isSessionActive.value) {
                val summary = tripStatisticsService.endSession()
                controllerScope.launch {
                    tripExportRepository.saveCompletedSession(summary)
                }
            }
        } else {
            sessionRecorder.startNewSession()
            if (!udpStreamer.status.value.isStreaming && !tripStatisticsService.isSessionActive.value) {
                val deviceId = stateHolder.stateFlow.value.deviceId
                tripStatisticsService.startSession(
                    sessionId = deviceId,
                    sessionLabel = "Local JSONL Recording",
                )
                recordingStatsJob?.cancel()
                recordingStatsJob = controllerScope.launch {
                    while (isActive && sessionRecorder.status.value.isRecording && !udpStreamer.status.value.isStreaming) {
                        tripStatisticsService.recordSnapshot(stateHolder.snapshot())
                        delay(100L)
                    }
                }
            }
        }
    }

    fun dismissTripSummary() {
        tripStatisticsService.dismissSummary()
    }

    fun reopenLastTripSummary() {
        tripStatisticsService.reopenLastSummary()
    }

    suspend fun exportTripStatsToJson(
        summary: TripStatisticsSummary? = null,
    ): TripJsonExportResult? {
        val target = summary ?: tripStatisticsService.lastCompletedSummary.value
        if (target != null) {
            tripExportRepository.saveCompletedSession(target)
        }
        return tripExportRepository.exportSummaryToJsonFile(target)
    }

    suspend fun exportAllSavedTripsToJson(): TripJsonExportResult? {
        return tripExportRepository.exportAllSavedSessionsToJsonFile()
    }

    fun getLatestRecordingShareUri(): Uri? = sessionRecorder.getLatestShareUri()

    fun calibrateImuZero() {
        if (_isCalibrating.value) return
        _isCalibrating.value = true
        controllerScope.launch {
            sensorManager.setHighRateMode(true)
            sensorManager.startImuSensors()
            sensorManager.beginCalibrationSampleWindow()
            delay(1000L)
            val result = sensorManager.finishCalibrationSampleWindow()
            preferencesRepository.setCalibrationOffsets(
                accelBiasX = result.accelBiasX,
                accelBiasY = result.accelBiasY,
                gyroBiasX = result.gyroBiasX,
                gyroBiasY = result.gyroBiasY,
                gyroBiasZ = result.gyroBiasZ,
            )
            if (!udpStreamer.status.value.isStreaming) {
                sensorManager.setHighRateMode(false)
            }
            _isCalibrating.value = false
        }
    }

    fun resetImuCalibration() {
        controllerScope.launch {
            sensorManager.setCalibrationOffsets(0f, 0f, 0f, 0f, 0f)
            preferencesRepository.setCalibrationOffsets(0f, 0f, 0f, 0f, 0f)
        }
    }

    companion object {
        @Volatile
        private var instance: TelemetrySessionController? = null

        fun getInstance(context: Context): TelemetrySessionController {
            return instance ?: synchronized(this) {
                instance ?: TelemetrySessionController(context).also { instance = it }
            }
        }
    }
}
