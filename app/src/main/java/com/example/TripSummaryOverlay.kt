package com.example

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.AmberTelemetry
import com.example.ui.theme.CockpitOutline
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Full-screen translucent modal overlay displaying end-of-session trip statistics in a scrollable sheet.
 */
@Composable
fun TripSummaryOverlay(
    summary: TripStatisticsSummary?,
    canExportRecording: Boolean,
    onDismiss: () -> Unit,
    onExportRecording: () -> Unit,
    modifier: Modifier = Modifier,
    lastExportedFileName: String? = null,
    isExportingJson: Boolean = false,
    onExportStatsJson: (() -> Unit)? = null,
) {
    BackHandler(enabled = summary != null) {
        onDismiss()
    }

    AnimatedVisibility(
        visible = summary != null,
        enter = fadeIn(animationSpec = tween(220)) +
            scaleIn(
                initialScale = 0.94f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            ),
        exit = fadeOut(animationSpec = tween(180)) +
            scaleOut(targetScale = 0.96f, animationSpec = tween(180)),
        modifier = modifier,
    ) {
        if (summary != null) {
            TripSummaryOverlayContent(
                summary = summary,
                canExportRecording = canExportRecording,
                lastExportedFileName = lastExportedFileName,
                isExportingJson = isExportingJson,
                onDismiss = onDismiss,
                onExportRecording = onExportRecording,
                onExportStatsJson = onExportStatsJson,
            )
        }
    }
}

