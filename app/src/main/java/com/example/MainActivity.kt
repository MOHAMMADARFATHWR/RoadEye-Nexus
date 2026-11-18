package com.example

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.view.animation.AnticipateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.RoadEyeNexusTheme

class MainActivity : ComponentActivity() {

    private val viewModel: RoadEyeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(viewModel)

        splashScreen.setKeepOnScreenCondition { !viewModel.isInitialReady.value }
        splashScreen.setOnExitAnimationListener { splashScreenView ->
            val iconScaleX = ObjectAnimator.ofFloat(
                splashScreenView.iconView,
                View.SCALE_X,
                1f,
                1.08f,
            )
            val iconScaleY = ObjectAnimator.ofFloat(
                splashScreenView.iconView,
                View.SCALE_Y,
                1f,
                1.08f,
            )
            val fadeOut = ObjectAnimator.ofFloat(
                splashScreenView.view,
                View.ALPHA,
                1f,
                0f,
            )
            val slideUp = ObjectAnimator.ofFloat(
                splashScreenView.view,
                View.TRANSLATION_Y,
                0f,
                -splashScreenView.view.height * 0.06f,
            )
            AnimatorSet().apply {
                playTogether(iconScaleX, iconScaleY, fadeOut, slideUp)
                interpolator = AnticipateInterpolator()
                duration = 280L
                doOnEnd { splashScreenView.remove() }
                start()
            }
        }

        enableEdgeToEdge()
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(viewModel) {
                viewModel.shareJsonEvents.collect { payload ->
                    shareTripStatsJson(payload)
                }
            }

            DisposableEffect(uiState.streamerStatus.isStreaming) {
                if (uiState.streamerStatus.isStreaming) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                onDispose {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            RoadEyeNexusTheme(pureOledBlack = uiState.settings.pureOledBlack) {
                RoadEyeCockpitScreen(
                    uiState = uiState,
                    onHudModeSelected = viewModel::setHudMode,
                    onUpdateIp = viewModel::updateTargetIp,
                    onUpdatePort = viewModel::updateTargetPort,
                    onUpdateDeviceId = viewModel::updateDeviceId,
                    onStreamRateSelected = viewModel::setStreamRateHz,
                    onObdPollDelaySelected = viewModel::setObdPollDelayMs,
                    onAutoReconnectChanged = viewModel::setAutoReconnectObd,
                    onPureOledChanged = viewModel::setPureOledBlack,
                    onAcousticSensingChanged = viewModel::setAcousticSensingEnabled,
                    onToggleStream = viewModel::toggleStreaming,
                    onRefreshDevices = viewModel::refreshHardwareAndDevices,
                    onSelectObd2Device = viewModel::connectObd2Device,
                    onQuickConnectObd2 = viewModel::quickConnectLastObd2,
                    onDisconnectObd2 = viewModel::disconnectObd2,
                    onScanDtcs = viewModel::scanObd2Dtcs,
                    onToggleRecording = viewModel::toggleRecording,
                    onShareLatestRecording = { shareLatestRecording() },
                    onCalibrateImu = viewModel::calibrateImuZero,
                    onResetCalibration = viewModel::resetImuCalibration,
                    onOpenAppSettings = { openAppSettings() },
                    onPermissionsUpdated = viewModel::onPermissionsUpdated,
                    onDismissTripSummary = viewModel::dismissTripSummary,
                    onViewLastTripSummary = viewModel::viewLastTripSummary,
                    onExportStatsJson = { viewModel.exportAndShareSessionJson(uiState.activeTripSummary) },
                    onExportAllTripsJson = viewModel::exportAllTripHistoryJson,
                )
            }
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    private fun shareLatestRecording() {
        val uri = viewModel.getLatestRecordingShareUri() ?: return
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/x-ndjson"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "RoadEye Nexus Telemetry Session")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Export Telemetry JSONL"))
    }

    private fun shareTripStatsJson(payload: TripJsonSharePayload) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, payload.uri)
            putExtra(Intent.EXTRA_SUBJECT, payload.subject)
            putExtra(Intent.EXTRA_TEXT, payload.summaryText)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Share Trip Statistics JSON"))
    }
}

