package com.mobilecomputing.networkmeter.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MeasurementRecord(
    val sessionId: String,
    val date: String,
    val day: String,
    val timeWindow: String,
    val timeCategory: String,
    val timestamp: String,
    val locationId: String,
    val locationName: String,
    val locationType: String,
    val deviceModel: String,
    val androidVersion: String,
    val intendedConnection: String,
    val actualConnection: String,
    val connectionChanged: Boolean,
    val wifiSsid: String,
    val wifiFrequencyMhz: Int,
    val cellularOperator: String,
    val cellularTech: String,
    val signalReadingDbm: Double,
    val signalLevel0To4: Int,
    val probe1RttMs: Double,
    val probe2RttMs: Double,
    val probe3RttMs: Double,
    val probeAvgRttMs: Double,
    val downloadBytes: Long,
    val downloadDurationMs: Double,
    val downloadSpeedMbps: Double,
    val outcome: String,
    val notes: String
) {
    fun toCsvRow(): String {
        val escape = { s: String ->
            if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
                "\"${s.replace("\"", "\"\"")}\""
            } else {
                s
            }
        }
        return listOf(
            escape(sessionId),
            escape(date),
            escape(day),
            escape(timeWindow),
            escape(timeCategory),
            escape(timestamp),
            escape(locationId),
            escape(locationName),
            escape(locationType),
            escape(deviceModel),
            escape(androidVersion),
            escape(intendedConnection),
            escape(actualConnection),
            connectionChanged.toString(),
            escape(wifiSsid),
            wifiFrequencyMhz.toString(),
            escape(cellularOperator),
            escape(cellularTech),
            String.format(Locale.US, "%.1f", signalReadingDbm),
            signalLevel0To4.toString(),
            String.format(Locale.US, "%.2f", probe1RttMs),
            String.format(Locale.US, "%.2f", probe2RttMs),
            String.format(Locale.US, "%.2f", probe3RttMs),
            String.format(Locale.US, "%.2f", probeAvgRttMs),
            downloadBytes.toString(),
            String.format(Locale.US, "%.2f", downloadDurationMs),
            String.format(Locale.US, "%.3f", downloadSpeedMbps),
            escape(outcome),
            escape(notes)
        ).joinToString(",")
    }

    companion object {
        fun csvHeader(): String {
            return listOf(
                "session_id",
                "date",
                "day",
                "time_window",
                "time_category",
                "timestamp",
                "location_id",
                "location_name",
                "location_type",
                "device_model",
                "android_version",
                "intended_connection",
                "actual_connection",
                "connection_changed",
                "wifi_ssid",
                "wifi_frequency_mhz",
                "cellular_operator",
                "cellular_tech",
                "signal_reading_dbm",
                "signal_level_0_to_4",
                "probe1_rtt_ms",
                "probe2_rtt_ms",
                "probe3_rtt_ms",
                "probe_avg_rtt_ms",
                "download_bytes",
                "download_duration_ms",
                "download_speed_mbps",
                "outcome",
                "notes"
            ).joinToString(",")
        }

        fun currentIsoTimestamp(): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            return sdf.format(Date())
        }

        fun currentDateString(): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            return sdf.format(Date())
        }
    }
}
