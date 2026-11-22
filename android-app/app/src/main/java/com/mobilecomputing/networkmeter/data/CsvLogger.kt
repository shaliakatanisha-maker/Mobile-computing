package com.mobilecomputing.networkmeter.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.net.HttpURLConnection
import java.net.URL

class CsvLogger(private val context: Context) {
    private val fileName = "measurements.csv"
    private val file: File = File(context.filesDir, fileName)

    init {
        ensureHeaderExists()
    }

    @Synchronized
    private fun ensureHeaderExists() {
        if (!file.exists() || file.length() == 0L) {
            file.parentFile?.mkdirs()
            file.writeText(MeasurementRecord.csvHeader() + "\n", Charsets.UTF_8)
        }
    }

    @Synchronized
    fun appendRecord(record: MeasurementRecord) {
        ensureHeaderExists()
        file.appendText(record.toCsvRow() + "\n", Charsets.UTF_8)
    }

    @Synchronized
    fun getRecordCount(): Int {
        if (!file.exists()) return 0
        val lines = file.readLines(Charsets.UTF_8)
        return if (lines.size > 1) lines.size - 1 else 0
    }

    @Synchronized
    fun getCsvContent(): String {
        ensureHeaderExists()
        return file.readText(Charsets.UTF_8)
    }

    fun getExportUri(): Uri {
        ensureHeaderExists()
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    suspend fun uploadToServer(serverUrl: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            ensureHeaderExists()
            val targetUrl = serverUrl.trimEnd('/') + "/upload"
            val url = URL(targetUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.setRequestProperty("Content-Type", "text/csv")
            conn.setRequestProperty("User-Agent", "CS4473-NetworkMeter-Android")

            val bytes = file.readBytes()
            conn.setFixedLengthStreamingMode(bytes.size)

            conn.outputStream.use { os ->
                os.write(bytes)
                os.flush()
            }

            val code = conn.responseCode
            if (code in 200..299) {
                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                Pair(true, "Uploaded ${bytes.size} bytes successfully (HTTP $code).")
            } else {
                Pair(false, "Server returned error: HTTP $code")
            }
        } catch (e: Exception) {
            Pair(false, "Upload failed: ${e.message}")
        }
    }
}
