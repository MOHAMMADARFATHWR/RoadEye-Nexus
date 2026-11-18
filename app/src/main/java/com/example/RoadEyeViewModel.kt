package com.example

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

data class TripJsonSharePayload(
    val uri: Uri,
    val fileName: String,
    val subject: String,
    val summaryText: String,
)

enum class HudMode(val label: String) {
    COCKPIT("COCKPIT"),
    RACE("RACE HUD"),
    SETTINGS("SETUP"),
}

data class HardwarePermissionsState(
    val pairedDevices: List<PairedBluetoothDevice> = emptyList(),
    val isBluetoothSupported: Boolean = true,
    val isBluetoothEnabled: Boolean = false,
    val hasBluetoothPermission: Boolean = false,
    val hasLocationPermission: Boolean = false,
    val hasAudioPermission: Boolean = false,
)

data class CockpitUiState(
    val hudMode: HudMode = HudMode.COCKPIT,
    val targetIpInput: String = UdpTelemetryStreamer.DEFAULT_IP,
    val targetPortInput: String = UdpTelemetryStreamer.DEFAULT_PORT.toString(),
    val deviceIdInput: String = TelemetrySnapshot.DEFAULT_DEVICE_ID,
    val ipError: String? = null,
    val portError: String? = null,
    val settings: TelemetrySettings = TelemetrySettings(),
    val telemetry: TelemetrySnapshot = TelemetrySnapshot(),
    val streamerStatus: StreamerStatus = StreamerStatus(),
    val recordingStatus: RecordingStatus = RecordingStatus(),
    val hardware: HardwarePermissionsState = HardwarePermissionsState(),
    val isCalibrating: Boolean = false,
    val isCollectingSensors: Boolean = false,
    val activeTripSummary: TripStatisticsSummary? = null,
    val lastTripSummary: TripStatisticsSummary? = null,
    val tripExportState: TripExportState = TripExportState(),
    val isExportingTripJson: Boolean = false,
)

private data class InputDraftState(
    val hudMode: HudMode = HudMode.COCKPIT,
    val ipInput: String? = null,
    val portInput: String? = null,
    val deviceIdInput: String? = null,
    val ipError: String? = null,
    val portError: String? = null,
)

