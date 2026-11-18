package com.example

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.AmberTelemetry
import com.example.ui.theme.CockpitOutline
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Status pill with a rememberInfiniteTransition pulsing live beacon when [active] is true.
 */
@Composable
fun LiveBeaconStatusPill(
    label: String,
    color: Color,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "live_beacon_$label")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = if (active) 0.30f else 1f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "beacon_alpha",
    )
    val haloScale by infiniteTransition.animateFloat(
        initialValue = if (active) 0.9f else 1f,
        targetValue = if (active) 1.55f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "beacon_halo",
    )

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = if (active) 0.16f else 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = if (active) 0.65f else 0.35f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (active) {
                    Box(
                        modifier = Modifier
                            .size((7f * haloScale).dp)
                            .clip(CircleShape)
                            .background(color.copy(alpha = 0.28f * pulseAlpha)),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (active) color.copy(alpha = pulseAlpha) else color.copy(alpha = 0.7f)),
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                maxLines = 1,
            )
        }
    }
}

/**
 * Vertical slide-and-fade numeric readout transition for fast-changing telemetry counters.
 */
@Composable
fun AnimatedNumericReadout(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (slideInVertically(animationSpec = tween(160)) { height -> height / 3 } +
                fadeIn(animationSpec = tween(140)))
                .togetherWith(
                    slideOutVertically(animationSpec = tween(160)) { height -> -height / 3 } +
                        fadeOut(animationSpec = tween(120)),
                )
                .using(SizeTransform(clip = false))
        },
        label = "numeric_digit_transition",
        modifier = modifier,
    ) { targetText ->
        Text(
            text = targetText,
            style = style,
            color = color,
            maxLines = 1,
        )
    }
}

/**
 * Custom Canvas 2D G-force target reticle visualizing lateral (X) and longitudinal (Y) IMU acceleration.
 */
