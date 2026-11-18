package com.example

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.AmberTelemetry
import com.example.ui.theme.CockpitOutline
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.sqrt

@Composable
fun CockpitHeaderBanner(
    streamerStatus: StreamerStatus,
    recordingStatus: RecordingStatus,
    deviceId: String,
    hudMode: HudMode,
    onHudModeSelected: (HudMode) -> Unit,
    configuredHz: Int = streamerStatus.effectiveHz,
) {
    val isStreaming = streamerStatus.isStreaming
    val displayRateHz = if (isStreaming) streamerStatus.effectiveHz else configuredHz

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isStreaming) SignalEmerald.copy(alpha = 0.65f) else CockpitOutline,
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_hud_horizon_1794862712971),
                    contentDescription = stringResource(R.string.cd_hud_banner),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.55f),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.72f),
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                                ),
                            ),
                        ),
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ROADEYE NEXUS",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "$deviceId // ${displayRateHz}HZ UDP",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (recordingStatus.isRecording) {
                                LiveBeaconStatusPill(label = "REC", color = AlertCrimson, active = true)
                            }
                            LiveBeaconStatusPill(
                                label = stringResource(
                                    if (isStreaming) R.string.status_streaming else R.string.status_standby,
                                ),
                                color = if (isStreaming) SignalEmerald else TextSecondary,
                                active = isStreaming,
                            )
                        }
                    }

                    val animatedMeasuredHz by animateFloatAsState(
                        targetValue = if (isStreaming) streamerStatus.measuredHz else 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                        label = "header_measured_hz",
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AnimatedNumericReadout(
                                text = String.format(Locale.US, "%.1f Hz", animatedMeasuredHz),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isStreaming) SignalEmerald else TextMuted,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            AnimatedNumericReadout(
                                text = "#${streamerStatus.sequenceNumber}",
                                style = MaterialTheme.typography.labelMedium,
                                color = ElectricCyan,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            val thermalColor = when (streamerStatus.thermalStatus) {
                                "THROTTLED" -> AlertCrimson
                                "WARM" -> AmberTelemetry
                                else -> TextSecondary
                            }
                            Icon(
                                imageVector = Icons.Default.Thermostat,
                                contentDescription = "Thermal status",
                                tint = thermalColor,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = streamerStatus.thermalStatus,
                                style = MaterialTheme.typography.labelSmall,
                                color = thermalColor,
                            )
                        }

                        if (isStreaming) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Lock,
                                    contentDescription = "Foreground wake lock active",
                                    tint = AmberTelemetry,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "FGS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AmberTelemetry,
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = CockpitOutline.copy(alpha = 0.7f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HudMode.entries.forEach { mode ->
                    val selected = mode == hudMode
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) {
                            ElectricCyan.copy(alpha = 0.16f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (selected) ElectricCyan else CockpitOutline,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onHudModeSelected(mode) }
                            .minimumInteractiveComponentSize()
                            .testTag("hud_mode_${mode.name.lowercase()}"),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 8.dp),
                        ) {
                            Text(
                                text = mode.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) ElectricCyan else TextSecondary,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionBannerCard(
    onGrantClick: () -> Unit,
    onOpenSettingsClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = AmberTelemetry.copy(alpha = 0.12f)),
        border = BorderStroke(1.dp, AmberTelemetry.copy(alpha = 0.6f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Permissions required",
                    tint = AmberTelemetry,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.permissions_banner_msg),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onGrantClick,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("grant_permissions_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AmberTelemetry,
                        contentColor = Color(0xFF1F1400),
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.grant_permissions),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                OutlinedButton(
                    onClick = onOpenSettingsClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("open_app_settings_button"),
                    border = BorderStroke(1.dp, AmberTelemetry.copy(alpha = 0.7f)),
                ) {
                    Text(
                        text = stringResource(R.string.open_app_settings),
                        style = MaterialTheme.typography.titleSmall,
                        color = AmberTelemetry,
                    )
                }
            }
        }
    }
}

