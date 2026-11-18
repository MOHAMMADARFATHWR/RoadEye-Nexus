package com.example

import java.io.Closeable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineDispatcher
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class UdpClientStats(
    val targetIp: String = UdpTelemetryClient.DEFAULT_TARGET_IP,
    val targetPort: Int = UdpTelemetryClient.DEFAULT_PORT,
    val isActive: Boolean = false,
    val packetsSent: Long = 0L,
    val lastPacketBytes: Int = 0,
    val lastError: String? = null,
)

/**
 * Manages a dedicated CoroutineScope to asynchronously transmit [SensorTelemetryData]
 * JSON payloads over UDP via [DatagramSocket] to a configurable target IP and port (default 5005).
 */
class UdpTelemetryClient(
    initialTargetIp: String = DEFAULT_TARGET_IP,
    initialTargetPort: Int = DEFAULT_PORT,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : Closeable {

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val socketMutex = Mutex()
    private val sequenceCounter = AtomicLong(0L)
    private val packetsSentCounter = AtomicLong(0L)

    @Volatile
    private var targetIp: String = initialTargetIp.trim().ifEmpty { DEFAULT_TARGET_IP }

    @Volatile
    private var targetPort: Int = initialTargetPort.coerceIn(1, 65535)

    @Volatile
    private var cachedAddressIp: String? = null

    @Volatile
    private var cachedAddress: InetAddress? = null

    @Volatile
    private var socket: DatagramSocket? = null

    @Volatile
    private var periodicJob: Job? = null

    private val _stats = MutableStateFlow(
        UdpClientStats(
            targetIp = targetIp,
            targetPort = targetPort,
        ),
    )
    val stats: StateFlow<UdpClientStats> = _stats.asStateFlow()

    fun setTarget(ip: String, port: Int = DEFAULT_PORT) {
        val cleanIp = ip.trim().ifEmpty { DEFAULT_TARGET_IP }
        val safePort = port.coerceIn(1, 65535)
        targetIp = cleanIp
        targetPort = safePort
        if (cachedAddressIp != cleanIp) {
            cachedAddress = null
            cachedAddressIp = null
        }
        _stats.value = _stats.value.copy(
            targetIp = cleanIp,
            targetPort = safePort,
        )
    }

    /**
     * Enqueues asynchronous UDP transmission of [data] on the managed IO [CoroutineScope].
     */
    fun sendAsync(data: SensorTelemetryData): Job {
        return scope.launch {
            sendInternal(data)
        }
    }

    /**
     * Starts an asynchronous periodic UDP stream on the managed [CoroutineScope].
     */
    fun startPeriodicStreaming(
        rateHz: Int = 20,
        dataProvider: () -> SensorTelemetryData,
    ) {
        stopPeriodicStreaming()
        val safeHz = rateHz.coerceIn(1, 100)
        val periodNs = 1_000_000_000L / safeHz.toLong()

        _stats.value = _stats.value.copy(isActive = true, lastError = null)
        periodicJob = scope.launch {
            var nextTickNs = System.nanoTime()
            try {
                while (isActive) {
                    val sample = dataProvider()
                    val stamped = if (sample.seq == 0L) {
                        sample.copy(seq = sequenceCounter.incrementAndGet())
                    } else {
                        sample
                    }
                    sendInternal(stamped)

                    nextTickNs += periodNs
                    val sleepMs = (nextTickNs - System.nanoTime()) / 1_000_000L
                    if (sleepMs > 0L) {
                        delay(sleepMs)
                    } else if (sleepMs < -MAX_DRIFT_RESET_MS) {
                        nextTickNs = System.nanoTime()
                    }
                }
            } finally {
                if (periodicJob === coroutineContext[Job]) {
                    _stats.value = _stats.value.copy(isActive = false)
                }
            }
        }
    }

    fun stopPeriodicStreaming() {
        periodicJob?.cancel()
        periodicJob = null
        _stats.value = _stats.value.copy(isActive = false)
    }

    private suspend fun sendInternal(data: SensorTelemetryData) {
        val currentIp = targetIp
        val currentPort = targetPort

        try {
            val payloadBytes = SensorTelemetryJsonSerializer.toUdpPayloadBytes(data)
            socketMutex.withLock {
                val activeSocket = socket?.takeIf { !it.isClosed }
                    ?: DatagramSocket().also { socket = it }
                val address = if (cachedAddressIp == currentIp && cachedAddress != null) {
                    cachedAddress!!
                } else {
                    InetAddress.getByName(currentIp).also {
                        cachedAddress = it
                        cachedAddressIp = currentIp
                    }
                }
                val packet = DatagramPacket(
                    payloadBytes,
                    payloadBytes.size,
                    address,
                    currentPort,
                )
                activeSocket.send(packet)
            }
            val sent = packetsSentCounter.incrementAndGet()
            _stats.value = _stats.value.copy(
                targetIp = currentIp,
                targetPort = currentPort,
                packetsSent = sent,
                lastPacketBytes = payloadBytes.size,
                lastError = null,
            )
        } catch (e: Exception) {
            _stats.value = _stats.value.copy(
                targetIp = currentIp,
                targetPort = currentPort,
                lastError = e.message ?: "UDP send error",
            )
        }
    }

    override fun close() {
        stopPeriodicStreaming()
        try {
            socket?.close()
        } catch (_: Exception) {
            // Safe to ignore on teardown.
        }
        socket = null
        scope.cancel()
    }

    companion object {
        const val DEFAULT_TARGET_IP = UdpTelemetryStreamer.DEFAULT_IP
        const val DEFAULT_PORT = 5005
        private const val MAX_DRIFT_RESET_MS = 200L
    }
}
