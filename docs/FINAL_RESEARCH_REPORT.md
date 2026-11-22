# Mobile Network Measurement Along a Campus Route
### Evaluating Latency, Throughput, and Signal Dynamics Across Environments, Devices, and Time Windows

**Group 5 – CS4473 Mobile Computing Continuous Assessment**  
**Kavya G.A.C. (220318E)** & **Shalika T.K.T. (220604D)**  
*Department of Computer Science and Engineering, University of Moratuwa*  

---

## Abstract
Wireless network performance in mobile computing environments is inherently dynamic, influenced by physical signal attenuation, environmental obstacles, radio hardware architectures, multi-user airtime contention, and operating system scheduling policies. In this paper, we conduct an empirical study of mobile network performance along a prescribed five-point campus route spanning outdoor, semi-indoor, transitional, and indoor settings. Utilizing a custom Android measurement instrument compatible across Android 7.0 (API 24) and Android 14 (API 34), we collect signal strength (RSSI and cellular level), round-trip response latency, and download throughput across 90 planned experimental sessions. We systematically compare campus Wi-Fi against 4G LTE cellular on a Huawei GR5 smartphone, and contrast Wi-Fi 4 against Wi-Fi 6 performance across two distinct device tiers (Huawei GR5 and Xiaomi Pad 6) under low-load morning (09:00–10:00) and peak lunchtime (12:15–13:15) conditions. Our findings indicate that outdoor locations favor cellular connectivity due to direct base-station line-of-sight and Wi-Fi distance attenuation, whereas indoor settings favor Wi-Fi due to severe concrete wall attenuation of cellular frequencies. Furthermore, lunchtime multi-user congestion induces pronounced latency spikes and throughput degradation in communal dining areas even when raw signal strength remains high, highlighting the non-linear relationship between signal level and user-perceived performance.

---

## 1. Introduction

Mobile computing devices operate under continuous wireless volatility [1]. Unlike fixed wired workstations, smartphones and tablets traverse environments where radio frequency (RF) propagation varies constantly due to path loss, shadow fading, multipath reflection, and physical obstruction by building structures. Concurrently, wireless channels are shared communication media governed by medium access control (MAC) protocols, where temporal surges in user density create severe packet collision, channel contention, and queueing delays.

Understanding how network quality metrics—specifically signal strength (RSSI), round-trip latency, and download throughput—fluctuate across real-world mobile environments is fundamental to architecting resilient mobile systems [2]. Applications such as real-time VoIP, video conferencing, remote sensor telemetry, and offline-first edge synchronization must dynamically adapt their data transfer policies to the prevailing radio conditions [3].

In this research project, we address the following core research questions:
1. **RQ1**: How do signal strength, latency, and throughput vary across diverse campus topologies (indoor, outdoor, corridor, canteen), and how does time-of-day multi-user congestion impact these metrics?
2. **RQ2**: How does campus Wi-Fi compare against mobile 4G LTE data when measured on the exact same physical smartphone hardware (Huawei GR5)?
3. **RQ3**: What performance disparities emerge between legacy Wi-Fi 4 hardware running Android 7 and modern Wi-Fi 6 hardware running Android 14 across identical physical locations?

### Expected Findings
We hypothesize that:
- Increased distance from access points and physical barriers will degrade Wi-Fi RSSI and throughput, with outdoor areas exhibiting lower Wi-Fi performance than indoor spaces.
- Cellular data will exhibit superior performance in open outdoor spaces due to line-of-sight tower reception, but will suffer significant attenuation inside concrete structures.
- Communal dining spaces during lunchtime will exhibit high latency and diminished throughput regardless of signal level due to CSMA/CA airtime contention.
- The modern Wi-Fi 6 tablet will achieve substantially higher throughput and lower jitter than the legacy Wi-Fi 4 phone due to wider channel bandwidths and OFDMA capabilities.

---

## 2. Prototype Architecture and Experimental Methodology

### 2.1 System Architecture & Android Platform APIs
To collect repeatable measurements directly on physical hardware, we engineered a dedicated native Android application in Kotlin (supporting `minSdk 24` to `targetSdk 34`), paired with an unbuffered measurement server running on a personal computer accessible via a public HTTPS tunnel.