@Composable
fun GForceTargetPlot(
    accelX: Float,
    accelY: Float,
    modifier: Modifier = Modifier,
) {
    val maxG = 1.5f
    val rawGx = (accelX / 9.80665f).coerceIn(-maxG, maxG)
    val rawGy = (accelY / 9.80665f).coerceIn(-maxG, maxG)

    val animGx by animateFloatAsState(
        targetValue = rawGx,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "gforce_x",
    )
    val animGy by animateFloatAsState(
        targetValue = rawGy,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "gforce_y",
    )

    val trail = remember { mutableStateListOf<Pair<Float, Float>>() }
    LaunchedEffect(rawGx, rawGy) {
        if (trail.size >= 10) {
            trail.removeAt(0)
        }
        trail.add(rawGx to rawGy)
    }

    val radarTransition = rememberInfiniteTransition(label = "gforce_radar_sweep")
    val sweepAngle by radarTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "radar_sweep_angle",
    )

    val totalG = hypot(rawGx.toDouble(), rawGy.toDouble()).toFloat()

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = (size.minDimension / 2f) - 4.dp.toPx()

            // Rotating radar sweep beam
            rotate(degrees = sweepAngle, pivot = center) {
                drawArc(
                    brush = Brush.sweepGradient(
                        0.0f to Color.Transparent,
                        0.75f to Color.Transparent,
                        1.0f to ElectricCyan.copy(alpha = 0.22f),
                        center = center,
                    ),
                    startAngle = 0f,
                    sweepAngle = 90f,
                    useCenter = true,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
                )
            }

            // Outer & inner G-force reference rings (1.5G, 1.0G, 0.5G)
            drawCircle(
                color = CockpitOutline,
                radius = radius,
                center = center,
                style = Stroke(width = 1.2.dp.toPx()),
            )
            drawCircle(
                color = ElectricCyan.copy(alpha = 0.28f),
                radius = radius * (2f / 3f),
                center = center,
                style = Stroke(width = 1.dp.toPx()),
            )
            drawCircle(
                color = CockpitOutline.copy(alpha = 0.6f),
                radius = radius * (1f / 3f),
                center = center,
                style = Stroke(width = 1.dp.toPx()),
            )

            // Cardinal + 45-degree radar crosshair axes
            drawLine(
                color = CockpitOutline.copy(alpha = 0.7f),
                start = Offset(center.x - radius, center.y),
                end = Offset(center.x + radius, center.y),
                strokeWidth = 1.dp.toPx(),
            )
            drawLine(
                color = CockpitOutline.copy(alpha = 0.7f),
                start = Offset(center.x, center.y - radius),
                end = Offset(center.x, center.y + radius),
                strokeWidth = 1.dp.toPx(),
            )
            val diag = radius * 0.7071f
            drawLine(
                color = CockpitOutline.copy(alpha = 0.35f),
                start = Offset(center.x - diag, center.y - diag),
                end = Offset(center.x + diag, center.y + diag),
                strokeWidth = 0.8.dp.toPx(),
            )
            drawLine(
                color = CockpitOutline.copy(alpha = 0.35f),
                start = Offset(center.x - diag, center.y + diag),
                end = Offset(center.x + diag, center.y - diag),
                strokeWidth = 0.8.dp.toPx(),
            )

            // Rolling G-force trail dots
            val trailSize = trail.size.coerceAtLeast(1)
            trail.forEachIndexed { idx, (tx, ty) ->
                val tNormX = (tx / maxG).coerceIn(-1f, 1f)
                val tNormY = (ty / maxG).coerceIn(-1f, 1f)
                val tPos = Offset(
                    x = center.x + tNormX * radius * 0.90f,
                    y = center.y - tNormY * radius * 0.90f,
                )
                val fade = (idx + 1).toFloat() / trailSize.toFloat()
                drawCircle(
                    color = ElectricCyan.copy(alpha = 0.35f * fade),
                    radius = 2.dp.toPx(),
                    center = tPos,
                )
            }

            // Current spring-animated G vector offset
            val normX = (animGx / maxG).coerceIn(-1f, 1f)
            val normY = (animGy / maxG).coerceIn(-1f, 1f)
            val dotPos = Offset(
                x = center.x + normX * radius * 0.90f,
                y = center.y - normY * radius * 0.90f,
            )

            val dotColor = when {
                totalG >= 1.0f -> AlertCrimson
                totalG >= 0.55f -> AmberTelemetry
                else -> SignalEmerald
            }

            // Vector tether line
            drawLine(
                color = dotColor.copy(alpha = 0.75f),
                start = center,
                end = dotPos,
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )

            // Glow halo & target dot
            drawCircle(
                color = dotColor.copy(alpha = 0.28f),
                radius = 10.dp.toPx(),
                center = dotPos,
            )
            drawCircle(
                color = dotColor,
                radius = 5.dp.toPx(),
                center = dotPos,
            )
        }
    }
}

/**
 * Custom Canvas rolling sparkline for vibration RMS time-series visualization.
 */
@Composable
fun RollingSparklineChart(
    samples: List<Float>,
    lineColor: Color,
    modifier: Modifier = Modifier,
    maxScale: Float = 6.0f,
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f) return@Canvas

        // Reference horizontal guide lines
        drawLine(
            color = CockpitOutline.copy(alpha = 0.45f),
            start = Offset(0f, height * 0.5f),
            end = Offset(width, height * 0.5f),
            strokeWidth = 1.dp.toPx(),
        )
        drawLine(
            color = CockpitOutline.copy(alpha = 0.65f),
            start = Offset(0f, height - 1.dp.toPx()),
            end = Offset(width, height - 1.dp.toPx()),
            strokeWidth = 1.dp.toPx(),
        )

        if (samples.isEmpty()) return@Canvas

        val effectiveMax = maxOf(maxScale, (samples.maxOrNull() ?: 1f) * 1.15f)
        val count = samples.size
        val stepX = if (count > 1) width / (count - 1).toFloat() else width

        val linePath = Path()
        val fillPath = Path()
        var lastPoint = Offset(0f, height)

        samples.forEachIndexed { idx, value ->
            val x = if (count > 1) idx * stepX else width
            val norm = (value / effectiveMax).coerceIn(0f, 1f)
            val y = height - (norm * (height - 6.dp.toPx())) - 3.dp.toPx()
            val pt = Offset(x, y)
            if (idx == 0) {
                linePath.moveTo(pt.x, pt.y)
                fillPath.moveTo(pt.x, height)
                fillPath.lineTo(pt.x, pt.y)
            } else {
                linePath.lineTo(pt.x, pt.y)
                fillPath.lineTo(pt.x, pt.y)
            }
            lastPoint = pt
        }
        fillPath.lineTo(lastPoint.x, height)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    lineColor.copy(alpha = 0.28f),
                    lineColor.copy(alpha = 0.02f),
                ),
            ),
        )
        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
        drawCircle(
            color = lineColor,
            radius = 3.5.dp.toPx(),
            center = lastPoint,
        )
    }
}

