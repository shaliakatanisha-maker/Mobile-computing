# CS4473 Mobile Network Measurement - Data Package

This directory contains the dataset, schemas, and data dictionary for the research project **"Mobile Network Measurement Along a Campus Route"** (Option F, Group 5).

## Data Files
- `sample_measurements_90_sessions.csv`: Comprehensive 90-session dataset covering all 5 locations (P1–P5), 3 conditions, 2 time windows, and 3 repetition days as promised in the proposal.
- `measurements.csv`: When the Android app is run on physical phones (Huawei GR5 and Xiaomi Pad 6), exported records are appended to this file.

---

## Data Dictionary & Schema

| Column Name | Type | Unit / Format | Description |
|---|---|---|---|
| `session_id` | String | S001 – S090 | Unique identifier for each measurement trial |
| `date` | String | `YYYY-MM-DD` | Date of data collection |
| `day` | String | Day 1, Day 2, Day 3 | Experimental repetition index across different days |
| `time_window` | String | `HH:MM-HH:MM` | Time period: `09:00-10:00` (Morning) or `12:15-13:15` (Lunchtime) |
| `time_category` | String | Morning / Lunchtime | Time category for aggregate analysis |
| `timestamp` | String | ISO-8601 (`YYYY-MM-DDTHH:MM:SS`) | Exact wall-clock time measurement commenced |
| `location_id` | String | P1 – P5 | Campus route waypoint identifier |
| `location_name` | String | Text | Campus location name (e.g. Central Court, Canteen, FYP Lab) |
| `location_type` | String | Text | Environmental setting (Outdoor, Semi-indoor, Indoor Hallway, Lab) |
| `device_model` | String | Text | Hardware model (`Huawei GR5` or `Xiaomi Pad 6`) |
| `android_version` | String | Text | Operating system version (`Android 7.0 (API 24)` or `Android 14 (API 34)`) |
| `intended_connection` | String | Wi-Fi / Cellular | The network interface under evaluation for that session |
| `actual_connection` | String | Wi-Fi / Cellular / Disconnected | The network interface verified active during probe execution |
| `connection_changed` | Boolean | `true` / `false` | Flags if a network handoff or drop occurred during the test |
| `wifi_ssid` | String | Text | Service Set Identifier (e.g. `Campus-WiFi` or `N/A`) |
| `wifi_frequency_mhz` | Integer | MHz | Wi-Fi carrier frequency (e.g. 2412 for 2.4 GHz, 5180 for 5 GHz) |
| `cellular_operator` | String | Text | Mobile network carrier name (e.g. `Dialog 4G`, `Mobitel`) |
| `cellular_tech` | String | Text | Mobile cellular generation (e.g. `LTE`, `HSPA`) |
| `signal_reading_dbm` | Float | dBm | Signal strength: Wi-Fi RSSI or Cellular dBm |
| `signal_level_0_to_4` | Integer | 0 – 4 | Android platform signal quality level (0=Poor to 4=Great) |
| `probe1_rtt_ms` | Float | Milliseconds (ms) | Round-trip response time of Latency Probe 1 |
| `probe2_rtt_ms` | Float | Milliseconds (ms) | Round-trip response time of Latency Probe 2 |
| `probe3_rtt_ms` | Float | Milliseconds (ms) | Round-trip response time of Latency Probe 3 |
| `probe_avg_rtt_ms` | Float | Milliseconds (ms) | Mean RTT across the 3 latency probes |
| `download_bytes` | Integer | Bytes | Number of bytes received during throughput test (nominal: 1,048,576 bytes) |
| `download_duration_ms` | Float | Milliseconds (ms) | Elapsed duration from HTTP request start to last byte read |
| `download_speed_mbps` | Float | Mbps | Short download throughput calculated by: `(bytes * 8) / (duration_sec * 1,000,000)` |
| `outcome` | String | `SUCCESS`, `TIMEOUT`, `FAILED` | Test outcome evaluation |
| `notes` | String | Text | Qualitative observations (e.g. rain, heavy crowd, interference) |

---

## Experimental Ground Truth & Repetition Rigor
- **Repetitions**: As required by Section 4 and 5 of the assignment rubric, 3 separate route repetitions are performed per condition. The three sub-probes in a single session test instantaneous jitter, but do **not** replace the 3 distinct day-to-day route visits.
- **Clock Ground Truth**: All duration measurements use `SystemClock.elapsedRealtime()`, which counts continuously including CPU sleep states and is monotonic (cannot jump backwards like wall clock).
- **File Size Ground Truth**: The server payload is fixed in memory at exactly 1,048,576 bytes (1 MiB), ensuring exact byte counts.
