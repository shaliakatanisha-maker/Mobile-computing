# CS4473 Mobile Computing - Mobile Network Measurement Along a Campus Route
**Option F Research Project | Group 5**
- **220318E** – Kavya G.A.C.
- **220604D** – Shalika T.K.T.

This repository contains the complete implementation, measurement server, raw dataset, analysis pipeline, and documentation for the CS4473 research project evaluating mobile network performance across a 5-point campus route.

---

## 📁 Repository Structure

```text
Mobile computing/
├── android-app/                   # Complete Native Kotlin Android App
│   ├── app/                       # Android application module
│   │   ├── build.gradle           # SDK 24 (Android 7.0) to SDK 34 (Android 14)
│   │   └── src/main/
│   │       ├── AndroidManifest.xml # Permissions (Fine Location, Phone State, Internet)
│   │       ├── java/com/mobilecomputing/networkmeter/
│   │       │   ├── MainActivity.kt               # Main UI and experiment coordinator
│   │       │   ├── data/
│   │       │   │   ├── MeasurementRecord.kt      # Data model matching proposal schema
│   │       │   │   └── CsvLogger.kt              # Thread-safe storage & FileProvider export
│   │       │   └── network/
│   │       │       ├── ConnectionStateTracker.kt # NetworkCallback active interface tracker
│   │       │       ├── SignalMonitor.kt          # 5-second polling (Wi-Fi RSSI & Cell Level)
│   │       │       └── NetworkProbeManager.kt    # 3x RTT probes + 1 MiB download test
│   │       └── res/                              # Layout, strings, colors, network security
│   ├── build.gradle               # Root project Gradle configuration
│   └── settings.gradle
├── server/                        # Python Measurement Server
│   ├── server.py                  # Non-caching HTTP server (/probe, /download, /upload)
│   ├── generate_certs.py          # SSL cert generator for local HTTPS testing
│   └── requirements.txt           # Standard library (zero mandatory pip dependencies)
├── data/                          # Datasets and Schemas
│   ├── sample_measurements_90_sessions.csv # 90-session dataset (5 locs x 3 conds x 2 times x 3 days)
│   └── README.md                  # Comprehensive Data Dictionary & sampling protocol
├── analysis/                      # Data Analysis and Figure Generation
│   ├── analyze_data.py            # Computes Mean ± StdDev, Markdown tables, and SVG charts
│   ├── generate_sample_data.py    # Realistic synthetic generator matching campus physics
│   ├── summary_statistics.md      # Auto-generated aggregate tables
│   ├── requirements.txt           # pandas, matplotlib, seaborn (optional for PNGs)
│   └── figures/                   # Publication-ready vector comparison charts
│       ├── fig1_latency_by_location.svg
│       ├── fig2_throughput_wifi_vs_cellular.svg
│       ├── fig3_device_comparison_wifi.svg
│       └── fig4_signal_vs_throughput.svg
├── docs/                          # Academic Deliverables & Assessment
│   ├── FINAL_RESEARCH_REPORT.md   # Complete 4-page academic research paper
│   ├── AI_USAGE_LOG.md            # AI usage attribution log (Section 3.2 rubric requirement)
│   └── DEMO_CHECKLIST.md          # Live demonstration guide & oral interview defense Q&A
└── README.md                      # This master instruction guide
```

---

## 🚀 Step-by-Step Execution Guide

### Step 1: Start the Measurement Server
The server must be accessible by both devices over campus Wi-Fi and mobile cellular data.

1. **Launch the server on your computer**:
   ```bash
   cd "server"
   python3 server.py --port 8000
   ```
2. **Expose the server via public HTTPS** (so both Wi-Fi and Cellular reach the exact same URL):
   - **Option A (Recommended & Free - Cloudflare Tunnel)**:
     ```bash
     cloudflared tunnel --url http://127.0.0.1:8000
     ```
     Copy the issued URL (e.g., `https://your-tunnel-name.trycloudflare.com`).
   - **Option B (ngrok)**:
     ```bash
     ngrok http 8000
     ```
3. Verify the server is live by visiting `https://<your-tunnel-url>/status` in any web browser.

---

### Step 2: Build & Install the Android App

The app is built with pure Kotlin and modern Android APIs, compatible from **Android 7.0 (API 24, Huawei GR5)** to **Android 14 (API 34, Xiaomi Pad 6)**.

1. Open **Android Studio**.
2. Select **Open an Existing Project** and choose the `android-app` directory.
3. Allow Gradle to sync.
4. Connect the **Huawei GR5** via USB (ensure USB Debugging is enabled in Developer Options) and click **Run (Shift + F10)**.
5. Connect the **Xiaomi Pad 6** via USB and click **Run**.
6. When the app launches on the device:
   - Accept the runtime permissions for **Location** and **Phone State** (required to read Wi-Fi RSSI and cellular signal strength).
   - Paste your Cloudflare/ngrok server URL into the **Measurement Server URL** field.
   - Tap **"Test Server"** to confirm reachability (displays green ping latency).

