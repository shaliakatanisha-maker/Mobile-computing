package com.mobilecomputing.networkmeter.network

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

data class ProbeResult(
    val probe1Ms: Double,
    val probe2Ms: Double,
    val probe3Ms: Double,
    val avgLatencyMs: Double,
    val downloadBytes: Long,
    val downloadDurationMs: Double,
    val downloadSpeedMbps: Double,
    val outcome: String,
    val failureReason: String = ""
)

class NetworkProbeManager {

    companion object {
        private const val TIMEOUT_MS = 15000 // 15 seconds per request as per specification
        private const val ONE_MIB = 1048576L  // 1,048,576 bytes
    }

    suspend fun executeMeasurementSession(
        baseUrl: String,
        onProgress: (step: String) -> Unit
    ): ProbeResult = withContext(Dispatchers.IO) {
        val cleanBaseUrl = baseUrl.trimEnd('/')

        // 1. Send three small server requests to /probe
        val probeTimes = mutableListOf<Double>()
        for (i in 1..3) {
            onProgress("Running Latency Probe $i of 3...")
            val probeUrl = "$cleanBaseUrl/probe"
            val singleResult = measureSingleProbe(probeUrl)
            if (!singleResult.first) {
                return@withContext ProbeResult(
                    probe1Ms = probeTimes.getOrElse(0) { 0.0 },
                    probe2Ms = probeTimes.getOrElse(1) { 0.0 },
                    probe3Ms = 0.0,
                    avgLatencyMs = 0.0,
                    downloadBytes = 0L,
                    downloadDurationMs = 0.0,
                    downloadSpeedMbps = 0.0,
                    outcome = "FAILED",
                    failureReason = "Probe $i failed: ${singleResult.third}"
                )
            }
            probeTimes.add(singleResult.second)
        }

        val p1 = probeTimes[0]
        val p2 = probeTimes[1]
        val p3 = probeTimes[2]
        val avgLatency = (p1 + p2 + p3) / 3.0

        // 2. Download 1 MiB file from /download
        onProgress("Downloading 1 MiB payload...")
        val downloadUrl = "$cleanBaseUrl/download"
        val dlResult = measureDownloadThroughput(downloadUrl)

        if (!dlResult.first) {
            return@withContext ProbeResult(
                probe1Ms = p1,
                probe2Ms = p2,
                probe3Ms = p3,
                avgLatencyMs = avgLatency,
                downloadBytes = dlResult.second,
                downloadDurationMs = dlResult.third,
                downloadSpeedMbps = 0.0,
                outcome = "FAILED",
                failureReason = "Download failed: ${dlResult.fifth}"
            )
        }

        val bytesReceived = dlResult.second
        val durationMs = dlResult.third
        val speedMbps = dlResult.fourth

        val outcome = if (bytesReceived >= ONE_MIB) "SUCCESS" else "PARTIAL"

        ProbeResult(
            probe1Ms = p1,
            probe2Ms = p2,
            probe3Ms = p3,
            avgLatencyMs = avgLatency,
            downloadBytes = bytesReceived,
            downloadDurationMs = durationMs,
            downloadSpeedMbps = speedMbps,
            outcome = outcome
        )
    }

    suspend fun testServerReachability(baseUrl: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val testUrl = baseUrl.trimEnd('/') + "/probe"
        val (success, timeMs, error) = measureSingleProbe(testUrl)
        if (success) {
            Pair(true, "Server reachable! Ping: ${String.format("%.1f", timeMs)} ms")
        } else {
            Pair(false, "Connection error: $error")
        }
    }

    /**
     * Measures response time: from starting a small request until its response is fully read,
     * including connection setup.
     */
    private fun measureSingleProbe(urlString: String): Triple<Boolean, Double, String> {
        var conn: HttpURLConnection? = null
        return try {
            val startRealtime = SystemClock.elapsedRealtime()

            val url = URL(urlString)
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            // Proposal requirement: disable caching and compression
            conn.useCaches = false
            conn.setRequestProperty("Cache-Control", "no-cache, no-store")
            conn.setRequestProperty("Pragma", "no-cache")
            conn.setRequestProperty("Accept-Encoding", "identity")

            val responseCode = conn.responseCode
            if (responseCode !in 200..299) {
                return Triple(false, 0.0, "HTTP $responseCode")
            }

            // Fully read response stream
            conn.inputStream.use { stream ->
                val buffer = ByteArray(512)
                while (stream.read(buffer) != -1) {
                    // Consume stream fully
                }
            }

            val endRealtime = SystemClock.elapsedRealtime()
            val durationMs = (endRealtime - startRealtime).toDouble()
            Triple(true, durationMs, "")
        } catch (e: SocketTimeoutException) {
            Triple(false, 0.0, "Request timed out (>15s)")
        } catch (e: Exception) {
            Triple(false, 0.0, e.message ?: "Unknown error")
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Downloads 1 MiB payload and computes throughput:
     * Download speed (Mbps) = (receivedbytes * 8) / (requestduration(seconds) * 1,000,000)
     */
    private fun measureDownloadThroughput(urlString: String): Quintuple<Boolean, Long, Double, Double, String> {
        var conn: HttpURLConnection? = null
        return try {
            val startRealtime = SystemClock.elapsedRealtime()

            val url = URL(urlString)
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.useCaches = false
            conn.setRequestProperty("Cache-Control", "no-cache, no-store")
            conn.setRequestProperty("Pragma", "no-cache")
            conn.setRequestProperty("Accept-Encoding", "identity")

            val responseCode = conn.responseCode
            if (responseCode !in 200..299) {
                return Quintuple(false, 0L, 0.0, 0.0, "HTTP $responseCode")
            }

            var totalBytesRead = 0L
            val buffer = ByteArray(16384) // 16 KiB buffer
            val inputStream: InputStream = conn.inputStream
            inputStream.use { stream ->
                var bytesRead: Int
                while (stream.read(buffer).also { bytesRead = it } != -1) {
                    totalBytesRead += bytesRead
                }
            }

            val endRealtime = SystemClock.elapsedRealtime()
            val durationMs = (endRealtime - startRealtime).toDouble()
            val durationSeconds = durationMs / 1000.0

            // Formula from proposal:
            // Download speed (Mbps) = (receivedbytes * 8) / (requestduration(seconds) * 1,000,000)
            val speedMbps = if (durationSeconds > 0) {
                (totalBytesRead * 8.0) / (durationSeconds * 1_000_000.0)
            } else {
                0.0
            }

            Quintuple(true, totalBytesRead, durationMs, speedMbps, "")
        } catch (e: SocketTimeoutException) {
            Quintuple(false, 0L, 0.0, 0.0, "Download timed out (>15s)")
        } catch (e: Exception) {
            Quintuple(false, 0L, 0.0, 0.0, e.message ?: "Download exception")
        } finally {
            conn?.disconnect()
        }
    }

    data class Quintuple<A, B, C, D, E>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
        val fifth: E
    )
}