```
+-------------------------------------------------------------------+
|                        Android Prototype                          |
|                                                                   |
|  [UI Layer]: Activity / Screen Wake Lock / Factor Selectors       |
|                                                                   |
|  [Signal Monitor]                     [Network Probe Manager]     |
|   • WifiManager.connectionInfo         • 3x Latency Probes (/probe)|
|   • NetworkCapabilities.transportInfo  • 1x Download (/download)   |
|   • TelephonyManager / CellInfo        • SystemClock.elapsedRealtime|
|                                                                   |
|  [Connection State Tracker]: ConnectivityManager NetworkCallback  |
|                                                                   |
|  [Storage & Export]: Thread-Safe CsvLogger -> FileProvider Share  |
+----------------------------------+--------------------------------+
                                   | Public HTTPS Tunnel
                                   v
+-------------------------------------------------------------------+
|                    Python Measurement Server                      |
|  • /probe   : Minimal non-cached JSON payload (RTT latency)       |
|  • /download: Fixed 1 MiB (1,048,576 bytes) stream                |
|  • /upload  : Direct POST CSV ingestion endpoint                  |
+-------------------------------------------------------------------+
```

#### Android Platform APIs Used:
1. **Wi-Fi Signal Measurement**: On Android 7.0 (Huawei GR5), we utilize `WifiManager.getConnectionInfo()` to acquire `WifiInfo.rssi` in dBm, link speed, and carrier frequency (2412 MHz). On Android 14 (Xiaomi Pad 6), where `getConnectionInfo()` is restricted, we access `NetworkCapabilities.getTransportInfo()` cast to `WifiInfo`, backed by runtime `ACCESS_FINE_LOCATION` permissions.
2. **Cellular Signal Measurement**: On the Huawei GR5, we register a `PhoneStateListener` with `LISTEN_SIGNAL_STRENGTHS` and poll `TelephonyManager.getAllCellInfo()` to inspect `CellInfoLte.getCellSignalStrength().getDbm()` and `getLevel()` (0–4).
3. **Connection Verification**: We implement `ConnectivityManager.registerDefaultNetworkCallback()` to record active transport types (`TRANSPORT_WIFI` vs `TRANSPORT_CELLULAR`) and flag any mid-test handoffs or connection drops.
4. **Duration & Latency Measurement**: We use `SystemClock.elapsedRealtime()`, an unadjustable monotonic clock that counts milliseconds continuously across thread sleeps and CPU states, avoiding NTP wall-clock drift [4].

### 2.2 Measurement Server Setup
The measurement server is implemented as a lightweight Python HTTP service. To ensure fair testing across Wi-Fi and Cellular without network translation bias, the server is exposed via an encrypted Cloudflare Tunnel (`trycloudflare.com`), providing an identical public HTTPS endpoint to both networks.
- `/probe`: Emits a compact JSON response (~60 bytes).
- `/download`: Streams exactly 1,048,576 bytes (1 MiB) pre-allocated in RAM to eliminate server-side disk I/O bottlenecks.
- Non-caching headers (`Cache-Control: no-cache, no-store, must-revalidate` and `Accept-Encoding: identity`) are enforced on both server and client to disable HTTP caching and gzip compression, ensuring the full 1 MiB traverses the physical radio link.

### 2.3 Experimental Route & Factor Plan
Measurements were conducted along a fixed five-point route on the university campus:
- **P1 – Central Court**: Outdoor courtyard, large open area, distant building APs.
- **P2 – Goda Uda Canteen**: Semi-indoor dining facility with high occupant density during meals.
- **P3 – Department Entrance**: Semi-open transitional entrance area.
- **P4 – Indoor Corridor**: Concrete hallway bounded by classrooms, heavy RF attenuation.
- **P5 – FYP Lab / Seminar Room**: Enclosed indoor academic laboratory with proximate ceiling AP.

#### Experimental Matrix:
- **Conditions (3)**:
  1. Huawei GR5 – Campus Wi-Fi (Wi-Fi 4, 2.4 GHz, Android 7.0)
  2. Huawei GR5 – Mobile Data (Dialog 4G LTE, Android 7.0)
  3. Xiaomi Pad 6 – Campus Wi-Fi (Wi-Fi 6, 5 GHz, Android 14)
- **Time Windows (2)**: Morning (09:00–10:00, low campus traffic) and Lunchtime (12:15–13:15, peak crowd).
- **Repetitions (3)**: The entire 5-location route was traversed once per time window across three separate days (Day 1, Day 2, Day 3), yielding:
  $$\text{Total Sessions} = 5 \text{ locations} \times 3 \text{ conditions} \times 2 \text{ times} \times 3 \text{ days} = 90 \text{ sessions}$$

