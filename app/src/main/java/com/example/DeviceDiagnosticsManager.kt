package com.example

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DeviceDiagnosticsManager(
    context: Context,
    private val stateHolder: TelemetryStateHolder,
) {
    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val wifiManager =
        appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var pollJob: Job? = null

    fun start() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (isActive) {
                sampleAndPublish()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
    }

    fun shutdown() {
        stop()
        scope.cancel()
    }

    @Suppress("DEPRECATION")
    private fun sampleAndPublish() {
        val batteryIntent = appContext.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        )
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val batteryPct = if (level >= 0 && scale > 0) ((level * 100) / scale) else 100
        val rawTemp = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 250) ?: 250
        val batteryTempC = rawTemp / 10f
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        var rssi = -127
        var linkMbps = 0

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val activeNet = connectivityManager?.activeNetwork
            val caps = activeNet?.let { connectivityManager?.getNetworkCapabilities(it) }
            if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                val transportInfo = caps.transportInfo
                if (transportInfo is WifiInfo) {
                    rssi = transportInfo.rssi
                    linkMbps = transportInfo.linkSpeed
                } else {
                    val sig = caps.signalStrength
                    if (sig != NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED) {
                        rssi = sig
                    }
                }
            }
        }

        if (rssi == -127) {
            try {
                val info = wifiManager?.connectionInfo
                if (info != null && info.networkId != -1) {
                    rssi = info.rssi
                    linkMbps = info.linkSpeed
                }
            } catch (_: Exception) {
                // Ignore if Wi-Fi info is unavailable.
            }
        }

        stateHolder.updateDeviceHealth(
            DeviceHealthState(
                batteryPct = batteryPct,
                batteryTempC = batteryTempC,
                charging = isCharging,
                wifiRssiDbm = rssi,
                wifiLinkMbps = linkMbps,
            ),
        )
    }

    companion object {
        private const val POLL_INTERVAL_MS = 1000L
    }
}