@Composable
fun RoadEyeCockpitScreen(
    uiState: CockpitUiState,
    onHudModeSelected: (HudMode) -> Unit,
    onUpdateIp: (String) -> Unit,
    onUpdatePort: (String) -> Unit,
    onUpdateDeviceId: (String) -> Unit,
    onStreamRateSelected: (Int) -> Unit,
    onObdPollDelaySelected: (Long) -> Unit,
    onAutoReconnectChanged: (Boolean) -> Unit,
    onPureOledChanged: (Boolean) -> Unit,
    onAcousticSensingChanged: (Boolean) -> Unit,
    onToggleStream: () -> Unit,
    onRefreshDevices: () -> Unit,
    onSelectObd2Device: (PairedBluetoothDevice) -> Unit,
    onQuickConnectObd2: () -> Unit,
    onDisconnectObd2: () -> Unit,
    onScanDtcs: () -> Unit,
    onToggleRecording: () -> Unit,
    onShareLatestRecording: () -> Unit,
    onCalibrateImu: () -> Unit,
    onResetCalibration: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onPermissionsUpdated: () -> Unit,
    onDismissTripSummary: () -> Unit = {},
    onViewLastTripSummary: () -> Unit = {},
    onExportStatsJson: () -> Unit = {},
    onExportAllTripsJson: () -> Unit = {},
) {
    val context = LocalContext.current
    var showDevicePicker by remember { mutableStateOf(false) }

    val requiredPermissions = remember {
        buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        onPermissionsUpdated()
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        onPermissionsUpdated()
    }

    LaunchedEffect(Unit) {
        val missing = requiredPermissions.any { permission ->
            ContextCompat.checkSelfPermission(context, permission) !=
                PackageManager.PERMISSION_GRANTED
        }
        if (missing) {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    val needsPermissions = !uiState.hardware.hasLocationPermission ||
        !uiState.hardware.hasBluetoothPermission

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
        ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            val isWideLayout = maxWidth >= 700.dp

            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 980.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "header") {
                    CockpitHeaderBanner(
                        streamerStatus = uiState.streamerStatus,
                        recordingStatus = uiState.recordingStatus,
                        deviceId = uiState.settings.deviceId,
                        hudMode = uiState.hudMode,
                        onHudModeSelected = onHudModeSelected,
                        configuredHz = uiState.settings.streamRateHz,
                    )
                }

                if (needsPermissions) {
                    item(key = "permissions") {
                        PermissionBannerCard(
                            onGrantClick = { permissionLauncher.launch(requiredPermissions) },
                            onOpenSettingsClick = onOpenAppSettings,
                        )
                    }
                }

                item(key = "master_toggle") {
                    MasterStreamToggleButton(
                        isStreaming = uiState.streamerStatus.isStreaming,
                        rateHz = uiState.settings.streamRateHz,
                        enabled = uiState.ipError == null && uiState.portError == null,
                        onToggle = onToggleStream,
                    )
                }

                item(key = "hud_mode_content") {
                    AnimatedContent(
                        targetState = uiState.hudMode,
                        transitionSpec = {
                            val forward = targetState.ordinal >= initialState.ordinal
                            val enter = slideInHorizontally(animationSpec = tween(240)) { width ->
                                if (forward) width / 4 else -width / 4
                            } + fadeIn(animationSpec = tween(220))
                            val exit = slideOutHorizontally(animationSpec = tween(200)) { width ->
                                if (forward) -width / 4 else width / 4
                            } + fadeOut(animationSpec = tween(180))
                            enter togetherWith exit
                        },
                        label = "hud_tab_transition",
                    ) { activeMode ->
                        when (activeMode) {
                            HudMode.COCKPIT -> {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    StreamHealthSummaryCard(
                                        uiState = uiState,
                                        onViewLastTripSummary = onViewLastTripSummary,
                                    )

                                    if (isWideLayout) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                NetworkConfigCard(
                                                    targetIp = uiState.targetIpInput,
                                                    targetPort = uiState.targetPortInput,
                                                    ipError = uiState.ipError,
                                                    portError = uiState.portError,
                                                    isStreaming = uiState.streamerStatus.isStreaming,
                                                    onIpChange = onUpdateIp,
                                                    onPortChange = onUpdatePort,
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Obd2SelectorCard(
                                                    obd2 = uiState.telemetry.obd2,
                                                    lastDeviceName = uiState.settings.lastObdDeviceName,
                                                    lastDeviceAddress = uiState.settings.lastObdDeviceAddress,
                                                    isBluetoothSupported = uiState.hardware.isBluetoothSupported,
                                                    isBluetoothEnabled = uiState.hardware.isBluetoothEnabled,
                                                    hasBluetoothPermission = uiState.hardware.hasBluetoothPermission,
                                                    onSelectClick = {
                                                        onRefreshDevices()
                                                        showDevicePicker = true
                                                    },
                                                    onQuickConnectClick = onQuickConnectObd2,
                                                    onDisconnectClick = onDisconnectObd2,
                                                )
                                            }
                                        }
                                    } else {
                                        NetworkConfigCard(
                                            targetIp = uiState.targetIpInput,
                                            targetPort = uiState.targetPortInput,
                                            ipError = uiState.ipError,
                                            portError = uiState.portError,
                                            isStreaming = uiState.streamerStatus.isStreaming,
                                            onIpChange = onUpdateIp,
                                            onPortChange = onUpdatePort,
                                        )
                                        Obd2SelectorCard(
                                            obd2 = uiState.telemetry.obd2,
                                            lastDeviceName = uiState.settings.lastObdDeviceName,
                                            lastDeviceAddress = uiState.settings.lastObdDeviceAddress,
                                            isBluetoothSupported = uiState.hardware.isBluetoothSupported,
                                            isBluetoothEnabled = uiState.hardware.isBluetoothEnabled,
                                            hasBluetoothPermission = uiState.hardware.hasBluetoothPermission,
                                            onSelectClick = {
                                                onRefreshDevices()
                                                showDevicePicker = true
                                            },
                                            onQuickConnectClick = onQuickConnectObd2,
                                            onDisconnectClick = onDisconnectObd2,
                                        )
                                    }

                                    if (isWideLayout) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            Obd2TelemetryCard(
                                                obd2 = uiState.telemetry.obd2,
                                                modifier = Modifier.weight(1f),
                                            )
                                            ImuTelemetryCard(
                                                sensors = uiState.telemetry.sensors,
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                    } else {
                                        Obd2TelemetryCard(obd2 = uiState.telemetry.obd2)
                                        ImuTelemetryCard(sensors = uiState.telemetry.sensors)
                                    }

                                    if (isWideLayout) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            GpsTelemetryCard(
                                                gps = uiState.telemetry.sensors.gps,
                                                hasLocationPermission = uiState.hardware.hasLocationPermission,
                                                modifier = Modifier.weight(1f),
                                            )
                                            UdpDiagnosticsCard(
                                                streamerStatus = uiState.streamerStatus,
                                                snapshot = uiState.telemetry,
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                    } else {
                                        GpsTelemetryCard(
                                            gps = uiState.telemetry.sensors.gps,
                                            hasLocationPermission = uiState.hardware.hasLocationPermission,
                                        )
                                        UdpDiagnosticsCard(
                                            streamerStatus = uiState.streamerStatus,
                                            snapshot = uiState.telemetry,
                                        )
                                    }

                                    if (isWideLayout) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            RoadIntelligenceCard(
                                                road = uiState.telemetry.roadIntelligence,
                                                modifier = Modifier.weight(1f),
                                            )
                                            EnvironmentAndHealthCard(
                                                sensors = uiState.telemetry.sensors,
                                                health = uiState.telemetry.deviceHealth,
                                                hasAudioPermission = uiState.hardware.hasAudioPermission,
                                                acousticEnabled = uiState.settings.acousticSensingEnabled,
                                                onRequestAudioPermission = {
                                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                },
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                    } else {
                                        RoadIntelligenceCard(
                                            road = uiState.telemetry.roadIntelligence,
                                        )
                                        EnvironmentAndHealthCard(
                                            sensors = uiState.telemetry.sensors,
                                            health = uiState.telemetry.deviceHealth,
                                            hasAudioPermission = uiState.hardware.hasAudioPermission,
                                            acousticEnabled = uiState.settings.acousticSensingEnabled,
                                            onRequestAudioPermission = {
                                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            },
                                        )
                                    }
                                }
                            }

                            HudMode.RACE -> {
                                RaceHudPanel(
                                    telemetry = uiState.telemetry,
                                    streamerStatus = uiState.streamerStatus,
                                )
                            }

                            HudMode.SETTINGS -> {
                                SettingsAndCalibrationPanel(
                                    uiState = uiState,
                                    onDeviceIdChange = onUpdateDeviceId,
                                    onStreamRateSelected = onStreamRateSelected,
                                    onObdPollDelaySelected = onObdPollDelaySelected,
                                    onAutoReconnectChanged = onAutoReconnectChanged,
                                    onPureOledChanged = onPureOledChanged,
                                    onAcousticSensingChanged = onAcousticSensingChanged,
                                    onCalibrateImu = onCalibrateImu,
                                    onResetCalibration = onResetCalibration,
                                    onScanDtcs = onScanDtcs,
                                    onToggleRecording = onToggleRecording,
                                    onShareLatestRecording = onShareLatestRecording,
                                    onExportStatsJson = onExportStatsJson,
                                    onExportAllTripsJson = onExportAllTripsJson,
                                )
                            }
                        }
                    }
                }
            }
        }
        }

        TripSummaryOverlay(
            summary = uiState.activeTripSummary,
            canExportRecording = !uiState.recordingStatus.isRecording &&
                uiState.recordingStatus.lastSavedFileName != null,
            lastExportedFileName = uiState.tripExportState.lastExportedFileName,
            isExportingJson = uiState.isExportingTripJson,
            onDismiss = onDismissTripSummary,
            onExportRecording = onShareLatestRecording,
            onExportStatsJson = onExportStatsJson,
        )
    }

    if (showDevicePicker) {
        BluetoothDevicePickerDialog(
            devices = uiState.hardware.pairedDevices,
            hasPermission = uiState.hardware.hasBluetoothPermission,
            isBluetoothEnabled = uiState.hardware.isBluetoothEnabled,
            onDismiss = { showDevicePicker = false },
            onRefresh = onRefreshDevices,
            onDeviceSelected = { device ->
                showDevicePicker = false
                onSelectObd2Device(device)
            },
        )
    }
}
