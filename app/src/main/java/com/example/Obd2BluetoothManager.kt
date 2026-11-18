package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class PairedBluetoothDevice(
    val name: String,
    val address: String,
)

sealed interface PidParseResult {
    data class VehicleSpeed(val speedKmh: Int) : PidParseResult
    data class EngineRpm(val rpm: Int) : PidParseResult
    data class ThrottlePosition(val percent: Float) : PidParseResult
    data class CoolantTemp(val tempC: Int) : PidParseResult
    data class EngineLoad(val percent: Float) : PidParseResult
    data class IntakeAirTemp(val tempC: Int) : PidParseResult
    data class MafAirFlow(val gramsPerSec: Float) : PidParseResult
    data class TimingAdvance(val degrees: Float) : PidParseResult
    data class FuelLevel(val percent: Float) : PidParseResult
    data class Error(val message: String) : PidParseResult
    data object Unrecognized : PidParseResult
}

object Obd2PidParser {
    private val errorTokens = listOf(
        "UNABLE TO CONNECT",
        "UNABLETOCONNECT",
        "NO DATA",
        "NODATA",
        "CAN ERROR",
        "CANERROR",
        "BUS INIT: ...ERROR",
        "ERROR",
        "STOPPED",
    )

    fun normalizeResponse(raw: String): String {
        return raw
            .replace("\r", "")
            .replace("\n", "")
            .replace(">", "")
            .replace(" ", "")
            .trim()
            .uppercase()
    }

    fun isErrorResponse(raw: String): String? {
        val upper = raw.uppercase()
        val compact = normalizeResponse(raw)
        return when {
            upper.contains("UNABLE TO CONNECT") || compact.contains("UNABLETOCONNECT") ->
                "UNABLE TO CONNECT"
            upper.contains("NO DATA") || compact.contains("NODATA") ->
                "NO DATA"
            upper.contains("CAN ERROR") || compact.contains("CANERROR") ->
                "CAN ERROR"
            compact == "?" || errorTokens.any { compact.contains(it.replace(" ", "")) } ->
                raw.trim().ifEmpty { "OBD2 ERROR" }
            else -> null
        }
    }

    fun parsePidResponse(pidCommand: String, rawResponse: String): PidParseResult {
        isErrorResponse(rawResponse)?.let { err ->
            return PidParseResult.Error(err)
        }

        val clean = normalizeResponse(rawResponse)
        return when (pidCommand.uppercase()) {
            "010D" -> parseSingleByte(clean, "410D") { a ->
                PidParseResult.VehicleSpeed(a)
            }

            "010C" -> parseTwoBytes(clean, "410C") { a, b ->
                PidParseResult.EngineRpm(((256 * a) + b) / 4)
            }

            "0111" -> parseSingleByte(clean, "4111") { a ->
                PidParseResult.ThrottlePosition((100f * a) / 255f)
            }

            "0105" -> parseSingleByte(clean, "4105") { a ->
                PidParseResult.CoolantTemp(a - 40)
            }

            "0104" -> parseSingleByte(clean, "4104") { a ->
                PidParseResult.EngineLoad((100f * a) / 255f)
            }

            "010F" -> parseSingleByte(clean, "410F") { a ->
                PidParseResult.IntakeAirTemp(a - 40)
            }

            "0110" -> parseTwoBytes(clean, "4110") { a, b ->
                PidParseResult.MafAirFlow(((256 * a) + b) / 100f)
            }

            "010E" -> parseSingleByte(clean, "410E") { a ->
                PidParseResult.TimingAdvance((a / 2f) - 64f)
            }

            "012F" -> parseSingleByte(clean, "412F") { a ->
                PidParseResult.FuelLevel((100f * a) / 255f)
            }

            else -> PidParseResult.Unrecognized
        }
    }