@OptIn(FlowPreview::class)
class RoadEyeViewModel(application: Application) :
    AndroidViewModel(application),
    DefaultLifecycleObserver {

    private val controller = TelemetrySessionController.getInstance(application)
    private val prefsRepo = controller.preferencesRepository

    private val inputDrafts = MutableStateFlow(InputDraftState())
    private val hardwareState = MutableStateFlow(readHardwareState())

    private val _isInitialReady = MutableStateFlow(false)
    val isInitialReady: StateFlow<Boolean> = _isInitialReady.asStateFlow()

    private val _isExportingTripJson = MutableStateFlow(false)
    private val _shareJsonEvents = MutableSharedFlow<TripJsonSharePayload>(extraBufferCapacity = 1)
    val shareJsonEvents: SharedFlow<TripJsonSharePayload> = _shareJsonEvents.asSharedFlow()

    private val throttledTelemetryFlow = controller.stateHolder.stateFlow.sample(50L)

    val uiState: StateFlow<CockpitUiState> = combine(
        inputDrafts,
        prefsRepo.settingsFlow,
        throttledTelemetryFlow,
        controller.udpStreamer.status,
        controller.sessionRecorder.status,
        hardwareState,
        controller.isCalibrating,
        controller.sensorManager.isCollecting,
        controller.tripStatisticsService.latestSummary,
        controller.tripStatisticsService.lastCompletedSummary,
        controller.tripExportRepository.exportStateFlow,
        _isExportingTripJson,
    ) { values ->
        val draft = values[0] as InputDraftState
        val settings = values[1] as TelemetrySettings
        val telemetry = values[2] as TelemetrySnapshot
        val streamer = values[3] as StreamerStatus
        val recording = values[4] as RecordingStatus
        val hardware = values[5] as HardwarePermissionsState
        val calibrating = values[6] as Boolean
        val collectingSensors = values[7] as Boolean
        val activeSummary = values[8] as TripStatisticsSummary?
        val lastSummary = values[9] as TripStatisticsSummary?
        val exportState = values[10] as TripExportState
        val exportingJson = values[11] as Boolean

        CockpitUiState(
            hudMode = draft.hudMode,
            targetIpInput = draft.ipInput ?: settings.targetIp,
            targetPortInput = draft.portInput ?: settings.targetPort.toString(),
            deviceIdInput = draft.deviceIdInput ?: settings.deviceId,
            ipError = draft.ipError,
            portError = draft.portError,
            settings = settings,
            telemetry = telemetry,
            streamerStatus = streamer,
            recordingStatus = recording,
            hardware = hardware,
            isCalibrating = calibrating,
            isCollectingSensors = collectingSensors,
            activeTripSummary = activeSummary,
            lastTripSummary = lastSummary ?: exportState.latestSummary,
            tripExportState = exportState,
            isExportingTripJson = exportingJson,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = CockpitUiState(hardware = hardwareState.value),
    )

    init {
        controller.startSensors()
        viewModelScope.launch {
            prefsRepo.settingsFlow.first()
            _isInitialReady.value = true
        }
    }

    fun setHudMode(mode: HudMode) {
        inputDrafts.update { it.copy(hudMode = mode) }
    }

    fun updateTargetIp(newIp: String) {
        val sanitized = newIp.filter { it.isDigit() || it == '.' }
        val valid = IPV4_REGEX.matches(sanitized)
        val ipError = if (sanitized.isBlank() || valid) {
            null
        } else {
            "Enter valid IPv4 (e.g. 192.168.43.100)"
        }
        inputDrafts.update { it.copy(ipInput = sanitized, ipError = ipError) }
        if (valid) {
            viewModelScope.launch { prefsRepo.setTargetIp(sanitized) }
        }
    }

    fun updateTargetPort(newPort: String) {
        val digitsOnly = newPort.filter { it.isDigit() }.take(5)
        val parsed = digitsOnly.toIntOrNull()
        val portError = when {
            digitsOnly.isEmpty() -> "Port required"
            parsed == null || parsed !in 1..65535 -> "Port 1-65535"
            else -> null
        }
        inputDrafts.update { it.copy(portInput = digitsOnly, portError = portError) }
        if (parsed != null && parsed in 1..65535) {
            viewModelScope.launch { prefsRepo.setTargetPort(parsed) }
        }
    }

    fun updateDeviceId(newId: String) {
        val cleaned = newId.filter { it.isLetterOrDigit() || it == '_' || it == '-' }.take(32)
        inputDrafts.update { it.copy(deviceIdInput = cleaned) }
        if (cleaned.isNotBlank()) {
            viewModelScope.launch { prefsRepo.setDeviceId(cleaned) }
        }
    }

    fun setStreamRateHz(rateHz: Int) {
        viewModelScope.launch { prefsRepo.setStreamRateHz(rateHz) }
    }

    fun setObdPollDelayMs(delayMs: Long) {
        viewModelScope.launch { prefsRepo.setObdPollDelayMs(delayMs) }
    }

    fun setAutoReconnectObd(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setAutoReconnectObd(enabled) }
    }

    fun setPureOledBlack(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setPureOledBlack(enabled) }
    }

    fun setAcousticSensingEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setAcousticSensingEnabled(enabled) }
    }

    fun toggleStreaming() {
        val current = uiState.value
        if (current.streamerStatus.isStreaming) {
            controller.stopStreaming()
            return
        }

        val ip = current.targetIpInput.trim()
        val port = current.targetPortInput.toIntOrNull()
        val validIp = IPV4_REGEX.matches(ip)
        val validPort = port != null && port in 1..65535

        if (!validIp || !validPort) {
            inputDrafts.update {
                it.copy(
                    ipError = if (validIp) null else "Enter valid IPv4 (e.g. 192.168.43.100)",
                    portError = if (validPort) null else "Port 1-65535",
                )
            }
            return
        }

        val deviceId = current.deviceIdInput.trim().ifEmpty { TelemetrySnapshot.DEFAULT_DEVICE_ID }
        viewModelScope.launch {
            prefsRepo.setTargetIp(ip)
            prefsRepo.setTargetPort(port!!)
            prefsRepo.setDeviceId(deviceId)
        }
        controller.startStreaming(
            targetIp = ip,
            targetPort = port!!,
            deviceId = deviceId,
            rateHz = current.settings.streamRateHz,
        )
    }

    fun refreshHardwareAndDevices() {
        hardwareState.value = readHardwareState()
    }

    fun onPermissionsUpdated() {
        controller.onPermissionsUpdated()
        refreshHardwareAndDevices()
    }

    override fun onStart(owner: LifecycleOwner) {
        onForegroundStarted()
    }

    override fun onStop(owner: LifecycleOwner) {
        onForegroundStopped()
    }

    override fun onCleared() {
        super.onCleared()
        controller.stopSensors()
    }

    fun onForegroundStarted() {
        controller.startSensors()
        refreshHardwareAndDevices()
    }

    fun onForegroundStopped() {
        controller.stopSensors()
    }

    fun connectObd2Device(device: PairedBluetoothDevice) {
        controller.connectObd2(device)
    }

    fun quickConnectLastObd2() {
        controller.quickConnectLastObd2()
    }

    fun disconnectObd2() {
        controller.disconnectObd2()
    }

    fun scanObd2Dtcs() {
        controller.scanDtcs()
    }

    fun toggleRecording() {
        controller.toggleRecording()
    }

    fun getLatestRecordingShareUri(): Uri? = controller.getLatestRecordingShareUri()

    fun calibrateImuZero() {
        controller.calibrateImuZero()
    }

    fun resetImuCalibration() {
        controller.resetImuCalibration()
    }

    fun dismissTripSummary() {
        controller.dismissTripSummary()
    }

    fun viewLastTripSummary() {
        val inMemory = controller.tripStatisticsService.lastCompletedSummary.value
        if (inMemory == null) {
            controller.tripStatisticsService.restoreLastCompletedSummary(uiState.value.lastTripSummary)
        }
        controller.reopenLastTripSummary()
    }

    fun exportAndShareSessionJson(summary: TripStatisticsSummary? = null) {
        if (_isExportingTripJson.value) return
        viewModelScope.launch {
            _isExportingTripJson.value = true
            try {
                val target = summary ?: uiState.value.lastTripSummary
                val result = controller.exportTripStatsToJson(target)
                if (result != null) {
                    val text = if (target != null) {
                        String.format(
                            Locale.US,
                            "RoadEye Nexus Trip Summary (%s): Avg Speed %.1f km/h, Max Alt Change %.1f m, Road Noise Exposure %.0f dB·s",
                            target.sessionId,
                            target.averageSpeedKmh,
                            target.maxAltitudeChangeM,
                            target.totalRoadNoiseExposureDbSec,
                        )
                    } else {
                        "RoadEye Nexus Telemetry Session Statistics JSON"
                    }
                    _shareJsonEvents.tryEmit(
                        TripJsonSharePayload(
                            uri = result.shareUri,
                            fileName = result.fileName,
                            subject = "RoadEye Nexus Trip Statistics (${result.fileName})",
                            summaryText = text,
                        ),
                    )
                }
            } finally {
                _isExportingTripJson.value = false
            }
        }
    }

    fun exportAllTripHistoryJson() {
        if (_isExportingTripJson.value) return
        viewModelScope.launch {
            _isExportingTripJson.value = true
            try {
                val result = controller.exportAllSavedTripsToJson()
                if (result != null) {
                    _shareJsonEvents.tryEmit(
                        TripJsonSharePayload(
                            uri = result.shareUri,
                            fileName = result.fileName,
                            subject = "RoadEye Nexus Trip Statistics History (${result.fileName})",
                            summaryText = "Exported ${uiState.value.tripExportState.savedSessions.size} RoadEye Nexus telemetry session summaries.",
                        ),
                    )
                }
            } finally {
                _isExportingTripJson.value = false
            }
        }
    }

    private fun readHardwareState(): HardwarePermissionsState {
        return HardwarePermissionsState(
            pairedDevices = controller.obd2Manager.getPairedDevices(),
            isBluetoothSupported = controller.obd2Manager.isBluetoothSupported(),
            isBluetoothEnabled = controller.obd2Manager.isBluetoothEnabled(),
            hasBluetoothPermission = controller.obd2Manager.hasConnectPermission(),
            hasLocationPermission = controller.sensorManager.hasLocationPermission(),
            hasAudioPermission = controller.audioManager.hasAudioPermission(),
        )
    }

    companion object {
        private val IPV4_REGEX = Regex(
            "^((25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)$",
        )
    }
}
