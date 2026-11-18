package com.example

import android.content.Context
import android.os.Build
import android.os.PowerManager
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PacketInspectionEntry(
    val seq: Long,
    val timestampSec: Double,
    val byteSize: Int,
    val crc32: String,
    val rawJson: String,
)

data class StreamerStatus(
    val isStreaming: Boolean = false,
    val packetsSent: Long = 0L,
    val sequenceNumber: Long = 0L,
    val lastPacketBytes: Int = 0,
    val measuredHz: Float = 0f,
    val configuredHz: Int = 20,
    val effectiveHz: Int = 20,
    val thermalStatus: String = "NOMINAL",
    val targetEndpoint: String = "",
    val lastError: String? = null,
    val recentPackets: List<PacketInspectionEntry> = emptyList(),
)

class UdpTelemetryStreamer(
    context: Context,
    private val stateHolder: TelemetryStateHolder,
    private val sessionRecorder: SessionRecorder,
    private val onSnapshotSampled: ((TelemetrySnapshot, Long) -> Unit)? = null,
) {
    private val appContext = context.applicationContext
    private val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _status = MutableStateFlow(StreamerStatus())
    val status: StateFlow<StreamerStatus> = _status.asStateFlow()

    private val sequenceCounter = AtomicLong(0L)

    @Volatile
    private var streamJob: Job? = null

    @Volatile
    private var activeSocket: DatagramSocket? = null

    @Volatile
    private var currentThermalLabel: String = "NOMINAL"

    @Volatile
    private var thermalSevere: Boolean = false

    private val thermalListener: Any? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        PowerManager.OnThermalStatusChangedListener { status ->
            when {
                status >= PowerManager.THERMAL_STATUS_SEVERE -> {
                    thermalSevere = true
                    currentThermalLabel = "THROTTLED"
                }
                status == PowerManager.THERMAL_STATUS_MODERATE -> {
                    thermalSevere = false
                    currentThermalLabel = "WARM"
                }
                else -> {
                    thermalSevere = false
                    currentThermalLabel = "NOMINAL"
                }
            }
        }
    } else {
        null
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && thermalListener != null) {
            try {
                powerManager?.addThermalStatusListener(
                    thermalListener as PowerManager.OnThermalStatusChangedListener,
                )
            } catch (_: Exception) {
                // Ignore if thermal service is unavailable in container/test environments.
            }
        }
    }

    fun start(
        targetIp: String,
        targetPort: Int,
        deviceId: String,
        rateHz: Int,
    ) {
        stop()

        val sanitizedIp = targetIp.trim()
        val safeRateHz = rateHz.coerceIn(10, 50)
        val endpointLabel = "$sanitizedIp:$targetPort"

        _status.value = StreamerStatus(
            isStreaming = true,
            packetsSent = 0L,
            sequenceNumber = sequenceCounter.get(),
            lastPacketBytes = 0,
            measuredHz = 0f,
            configuredHz = safeRateHz,
            effectiveHz = safeRateHz,
            thermalStatus = currentThermalLabel,
            targetEndpoint = endpointLabel,
            lastError = null,
            recentPackets = emptyList(),
        )

        streamJob = scope.launch {
            var socket: DatagramSocket? = null
            val ringBuffer = ArrayDeque<PacketInspectionEntry>(MAX_INSPECTOR_PACKETS)
            try {
                val destination = InetAddress.getByName(sanitizedIp)
                socket = DatagramSocket()
                activeSocket = socket

                var sentCount = 0L
                var hzWindowStartNs = System.nanoTime()
                var hzWindowCount = 0
                var currentHz = 0f
                var nextTickNs = System.nanoTime()

                while (isActive) {
                    val effectiveHz = if (thermalSevere) {
                        (safeRateHz / 2).coerceAtLeast(5)
                    } else {
                        safeRateHz
                    }
                    val periodNs = 1_000_000_000L / effectiveHz.toLong()

                    val seq = sequenceCounter.incrementAndGet()
                    val timestampSec = System.currentTimeMillis() / 1000.0
                    stateHolder.setMetadata(
                        deviceId = deviceId,
                        streamHz = effectiveHz,
                        thermalStatus = currentThermalLabel,
                    )

                    val snapshot = stateHolder.snapshot(timestampSec, seq)
                    onSnapshotSampled?.invoke(snapshot, (timestampSec * 1000.0).toLong())
                    val jsonObj = snapshot.toJsonObject()
                    val crc32 = jsonObj.optString("crc32", "")
                    val jsonString = jsonObj.toString()
                    val payloadBytes = jsonString.toByteArray(Charsets.UTF_8)

                    sessionRecorder.appendLine(jsonString)

                    var sendError: String? = null
                    try {
                        val packet = DatagramPacket(
                            payloadBytes,
                            payloadBytes.size,
                            destination,
                            targetPort,
                        )
                        socket.send(packet)
                        sentCount++
                        hzWindowCount++
                    } catch (e: Exception) {
                        sendError = e.message ?: "UDP send failed"
                    }

                    if (ringBuffer.size >= MAX_INSPECTOR_PACKETS) {
                        ringBuffer.removeFirst()
                    }
                    ringBuffer.addLast(
                        PacketInspectionEntry(
                            seq = seq,
                            timestampSec = timestampSec,
                            byteSize = payloadBytes.size,
                            crc32 = crc32,
                            rawJson = jsonString,
                        ),
                    )

                    val nowNs = System.nanoTime()
                    val elapsedWindowNs = nowNs - hzWindowStartNs
                    if (elapsedWindowNs >= 500_000_000L) {
                        currentHz = (hzWindowCount * 1_000_000_000f) / elapsedWindowNs.toFloat()
                        hzWindowStartNs = nowNs
                        hzWindowCount = 0
                    }

                    _status.value = StreamerStatus(
                        isStreaming = true,
                        packetsSent = sentCount,
                        sequenceNumber = seq,
                        lastPacketBytes = payloadBytes.size,
                        measuredHz = currentHz,
                        configuredHz = safeRateHz,
                        effectiveHz = effectiveHz,
                        thermalStatus = currentThermalLabel,
                        targetEndpoint = endpointLabel,
                        lastError = sendError,
                        recentPackets = ringBuffer.toList().asReversed(),
                    )

                    nextTickNs += periodNs
                    val sleepMs = (nextTickNs - System.nanoTime()) / 1_000_000L
                    if (sleepMs > 0L) {
                        delay(sleepMs)
                    } else if (sleepMs < -MAX_DRIFT_RESET_MS) {
                        nextTickNs = System.nanoTime()
                    }
                }
            } catch (e: Exception) {
                _status.value = _status.value.copy(
                    isStreaming = false,
                    lastError = e.message ?: "Unable to initialize UDP socket",
                )
            } finally {
                try {
                    socket?.close()
                } catch (_: Exception) {
                    // Ignore close error on teardown.
                }
                if (activeSocket === socket) {
                    activeSocket = null
                }
                if (streamJob === coroutineContext[Job]) {
                    _status.value = _status.value.copy(
                        isStreaming = false,
                        measuredHz = 0f,
                    )
                }
            }
        }
    }

    fun stop() {
        streamJob?.cancel()
        streamJob = null
        try {
            activeSocket?.close()
        } catch (_: Exception) {
            // Safe to ignore on teardown.
        }
        activeSocket = null
        _status.value = _status.value.copy(
            isStreaming = false,
            measuredHz = 0f,
        )
    }

    fun shutdown() {
        stop()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && thermalListener != null) {
            try {
                powerManager?.removeThermalStatusListener(
                    thermalListener as PowerManager.OnThermalStatusChangedListener,
                )
            } catch (_: Exception) {
                // Ignore teardown error.
            }
        }
        scope.cancel()
    }

    companion object {
        const val DEFAULT_IP = "192.168.43.100"
        const val DEFAULT_PORT = 5005
        private const val MAX_DRIFT_RESET_MS = 200L
        private const val MAX_INSPECTOR_PACKETS = 5
    }
}