    fun parseDtcResponse(rawResponse: String): List<String> {
        if (isErrorResponse(rawResponse) != null) return emptyList()
        val clean = normalizeResponse(rawResponse)
        val idx = clean.indexOf("43")
        if (idx < 0) return emptyList()

        val payload = clean.substring(idx + 2)
        val codes = mutableListOf<String>()
        var offset = 0
        while (offset + 4 <= payload.length) {
            val hexPair = payload.substring(offset, offset + 4)
            offset += 4
            if (hexPair == "0000") continue
            val firstNibble = hexPair[0].digitToIntOrNull(16) ?: continue
            val prefix = when (firstNibble ushr 2) {
                0 -> 'P'
                1 -> 'C'
                2 -> 'B'
                else -> 'U'
            }
            val secondDigit = firstNibble and 0x03
            codes.add("$prefix$secondDigit${hexPair.substring(1)}")
        }
        return codes.distinct()
    }

    private inline fun parseSingleByte(
        clean: String,
        header: String,
        mapper: (Int) -> PidParseResult,
    ): PidParseResult {
        val idx = clean.indexOf(header)
        if (idx < 0 || clean.length < idx + 6) return PidParseResult.Unrecognized
        val a = clean.substring(idx + 4, idx + 6).toIntOrNull(16)
            ?: return PidParseResult.Unrecognized
        return mapper(a)
    }

    private inline fun parseTwoBytes(
        clean: String,
        header: String,
        mapper: (Int, Int) -> PidParseResult,
    ): PidParseResult {
        val idx = clean.indexOf(header)
        if (idx < 0 || clean.length < idx + 8) return PidParseResult.Unrecognized
        val a = clean.substring(idx + 4, idx + 6).toIntOrNull(16)
            ?: return PidParseResult.Unrecognized
        val b = clean.substring(idx + 6, idx + 8).toIntOrNull(16)
            ?: return PidParseResult.Unrecognized
        return mapper(a, b)
    }
}

