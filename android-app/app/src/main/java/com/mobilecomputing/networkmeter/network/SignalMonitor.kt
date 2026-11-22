package com.mobilecomputing.networkmeter.network

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoWcdma
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyManager

data class SignalSnapshot(
    val connectionType: String,
    val signalDbm: Double,
    val signalLevel: Int,
    val ssidOrCarrier: String,
    val frequencyMhz: Int,
    val linkSpeedMbps: Int,
    val cellularTech: String
)

class SignalMonitor(
    private val context: Context,
    private val onSignalUpdate: (SignalSnapshot) -> Unit
) {
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val handler = Handler(Looper.getMainLooper())
    private var isPolling = false

    private var latestCellLevel = 0
    private var latestCellDbm = -100.0

    private val phoneStateListener = object : PhoneStateListener() {
        @Deprecated("Deprecated in Java")
        override fun onSignalStrengthsChanged(signalStrength: SignalStrength?) {
            super.onSignalStrengthsChanged(signalStrength)
            signalStrength?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    latestCellLevel = it.level
                }
            }
        }
    }

    private val pollingRunnable = object : Runnable {
        override fun run() {
            if (isPolling) {
                val snapshot = captureSnapshot()
                onSignalUpdate(snapshot)
                // Schedule next poll in 5 seconds as specified in experiment requirements
                handler.postDelayed(this, 5000)
            }
        }
    }

    fun startMonitoring() {
        if (isPolling) return
        isPolling = true

        try {
            telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
        } catch (e: SecurityException) {
            // Permission not yet granted
        }

        // Run immediate capture, then 5-second recurring loop
        handler.post(pollingRunnable)
    }

    fun stopMonitoring() {
        isPolling = false
        handler.removeCallbacks(pollingRunnable)
        try {
            telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
        } catch (e: Exception) {
            // Ignore
        }
    }

    @SuppressLint("MissingPermission")
    fun captureSnapshot(): SignalSnapshot {
        val isWifi = isWifiActive()

        if (isWifi) {
            val (rssi, ssid, freq, linkSpeed) = getWifiDetails()
            val level = calculateWifiLevel(rssi)
            return SignalSnapshot(
                connectionType = "Wi-Fi",
                signalDbm = rssi.toDouble(),
                signalLevel = level,
                ssidOrCarrier = ssid,
                frequencyMhz = freq,
                linkSpeedMbps = linkSpeed,
                cellularTech = "N/A"
            )
        } else {
            val (dbm, level, carrier, tech) = getCellularDetails()
            return SignalSnapshot(
                connectionType = "Cellular",
                signalDbm = dbm,
                signalLevel = level,
                ssidOrCarrier = carrier,
                frequencyMhz = -1,
                linkSpeedMbps = -1,
                cellularTech = tech
            )
        }
    }

    private fun isWifiActive(): Boolean {
        val cm = connectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun getWifiDetails(): Quadruple<Int, String, Int, Int> {
        var rssi = -127
        var ssid = "Unknown"
        var freq = -1
        var linkSpeed = -1

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val cm = connectivityManager
                val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
                val wifiInfo = caps?.transportInfo as? WifiInfo
                if (wifiInfo != null) {
                    rssi = wifiInfo.rssi
                    ssid = cleanSsid(wifiInfo.ssid)
                    freq = wifiInfo.frequency
                    linkSpeed = wifiInfo.linkSpeed
                    return Quadruple(rssi, ssid, freq, linkSpeed)
                }
            }

            // Fallback for Android 7-9 (Huawei GR5) and when transportInfo is null
            val info = wifiManager?.connectionInfo
            if (info != null) {
                rssi = info.rssi
                ssid = cleanSsid(info.ssid)
                freq = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) info.frequency else -1
                linkSpeed = info.linkSpeed
            }
        } catch (e: Exception) {
            // Permission or security exception
        }

        return Quadruple(rssi, ssid, freq, linkSpeed)
    }

    @SuppressLint("MissingPermission")
    private fun getCellularDetails(): Quadruple<Double, Int, String, String> {
        val tm = telephonyManager ?: return Quadruple(-100.0, 0, "No SIM", "N/A")
        val carrier = tm.networkOperatorName.ifBlank { "Cellular" }
        var tech = "Cellular"
        var dbm = latestCellDbm
        var level = latestCellLevel

        try {
            val cellList: List<CellInfo>? = tm.allCellInfo
            if (!cellList.isNullOrEmpty()) {
                for (cell in cellList) {
                    if (cell.isRegistered) {
                        when (cell) {
                            is CellInfoLte -> {
                                tech = "4G/LTE"
                                dbm = cell.cellSignalStrength.dbm.toDouble()
                                level = cell.cellSignalStrength.level
                                break
                            }
                            is CellInfoWcdma -> {
                                tech = "3G/HSPA"
                                dbm = cell.cellSignalStrength.dbm.toDouble()
                                level = cell.cellSignalStrength.level
                                break
                            }
                            is CellInfoGsm -> {
                                tech = "2G/EDGE"
                                dbm = cell.cellSignalStrength.dbm.toDouble()
                                level = cell.cellSignalStrength.level
                                break
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Keep latestCellLevel and fallback dbm
        }

        latestCellDbm = dbm
        latestCellLevel = level
        return Quadruple(dbm, level, carrier, tech)
    }

    private fun cleanSsid(rawSsid: String?): String {
        if (rawSsid == null || rawSsid == "<unknown ssid>") return "Campus-WiFi"
        return rawSsid.trim('"')
    }

    private fun calculateWifiLevel(rssi: Int): Int {
        return when {
            rssi >= -55 -> 4
            rssi >= -65 -> 3
            rssi >= -75 -> 2
            rssi >= -85 -> 1
            else -> 0
        }
    }

    data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
