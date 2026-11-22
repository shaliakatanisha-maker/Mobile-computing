# AI-Usage Log & Source Attribution

**Course**: CS4473 Mobile Computing  
**Project**: Group Research Project (Assignment 2, Option F)  
**Group**: Group 5  
- 220318E – Kavya G.A.C.  
- 220604D – Shalika T.K.T.  

In compliance with Sections 3.2, 4, and 5 of the CS4473 Assignment Specification, this document provides the attribution of AI tools, open-source references, and API documentation used in developing the prototype and analysis pipeline.

---

## 1. AI Tool Usage Log

| Tool | Purpose | Prompts / Queries | How Output Was Used & Modifications |
|---|---|---|---|
| Google Antigravity / Gemini 3.8 Flash | Architecture & Prototype Scaffolding | "Implement CS4473 Option F mobile network measurement app supporting Android 7 to 14, Python measurement server, and data analysis pipeline" | Generated initial Kotlin coroutine templates, HTTP streaming logic in `NetworkProbeManager`, and SVG visualization routines in `analyze_data.py`. The team adapted the network callbacks to handle device-specific vendor quirks on Huawei GR5 and Xiaomi Pad 6, configured cleartext allowances, and designed the UI layout. |
| Google Antigravity / Gemini 3.8 Flash | Statistical Analysis & Math Formulae | "Compute Mean and StdDev across 3 repetitions and verify Mbps throughput formula" | Created standard deviation formulas and grouping logic matching the proposal specifications (`receivedbytes * 8 / (requestduration_sec * 1,000,000)`). The team reviewed and verified the code with sample campus measurements. |
| Google Antigravity / Gemini 3.8 Flash | Research Report Drafting | "Format IEEE 2-column paper structure based on Section 5 marking criteria" | Structured the 4-page academic research paper template, synthesized the mobile computing challenges (attenuation, 802.11 contention, Doze mode), and integrated the calculated metrics. |

---

## 2. Platform Documentation & API References

| Component / Function | Reference / Source | Changes / Customizations |
|---|---|---|
| Wi-Fi RSSI Acquisition (`SignalMonitor.kt`) | Android Developers: `android.net.wifi.WifiManager` & `NetworkCapabilities.getTransportInfo()` | Added dual-version fallback branch: uses `WifiInfo.rssi` directly via `WifiManager` on Android 7 (API 24), and modern `NetworkCapabilities` on Android 14 (API 34). |
| Cellular Signal Strength (`SignalMonitor.kt`) | Android Developers: `android.telephony.PhoneStateListener` & `SignalStrength.getLevel()` | Implemented `PhoneStateListener.LISTEN_SIGNAL_STRENGTHS` and polled `allCellInfo` for registered LTE/WCDMA cells to obtain dBm readings. |
| Monotonic Duration Measurement (`NetworkProbeManager.kt`) | Android Developers: `android.os.SystemClock.elapsedRealtime()` | Used monotonic clock timestamps rather than system wall clock to avoid NTP adjustments or clock drift distorting latency calculations. |
| Non-Caching HTTP Streams (`server.py` & `NetworkProbeManager.kt`) | IETF RFC 7234 (HTTP Caching) & RFC 7230 | Added explicit `Cache-Control: no-cache, no-store, must-revalidate` and `Accept-Encoding: identity` headers to prevent transparent ISP/gateway gzip compression from biasing throughput numbers. |
| File Sharing (`CsvLogger.kt`) | AndroidX `FileProvider` Documentation | Configured `<paths>` and `getUriForFile` to enable instant sharing of the recorded CSV via Android system share sheet. |

---

## 3. Student Responsibility Statement
We confirm that:
1. All physical measurements, real phone tests, campus route trials, and experimental observations submitted in the final data package represent authentic measurements taken by our team members.
2. We thoroughly understand every line of code in the Android application, server, and analysis scripts, and are prepared to explain and demonstrate any component during the live oral interview.
3. No artificial or AI-generated data was substituted for physical experiment trials.
