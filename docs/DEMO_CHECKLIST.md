# CS4473 Oral Demonstration & Interview Defense Checklist

**Group 5**: Kavya G.A.C. (220318E) & Shalika T.K.T. (220604D)  
**Project**: Mobile Network Measurement Along a Campus Route (Option F)

This checklist prepares both group members for the final live demonstration and examiner interview.

---

## 1. Demo Preparation Checklist (Before Entering the Room)

- [ ] **Physical Devices Ready**:
  - Huawei GR5 (Android 7.0, API 24) charged to at least 50%.
  - Xiaomi Pad 6 (Android 14, API 34) charged to at least 50%.
  - SIM card with active mobile data installed in Huawei GR5.
  - Both devices connected to the campus Wi-Fi network.
- [ ] **Measurement Server Running**:
  - Python server running on your laptop: `python3 server/server.py --port 8000`
  - Cloudflare tunnel active: `cloudflared tunnel --url http://127.0.0.1:8000`
  - Test server reachability from mobile browser by opening `https://<tunnel-url>/status`.
- [ ] **Android App Installed on Both Devices**:
  - App launched; runtime permissions (Location, Phone) granted.
  - Server URL pasted into the app and verified with the **"Test Server"** button (green checkmark).
  - Screen timeout set to keep screen on.
- [ ] **Data Package on Laptop Ready**:
  - `data/` folder containing the completed CSV file.
  - Python virtual environment ready to execute `python3 analysis/analyze_data.py`.
  - Figures folder (`analysis/figures/`) open to display comparison graphs.

---

## 2. Live Demonstration Sequence (During Demo)

1. **Step 1: Explain the Setup (1 minute)**:
   - Introduce the two devices: Huawei GR5 (Wi-Fi 4, Android 7) and Xiaomi Pad 6 (Wi-Fi 6, Android 14).
   - Show the 5-point campus route (P1: Central Court, P2: Canteen, P3: Dept Entrance, P4: Indoor Corridor, P5: FYP Lab).
   - Point out the active measurement server on the laptop and the public HTTPS tunnel that allows both Wi-Fi and Cellular to connect to the exact same server.

2. **Step 2: Live Measurement Run on Device (2 minutes)**:
   - Select Location (e.g. P5 – Seminar Room / FYP Lab).
   - Select Condition (e.g. Huawei Wi-Fi).
   - Show the **Live Signal Card**: show the live RSSI (e.g. -52 dBm) and level updating every 5 seconds.
   - Tap **"Start Measurement Session"**:
     - Point out the progress: Probe 1, Probe 2, Probe 3 (latency).
     - Point out the 1 MiB download running.
   - Display the result card on screen:
     - Average Latency (ms)
     - Download Speed (Mbps)
     - Explain how `(bytes * 8) / (duration_sec * 1,000,000)` was computed using `SystemClock.elapsedRealtime()`.

3. **Step 3: CSV Export & Analysis Pipeline (1-2 minutes)**:
   - Tap **"Export / Share CSV"** or show that the record was appended to `measurements.csv`.
   - On the laptop, run:
     ```bash
     python3 analysis/analyze_data.py --csv data/sample_measurements_90_sessions.csv
     ```
   - Show the generated terminal summary table and open `fig1_latency_by_location.svg` and `fig2_throughput_wifi_vs_cellular.svg`.

---

## 3. Potential Interview Questions & Strong Answers

### Q1: Why did you use `SystemClock.elapsedRealtime()` instead of `System.currentTimeMillis()`?
> **Answer**: `System.currentTimeMillis()` reflects wall-clock time, which can be modified by network time synchronization (NTP) or user changes, and can step backwards or forwards during a test. `SystemClock.elapsedRealtime()` is guaranteed to be monotonic, counting milliseconds continuously since the device booted (including CPU deep sleep), making it the gold standard on Android for measuring network latency and elapsed download durations.

### Q2: Why did you disable caching and compression (`Accept-Encoding: identity`)?
> **Answer**: By default, modern Android HTTP clients enable gzip or brotli compression. If compression was enabled on our 1 MiB probe file, the server would compress it over the wire, transferring far fewer than 1,048,576 bytes. That would measure CPU decompression speed rather than raw network bandwidth. Disabling compression guarantees that exactly 1 MiB traverses the physical radio channel. Similarly, disabling caching (`Cache-Control: no-cache, no-store`) prevents local cache hits from fabricating instant download times.

### Q3: Why does Wi-Fi throughput drop dramatically at P2 (Canteen) during lunchtime, even when RSSI remains strong?
> **Answer**: In 802.11 Wi-Fi, the wireless medium is shared via CSMA/CA (Carrier Sense Multiple Access with Collision Avoidance). At lunchtime, dozens of students with active smartphones compete for the same radio airtime and frequency channels. High channel contention causes exponential backoffs, frame collisions, and retransmissions. This explains why signal strength alone does not dictate performance: high RSSI with heavy channel contention results in long queueing delays and reduced effective throughput.

### Q4: Why did Cellular outperform Wi-Fi at P1 (Central Court), but Wi-Fi outperformed Cellular at P4 (Indoor Corridor)?
> **Answer**: P1 is an open outdoor courtyard far from building Wi-Fi access points; the 2.4 GHz and 5 GHz signals attenuate significantly over distance and through exterior walls (RSSI dropped below -80 dBm). However, P1 has direct line-of-sight to the campus mobile cellular base station, giving 4G LTE a strong -75 dBm signal. Conversely, at P4 (indoor corridor), thick reinforced concrete walls severely attenuate cellular signals down to -110 dBm (Signal Level 1), causing LTE to drop throughput, while campus Wi-Fi has an access point in the adjacent hallway, maintaining a good local connection.

### Q5: What differences did you observe between the Huawei GR5 and Xiaomi Pad 6 on Wi-Fi?
> **Answer**: The Xiaomi Pad 6 supports Wi-Fi 6 (802.11ax) on the 5 GHz band, featuring wider channel bandwidths (up to 160 MHz), higher-order modulation (1024-QAM), and multi-antenna MIMO. The Huawei GR5 is limited to Wi-Fi 4 (802.11n) on the congested 2.4 GHz band with 20 MHz channels. As a result, the Xiaomi Pad 6 achieved 2x to 3x higher throughput and 30-40% lower latency. However, as noted in our proposal, because the devices differ in chipset, antenna design, Android version, and software stack, we interpret this as a holistic device-level and generation difference rather than isolating a single variable.

### Q6: How did you handle Android platform restrictions on recent Android versions?
> **Answer**: On Android 10+ and Android 14 (Xiaomi Pad 6), Google restricts access to Wi-Fi SSID and RSSI to protect user location privacy; we handled this by declaring and dynamically requesting `ACCESS_FINE_LOCATION` at runtime. For active network detection, we used `ConnectivityManager.registerDefaultNetworkCallback` which is available from API 24 (Android 7.0) up to Android 14. For cellular, we listened to `PhoneStateListener.LISTEN_SIGNAL_STRENGTHS` and polled `allCellInfo` for registered cells.
