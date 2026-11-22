package com.mobilecomputing.networkmeter

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.mobilecomputing.networkmeter.data.CsvLogger
import com.mobilecomputing.networkmeter.data.MeasurementRecord
import com.mobilecomputing.networkmeter.databinding.ActivityMainBinding
import com.mobilecomputing.networkmeter.network.ConnectionStateTracker
import com.mobilecomputing.networkmeter.network.NetworkProbeManager
import com.mobilecomputing.networkmeter.network.SignalMonitor
import com.mobilecomputing.networkmeter.network.SignalSnapshot
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var csvLogger: CsvLogger
    private lateinit var signalMonitor: SignalMonitor
    private lateinit var connectionTracker: ConnectionStateTracker
    private val probeManager = NetworkProbeManager()

    private var latestSnapshot: SignalSnapshot? = null
    private var isTestingInProgress = false

    private val locationOptions = listOf(
        Pair("P1", "Central Court (Outdoor)"),
        Pair("P2", "Goda Uda Canteen (Semi-indoor)"),
        Pair("P3", "Department Entrance (Transitional)"),
        Pair("P4", "Indoor Corridor (Indoor)"),
        Pair("P5", "FYP lab/Seminar Room (Indoor)")
    )

    private val conditionOptions = listOf(
        Triple("Huawei GR5", "Android 7.0 (API 24)", "Wi-Fi"),
        Triple("Huawei GR5", "Android 7.0 (API 24)", "Cellular"),
        Triple("Xiaomi Pad 6", "Android 14 (API 34)", "Wi-Fi")
    )

    private val timeWindowOptions = listOf(
        Pair("Morning", "09:00-10:00"),
        Pair("Lunchtime", "12:15-13:15")
    )

    private val dayOptions = listOf("Day 1 (Repetition 1)", "Day 2 (Repetition 2)", "Day 3 (Repetition 3)")

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocation = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val phoneState = permissions[Manifest.permission.READ_PHONE_STATE] ?: false
        logToConsole("Permissions granted: Location=$fineLocation, PhoneState=$phoneState")
        signalMonitor.startMonitoring()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Proposal requirement: The app will stay open with screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        csvLogger = CsvLogger(this)

        setupSpinners()
        setupListeners()
        initMonitors()
        checkAndRequestPermissions()
        updateStoredCountBadge()
    }

    private fun setupSpinners() {
        val locAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            locationOptions.map { "${it.first} – ${it.second}" }
        )
        binding.spnLocation.adapter = locAdapter

        val condAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            conditionOptions.map { "${it.first} [${it.third}]" }
        )
        binding.spnCondition.adapter = condAdapter

        val timeAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            timeWindowOptions.map { "${it.first} (${it.second})" }
        )
        binding.spnTimeWindow.adapter = timeAdapter

        val dayAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            dayOptions
        )
        binding.spnDay.adapter = dayAdapter

        // Auto-select condition matching device model if possible
        val currentModel = Build.MODEL
        if (currentModel.contains("Pad", ignoreCase = true) || currentModel.contains("Xiaomi", ignoreCase = true)) {
            binding.spnCondition.setSelection(2) // Xiaomi Pad 6 Wi-Fi
        }
    }

    private fun setupListeners() {
        binding.btnTestServer.setOnClickListener {
            testServerReachability()
        }

        binding.btnStartTest.setOnClickListener {
            if (!isTestingInProgress) {
                runMeasurementSession()
            }
        }

        binding.btnExportCsv.setOnClickListener {
            exportCsv()
        }

        binding.btnUploadCsv.setOnClickListener {
            uploadCsvToServer()
        }
    }

    private fun initMonitors() {
        connectionTracker = ConnectionStateTracker(this) { type, isConnected ->
            runOnUiThread {
                binding.tvActiveNetwork.text = "Active: $type"
                if (!isConnected) {
                    binding.tvActiveNetwork.setTextColor(ContextCompat.getColor(this, R.color.error))
                } else {
                    binding.tvActiveNetwork.setTextColor(ContextCompat.getColor(this, R.color.primary))
                }
            }
        }
        connectionTracker.startTracking()

        signalMonitor = SignalMonitor(this) { snapshot ->
            latestSnapshot = snapshot
            runOnUiThread {
                binding.tvSignalReading.text = "${String.format(Locale.US, "%.1f", snapshot.signalDbm)} dBm"
                binding.tvSignalLevel.text = "Level: ${snapshot.signalLevel} / 4"
                val detailStr = if (snapshot.connectionType == "Wi-Fi") {
                    "SSID: ${snapshot.ssidOrCarrier} | ${snapshot.frequencyMhz} MHz | ${snapshot.linkSpeedMbps} Mbps"
                } else {
                    "Carrier: ${snapshot.ssidOrCarrier} | Tech: ${snapshot.cellularTech}"
                }
                binding.tvNetworkDetails.text = detailStr
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            permissions.add(Manifest.permission.READ_PHONE_STATE)
        }

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty()) {
            logToConsole("Requesting permissions: ${needed.joinToString()}")
            requestPermissionLauncher.launch(needed.toTypedArray())
        } else {
            signalMonitor.startMonitoring()
        }
    }

    private fun testServerReachability() {
        val serverUrl = binding.etServerUrl.text.toString().trim()
        if (serverUrl.isBlank()) {
            Snackbar.make(binding.root, "Please enter a valid server URL", Snackbar.LENGTH_SHORT).show()
            return
        }

        binding.btnTestServer.isEnabled = false
        binding.tvServerStatus.text = "Testing server reachability..."

        lifecycleScope.launch {
            val (reachable, message) = probeManager.testServerReachability(serverUrl)
            binding.btnTestServer.isEnabled = true
            binding.tvServerStatus.text = message
            if (reachable) {
                binding.tvServerStatus.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.success))
                logToConsole("Server check: $message")
            } else {
                binding.tvServerStatus.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.error))
                logToConsole("Server check error: $message")
            }
        }
    }

    private fun runMeasurementSession() {
        val serverUrl = binding.etServerUrl.text.toString().trim()
        if (serverUrl.isBlank()) {
            Toast.makeText(this, "Please enter server URL first", Toast.LENGTH_SHORT).show()
            return
        }

        isTestingInProgress = true
        binding.btnStartTest.isEnabled = false
        binding.progressBar.visibility = android.view.View.VISIBLE

        val selectedLoc = locationOptions[binding.spnLocation.selectedItemPosition]
        val selectedCond = conditionOptions[binding.spnCondition.selectedItemPosition]
        val selectedTime = timeWindowOptions[binding.spnTimeWindow.selectedItemPosition]
        val selectedDay = "Day ${binding.spnDay.selectedItemPosition + 1}"

        val sessionCount = csvLogger.getRecordCount() + 1
        val sessionId = String.format(Locale.US, "S%03d", sessionCount)

        logToConsole("Starting Session $sessionId: ${selectedLoc.first} | ${selectedCond.first} (${selectedCond.third}) | ${selectedTime.first}")

        // Capture starting connection state
        val initialConnection = connectionTracker.getCurrentConnectionType()
        val snapshot = signalMonitor.captureSnapshot()
        latestSnapshot = snapshot

        lifecycleScope.launch {
            val result = probeManager.executeMeasurementSession(serverUrl) { step ->
                runOnUiThread {
                    binding.tvProgressStatus.text = step
                }
            }

            // Verify connection consistency after test
            val finalConnection = connectionTracker.getCurrentConnectionType()
            val connectionChanged = (initialConnection != finalConnection)

            val record = MeasurementRecord(
                sessionId = sessionId,
                date = MeasurementRecord.currentDateString(),
                day = selectedDay,
                timeWindow = selectedTime.second,
                timeCategory = selectedTime.first,
                timestamp = MeasurementRecord.currentIsoTimestamp(),
                locationId = selectedLoc.first,
                locationName = selectedLoc.second.substringBefore(" ("),
                locationType = selectedLoc.second.substringAfter("(").removeSuffix(")"),
                deviceModel = selectedCond.first,
                androidVersion = selectedCond.second,
                intendedConnection = selectedCond.third,
                actualConnection = initialConnection,
                connectionChanged = connectionChanged,
                wifiSsid = if (snapshot.connectionType == "Wi-Fi") snapshot.ssidOrCarrier else "N/A",
                wifiFrequencyMhz = snapshot.frequencyMhz,
                cellularOperator = if (snapshot.connectionType == "Cellular") snapshot.ssidOrCarrier else "N/A",
                cellularTech = snapshot.cellularTech,
                signalReadingDbm = snapshot.signalDbm,
                signalLevel0To4 = snapshot.signalLevel,
                probe1RttMs = result.probe1Ms,
                probe2RttMs = result.probe2Ms,
                probe3RttMs = result.probe3Ms,
                probeAvgRttMs = result.avgLatencyMs,
                downloadBytes = result.downloadBytes,
                downloadDurationMs = result.downloadDurationMs,
                downloadSpeedMbps = result.downloadSpeedMbps,
                outcome = result.outcome,
                notes = if (connectionChanged) "Connection changed from $initialConnection to $finalConnection" else ""
            )

            // Save record
            csvLogger.appendRecord(record)

            // Update UI with results
            runOnUiThread {
                binding.tvResultLatency.text = "${String.format(Locale.US, "%.1f", result.avgLatencyMs)} ms"
                binding.tvProbeDetails.text = "P1: ${String.format(Locale.US, "%.1f", result.probe1Ms)} | P2: ${String.format(Locale.US, "%.1f", result.probe2Ms)} | P3: ${String.format(Locale.US, "%.1f", result.probe3Ms)}"
                binding.tvResultSpeed.text = "${String.format(Locale.US, "%.2f", result.downloadSpeedMbps)} Mbps"
                binding.tvDownloadDetails.text = "1 MiB in ${String.format(Locale.US, "%.0f", result.downloadDurationMs)} ms"

                binding.tvProgressStatus.text = "Session $sessionId completed: ${result.outcome}"
                binding.progressBar.visibility = android.view.View.GONE
                binding.btnStartTest.isEnabled = true
                isTestingInProgress = false

                updateStoredCountBadge()
                logToConsole("Session $sessionId saved! Latency: ${String.format(Locale.US, "%.1f", result.avgLatencyMs)}ms, Speed: ${String.format(Locale.US, "%.2f", result.downloadSpeedMbps)}Mbps (${result.outcome})")
            }
        }
    }

    private fun exportCsv() {
        try {
            val uri = csvLogger.getExportUri()
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "CS4473 Mobile Network Measurements CSV")
                putExtra(Intent.EXTRA_TEXT, "Attached is the measurement data CSV file from CS4473 Group 5.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Export / Share CSV Data"))
            logToConsole("Exporting CSV file...")
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
            logToConsole("Export error: ${e.message}")
        }
    }

    private fun uploadCsvToServer() {
        val serverUrl = binding.etServerUrl.text.toString().trim()
        if (serverUrl.isBlank()) {
            Toast.makeText(this, "Please enter server URL", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnUploadCsv.isEnabled = false
        lifecycleScope.launch {
            val (success, message) = csvLogger.uploadToServer(serverUrl)
            binding.btnUploadCsv.isEnabled = true
            logToConsole("Server CSV upload: $message")
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun updateStoredCountBadge() {
        val count = csvLogger.getRecordCount()
        binding.tvRecordCount.text = "Stored Sessions: $count (Goal: 90 planned sessions)"
    }

    private fun logToConsole(message: String) {
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val currentLog = binding.tvLogConsole.text.toString()
        val newLog = "[$timeStr] $message\n$currentLog"
        // Keep within 4000 characters to avoid memory buildup
        val trimmedLog = if (newLog.length > 4000) newLog.substring(0, 4000) else newLog
        binding.tvLogConsole.text = trimmedLog
    }

    override fun onDestroy() {
        super.onDestroy()
        signalMonitor.stopMonitoring()
        connectionTracker.stopTracking()
    }
}