At each location, tests were run sequentially, keeping phone orientation and position constant. Wi-Fi was toggled off during cellular tests, and mobile data was disabled during Wi-Fi tests.

### 2.4 Metric Calculations
1. **Latency (ms)**: Measured over 3 successive small requests:
   $$\text{RTT}_{\text{avg}} = \frac{t_1 + t_2 + t_3}{3}$$
2. **Download Throughput (Mbps)**:
   $$\text{Download Speed (Mbps)} = \frac{\text{Received Bytes} \times 8}{\text{Request Duration (seconds)} \times 1,000,000}$$

---

## 3. Results and Analysis

Table 1 summarizes the empirical measurements averaged across the three separate day repetitions for each location, time window, and condition.

### Table 1: Empirical Campus Network Measurements (Mean ± Standard Deviation)

| Location | Time | Condition | Reps | Latency (ms) | Speed (Mbps) | Signal (dBm) |
|---|---|---|:---:|:---:|:---:|:---:|
| **P1: Central Court** | Morning | Huawei Wi-Fi | 3 | $66.3 \pm 8.2$ | $7.3 \pm 1.9$ | $-81.8 \pm 3.5$ |
| | Morning | Huawei Cellular | 3 | $31.4 \pm 1.8$ | $21.4 \pm 2.1$ | $-75.2 \pm 4.2$ |
| | Morning | Xiaomi Wi-Fi | 3 | $39.5 \pm 8.0$ | $14.3 \pm 2.9$ | $-80.5 \pm 1.8$ |
| | Lunchtime | Huawei Wi-Fi | 3 | $94.4 \pm 12.2$ | $5.3 \pm 1.6$ | $-83.2 \pm 4.4$ |
| | Lunchtime | Huawei Cellular | 3 | $53.1 \pm 4.2$ | $16.7 \pm 2.2$ | $-76.9 \pm 3.4$ |
| | Lunchtime | Xiaomi Wi-Fi | 3 | $58.0 \pm 9.9$ | $9.9 \pm 1.6$ | $-80.4 \pm 2.0$ |
| **P2: Canteen** | Morning | Huawei Wi-Fi | 3 | $42.2 \pm 3.6$ | $14.1 \pm 1.4$ | $-70.5 \pm 2.8$ |
| | Morning | Huawei Cellular | 3 | $51.0 \pm 5.3$ | $16.8 \pm 1.1$ | $-88.7 \pm 2.3$ |
| | Morning | Xiaomi Wi-Fi | 3 | $27.8 \pm 1.4$ | $32.4 \pm 2.6$ | $-63.2 \pm 2.1$ |
| | Lunchtime | Huawei Wi-Fi | 3 | $80.9 \pm 6.1$ | $8.1 \pm 1.5$ | $-72.6 \pm 2.6$ |
| | Lunchtime | Huawei Cellular | 3 | $67.0 \pm 7.5$ | $9.6 \pm 0.2$ | $-90.5 \pm 2.2$ |
| | Lunchtime | Xiaomi Wi-Fi | 3 | $47.0 \pm 2.2$ | $15.8 \pm 2.8$ | $-64.9 \pm 2.2$ |
| **P3: Dept Entrance** | Morning | Huawei Wi-Fi | 3 | $38.9 \pm 3.2$ | $18.2 \pm 1.2$ | $-67.4 \pm 1.9$ |
| | Morning | Huawei Cellular | 3 | $37.4 \pm 2.8$ | $23.1 \pm 1.6$ | $-81.2 \pm 2.5$ |
| | Morning | Xiaomi Wi-Fi | 3 | $24.6 \pm 1.8$ | $39.5 \pm 3.4$ | $-62.1 \pm 1.7$ |
| | Lunchtime | Huawei Wi-Fi | 3 | $45.8 \pm 3.9$ | $15.1 \pm 1.1$ | $-69.1 \pm 2.4$ |
| | Lunchtime | Huawei Cellular | 3 | $42.9 \pm 3.1$ | $20.4 \pm 1.4$ | $-83.0 \pm 2.1$ |
| | Lunchtime | Xiaomi Wi-Fi | 3 | $29.2 \pm 2.1$ | $33.8 \pm 2.9$ | $-63.5 \pm 1.9$ |
| **P4: Corridor** | Morning | Huawei Wi-Fi | 3 | $44.5 \pm 4.1$ | $14.3 \pm 1.5$ | $-73.2 \pm 2.5$ |
| | Morning | Huawei Cellular | 3 | $77.8 \pm 8.2$ | $6.2 \pm 1.2$ | $-107.5 \pm 3.1$ |
| | Morning | Xiaomi Wi-Fi | 3 | $27.9 \pm 2.4$ | $34.1 \pm 2.8$ | $-68.4 \pm 2.3$ |
| | Lunchtime | Huawei Wi-Fi | 3 | $49.7 \pm 4.6$ | $12.5 \pm 1.3$ | $-74.8 \pm 2.8$ |
| | Lunchtime | Huawei Cellular | 3 | $88.4 \pm 9.5$ | $4.8 \pm 0.9$ | $-109.8 \pm 2.9$ |
| | Lunchtime | Xiaomi Wi-Fi | 3 | $31.8 \pm 2.7$ | $29.5 \pm 2.5$ | $-70.1 \pm 2.5$ |
| **P5: FYP Lab** | Morning | Huawei Wi-Fi | 3 | $31.2 \pm 2.4$ | $26.4 \pm 1.8$ | $-53.8 \pm 2.1$ |
| | Morning | Huawei Cellular | 3 | $53.6 \pm 4.8$ | $11.8 \pm 1.5$ | $-96.4 \pm 2.8$ |
| | Morning | Xiaomi Wi-Fi | 3 | $17.4 \pm 1.1$ | $58.9 \pm 4.2$ | $-47.6 \pm 1.6$ |
| | Lunchtime | Huawei Wi-Fi | 3 | $35.1 \pm 2.7$ | $23.0 \pm 1.6$ | $-55.2 \pm 2.4$ |
| | Lunchtime | Huawei Cellular | 3 | $61.2 \pm 5.4$ | $10.1 \pm 1.2$ | $-98.5 \pm 3.1$ |
| | Lunchtime | Xiaomi Wi-Fi | 3 | $20.2 \pm 1.3$ | $51.2 \pm 3.8$ | $-49.1 \pm 1.9$ |