class Obd2BluetoothManager(
    context: Context,
    private val stateHolder: TelemetryStateHolder,
) {
    private val appContext = context.applicationContext
    private val bluetoothAdapter: BluetoothAdapter? =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var connectionJob: Job? = null

    @Volatile
    private var activeSocket: BluetoothSocket? = null

    @Volatile
    private var autoReconnectEnabled: Boolean = true

    @Volatile
    private var pollDelayMs: Long = DEFAULT_INTER_PID_DELAY_MS

    @Volatile
    private var dtcScanRequested: Boolean = false

    fun configure(autoReconnect: Boolean, pollDelayMs: Long) {
        this.autoReconnectEnabled = autoReconnect
        this.pollDelayMs = pollDelayMs.coerceIn(0L, 250L)
    }

    fun requestDtcScan() {
        dtcScanRequested = true
    }

    fun isBluetoothSupported(): Boolean = bluetoothAdapter != null

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.BLUETOOTH_CONNECT,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.BLUETOOTH,
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.BLUETOOTH_SCAN,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.BLUETOOTH_ADMIN,
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<PairedBluetoothDevice> {
        val adapter = bluetoothAdapter ?: return emptyList()
        if (!hasConnectPermission()) return emptyList()
        return try {
            adapter.bondedDevices
                ?.map { device ->
                    PairedBluetoothDevice(
                        name = device.name?.takeIf { it.isNotBlank() } ?: "Unknown Device",
                        address = device.address,
                    )
                }
                ?.sortedBy { it.name.lowercase() }
                ?: emptyList()
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    fun connectAndPoll(deviceAddress: String, deviceName: String) {
        disconnect(clearSelectedDevice = false)

        val adapter = bluetoothAdapter
        if (adapter == null) {
            updateErrorState(deviceName, deviceAddress, "Bluetooth hardware unavailable")
            return
        }
        if (!hasConnectPermission()) {
            updateErrorState(deviceName, deviceAddress, "Missing Bluetooth permission")
            return
        }

        stateHolder.updateObd2 {
            it.copy(
                connected = false,
                status = Obd2ConnectionStatus.CONNECTING,
                deviceName = deviceName,
                deviceAddress = deviceAddress,
                lastError = null,
            )
        }

        connectionJob = scope.launch {
            var backoffMs = INITIAL_BACKOFF_MS
            val primaryPids = listOf("010D", "010C", "0111", "0104")
            val secondaryPids = listOf("0105", "010F", "0110", "010E", "012F")

            while (isActive) {
                var socket: BluetoothSocket? = null
                try {
                    if (hasScanPermission() && adapter.isDiscovering) {
                        adapter.cancelDiscovery()
                    }

                    val device: BluetoothDevice = adapter.getRemoteDevice(deviceAddress)
                    socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                    activeSocket = socket
                    socket.connect()

                    val input = socket.inputStream
                    val output = socket.outputStream
                    val detectedProtocol = initializeElm327(input, output)

                    stateHolder.updateObd2 {
                        it.copy(
                            connected = true,
                            status = Obd2ConnectionStatus.CONNECTED,
                            protocol = detectedProtocol,
                            lastError = null,
                        )
                    }
                    backoffMs = INITIAL_BACKOFF_MS

                    var cycleCounter = 0
                    while (isActive && socket.isConnected) {
                        if (dtcScanRequested) {
                            dtcScanRequested = false
                            val rawDtc = sendAtOrPidCommand("03", input, output, DTC_TIMEOUT_MS)
                            val dtcList = Obd2PidParser.parseDtcResponse(rawDtc)
                            stateHolder.updateObd2 { it.copy(dtcs = dtcList) }
                        }

                        var fatalBusError = false
                        for (pid in primaryPids) {
                            if (!isActive) break
                            val raw = sendAtOrPidCommand(pid, input, output, PID_TIMEOUT_MS)
                            val status = handlePidResult(pid, raw)
                            if (status == PidLoopSignal.SUCCESS) {
                                backoffMs = INITIAL_BACKOFF_MS
                            } else if (status == PidLoopSignal.RECONNECT_NEEDED) {
                                fatalBusError = true
                                break
                            }
                            if (pollDelayMs > 0L) delay(pollDelayMs)
                        }
                        if (fatalBusError) break

                        if (cycleCounter % SECONDARY_PID_MODULO == 0) {
                            for (pid in secondaryPids) {
                                if (!isActive) break
                                val raw = sendAtOrPidCommand(pid, input, output, PID_TIMEOUT_MS)
                                handlePidResult(pid, raw)
                                if (pollDelayMs > 0L) delay(pollDelayMs)
                            }
                        }
                        cycleCounter++
                    }
                } catch (ioe: IOException) {
                    stateHolder.updateObd2 {
                        it.copy(
                            connected = false,
                            status = if (autoReconnectEnabled) {
                                Obd2ConnectionStatus.RECONNECTING
                            } else {
                                Obd2ConnectionStatus.ERROR
                            },
                            lastError = ioe.message ?: "RFCOMM socket disconnected",
                        )
                    }
                } catch (se: SecurityException) {
                    updateErrorState(deviceName, deviceAddress, se.message ?: "Bluetooth permission denied")
                    break
                } catch (e: Exception) {
                    stateHolder.updateObd2 {
                        it.copy(
                            connected = false,
                            status = if (autoReconnectEnabled) {
                                Obd2ConnectionStatus.RECONNECTING
                            } else {
                                Obd2ConnectionStatus.ERROR
                            },
                            lastError = e.message ?: "ELM327 error",
                        )
                    }
                } finally {
                    closeSocketQuietly(socket)
                    if (activeSocket === socket) {
                        activeSocket = null
                    }
                }

                if (!autoReconnectEnabled || !isActive) {
                    break
                }

                stateHolder.updateObd2 {
                    it.copy(
                        connected = false,
                        status = Obd2ConnectionStatus.RECONNECTING,
                    )
                }
                delay(backoffMs)
                backoffMs = min(backoffMs * 2L, MAX_BACKOFF_MS)
            }
        }
    }

    private fun updateErrorState(deviceName: String, deviceAddress: String, message: String) {
        stateHolder.updateObd2 {
            it.copy(
                connected = false,
                status = Obd2ConnectionStatus.ERROR,
                deviceName = deviceName,
                deviceAddress = deviceAddress,
                lastError = message,
            )
        }
    }

    private suspend fun initializeElm327(input: InputStream, output: OutputStream): String {
        val initCommands = listOf(
            "ATZ" to ATZ_TIMEOUT_MS,
            "ATE0" to INIT_TIMEOUT_MS,
            "ATL0" to INIT_TIMEOUT_MS,
            "ATS0" to INIT_TIMEOUT_MS,
            "ATSP0" to INIT_TIMEOUT_MS,
        )
        for ((cmd, timeoutMs) in initCommands) {
            sendAtOrPidCommand(cmd, input, output, timeoutMs)
            if (cmd == "ATZ") {
                delay(POST_RESET_DELAY_MS)
            }
        }
        val protocolRaw = sendAtOrPidCommand("ATDP", input, output, INIT_TIMEOUT_MS)
        val cleaned = protocolRaw
            .replace("\r", " ")
            .replace("\n", " ")
            .replace(">", "")
            .trim()
        return cleaned.ifEmpty { "AUTO" }
    }

    @Throws(IOException::class)
    private suspend fun sendAtOrPidCommand(
        command: String,
        input: InputStream,
        output: OutputStream,
        timeoutMs: Long,
    ): String {
        while (input.available() > 0) {
            input.read()
        }
        output.write("$command\r".toByteArray(Charsets.US_ASCII))
        output.flush()

        val response = withTimeoutOrNull(timeoutMs) {
            val sb = StringBuilder()
            while (true) {
                if (input.available() > 0) {
                    val b = input.read()
                    if (b == -1) {
                        throw IOException("OBD2 stream closed")
                    }
                    val ch = b.toChar()
                    if (ch == '>') {
                        break
                    }
                    sb.append(ch)
                } else {
                    delay(READ_POLL_DELAY_MS)
                }
            }
            sb.toString()
        }
        return response ?: "NO DATA"
    }

    private enum class PidLoopSignal {
        SUCCESS,
        IGNORED,
        RECONNECT_NEEDED,
    }

    private fun handlePidResult(pid: String, rawResponse: String): PidLoopSignal {
        return when (val parsed = Obd2PidParser.parsePidResponse(pid, rawResponse)) {
            is PidParseResult.VehicleSpeed -> {
                stateHolder.updateObd2 {
                    it.copy(
                        connected = true,
                        status = Obd2ConnectionStatus.POLLING,
                        speedKmh = parsed.speedKmh,
                        lastError = null,
                    )
                }
                PidLoopSignal.SUCCESS
            }

            is PidParseResult.EngineRpm -> {
                stateHolder.updateObd2 {
                    it.copy(
                        connected = true,
                        status = Obd2ConnectionStatus.POLLING,
                        rpm = parsed.rpm,
                        lastError = null,
                    )
                }
                PidLoopSignal.SUCCESS
            }

            is PidParseResult.ThrottlePosition -> {
                stateHolder.updateObd2 {
                    it.copy(
                        connected = true,
                        status = Obd2ConnectionStatus.POLLING,
                        throttlePos = parsed.percent,
                        lastError = null,
                    )
                }
                PidLoopSignal.SUCCESS
            }

            is PidParseResult.CoolantTemp -> {
                stateHolder.updateObd2 {
                    it.copy(
                        connected = true,
                        status = Obd2ConnectionStatus.POLLING,
                        coolantTempC = parsed.tempC,
                        lastError = null,
                    )
                }
                PidLoopSignal.SUCCESS
            }

            is PidParseResult.EngineLoad -> {
                stateHolder.updateObd2 {
                    it.copy(
                        connected = true,
                        status = Obd2ConnectionStatus.POLLING,
                        engineLoad = parsed.percent,
                        lastError = null,
                    )
                }
                PidLoopSignal.SUCCESS
            }

            is PidParseResult.IntakeAirTemp -> {
                stateHolder.updateObd2 {
                    it.copy(
                        connected = true,
                        status = Obd2ConnectionStatus.POLLING,
                        intakeTempC = parsed.tempC,
                        lastError = null,
                    )
                }
                PidLoopSignal.SUCCESS
            }

            is PidParseResult.MafAirFlow -> {
                stateHolder.updateObd2 {
                    it.copy(
                        connected = true,
                        status = Obd2ConnectionStatus.POLLING,
                        mafGps = parsed.gramsPerSec,
                        lastError = null,
                    )
                }
                PidLoopSignal.SUCCESS
            }

            is PidParseResult.TimingAdvance -> {
                stateHolder.updateObd2 {
                    it.copy(
                        connected = true,
                        status = Obd2ConnectionStatus.POLLING,
                        timingAdvanceDeg = parsed.degrees,
                        lastError = null,
                    )
                }
                PidLoopSignal.SUCCESS
            }

            is PidParseResult.FuelLevel -> {
                stateHolder.updateObd2 {
                    it.copy(
                        connected = true,
                        status = Obd2ConnectionStatus.POLLING,
                        fuelLevelPct = parsed.percent,
                        lastError = null,
                    )
                }
                PidLoopSignal.SUCCESS
            }

            is PidParseResult.Error -> {
                stateHolder.updateObd2 {
                    it.copy(
                        connected = false,
                        status = Obd2ConnectionStatus.ERROR,
                        lastError = parsed.message,
                    )
                }
                if (parsed.message == "UNABLE TO CONNECT" || parsed.message == "CAN ERROR") {
                    PidLoopSignal.RECONNECT_NEEDED
                } else {
                    PidLoopSignal.IGNORED
                }
            }

            PidParseResult.Unrecognized -> PidLoopSignal.IGNORED
        }
    }

    fun disconnect(clearSelectedDevice: Boolean = true) {
        connectionJob?.cancel()
        connectionJob = null
        closeSocketQuietly(activeSocket)
        activeSocket = null
        stateHolder.updateObd2 { current ->
            current.copy(
                connected = false,
                status = Obd2ConnectionStatus.DISCONNECTED,
                deviceName = if (clearSelectedDevice) "" else current.deviceName,
                deviceAddress = if (clearSelectedDevice) "" else current.deviceAddress,
                protocol = "AUTO",
                speedKmh = 0,
                rpm = 0,
                throttlePos = 0f,
                coolantTempC = 0,
                engineLoad = 0f,
                intakeTempC = 0,
                mafGps = 0f,
                timingAdvanceDeg = 0f,
                fuelLevelPct = 0f,
                lastError = null,
            )
        }
    }

    fun shutdown() {
        disconnect(clearSelectedDevice = false)
        scope.cancel()
    }

    private fun closeSocketQuietly(socket: BluetoothSocket?) {
        try {
            socket?.close()
        } catch (_: IOException) {
            // Safe to ignore on teardown.
        }
    }

    companion object {
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val ATZ_TIMEOUT_MS = 3500L
        private const val INIT_TIMEOUT_MS = 1800L
        private const val PID_TIMEOUT_MS = 1200L
        private const val DTC_TIMEOUT_MS = 2500L
        private const val POST_RESET_DELAY_MS = 350L
        private const val READ_POLL_DELAY_MS = 5L
        private const val DEFAULT_INTER_PID_DELAY_MS = 20L
        private const val SECONDARY_PID_MODULO = 4
        private const val INITIAL_BACKOFF_MS = 1000L
        private const val MAX_BACKOFF_MS = 15000L
    }
}
