package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.AmberTelemetry
import com.example.ui.theme.CockpitOutline
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun RoadIntelligenceCard(
    road: RoadIntelligenceState,
    modifier: Modifier = Modifier,
) {
    val surfaceColor = when (road.surfaceClass) {
        "IMPACT_ANOMALY" -> AlertCrimson
        "ROUGH" -> AlertCrimson
        "MODERATE" -> AmberTelemetry
        else -> SignalEmerald
    }

    val rmsHistory = remember { mutableStateListOf<Float>() }
    LaunchedEffect(road.vibrationRms, road.conditionScore) {
        if (rmsHistory.size >= 40) {
            rmsHistory.removeAt(0)
        }
        rmsHistory.add(road.vibrationRms)
    }

    HudCard(
        title = "ROAD CONDITION INTELLIGENCE",
        icon = Icons.Default.Route,
        iconContentDescription = stringResource(R.string.cd_road_intel_icon),
        accentColor = surfaceColor,
        modifier = modifier.testTag("road_intelligence_card"),
        trailingBadge = {
            LiveBeaconStatusPill(
                label = road.surfaceClass.replace("_", " "),
                color = surfaceColor,
                active = road.impactDetected || road.surfaceClass == "ROUGH",
            )
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "SURFACE QUALITY INDEX",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
                AnimatedNumericReadout(
                    text = "${road.conditionScore}/100",
                    style = MaterialTheme.typography.displaySmall,
                    color = surfaceColor,
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MetricBlock(
                    label = "VIBRATION RMS",
                    value = String.format(Locale.US, "%.2f m/s²", road.vibrationRms),
                )
                MetricBlock(
                    label = "IMPACT DETECTOR",
                    value = if (road.impactDetected) "SHOCK SPIKE" else "NOMINAL",
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "ROLLING VIBRATION RMS (m/s²)",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
            RollingSparklineChart(
                samples = rmsHistory,
                lineColor = surfaceColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
            )
        }

        GaugeBarRow(
            label = "RIDE SMOOTHNESS SCORE",
            valueText = "${road.conditionScore}%",
            fraction = (road.conditionScore / 100f).coerceIn(0f, 1f),
            color = surfaceColor,
        )
    }
}

@Composable
fun EnvironmentAndHealthCard(
    sensors: SensorsState,
    health: DeviceHealthState,
    hasAudioPermission: Boolean,
    acousticEnabled: Boolean,
    onRequestAudioPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val avail = sensors.availability

    HudCard(
        title = "ENVIRONMENT, ACOUSTIC & DEVICE HEALTH",
        icon = Icons.Default.LightMode,
        iconContentDescription = stringResource(R.string.cd_env_health_icon),
        accentColor = ElectricCyan,
        modifier = modifier.testTag("environment_health_card"),
        trailingBadge = {
            StatusPill(
                label = "${health.batteryPct}% · ${String.format(Locale.US, "%.1f°C", health.batteryTempC)}",
                color = if (health.batteryTempC >= 42f) AlertCrimson else SignalEmerald,
            )
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MetricBlock(
                label = "BAROMETER",
                value = if (avail.barometer) {
                    String.format(Locale.US, "%.1f hPa", sensors.barometer.pressureHpa)
                } else {
                    "N/A"
                },
                modifier = Modifier.weight(1f),
            )
            MetricBlock(
                label = "BARO ALT",
                value = if (avail.barometer) {
                    String.format(Locale.US, "%.1f m", sensors.barometer.relAltitudeM)
                } else {
                    "N/A"
                },
                modifier = Modifier.weight(1f),
            )
            MetricBlock(
                label = "AMBIENT LIGHT",
                value = if (avail.light) {
                    String.format(Locale.US, "%.0f lx · %s", sensors.ambientLight.lux, sensors.ambientLight.condition)
                } else {
                    "N/A"
                },
                modifier = Modifier.weight(1.3f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MetricBlock(
                label = "PROXIMITY",
                value = if (avail.proximity) {
                    val stateTag = if (sensors.proximity.isNear) "COVERED" else "CLEAR"
                    String.format(Locale.US, "%.1f cm (%s)", sensors.proximity.distanceCm, stateTag)
                } else {
                    "N/A"
                },
                modifier = Modifier.weight(1.1f),
            )
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = "Wi-Fi signal strength",
                    tint = if (health.wifiRssiDbm > -75) SignalEmerald else AmberTelemetry,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                MetricBlock(
                    label = "WI-FI LINK",
                    value = if (health.wifiRssiDbm <= -127) {
                        "OFFLINE"
                    } else {
                        "${health.wifiRssiDbm} dBm · ${health.wifiLinkMbps}M"
                    },
                )
            }
            Row(
                modifier = Modifier.weight(0.9f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (health.charging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryStd,
                    contentDescription = "Device battery status",
                    tint = if (health.charging) SignalEmerald else ElectricCyan,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                MetricBlock(
                    label = "POWER",
                    value = if (health.charging) "CHARGING" else "BATTERY",
                )
            }
        }

        HorizontalDivider(color = CockpitOutline.copy(alpha = 0.5f))

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
                    imageVector = when {
                        !hasAudioPermission || !acousticEnabled -> Icons.Default.MicOff
                        sensors.audio.active -> Icons.Default.GraphicEq
                        else -> Icons.Default.Mic
                    },
                    contentDescription = "Acoustic road sensor",
                    tint = when {
                        !hasAudioPermission -> AmberTelemetry
                        sensors.audio.active -> SignalEmerald
                        else -> TextMuted
                    },
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "ACOUSTIC ROAD SENSING",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                    Text(
                        text = when {
                            !hasAudioPermission -> "Microphone permission not granted"
                            !acousticEnabled -> "Paused in Setup tab"
                            sensors.audio.active -> String.format(
                                Locale.US,
                                "%.1f dBFS · ZCR %.0f Hz · Peak %.2f",
                                sensors.audio.rmsDbfs,
                                sensors.audio.zcrHz,
                                sensors.audio.peakAmp,
                            )
                            else -> "Initializing PCM capture..."
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            if (!hasAudioPermission) {
                OutlinedButton(
                    onClick = onRequestAudioPermission,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("grant_audio_button"),
                    border = BorderStroke(1.dp, AmberTelemetry.copy(alpha = 0.7f)),
                ) {
                    Text(
                        text = "Enable Mic",
                        color = AmberTelemetry,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SensorAvailabilityGrid(
    availability: SensorAvailabilityState,
    modifier: Modifier = Modifier,
) {
    val badges = listOf(
        "ACC" to availability.accel,
        "GYR" to availability.gyro,
        "MAG" to availability.mag,
        "GRAV" to availability.gravity,
        "LIN" to availability.linearAccel,
        "ROT" to availability.rotationVector,
        "BARO" to availability.barometer,
        "LUX" to availability.light,
        "PROX" to availability.proximity,
        "MIC" to availability.audio,
    )

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        badges.forEach { (label, active) ->
            val color: Color = if (active) SignalEmerald else TextMuted
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = color.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = color,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}