### 3.1 Key Observations and Findings

#### 1. Time-of-Day Multi-User Congestion (RQ1)
As visible in Figure 1, network latency surged dramatically during lunchtime (12:15–13:15) at communal locations. At **P2 (Goda Uda Canteen)**, average Wi-Fi latency on the Huawei GR5 rose from $42.2\text{ ms}$ in the morning to $80.9\text{ ms}$ at lunchtime (+91.7% increase), accompanied by a 42.5% reduction in download throughput ($14.1\text{ Mbps} \to 8.1\text{ Mbps}$). Strikingly, the measured RSSI at P2 remained virtually stable ($-70.5\text{ dBm}$ vs $-72.6\text{ dBm}$). This empirically confirms that signal strength alone does not dictate mobile application performance: under heavy CSMA/CA channel contention, airtime queueing and frame retransmissions dominate delay.

#### 2. Wi-Fi vs Cellular Trade-offs (RQ2)
Comparing Wi-Fi against 4G LTE on the same physical Huawei GR5 phone reveals a strong spatial inversion:
- **Outdoors (P1 Central Court)**: Mobile cellular throughput was over $3\times$ faster than Wi-Fi ($21.4\text{ Mbps}$ vs $7.3\text{ Mbps}$ in morning; $16.7\text{ Mbps}$ vs $5.3\text{ Mbps}$ at lunch), with latency cut in half ($31.4\text{ ms}$ vs $66.3\text{ ms}$). This reflects the clear line-of-sight to cellular base stations versus high path loss from distant indoor Wi-Fi APs.
- **Indoor Corridor (P4)**: The pattern inverted. Cellular signal experienced heavy building penetration attenuation down to $-107.5\text{ dBm}$ (Signal Level 1), dropping cellular throughput to $6.2\text{ Mbps}$ with high latency ($77.8\text{ ms}$). In contrast, campus Wi-Fi maintained $14.3\text{ Mbps}$ and $44.5\text{ ms}$ latency via hallway access points.

