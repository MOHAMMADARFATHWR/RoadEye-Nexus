package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.AmberTelemetry
import com.example.ui.theme.CockpitOutline
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun RaceHudPanel(
    telemetry: TelemetrySnapshot,
    streamerStatus: StreamerStatus,
) {
    val obd2 = telemetry.obd2
    val speedDisplay = if (obd2.connected) obd2.speedKmh else (telemetry.sensors.gps.speedMps * 3.6f).toInt()
    val speedSource = if (obd2.connected) "OBD2" else "GNSS"

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.55f)),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column {
                        Text(
                            text = "SPEED ($speedSource)",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                        )
                        AnimatedNumericReadout(
                            text = speedDisplay.toString(),
                            style = MaterialTheme.typography.displaySmall.copy(fontSize = 56.sp, lineHeight = 58.sp),
                            color = ElectricCyan,
                        )
                        Text(
                            text = "km/h",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary,
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "ENGINE RPM",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                        )
                        AnimatedNumericReadout(
                            text = obd2.rpm.toString(),
                            style = MaterialTheme.typography.displaySmall.copy(fontSize = 44.sp, lineHeight = 48.sp),
                            color = if (obd2.rpm > 5500) AlertCrimson else AmberTelemetry,
                        )
                        Text(
                            text = "${streamerStatus.effectiveHz}Hz · #${streamerStatus.sequenceNumber}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SignalEmerald,
                        )
                    }
                }

                GaugeBarRow(
                    label = "TACHOMETER (0 - 7000 RPM)",
                    valueText = "${obd2.rpm} RPM",
                    fraction = (obd2.rpm / 7000f).coerceIn(0f, 1f),
                    color = if (obd2.rpm > 5500) AlertCrimson else AmberTelemetry,
                )

                GaugeBarRow(
                    label = "THROTTLE",
                    valueText = String.format(Locale.US, "%.1f%%", obd2.throttlePos),
                    fraction = (obd2.throttlePos / 100f).coerceIn(0f, 1f),
                    color = ElectricCyan,
                )
            }
        }

        HudCard(
            title = "G-FORCE & ATTITUDE HORIZON",
            icon = Icons.Default.Speed,
            iconContentDescription = "G-force and vehicle attitude",
            accentColor = SignalEmerald,
            trailingBadge = {
                val roadColor = when (telemetry.roadIntelligence.surfaceClass) {
                    "IMPACT_ANOMALY", "ROUGH" -> AlertCrimson
                    "MODERATE" -> AmberTelemetry
                    else -> SignalEmerald
                }
                StatusPill(
                    label = "ROAD ${telemetry.roadIntelligence.conditionScore}/100",
                    color = roadColor,
                )
            },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AttitudeAndGForceScope(
                    accel = telemetry.sensors.accel,
                    rollDeg = telemetry.sensors.orientation.rollDeg,
                    pitchDeg = telemetry.sensors.orientation.pitchDeg,
                    modifier = Modifier.size(130.dp),
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MetricBlock(
                        label = "LATERAL (X)",
                        value = String.format(Locale.US, "%.2f G", telemetry.sensors.accel.x / 9.80665f),
                    )
                    MetricBlock(
                        label = "LONGITUDINAL (Y)",
                        value = String.format(Locale.US, "%.2f G", telemetry.sensors.accel.y / 9.80665f),
                    )
                    MetricBlock(
                        label = "PITCH / ROLL",
                        value = String.format(
                            Locale.US,
                            "%.1f° / %.1f°",
                            telemetry.sensors.orientation.pitchDeg,
                            telemetry.sensors.orientation.rollDeg,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun AttitudeAndGForceScope(
    accel: Vector3State,
    rollDeg: Float,
    pitchDeg: Float,
    modifier: Modifier = Modifier,
) {
    val gx by animateFloatAsState(
        targetValue = (accel.x / 9.80665f).coerceIn(-1.5f, 1.5f) / 1.5f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "race_g_x",
    )
    val gy by animateFloatAsState(
        targetValue = (accel.y / 9.80665f).coerceIn(-1.5f, 1.5f) / 1.5f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "race_g_y",
    )

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f - 4.dp.toPx()

        drawCircle(
            color = CockpitOutline,
            radius = radius,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()),
        )
        drawCircle(
            color = CockpitOutline.copy(alpha = 0.5f),
            radius = radius * 0.5f,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
        )

        val rollRad = Math.toRadians(rollDeg.toDouble())
        val pitchOffset = (pitchDeg / 45f).coerceIn(-0.6f, 0.6f) * radius
        val dx = (cos(rollRad) * radius * 0.8f).toFloat()
        val dy = (sin(rollRad) * radius * 0.8f).toFloat()

        drawLine(
            color = ElectricCyan.copy(alpha = 0.7f),
            start = Offset(center.x - dx, center.y - dy + pitchOffset),
            end = Offset(center.x + dx, center.y + dy + pitchOffset),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )

        val dot = Offset(center.x + gx * radius * 0.85f, center.y - gy * radius * 0.85f)
        drawCircle(
            color = AmberTelemetry,
            radius = 6.dp.toPx(),
            center = dot,
        )
    }
}

@Composable
fun SettingsAndCalibrationPanel(
    uiState: CockpitUiState,
    onDeviceIdChange: (String) -> Unit,
    onStreamRateSelected: (Int) -> Unit,
    onObdPollDelaySelected: (Long) -> Unit,
    onAutoReconnectChanged: (Boolean) -> Unit,
    onPureOledChanged: (Boolean) -> Unit,
    onAcousticSensingChanged: (Boolean) -> Unit,
    onCalibrateImu: () -> Unit,
    onResetCalibration: () -> Unit,
    onScanDtcs: () -> Unit,
    onToggleRecording: () -> Unit,
    onShareLatestRecording: () -> Unit,
    onExportStatsJson: () -> Unit = {},
    onExportAllTripsJson: () -> Unit = {},
) {
    val haptic = LocalHapticFeedback.current

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HudCard(
            title = "NODE & STREAMING PROFILE",
            icon = Icons.Default.Tune,
            iconContentDescription = "Streaming configuration",
            accentColor = ElectricCyan,
        ) {
            OutlinedTextField(
                value = uiState.deviceIdInput,
                onValueChange = onDeviceIdChange,
                enabled = !uiState.streamerStatus.isStreaming,
                singleLine = true,
                label = { Text("Device Node ID (multi-node)") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricCyan,
                    unfocusedBorderColor = CockpitOutline,
                    focusedLabelColor = ElectricCyan,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("device_id_input"),
            )

            Text(
                text = "UDP STREAM RATE",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(10, 20, 50).forEach { hz ->
                    ChipButton(
                        label = "${hz} Hz",
                        selected = uiState.settings.streamRateHz == hz,
                        accent = ElectricCyan,
                        onClick = { onStreamRateSelected(hz) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Text(
                text = "OBD2 INTER-PID DELAY",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(0L to "Max", 20L to "20ms", 50L to "50ms", 100L to "100ms").forEach { (ms, label) ->
                    ChipButton(
                        label = label,
                        selected = uiState.settings.obdPollDelayMs == ms,
                        accent = AmberTelemetry,
                        onClick = { onObdPollDelaySelected(ms) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "OBD2 Auto-Reconnect (Backoff)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Retries RFCOMM with exponential backoff on drop",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
                Switch(
                    checked = uiState.settings.autoReconnectObd,
                    onCheckedChange = onAutoReconnectChanged,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Pure Black OLED Theme",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Minimizes night glare on windshield mounts",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
                Switch(
                    checked = uiState.settings.pureOledBlack,
                    onCheckedChange = onPureOledChanged,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Acoustic Road Sensing (Mic)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Extracts in-memory RMS dBFS & ZCR only; never records audio",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
                Switch(
                    checked = uiState.settings.acousticSensingEnabled,
                    onCheckedChange = onAcousticSensingChanged,
                    modifier = Modifier.testTag("acoustic_sensing_switch"),
                )
            }
        }

        HudCard(
            title = "IMU ZERO-OFFSET CALIBRATION",
            icon = Icons.Default.CenterFocusStrong,
            iconContentDescription = "IMU calibration",
            accentColor = SignalEmerald,
        ) {
            Text(
                text = String.format(
                    Locale.US,
                    "Bias Accel(X=%.2f, Y=%.2f) · Gyro(X=%.3f, Y=%.3f, Z=%.3f)",
                    uiState.settings.accelBiasX,
                    uiState.settings.accelBiasY,
                    uiState.settings.gyroBiasX,
                    uiState.settings.gyroBiasY,
                    uiState.settings.gyroBiasZ,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onCalibrateImu()
                    },
                    enabled = !uiState.isCalibrating,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("calibrate_imu_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SignalEmerald,
                        contentColor = Color(0xFF002110),
                    ),
                ) {
                    Text(if (uiState.isCalibrating) "Sampling 1s..." else "Zero IMU Now")
                }
                OutlinedButton(
                    onClick = onResetCalibration,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                    border = BorderStroke(1.dp, CockpitOutline),
                ) {
                    Text("Reset", color = TextSecondary)
                }
            }
        }

        HudCard(
            title = "DIAGNOSTICS & LOCAL JSONL RECORDER",
            icon = Icons.Default.Build,
            iconContentDescription = "Diagnostics and session recorder",
            accentColor = AmberTelemetry,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onToggleRecording,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("toggle_recording_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.recordingStatus.isRecording) AlertCrimson else ElectricCyan,
                        contentColor = if (uiState.recordingStatus.isRecording) Color.White else Color(0xFF001F26),
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Default.FiberManualRecord,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (uiState.recordingStatus.isRecording) {
                            "Stop Rec (${uiState.recordingStatus.framesRecorded})"
                        } else {
                            "Record JSONL"
                        },
                    )
                }

                OutlinedButton(
                    onClick = onShareLatestRecording,
                    enabled = !uiState.recordingStatus.isRecording &&
                        uiState.recordingStatus.lastSavedFileName != null,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                    border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.65f)),
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Export JSONL log",
                        tint = ElectricCyan,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Export", color = ElectricCyan)
                }
            }

            if (uiState.recordingStatus.isRecording) {
                Text(
                    text = "Writing ${uiState.recordingStatus.currentFileName} (${uiState.recordingStatus.bytesWritten / 1024} KB)",
                    style = MaterialTheme.typography.labelSmall,
                    color = AlertCrimson,
                )
            } else if (uiState.recordingStatus.lastSavedFileName != null) {
                Text(
                    text = "Saved: ${uiState.recordingStatus.lastSavedFileName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = SignalEmerald,
                )
            }

            OutlinedButton(
                onClick = onScanDtcs,
                enabled = uiState.telemetry.obd2.connected,
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("scan_dtc_button"),
                border = BorderStroke(1.dp, AmberTelemetry.copy(alpha = 0.7f)),
            ) {
                Text(
                    text = if (uiState.telemetry.obd2.connected) {
                        "Scan Mode 03 DTC Fault Codes"
                    } else {
                        "Connect OBD2 to Scan DTCs"
                    },
                    color = AmberTelemetry,
                )
            }

            HorizontalDivider(color = CockpitOutline.copy(alpha = 0.55f))

            Text(
                text = "DATASTORE SESSION STATS JSON (${uiState.tripExportState.savedSessions.size} SAVED)",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )

            if (!uiState.tripExportState.lastExportedFileName.isNullOrBlank()) {
                Text(
                    text = "Exported: ${uiState.tripExportState.lastExportedFileName} (${uiState.tripExportState.lastExportedBytes} B)",
                    style = MaterialTheme.typography.labelSmall,
                    color = SignalEmerald,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onExportStatsJson,
                    enabled = uiState.lastTripSummary != null && !uiState.isExportingTripJson,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("settings_export_stats_json_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SignalEmerald,
                        contentColor = Color(0xFF002110),
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Default.DataObject,
                        contentDescription = "Export latest trip stats as JSON",
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (uiState.isExportingTripJson) "Exporting..." else "Export Stats JSON",
                        maxLines = 1,
                    )
                }

                if (uiState.tripExportState.savedSessions.size > 1) {
                    OutlinedButton(
                        onClick = onExportAllTripsJson,
                        enabled = !uiState.isExportingTripJson,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("settings_export_all_trips_json_button"),
                        border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.65f)),
                    ) {
                        Text(
                            text = "All (${uiState.tripExportState.savedSessions.size})",
                            color = ElectricCyan,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChipButton(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, if (selected) accent else CockpitOutline),
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize(),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) accent else TextSecondary,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}