@Composable
fun MasterStreamToggleButton(
    isStreaming: Boolean,
    rateHz: Int,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Button(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onToggle()
        },
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .testTag("master_stream_button"),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isStreaming) AlertCrimson else ElectricCyan,
            contentColor = if (isStreaming) Color.White else Color(0xFF001F26),
        ),
    ) {
        Icon(
            imageVector = if (isStreaming) Icons.Default.Stop else Icons.Default.PlayArrow,
            contentDescription = if (isStreaming) "Stop UDP stream" else "Start UDP stream",
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = if (isStreaming) {
                stringResource(R.string.btn_stop_stream)
            } else {
                "ENGAGE ${rateHz}HZ STREAM"
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun NetworkConfigCard(
    targetIp: String,
    targetPort: String,
    ipError: String?,
    portError: String?,
    isStreaming: Boolean,
    onIpChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
) {
    HudCard(
        title = stringResource(R.string.network_config_title),
        icon = Icons.Default.NetworkCheck,
        iconContentDescription = stringResource(R.string.cd_network_icon),
        accentColor = ElectricCyan,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = targetIp,
                onValueChange = onIpChange,
                enabled = !isStreaming,
                singleLine = true,
                label = { Text(stringResource(R.string.label_target_ip)) },
                isError = ipError != null,
                supportingText = ipError?.let { msg -> { Text(msg) } },
                textStyle = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricCyan,
                    unfocusedBorderColor = CockpitOutline,
                    focusedLabelColor = ElectricCyan,
                ),
                modifier = Modifier
                    .weight(1.65f)
                    .testTag("target_ip_input"),
            )

            OutlinedTextField(
                value = targetPort,
                onValueChange = onPortChange,
                enabled = !isStreaming,
                singleLine = true,
                label = { Text(stringResource(R.string.label_target_port)) },
                isError = portError != null,
                supportingText = portError?.let { msg -> { Text(msg) } },
                textStyle = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricCyan,
                    unfocusedBorderColor = CockpitOutline,
                    focusedLabelColor = ElectricCyan,
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("target_port_input"),
            )
        }
        if (isStreaming) {
            Text(
                text = "Target locked while UDP stream is active.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        }
    }
}

@Composable
fun Obd2SelectorCard(
    obd2: Obd2State,
    lastDeviceName: String,
    lastDeviceAddress: String,
    isBluetoothSupported: Boolean,
    isBluetoothEnabled: Boolean,
    hasBluetoothPermission: Boolean,
    onSelectClick: () -> Unit,
    onQuickConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit,
) {
    val (statusLabel, statusColor) = when (obd2.status) {
        Obd2ConnectionStatus.CONNECTED -> stringResource(R.string.status_obd_connected) to SignalEmerald
        Obd2ConnectionStatus.POLLING -> stringResource(R.string.status_obd_polling) to SignalEmerald
        Obd2ConnectionStatus.CONNECTING -> stringResource(R.string.status_obd_connecting) to AmberTelemetry
        Obd2ConnectionStatus.RECONNECTING -> stringResource(R.string.status_obd_reconnecting) to AmberTelemetry
        Obd2ConnectionStatus.ERROR -> "ERROR" to AlertCrimson
        Obd2ConnectionStatus.DISCONNECTED -> stringResource(R.string.status_obd_disconnected) to TextMuted
    }

    HudCard(
        title = stringResource(R.string.obd2_link_title),
        icon = when {
            obd2.connected -> Icons.Default.BluetoothConnected
            !isBluetoothEnabled || !isBluetoothSupported -> Icons.Default.BluetoothDisabled
            else -> Icons.Default.Bluetooth
        },
        iconContentDescription = stringResource(R.string.cd_bluetooth_icon),
        accentColor = AmberTelemetry,
        trailingBadge = {
            val isObdLive = obd2.connected ||
                obd2.status == Obd2ConnectionStatus.CONNECTING ||
                obd2.status == Obd2ConnectionStatus.RECONNECTING
            LiveBeaconStatusPill(label = statusLabel, color = statusColor, active = isObdLive)
        },
    ) {
        val selectedLabel = if (obd2.deviceName.isNotBlank()) {
            "${obd2.deviceName} (${obd2.deviceAddress}) · ${obd2.protocol}"
        } else {
            "No ELM327 dongle selected"
        }
        Text(
            text = selectedLabel,
            style = MaterialTheme.typography.labelMedium,
            color = if (obd2.deviceName.isNotBlank()) {
                MaterialTheme.colorScheme.onSurface
            } else {
                TextSecondary
            },
        )

        if (!obd2.lastError.isNullOrBlank()) {
            Text(
                text = "OBD2 Notice: ${obd2.lastError}",
                style = MaterialTheme.typography.bodyMedium,
                color = AlertCrimson,
            )
        } else if (!isBluetoothSupported) {
            Text(
                text = "Bluetooth hardware unavailable on this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        } else if (!hasBluetoothPermission) {
            Text(
                text = "Grant Bluetooth permission to list paired ELM327 adapters.",
                style = MaterialTheme.typography.bodyMedium,
                color = AmberTelemetry,
            )
        } else if (!isBluetoothEnabled) {
            Text(
                text = "Bluetooth radio is turned off.",
                style = MaterialTheme.typography.bodyMedium,
                color = AmberTelemetry,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onSelectClick,
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("select_obd2_button"),
                border = BorderStroke(1.dp, AmberTelemetry.copy(alpha = 0.7f)),
            ) {
                Text(
                    text = stringResource(R.string.btn_select_obd2),
                    color = AmberTelemetry,
                    maxLines = 1,
                )
            }

            if (
                obd2.status == Obd2ConnectionStatus.DISCONNECTED &&
                lastDeviceAddress.isNotBlank() &&
                hasBluetoothPermission &&
                isBluetoothEnabled
            ) {
                OutlinedButton(
                    onClick = onQuickConnectClick,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("quick_connect_obd2_button"),
                    border = BorderStroke(1.dp, SignalEmerald.copy(alpha = 0.75f)),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Bolt,
                        contentDescription = null,
                        tint = SignalEmerald,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = lastDeviceName.ifBlank { stringResource(R.string.btn_quick_connect_obd2) },
                        color = SignalEmerald,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (obd2.status != Obd2ConnectionStatus.DISCONNECTED) {
                OutlinedButton(
                    onClick = onDisconnectClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("disconnect_obd2_button"),
                    border = BorderStroke(1.dp, AlertCrimson.copy(alpha = 0.7f)),
                ) {
                    Text(
                        text = stringResource(R.string.btn_disconnect_obd2),
                        color = AlertCrimson,
                    )
                }
            }
        }
    }
}

@Composable
fun ImuTelemetryCard(
    sensors: SensorsState,
    modifier: Modifier = Modifier,
) {
    val gMag = sqrt(
        sensors.accel.x * sensors.accel.x +
            sensors.accel.y * sensors.accel.y +
            sensors.accel.z * sensors.accel.z,
    ) / 9.80665f

    HudCard(
        title = "IMU FUSION // 9-DOF & V2 MOTION",
        icon = Icons.Default.Sensors,
        iconContentDescription = stringResource(R.string.cd_imu_icon),
        accentColor = ElectricCyan,
        modifier = modifier.testTag("imu_telemetry_card"),
        trailingBadge = {
            StatusPill(
                label = String.format(Locale.US, "%.2f G", gMag),
                color = ElectricCyan,
            )
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                VectorRow(label = "ACC (m/s²)", v = sensors.accel, decimals = 2, accent = ElectricCyan)
                VectorRow(label = "GYR (rad/s)", v = sensors.gyro, decimals = 3, accent = SignalEmerald)
                VectorRow(label = "MAG (µT)", v = sensors.mag, decimals = 1, accent = AmberTelemetry)
            }
            GForceTargetPlot(
                accelX = sensors.accel.x,
                accelY = sensors.accel.y,
                modifier = Modifier.size(102.dp),
            )
        }
        VectorRow(label = "GRAVITY (m/s²)", v = sensors.gravity, decimals = 2, accent = ElectricCyan)
        VectorRow(label = "LINEAR ACC (m/s²)", v = sensors.linearAccel, decimals = 2, accent = SignalEmerald)
        MetricBlock(
            label = "ROTATION QUAT (X, Y, Z, W)",
            value = String.format(
                Locale.US,
                "%.3f, %.3f, %.3f, %.3f",
                sensors.rotationVector.x,
                sensors.rotationVector.y,
                sensors.rotationVector.z,
                sensors.rotationVector.w,
            ),
        )
        HorizontalDivider(color = CockpitOutline.copy(alpha = 0.5f))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MetricBlock(
                label = "PITCH",
                value = String.format(Locale.US, "%.1f°", sensors.orientation.pitchDeg),
                modifier = Modifier.weight(1f),
            )
            MetricBlock(
                label = "ROLL",
                value = String.format(Locale.US, "%.1f°", sensors.orientation.rollDeg),
                modifier = Modifier.weight(1f),
            )
            MetricBlock(
                label = "YAW",
                value = String.format(Locale.US, "%.1f°", sensors.orientation.yawDeg),
                modifier = Modifier.weight(1f),
            )
        }
        SensorAvailabilityGrid(availability = sensors.availability)
    }
}

@Composable
fun GpsTelemetryCard(
    gps: GpsState,
    hasLocationPermission: Boolean,
    modifier: Modifier = Modifier,
) {
    HudCard(
        title = "GNSS POSITIONING",
        icon = if (gps.hasFix) Icons.Default.GpsFixed else Icons.Default.GpsOff,
        iconContentDescription = stringResource(R.string.cd_gps_icon),
        accentColor = SignalEmerald,
        modifier = modifier.testTag("gps_telemetry_card"),
        trailingBadge = {
            LiveBeaconStatusPill(
                label = when {
                    !hasLocationPermission -> "NO PERM"
                    gps.hasFix -> String.format(Locale.US, "±%.0fm FIX", gps.accuracyM)
                    else -> "ACQUIRING"
                },
                color = when {
                    !hasLocationPermission -> AlertCrimson
                    gps.hasFix -> SignalEmerald
                    else -> AmberTelemetry
                },
                active = gps.hasFix,
            )
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MetricBlock(
                label = "LATITUDE",
                value = String.format(Locale.US, "%.6f", gps.lat),
                modifier = Modifier.weight(1f),
            )
            MetricBlock(
                label = "LONGITUDE",
                value = String.format(Locale.US, "%.6f", gps.lng),
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MetricBlock(
                label = "SPEED",
                value = String.format(
                    Locale.US,
                    "%.1f m/s (%.0f km/h)",
                    gps.speedMps,
                    gps.speedMps * 3.6f,
                ),
                modifier = Modifier.weight(1.3f),
            )
            MetricBlock(
                label = "BEARING",
                value = String.format(Locale.US, "%.1f°", gps.bearing),
                modifier = Modifier.weight(0.7f),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Obd2TelemetryCard(
    obd2: Obd2State,
    modifier: Modifier = Modifier,
) {
    HudCard(
        title = "OBD2 POWERTRAIN TELEMETRY",
        icon = Icons.Default.Speed,
        iconContentDescription = stringResource(R.string.cd_obd_icon),
        accentColor = AmberTelemetry,
        modifier = modifier.testTag("obd2_telemetry_card"),
        trailingBadge = {
            LiveBeaconStatusPill(
                label = if (obd2.connected) "LIVE ECU" else "OFFLINE",
                color = if (obd2.connected) SignalEmerald else TextMuted,
                active = obd2.connected,
            )
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "ENGINE RPM",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
                AnimatedNumericReadout(
                    text = obd2.rpm.toString(),
                    style = MaterialTheme.typography.displaySmall,
                    color = AmberTelemetry,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "VEHICLE SPEED",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
                AnimatedNumericReadout(
                    text = "${obd2.speedKmh} km/h",
                    style = MaterialTheme.typography.displaySmall,
                    color = ElectricCyan,
                )
            }
        }

        GaugeBarRow(
            label = "THROTTLE (0111)",
            valueText = String.format(Locale.US, "%.1f%%", obd2.throttlePos),
            fraction = (obd2.throttlePos / 100f).coerceIn(0f, 1f),
            color = ElectricCyan,
        )
        GaugeBarRow(
            label = "ENGINE LOAD (0104)",
            valueText = String.format(Locale.US, "%.1f%%", obd2.engineLoad),
            fraction = (obd2.engineLoad / 100f).coerceIn(0f, 1f),
            color = AmberTelemetry,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MetricBlock(
                label = "COOLANT",
                value = "${obd2.coolantTempC} °C",
                modifier = Modifier.weight(1f),
            )
            MetricBlock(
                label = "INTAKE",
                value = "${obd2.intakeTempC} °C",
                modifier = Modifier.weight(1f),
            )
            MetricBlock(
                label = "MAF",
                value = String.format(Locale.US, "%.1f g/s", obd2.mafGps),
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MetricBlock(
                label = "TIMING ADV",
                value = String.format(Locale.US, "%.1f°", obd2.timingAdvanceDeg),
                modifier = Modifier.weight(1f),
            )
            MetricBlock(
                label = "FUEL LEVEL",
                value = String.format(Locale.US, "%.0f%%", obd2.fuelLevelPct),
                modifier = Modifier.weight(1f),
            )
            MetricBlock(
                label = "DTC FAULTS",
                value = obd2.dtcs.size.toString(),
                modifier = Modifier.weight(1f),
            )
        }

        if (obd2.dtcs.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                obd2.dtcs.forEach { code ->
                    StatusPill(label = code, color = AlertCrimson)
                }
            }
        }
    }
}

@Composable
fun UdpDiagnosticsCard(
    streamerStatus: StreamerStatus,
    snapshot: TelemetrySnapshot,
    modifier: Modifier = Modifier,
) {
    var showInspector by remember { mutableStateOf(false) }

    HudCard(
        title = "UDP LINK & PACKET INSPECTOR",
        icon = Icons.Default.Explore,
        iconContentDescription = "UDP link telemetry",
        accentColor = ElectricCyan,
        modifier = modifier.testTag("udp_diagnostics_card"),
        trailingBadge = {
            LiveBeaconStatusPill(
                label = if (streamerStatus.isStreaming) "${streamerStatus.lastPacketBytes} B LIVE" else "5005 IDLE",
                color = if (streamerStatus.isStreaming) SignalEmerald else ElectricCyan,
                active = streamerStatus.isStreaming,
            )
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "PACKETS SENT",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
                AnimatedNumericReadout(
                    text = streamerStatus.packetsSent.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            MetricBlock(
                label = "TARGET",
                value = streamerStatus.targetEndpoint.ifBlank { "Standby" },
                modifier = Modifier.weight(1f),
            )
        }

        if (!streamerStatus.lastError.isNullOrBlank()) {
            Text(
                text = "Socket Notice: ${streamerStatus.lastError}",
                style = MaterialTheme.typography.bodyMedium,
                color = AlertCrimson,
            )
        }

        TextButton(
            onClick = { showInspector = !showInspector },
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("toggle_json_preview_button"),
        ) {
            Icon(
                imageVector = Icons.Outlined.Code,
                contentDescription = "Toggle JSON packet inspector",
                tint = ElectricCyan,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (showInspector) {
                    "Hide Live Packet Inspector"
                } else {
                    "Inspect Live Packets (${streamerStatus.recentPackets.size})"
                },
                style = MaterialTheme.typography.labelMedium,
                color = ElectricCyan,
            )
        }

        AnimatedVisibility(visible = showInspector) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val packets = streamerStatus.recentPackets
                if (packets.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.background,
                        border = BorderStroke(1.dp, CockpitOutline),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = snapshot.toJsonObject().toString(2),
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = SignalEmerald,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                } else {
                    packets.forEachIndexed { idx, pkt ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.background,
                            border = BorderStroke(1.dp, CockpitOutline),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "SEQ #${pkt.seq} · ${pkt.byteSize}B · CRC ${pkt.crc32}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ElectricCyan,
                                )
                                if (idx == 0) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = pkt.rawJson,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 10.sp,
                                        lineHeight = 14.sp,
                                        color = SignalEmerald,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BluetoothDevicePickerDialog(
    devices: List<PairedBluetoothDevice>,
    hasPermission: Boolean,
    isBluetoothEnabled: Boolean,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onDeviceSelected: (PairedBluetoothDevice) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = "Select Paired ELM327 Adapter",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            when {
                !hasPermission -> {
                    Text(
                        text = "Bluetooth permission is required to read paired OBD2 dongles.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AmberTelemetry,
                    )
                }

                !isBluetoothEnabled -> {
                    Text(
                        text = "Bluetooth is disabled. Enable Bluetooth in Android settings and tap Refresh.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AmberTelemetry,
                    )
                }

                devices.isEmpty() -> {
                    Text(
                        text = "No paired Bluetooth devices found. Pair your ELM327 OBDII dongle in Android Bluetooth settings first.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }

                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(devices, key = { it.address }) { device ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, CockpitOutline),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onDeviceSelected(device) }
                                    .minimumInteractiveComponentSize(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bluetooth,
                                        contentDescription = null,
                                        tint = AmberTelemetry,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = device.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = device.address,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onRefresh) {
                Text("Refresh", color = ElectricCyan)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = TextSecondary)
            }
        },
    )
}

@Composable
fun HudCard(
    title: String,
    icon: ImageVector,
    iconContentDescription: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    trailingBadge: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, CockpitOutline),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = iconContentDescription,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = accentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (trailingBadge != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    trailingBadge()
                }
            }
            HorizontalDivider(color = CockpitOutline.copy(alpha = 0.65f))
            content()
        }
    }
}

@Composable
fun StatusPill(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.55f)),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun VectorRow(
    label: String,
    v: Vector3State,
    decimals: Int,
    accent: Color,
) {
    val fmt = "%.${decimals}f"
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            AxisBadge("X", String.format(Locale.US, fmt, v.x), accent, Modifier.weight(1f))
            AxisBadge("Y", String.format(Locale.US, fmt, v.y), accent, Modifier.weight(1f))
            AxisBadge("Z", String.format(Locale.US, fmt, v.z), accent, Modifier.weight(1f))
        }
    }
}

@Composable
fun AxisBadge(
    axis: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$axis:",
            style = MaterialTheme.typography.labelSmall,
            color = accent,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
fun MetricBlock(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun GaugeBarRow(
    label: String,
    valueText: String,
    fraction: Float,
    color: Color,
) {
    val animatedFraction by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "gauge_bar_fraction",
    )

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                maxLines = 1,
                softWrap = false,
            )
        }
        LinearProgressIndicator(
            progress = { animatedFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round,
        )
    }
}