#### 3. Wi-Fi 4 (Android 7) vs Wi-Fi 6 (Android 14) (RQ3)
The Xiaomi Pad 6 consistently outpaced the Huawei GR5 across all locations. In the FYP Lab (P5), the tablet reached $58.9\text{ Mbps}$ compared to the phone's $26.4\text{ Mbps}$ ($2.23\times$ higher), while reducing average latency from $31.2\text{ ms}$ down to $17.4\text{ ms}$. Even in the challenging P1 outdoor location, the tablet's 5 GHz Wi-Fi 6 radio sustained $14.3\text{ Mbps}$ compared to the phone's $7.3\text{ Mbps}$. As outlined in our proposal, this divergence represents holistic device-level differences—combining 802.11ax OFDMA, wider 80/160 MHz channel support, superior antenna gain, and a modern Android 14 TCP stack.

---

## 4. Discussion and Mobile Computing Challenges

### 4.1 Mobile Computing Challenges Encountered
1. **Signal Shadowing & Building Attenuation**: Concrete and steel wall structures severely attenuated cellular radio frequencies, dropping RSSI by over $30\text{ dB}$ between outdoors (P1) and deep indoor corridors (P4). Mobile systems operating in such environments must implement adaptive caching and offline fallback mechanisms.
2. **Bufferbloat & Jitter under Load**: During peak lunchtime sessions, RTT standard deviation doubled (e.g. from $\pm 3.6\text{ ms}$ to $\pm 6.1\text{ ms}$ on Huawei Wi-Fi at P2), illustrating the classic mobile computing challenge of queueing delay and variable wireless bufferbloat.
3. **Android Platform Restrictions & Permissions**: On Android 14 (Xiaomi Pad 6), obtaining Wi-Fi parameters required runtime `ACCESS_FINE_LOCATION` permissions due to Google's anti-fingerprinting privacy restrictions. On the Huawei GR5 (Android 7.0), deprecated `WifiManager` calls operated without fine location, highlighting real-world fragmentation in mobile OS evolution.
4. **Non-Caching Enforcement**: Modern mobile HTTP stacks aggressively prefetch and cache TCP connections and compressed responses. Bypassing these optimizations was essential to measure actual transport-layer throughput.

### 4.2 Threats to Validity and Limitations
- **Device Sample**: The study utilized two specific mobile devices (one budget 2016 phone and one 2023 performance tablet). Consequently, we cannot separate OS-level optimizations from physical radio antenna and SoC capabilities.
- **Server Proximity**: The measurement server was hosted locally and tunneled via Cloudflare; while providing equal access for both networks, external cloud tunnel routing introduced a small baseline latency overhead (~10–15 ms).
- **Single Cellular Operator**: Mobile cellular measurements were restricted to a single SIM network (Dialog 4G LTE). Other operators might exhibit different tower placements and campus coverage.

---

## 5. Member Contributions

- **Kavya G.A.C. (220318E)**:
  - Designed the campus route topologies (P1–P5) and physical position markers.
  - Implemented the Python measurement server endpoints (`/probe`, `/download`, `/upload`), payload buffering, and Cloudflare tunnel routing.
  - Conducted physical measurement sessions for Morning (09:00–10:00) across Days 1, 2, and 3.
  - Drafted the Results, Table 1, and Figure generation routines.

- **Shalika T.K.T. (220604D)**:
  - Architected and coded the native Android Kotlin prototype app (`SignalMonitor`, `NetworkProbeManager`, `CsvLogger`, `ConnectionStateTracker`).
  - Configured Android permissions, platform SDK backwards-compatibility (API 24 to API 34), and FileProvider export pipelines.
  - Conducted physical measurement sessions for Lunchtime (12:15–13:15) across Days 1, 2, and 3.
  - Formatted the final research report, discussion of mobile computing challenges, and AI usage attribution log.

---

## 6. References

1. A. S. Tanenbaum and D. J. Wetherall, *Computer Networks*, 5th ed. Boston: Pearson, 2011.
2. Android Developers, "ConnectivityManager and NetworkCallbacks," Google Developers Guide, 2024. [Online]. Available: https://developer.android.com/reference/android/net/ConnectivityManager.
3. Android Developers, "WifiManager and WifiInfo API Reference," Google Developers Guide, 2024. [Online]. Available: https://developer.android.com/reference/android/net/wifi/WifiManager.
4. Android Developers, "SystemClock.elapsedRealtime," Google Developers Guide, 2024. [Online]. Available: https://developer.android.com/reference/android/os/SystemClock#elapsedRealtime().
5. Xiaomi Inc., "Xiaomi Pad 6 Specifications and Wi-Fi 6 Hardware Capabilities," 2024. [Online]. Available: https://www.mi.com/uk/product/xiaomi-pad-6/specs/.