/**
 * Cockpit dashboard overview visualizing stream health, sampling rates, and bridge status across IMU, GNSS, OBD2, and UDP 5005.
 */
@Composable
fun StreamHealthSummaryCard(
    uiState: CockpitUiState,
    modifier: Modifier = Modifier,
    onViewLastTripSummary: (() -> Unit)? = null,
) {
    val streamer = uiState.streamerStatus
    val telemetry = uiState.telemetry
    val gps = telemetry.sensors.gps
    val obd2 = telemetry.obd2
    val health = telemetry.deviceHealth

    val animatedHz by animateFloatAsState(
        targetValue = if (streamer.isStreaming) streamer.measuredHz else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "summary_hz",
    )

    val activeChannelsCount = listOf(
        uiState.isCollectingSensors && telemetry.sensors.availability.accel,
        gps.hasFix,
        obd2.connected,
        streamer.isStreaming,
    ).count { it }

    HudCard(
        title = "STREAM HEALTH",
        icon = Icons.Default.MonitorHeart,
        iconContentDescription = "Active stream health and sampling overview",
        accentColor = ElectricCyan,
        modifier = modifier.testTag("stream_health_summary_card"),
        trailingBadge = {
            LiveBeaconStatusPill(
                label = if (streamer.isStreaming) {
                    "UDP ${uiState.targetPortInput} LIVE"
                } else {
                    "UDP ${uiState.targetPortInput} IDLE"
                },
                color = if (streamer.isStreaming) SignalEmerald else TextMuted,
                active = streamer.isStreaming,
            )
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StreamChannelTile(
                title = "IMU",
                status = if (uiState.isCollectingSensors) "FASTEST" else "PAUSED",
                detail = String.format(Locale.US, "%d/10 CH", countAvailableSensors(telemetry.sensors.availability)),
                color = if (uiState.isCollectingSensors) SignalEmerald else TextMuted,
                active = uiState.isCollectingSensors,
                modifier = Modifier.weight(1f),
            )
            StreamChannelTile(
                title = "GNSS",
                status = when {
                    !uiState.hardware.hasLocationPermission -> "NO PERM"
                    gps.hasFix -> "2HZ FIX"
                    else -> "SEARCH"
                },
                detail = if (gps.hasFix) String.format(Locale.US, "±%.0fm", gps.accuracyM) else "500 ms",
                color = when {
                    !uiState.hardware.hasLocationPermission -> AlertCrimson
                    gps.hasFix -> SignalEmerald
                    else -> AmberTelemetry
                },
                active = gps.hasFix,
                modifier = Modifier.weight(1f),
            )
            StreamChannelTile(
                title = "OBD2",
                status = when (obd2.status) {
                    Obd2ConnectionStatus.POLLING, Obd2ConnectionStatus.CONNECTED -> "ECU OK"
                    Obd2ConnectionStatus.CONNECTING, Obd2ConnectionStatus.RECONNECTING -> "SYNC"
                    Obd2ConnectionStatus.ERROR -> "FAULT"
                    Obd2ConnectionStatus.DISCONNECTED -> "IDLE"
                },
                detail = "${uiState.settings.obdPollDelayMs}ms",
                color = when (obd2.status) {
                    Obd2ConnectionStatus.POLLING, Obd2ConnectionStatus.CONNECTED -> SignalEmerald
                    Obd2ConnectionStatus.CONNECTING, Obd2ConnectionStatus.RECONNECTING -> AmberTelemetry
                    Obd2ConnectionStatus.ERROR -> AlertCrimson
                    Obd2ConnectionStatus.DISCONNECTED -> TextMuted
                },
                active = obd2.connected,
                modifier = Modifier.weight(1f),
            )
            StreamChannelTile(
                title = "UDP TX",
                status = String.format(Locale.US, "%.1f Hz", animatedHz),
                detail = "${streamer.packetsSent} pkts",
                color = if (streamer.isStreaming) ElectricCyan else TextMuted,
                active = streamer.isStreaming,
                modifier = Modifier.weight(1f),
            )
        }

        val targetRateHz = (if (streamer.isStreaming) {
            streamer.effectiveHz
        } else {
            uiState.settings.streamRateHz
        }).coerceAtLeast(1)

        val primarySummaryText = "${targetRateHz}HZ · ${health.batteryPct}% BAT · ${streamer.thermalStatus}"
        var hasPrimaryTextOverflow by remember { mutableStateOf(false) }

        val statusIndicators = remember(
            primarySummaryText,
            hasPrimaryTextOverflow,
            health.batteryTempC,
            health.charging,
            health.wifiRssiDbm,
            telemetry.sensors.availability.barometer,
            telemetry.sensors.barometer.pressureHpa,
            telemetry.sensors.audio.active,
            telemetry.sensors.audio.rmsDbfs,
            telemetry.roadIntelligence.surfaceClass,
            uiState.recordingStatus.isRecording,
            uiState.recordingStatus.framesRecorded,
        ) {
            buildList {
                if (hasPrimaryTextOverflow) {
                    add(
                        StatusBarIndicator(
                            id = "primary_full",
                            label = primarySummaryText,
                            color = ElectricCyan,
                            testTag = "status_indicator_primary_full",
                        ),
                    )
                }
                add(
                    StatusBarIndicator(
                        id = "bat_temp",
                        label = String.format(
                            Locale.US,
                            "TEMP %.0f°C%s",
                            health.batteryTempC,
                            if (health.charging) " ⚡" else "",
                        ),
                        color = if (health.batteryTempC >= 42f) AmberTelemetry else TextSecondary,
                        testTag = "status_indicator_bat_temp",
                    ),
                )
                add(
                    StatusBarIndicator(
                        id = "wifi",
                        label = if (health.wifiRssiDbm != 0) "WIFI ${health.wifiRssiDbm}dBm" else "WIFI STANDBY",
                        color = if (health.wifiRssiDbm in -72..-1) SignalEmerald else TextSecondary,
                        testTag = "status_indicator_wifi",
                    ),
                )
                add(
                    StatusBarIndicator(
                        id = "baro",
                        label = if (telemetry.sensors.availability.barometer) {
                            String.format(Locale.US, "BARO %.0fhPa", telemetry.sensors.barometer.pressureHpa)
                        } else {
                            "BARO N/A"
                        },
                        color = if (telemetry.sensors.availability.barometer) ElectricCyan else TextMuted,
                        testTag = "status_indicator_baro",
                    ),
                )
                add(
                    StatusBarIndicator(
                        id = "noise",
                        label = if (telemetry.sensors.audio.active) {
                            String.format(Locale.US, "MIC %.0fdBFS", telemetry.sensors.audio.rmsDbfs)
                        } else {
                            "MIC MUTED"
                        },
                        color = if (telemetry.sensors.audio.active) SignalEmerald else TextMuted,
                        testTag = "status_indicator_noise",
                    ),
                )
                add(
                    StatusBarIndicator(
                        id = "road",
                        label = "ROAD ${telemetry.roadIntelligence.surfaceClass}",
                        color = when (telemetry.roadIntelligence.surfaceClass) {
                            "ROUGH" -> AlertCrimson
                            "MODERATE" -> AmberTelemetry
                            else -> SignalEmerald
                        },
                        testTag = "status_indicator_road",
                    ),
                )
                add(
                    StatusBarIndicator(
                        id = "rec",
                        label = if (uiState.recordingStatus.isRecording) {
                            "LOG ${uiState.recordingStatus.framesRecorded}"
                        } else {
                            "LOG IDLE"
                        },
                        color = if (uiState.recordingStatus.isRecording) AlertCrimson else TextMuted,
                        testTag = "status_indicator_rec",
                    ),
                )
            }
        }

        val inlineScrollState = rememberScrollState()
        val dragCoroutineScope = rememberCoroutineScope()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .clipToBounds()
                .draggable(
                    orientation = Orientation.Horizontal,
                    enabled = inlineScrollState.maxValue > 0,
                    state = rememberDraggableState { delta ->
                        dragCoroutineScope.launch {
                            inlineScrollState.scrollBy(-delta)
                        }
                    },
                )
                .testTag("stream_health_status_bar_container"),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds()
                    .testTag("stream_health_status_row"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = primarySummaryText,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { result ->
                        hasPrimaryTextOverflow = result.hasVisualOverflow
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("stream_health_throughput_label"),
                )
                Text(
                    text = "$activeChannelsCount/4 ACTIVE",
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricCyan,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.testTag("stream_health_active_channels_label"),
                )
            }
            LinearProgressIndicator(
                progress = { (animatedHz / targetRateHz.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (streamer.isStreaming) SignalEmerald else TextMuted,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round,
            )

            StatusIndicatorsOverflowBar(
                indicators = statusIndicators,
                inlineScrollState = inlineScrollState,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        val lastSummary = uiState.lastTripSummary
        if (lastSummary != null && !streamer.isStreaming && onViewLastTripSummary != null) {
            OutlinedButton(
                onClick = onViewLastTripSummary,
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("view_last_trip_summary_button"),
                border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.6f)),
            ) {
                Icon(
                    imageVector = Icons.Default.Flag,
                    contentDescription = "Open last trip summary",
                    tint = ElectricCyan,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = String.format(
                        Locale.US,
                        "Last Trip: %.1f km/h avg · Δ%.1f m · %.0f dB·s",
                        lastSummary.averageSpeedKmh,
                        lastSummary.maxAltitudeChangeM,
                        lastSummary.totalRoadNoiseExposureDbSec,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricCyan,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun StreamChannelTile(
    title: String,
    status: String,
    detail: String,
    color: Color,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = if (active) 0.12f else 0.06f),
        border = BorderStroke(1.dp, color.copy(alpha = if (active) 0.45f else 0.25f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
            AnimatedNumericReadout(
                text = status,
                style = MaterialTheme.typography.labelMedium,
                color = color,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                maxLines = 1,
            )
        }
    }
}

private data class StatusBarIndicator(
    val id: String,
    val label: String,
    val color: Color,
    val testTag: String,
)

@Composable
private fun StatusIndicatorsOverflowBar(
    indicators: List<StatusBarIndicator>,
    inlineScrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    var isDrawerExpanded by rememberSaveable { mutableStateOf(false) }
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelStyle = MaterialTheme.typography.labelSmall

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds(),
    ) {
        val availableWidthPx = constraints.maxWidth
        var lastMeasuredWidthPx by remember { mutableIntStateOf(0) }

        val (inlineIndicators, overflowIndicators) = remember<Pair<List<StatusBarIndicator>, List<StatusBarIndicator>>>(
            indicators,
            availableWidthPx,
            labelStyle,
            density,
        ) {
            with(density) {
                val spacingPx = 6.dp.roundToPx()
                val chipChromePx = 16.dp.roundToPx()
                val fontSizePx = labelStyle.fontSize.toPx()
                val toggleReservePx = maxOf(
                    textMeasurer.measure("+9", style = labelStyle).size.width,
                    (3 * 0.6f * fontSizePx).roundToInt(),
                ) + 34.dp.roundToPx()

                val measuredWidths = indicators.map { indicator ->
                    val textWidth = maxOf(
                        textMeasurer.measure(indicator.label, style = labelStyle).size.width,
                        (indicator.label.length * 0.6f * fontSizePx).roundToInt(),
                    )
                    textWidth + chipChromePx
                }

                val totalAllPx = measuredWidths.sum() +
                    ((indicators.size - 1).coerceAtLeast(0) * spacingPx)

                if (totalAllPx <= availableWidthPx) {
                    indicators to emptyList()
                } else {
                    val inlineBudgetPx = (availableWidthPx - toggleReservePx - spacingPx).coerceAtLeast(0)
                    val inline = mutableListOf<StatusBarIndicator>()
                    val overflow = mutableListOf<StatusBarIndicator>()
                    var usedPx = 0
                    indicators.forEachIndexed { index, indicator ->
                        val chipWidth = measuredWidths[index]
                        val needed = if (inline.isEmpty()) chipWidth else spacingPx + chipWidth
                        if (overflow.isEmpty() && usedPx + needed <= inlineBudgetPx) {
                            inline += indicator
                            usedPx += needed
                        } else {
                            overflow += indicator
                        }
                    }
                    inline.toList() to overflow.toList()
                }
            }
        }

        LaunchedEffect(availableWidthPx, overflowIndicators.isEmpty()) {
            if (overflowIndicators.isEmpty()) {
                isDrawerExpanded = false
            } else if (lastMeasuredWidthPx != 0 && lastMeasuredWidthPx != availableWidthPx) {
                isDrawerExpanded = false
            }
            lastMeasuredWidthPx = availableWidthPx
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clipToBounds(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds()
                    .testTag("status_bar_inline_row"),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(inlineScrollState)
                        .clipToBounds()
                        .testTag("status_bar_inline_scroller"),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val inlineCount = inlineIndicators.size
                    indicators.forEachIndexed { index, indicator ->
                        val pillTag = if (index < inlineCount) {
                            indicator.testTag
                        } else {
                            "scroll_${indicator.testTag}"
                        }
                        StatusIndicatorPill(
                            indicator = indicator,
                            testTagOverride = pillTag,
                        )
                    }
                }
                if (overflowIndicators.isNotEmpty()) {
                    Surface(
                        onClick = { isDrawerExpanded = !isDrawerExpanded },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("status_bar_overflow_toggle"),
                        shape = RoundedCornerShape(8.dp),
                        color = if (isDrawerExpanded) {
                            ElectricCyan.copy(alpha = 0.16f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (isDrawerExpanded) ElectricCyan.copy(alpha = 0.6f) else CockpitOutline,
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (isDrawerExpanded) "LESS" else "+${overflowIndicators.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan,
                                maxLines = 1,
                                softWrap = false,
                            )
                            Icon(
                                imageVector = if (isDrawerExpanded) {
                                    Icons.Default.KeyboardArrowUp
                                } else {
                                    Icons.Default.KeyboardArrowDown
                                },
                                contentDescription = if (isDrawerExpanded) {
                                    "Collapse hidden status indicators"
                                } else {
                                    "Expand ${overflowIndicators.size} hidden status indicators"
                                },
                                tint = ElectricCyan,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = isDrawerExpanded && overflowIndicators.isNotEmpty(),
                enter = expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                ) + fadeIn(tween(180)),
                exit = shrinkVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                ) + fadeOut(tween(140)),
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clipToBounds()
                        .testTag("status_bar_overflow_drawer"),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, CockpitOutline),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "HIDDEN STATUS (${overflowIndicators.size})",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "SCROLL →",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan.copy(alpha = 0.85f),
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .clipToBounds()
                                .testTag("status_bar_overflow_scroller"),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            overflowIndicators.forEach { indicator ->
                                StatusIndicatorPill(indicator = indicator)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusIndicatorPill(
    indicator: StatusBarIndicator,
    modifier: Modifier = Modifier,
    testTagOverride: String = indicator.testTag,
) {
    Surface(
        modifier = modifier.testTag(testTagOverride),
        shape = RoundedCornerShape(6.dp),
        color = indicator.color.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, indicator.color.copy(alpha = 0.35f)),
    ) {
        Text(
            text = indicator.label,
            style = MaterialTheme.typography.labelSmall,
            color = indicator.color,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}

private fun countAvailableSensors(avail: SensorAvailabilityState): Int {
    return listOf(
        avail.accel,
        avail.gyro,
        avail.mag,
        avail.gravity,
        avail.linearAccel,
        avail.rotationVector,
        avail.barometer,
        avail.light,
        avail.proximity,
        avail.audio,
    ).count { it }
}