---

### Step 3: Conducting the Experimental Sessions Along the Route

Follow the protocol specified in Proposal Section 5 & 6:

1. **Route Waypoints**:
   - `P1`: Central Court (Outdoor)
   - `P2`: Goda Uda Canteen (Semi-indoor dining)
   - `P3`: Department Entrance (Transitional)
   - `P4`: Indoor Corridor (Concrete hallway)
   - `P5`: FYP Lab / Seminar Room (Indoor lab)

2. **Testing Conditions**:
   - **Condition 1**: Huawei GR5 connected to **Campus Wi-Fi** (Mobile Data turned OFF).
   - **Condition 2**: Huawei GR5 connected to **Cellular** (Wi-Fi turned OFF).
   - **Condition 3**: Xiaomi Pad 6 connected to **Campus Wi-Fi**.

3. **Time Windows**:
   - **Morning**: 09:00 – 10:00 (low traffic).
   - **Lunchtime**: 12:15 – 13:15 (peak crowd).

4. **At Each Waypoint**:
   - Stand at the designated physical marker.
   - Select Location, Condition, Time Window, and Day on the spinners.
   - Note the live signal meter (updating every 5 seconds).
   - Tap **"Start Measurement Session"**.
   - The app will automatically execute:
     - 3x latency probes to `/probe`
     - 1x 1 MiB throughput download to `/download`
     - Compute duration via `SystemClock.elapsedRealtime()` and calculate Mbps
     - Append the result directly to `measurements.csv` on the device.

5. **Repeat across 3 separate days**:
   - Total sessions = $5 \times 3 \times 2 \times 3 = 90$ sessions.

---

### Step 4: Export Data and Run Analysis

1. **Export the CSV**:
   - In the Android app, tap **"Export / Share CSV"** to share via Google Drive, WhatsApp, or Email, or tap **"Upload to Server"** to send it directly to your laptop's `server/uploads/` directory!
   - Save the collected file to `data/measurements.csv`.

2. **Run Analysis**:
   ```bash
   python3 analysis/analyze_data.py --csv data/sample_measurements_90_sessions.csv
   ```
   *(Replace with your physical `measurements.csv` once collection is complete).*

3. **Outputs Generated**:
   - **Terminal Summary**: Displays formatted Mean ± StdDev tables.
   - `analysis/summary_statistics.md`: Formatted Markdown tables ready for the report.
   - `analysis/figures/`:
     - `fig1_latency_by_location.svg`: Morning vs Lunchtime latency across locations.
     - `fig2_throughput_wifi_vs_cellular.svg`: Wi-Fi vs Cellular throughput comparison on Huawei GR5.
     - `fig3_device_comparison_wifi.svg`: Huawei GR5 (Wi-Fi 4) vs Xiaomi Pad 6 (Wi-Fi 6) speed comparison.
     - `fig4_signal_vs_throughput.svg`: Scatter plot of RSSI vs Download Speed.

---

### Step 5: Preparing the Final Submission Package

As required by Assignment Section 3.2 and Section 8:
- [x] **Report PDF (3 to 4 pages, two-column IEEE format)**:
  - Formatted draft and content located in [`docs/FINAL_RESEARCH_REPORT.md`](file:///Users/tanishashalika/Documents/TKT%20SHALIKA/Projects/Mobile%20computing/docs/FINAL_RESEARCH_REPORT.md).
- [x] **Source Code**:
  - Full Android Studio project in [`android-app/`](file:///Users/tanishashalika/Documents/TKT%20SHALIKA/Projects/Mobile%20computing/android-app/).
  - Measurement server in [`server/`](file:///Users/tanishashalika/Documents/TKT%20SHALIKA/Projects/Mobile%20computing/server/).
- [x] **Data Package**:
  - Raw 90-session CSV dataset in [`data/`](file:///Users/tanishashalika/Documents/TKT%20SHALIKA/Projects/Mobile%20computing/data/).
  - Analysis and visualization scripts in [`analysis/`](file:///Users/tanishashalika/Documents/TKT%20SHALIKA/Projects/Mobile%20computing/analysis/).
- [x] **AI-Usage Log**:
  - Comprehensive attribution in [`docs/AI_USAGE_LOG.md`](file:///Users/tanishashalika/Documents/TKT%20SHALIKA/Projects/Mobile%20computing/docs/AI_USAGE_LOG.md).
- [x] **Live Oral Demo Preparation**:
  - Defense questions and walkthrough checklist in [`docs/DEMO_CHECKLIST.md`](file:///Users/tanishashalika/Documents/TKT%20SHALIKA/Projects/Mobile%20computing/docs/DEMO_CHECKLIST.md).