@Composable
private fun TripSummaryOverlayContent(
    summary: TripStatisticsSummary,
    canExportRecording: Boolean,
    lastExportedFileName: String?,
    isExportingJson: Boolean,
    onDismiss: () -> Unit,
    onExportRecording: () -> Unit,
    onExportStatsJson: (() -> Unit)?,
) {
    val scrollState = rememberScrollState()
    val backdropInteraction = remember { MutableInteractionSource() }
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }

    val noiseColor = when {
        !summary.hasAudioSamples -> TextMuted
        summary.noiseExposureIndexPct >= 80f || summary.equivalentNoiseDbfs > -24f -> AlertCrimson
        summary.noiseExposureIndexPct >= 40f || summary.equivalentNoiseDbfs > -35f -> AmberTelemetry
        else -> SignalEmerald
    }

    val roadColor = when (summary.worstSurfaceClass) {
        "IMPACT_ANOMALY", "ROUGH" -> AlertCrimson
        "MODERATE" -> AmberTelemetry
        else -> SignalEmerald
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.76f))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("trip_summary_overlay"),
        contentAlignment = Alignment.Center,
    ) {
        val maxOverlayHeight = ((maxHeight - 28.dp) * 0.88f).coerceAtLeast(320.dp)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = backdropInteraction,
                    indication = null,
                    onClick = onDismiss,
                ),
        )

        Surface(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .widthIn(max = 620.dp)
                .fillMaxWidth()
                .heightIn(max = maxOverlayHeight)
                .pointerInput(Unit) { detectTapGestures { } },
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.2.dp, ElectricCyan.copy(alpha = 0.65f)),
            tonalElevation = 8.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Pinned header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    ElectricCyan.copy(alpha = 0.16f),
                                    SignalEmerald.copy(alpha = 0.10f),
                                ),
                            ),
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Flag,
                                    contentDescription = "Trip summary complete",
                                    tint = SignalEmerald,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = "TRIP SUMMARY",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                )
                                StatusPill(label = "SESSION ENDED", color = SignalEmerald)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${summary.sessionId} · ${summary.sessionLabel}",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${timeFormatter.format(Date(summary.startedAtMs))} -> " +
                                    "${timeFormatter.format(Date(summary.endedAtMs))} · " +
                                    "${formatDuration(summary.durationSec)} (${summary.sampleCount} frames)",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("trip_summary_close_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close trip summary",
                                tint = TextSecondary,
                            )
                        }
                    }
                }

                HorizontalDivider(color = CockpitOutline)

                // Scrollable telemetry breakdown
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .testTag("trip_summary_scroll_container")
                        .verticalScroll(scrollState)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Hero KPI 2x2 summary grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SummaryKpiTile(
                            label = "AVG SPEED",
                            value = String.format(Locale.US, "%.1f km/h", summary.averageSpeedKmh),
                            subValue = String.format(Locale.US, "Peak %.1f km/h", summary.maxSpeedKmh),
                            accent = ElectricCyan,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("trip_kpi_avg_speed"),
                        )
                        SummaryKpiTile(
                            label = "MAX ALT CHANGE",
                            value = String.format(Locale.US, "%.1f m", summary.maxAltitudeChangeM),
                            subValue = String.format(Locale.US, "Gain +%.1f m", summary.totalElevationGainM),
                            accent = SignalEmerald,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("trip_kpi_max_alt_change"),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SummaryKpiTile(
                            label = "NOISE EXPOSURE",
                            value = if (summary.hasAudioSamples) {
                                String.format(Locale.US, "%.0f dB·s", summary.totalRoadNoiseExposureDbSec)
                            } else {
                                "0 dB·s"
                            },
                            subValue = if (summary.hasAudioSamples) {
                                String.format(Locale.US, "Leq %.1f dBFS", summary.equivalentNoiseDbfs)
                            } else {
                                "Audio inactive"
                            },
                            accent = noiseColor,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("trip_kpi_noise_exposure"),
                        )
                        SummaryKpiTile(
                            label = "DISTANCE / TIME",
                            value = String.format(Locale.US, "%.2f km", summary.estimatedDistanceKm),
                            subValue = "${formatDuration(summary.durationSec)} · ${
                                String.format(Locale.US, "%.1f Hz", summary.averageStreamHz)
                            }",
                            accent = AmberTelemetry,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("trip_kpi_distance"),
                        )
                    }

                    // Section 1: Speed & Motion Dynamics
                    HudCard(
                        title = "SPEED & MOTION DYNAMICS",
                        icon = Icons.Default.Speed,
                        iconContentDescription = "Speed and motion trip statistics",
                        accentColor = ElectricCyan,
                        modifier = Modifier.testTag("trip_summary_speed_section"),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            MetricBlock(
                                label = "AVG SPEED",
                                value = String.format(
                                    Locale.US,
                                    "%.1f km/h (%.1f m/s)",
                                    summary.averageSpeedKmh,
                                    summary.averageSpeedKmh / 3.6f,
                                ),
                                modifier = Modifier.weight(1.2f),
                            )
                            MetricBlock(
                                label = "MAX SPEED",
                                value = String.format(Locale.US, "%.1f km/h", summary.maxSpeedKmh),
                                modifier = Modifier.weight(0.8f),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            MetricBlock(
                                label = "PEAK LATERAL G",
                                value = String.format(Locale.US, "%.2f G", summary.peakLateralG),
                                modifier = Modifier.weight(1f),
                            )
                            MetricBlock(
                                label = "PEAK LONGITUDINAL G",
                                value = String.format(Locale.US, "%.2f G", summary.peakLongitudinalG),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    // Section 2: Altitude & Barometric Gradient
                    HudCard(
                        title = "ALTITUDE & GRADIENT PROFILE",
                        icon = Icons.Default.Terrain,
                        iconContentDescription = "Altitude and barometric pressure statistics",
                        accentColor = SignalEmerald,
                        modifier = Modifier.testTag("trip_summary_altitude_section"),
                        trailingBadge = {
                            StatusPill(label = summary.altitudeSource, color = SignalEmerald)
                        },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            MetricBlock(
                                label = "MAX ALTITUDE CHANGE",
                                value = String.format(Locale.US, "%.2f m", summary.maxAltitudeChangeM),
                                modifier = Modifier.weight(1f),
                            )
                            MetricBlock(
                                label = "TOTAL CLIMB GAIN",
                                value = String.format(Locale.US, "+%.2f m", summary.totalElevationGainM),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            MetricBlock(
                                label = "NET ELEVATION SHIFT",
                                value = String.format(Locale.US, "%+.2f m", summary.netAltitudeChangeM),
                                modifier = Modifier.weight(1f),
                            )
                            MetricBlock(
                                label = "ALT RANGE (MIN–MAX)",
                                value = String.format(
                                    Locale.US,
                                    "%.1f – %.1f m",
                                    summary.minAltitudeM,
                                    summary.maxAltitudeM,
                                ),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (summary.pressureMaxHpa > 0f) {
                            MetricBlock(
                                label = "BAROMETRIC PRESSURE SPAN (TYPE_PRESSURE 5HZ)",
                                value = String.format(
                                    Locale.US,
                                    "%.2f – %.2f hPa (Δ %.2f hPa)",
                                    summary.pressureMinHpa,
                                    summary.pressureMaxHpa,
                                    (summary.pressureMaxHpa - summary.pressureMinHpa).coerceAtLeast(0f),
                                ),
                            )
                        }
                    }

                    // Section 3: Total Road Noise Exposure
                    HudCard(
                        title = "TOTAL ROAD NOISE EXPOSURE",
                        icon = Icons.Default.GraphicEq,
                        iconContentDescription = "Acoustic road noise exposure statistics",
                        accentColor = noiseColor,
                        modifier = Modifier.testTag("trip_summary_noise_section"),
                        trailingBadge = {
                            StatusPill(
                                label = if (summary.hasAudioSamples) {
                                    "${summary.noiseExposureIndexPct.roundToInt()}% DOSE"
                                } else {
                                    "NO MIC"
                                },
                                color = noiseColor,
                            )
                        },
                    ) {
                        if (!summary.hasAudioSamples) {
                            Text(
                                text = "Microphone acoustic road sensing was inactive or denied during this session. Enable AudioRecord permission in Setup to measure road noise exposure.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                            )
                        } else {
                            GaugeBarRow(
                                label = "CUMULATIVE EXPOSURE INDEX",
                                valueText = String.format(
                                    Locale.US,
                                    "%.1f dB·s (%.1f%%)",
                                    summary.totalRoadNoiseExposureDbSec,
                                    summary.noiseExposureIndexPct,
                                ),
                                fraction = (summary.noiseExposureIndexPct / 100f).coerceIn(0f, 1f),
                                color = noiseColor,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                MetricBlock(
                                    label = "EQUIVALENT LEVEL (Leq)",
                                    value = String.format(Locale.US, "%.1f dBFS", summary.equivalentNoiseDbfs),
                                    modifier = Modifier.weight(1f),
                                )
                                MetricBlock(
                                    label = "PEAK CABIN NOISE",
                                    value = String.format(Locale.US, "%.1f dBFS", summary.peakNoiseDbfs),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                MetricBlock(
                                    label = "HIGH NOISE (> -35 dBFS)",
                                    value = formatDuration(summary.elevatedNoiseDurationSec),
                                    modifier = Modifier.weight(1f),
                                )
                                MetricBlock(
                                    label = "ACOUSTIC ENERGY DOSE",
                                    value = String.format(Locale.US, "%.4f Pa²·s", summary.totalNoiseEnergyDosage),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }

                    // Section 4: Road Surface & Powertrain Summary
                    HudCard(
                        title = "ROAD SURFACE & POWERTRAIN",
                        icon = Icons.Default.Route,
                        iconContentDescription = "Road surface quality and OBD2 trip metrics",
                        accentColor = roadColor,
                        modifier = Modifier.testTag("trip_summary_road_section"),
                        trailingBadge = {
                            StatusPill(label = summary.worstSurfaceClass, color = roadColor)
                        },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            MetricBlock(
                                label = "AVG ROAD SCORE",
                                value = "${summary.averageRoadScore} / 100",
                                modifier = Modifier.weight(1f),
                            )
                            MetricBlock(
                                label = "PEAK VIBRATION RMS",
                                value = String.format(Locale.US, "%.2f m/s²", summary.peakVibrationRms),
                                modifier = Modifier.weight(1f),
                            )
                            MetricBlock(
                                label = "IMPACTS",
                                value = summary.impactCount.toString(),
                                modifier = Modifier.weight(0.7f),
                            )
                        }

                        if (summary.obd2ConnectedDuringTrip) {
                            HorizontalDivider(color = CockpitOutline.copy(alpha = 0.5f))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                MetricBlock(
                                    label = "AVG / PEAK RPM",
                                    value = "${summary.averageRpm} / ${summary.peakRpm}",
                                    modifier = Modifier.weight(1f),
                                )
                                MetricBlock(
                                    label = "MAX COOLANT",
                                    value = "${summary.maxCoolantTempC} °C",
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = CockpitOutline)

                // Pinned footer
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!lastExportedFileName.isNullOrBlank()) {
                        Text(
                            text = "DataStore JSON saved: $lastExportedFileName",
                            style = MaterialTheme.typography.labelSmall,
                            color = SignalEmerald,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("trip_summary_exported_badge"),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (onExportStatsJson != null) {
                            OutlinedButton(
                                onClick = onExportStatsJson,
                                enabled = !isExportingJson,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("trip_summary_export_json_button"),
                                border = BorderStroke(1.dp, SignalEmerald.copy(alpha = 0.75f)),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DataObject,
                                    contentDescription = "Export and share trip statistics JSON",
                                    tint = SignalEmerald,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isExportingJson) "Saving..." else "Share JSON",
                                    color = SignalEmerald,
                                    maxLines = 1,
                                )
                            }
                        }

                        if (canExportRecording) {
                            OutlinedButton(
                                onClick = onExportRecording,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("trip_summary_export_button"),
                                border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.7f)),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Export session JSONL",
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Log", color = ElectricCyan, maxLines = 1)
                            }
                        }

                        Button(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("dismiss_trip_summary_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricCyan,
                                contentColor = Color(0xFF001F26),
                            ),
                        ) {
                            Text(
                                text = "Done",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryKpiTile(
    label: String,
    value: String,
    subValue: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = accent.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = accent,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subValue,
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun formatDuration(seconds: Double): String {
    val totalSec = seconds.roundToInt().coerceAtLeast(0)
    val hrs = totalSec / 3600
    val mins = (totalSec % 3600) / 60
    val secs = totalSec % 60
    return if (hrs > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format(Locale.US, "%02d:%02d", mins, secs)
    }
}
