#!/usr/bin/env python3
"""
CS4473 Mobile Computing - Sample Dataset Generator for Option F Proposal
Generates realistic measurement data for the 90 planned experimental sessions:
  5 Locations:
    - P1: Central Court (Outdoor)
    - P2: Goda Uda Canteen (Crowded / Semi-indoor)
    - P3: Department Entrance (Transitional)
    - P4: Indoor Corridor (Indoor hallway, walls)
    - P5: FYP lab/Seminar Room (Indoor dedicated AP)
  3 Conditions:
    - Huawei Wi-Fi (Wi-Fi 4, 2.4 GHz)
    - Huawei Mobile Data (4G LTE)
    - Xiaomi Wi-Fi (Wi-Fi 6, 5 GHz)
  2 Time Windows:
    - Morning (09:00 - 10:00)
    - Lunchtime (12:15 - 13:15)
  3 Days (Repetitions): Day 1, Day 2, Day 3
Total sessions: 5 x 3 x 2 x 3 = 90 sessions.

Incorporates realistic mobile computing phenomena:
  - Lunchtime congestion increases RTT and drops throughput in canteen and central court
  - Wi-Fi 6 (Xiaomi Pad 6) achieves higher throughput and lower jitter than Wi-Fi 4 (Huawei GR5)
  - Cellular performance is strong outdoors (P1) but attenuates through thick walls in corridor (P4)
  - Calculation formula: Download speed (Mbps) = (bytes * 8) / (duration_seconds * 1,000,000)
"""

import csv
import random
import os
from datetime import datetime, timedelta

ONE_MIB_BYTES = 1048576

LOCATIONS = [
    ("P1", "Central Court", "Outdoor"),
    ("P2", "Goda Uda Canteen", "Semi-indoor / Dining"),
    ("P3", "Department Entrance", "Transitional"),
    ("P4", "Indoor Corridor", "Indoor Hallway"),
    ("P5", "FYP lab/Seminar Room", "Indoor Lab")
]

CONDITIONS = [
    ("Huawei GR5", "Android 7.0 (API 24)", "Wi-Fi", "Wi-Fi"),
    ("Huawei GR5", "Android 7.0 (API 24)", "Cellular", "Cellular"),
    ("Xiaomi Pad 6", "Android 14 (API 34)", "Wi-Fi", "Wi-Fi")
]

TIME_WINDOWS = [
    ("Morning", "09:00-10:00", 9, 15),
    ("Lunchtime", "12:15-13:15", 12, 30)
]

DAYS = ["Day 1", "Day 2", "Day 3"]
BASE_DATE = datetime(2026, 11, 23)

def generate_session_data():
    random.seed(42)  # For reproducible, physically consistent dataset
    rows = []
    session_num = 1

    for day_idx, day_name in enumerate(DAYS):
        session_date = BASE_DATE + timedelta(days=day_idx)
        
        for time_name, time_str, hour_base, min_base in TIME_WINDOWS:
            # Vary order of testing conditions across days as promised in proposal section 5
            conditions_order = list(CONDITIONS)
            if day_idx == 1:
                conditions_order.reverse()
            elif day_idx == 2:
                conditions_order = [conditions_order[1], conditions_order[2], conditions_order[0]]

            for loc_id, loc_name, loc_type in LOCATIONS:
                for device, android_ver, intended_conn, actual_conn in conditions_order:
                    session_id = f"S{session_num:03d}"
                    session_num += 1

                    # Timestamp within the window
                    minute_offset = random.randint(0, 40)
                    second_offset = random.randint(0, 59)
                    record_time = session_date.replace(hour=hour_base, minute=min_base) + timedelta(minutes=minute_offset, seconds=second_offset)
                    timestamp_iso = record_time.strftime("%Y-%m-%dT%H:%M:%S")
                    date_str = session_date.strftime("%Y-%m-%d")

                    # Congestion factor: Lunchtime in Canteen (P2) and Central Court (P1) has higher crowd
                    is_lunch = (time_name == "Lunchtime")
                    crowd_multiplier = 1.0
                    if is_lunch and loc_id in ("P1", "P2"):
                        crowd_multiplier = 1.45
                    elif is_lunch:
                        crowd_multiplier = 1.15

                    wifi_ssid = "Campus-WiFi" if intended_conn == "Wi-Fi" else "N/A"
                    cell_operator = "Dialog 4G" if intended_conn == "Cellular" else "N/A"
                    cell_tech = "LTE" if intended_conn == "Cellular" else "N/A"

                    # Physical characteristics based on location and connection type
                    if intended_conn == "Wi-Fi":
                        if device == "Xiaomi Pad 6":
                            # Wi-Fi 6 (802.11ax), 5 GHz band
                            wifi_freq = 5180
                            if loc_id == "P5":  # Right next to lab AP
                                rssi = random.uniform(-44, -52)
                                base_latency = random.uniform(14, 20)
                                base_speed_mbps = random.uniform(48, 68)
                            elif loc_id == "P4":  # Corridor with wall attenuation
                                rssi = random.uniform(-65, -74)
                                base_latency = random.uniform(22, 32)
                                base_speed_mbps = random.uniform(28, 42)
                            elif loc_id == "P3":  # Entrance
                                rssi = random.uniform(-58, -66)
                                base_latency = random.uniform(18, 26)
                                base_speed_mbps = random.uniform(34, 48)
                            elif loc_id == "P2":  # Canteen (semi-indoor, AP nearby but crowded)
                                rssi = random.uniform(-62, -72)
                                base_latency = random.uniform(25, 40)
                                base_speed_mbps = random.uniform(22, 36)
                            else:  # P1 Central court (outdoor, far from AP)
                                rssi = random.uniform(-76, -85)
                                base_latency = random.uniform(32, 50)
                                base_speed_mbps = random.uniform(12, 22)
                        else:
                            # Huawei GR5 - Wi-Fi 4 (802.11n), 2.4 GHz band
                            wifi_freq = 2412
                            if loc_id == "P5":
                                rssi = random.uniform(-50, -58)
                                base_latency = random.uniform(26, 36)
                                base_speed_mbps = random.uniform(22, 32)
                            elif loc_id == "P4":
                                rssi = random.uniform(-70, -79)
                                base_latency = random.uniform(35, 52)
                                base_speed_mbps = random.uniform(11, 18)
                            elif loc_id == "P3":
                                rssi = random.uniform(-64, -72)
                                base_latency = random.uniform(30, 44)
                                base_speed_mbps = random.uniform(15, 24)
                            elif loc_id == "P2":
                                rssi = random.uniform(-68, -78)
                                base_latency = random.uniform(38, 60)
                                base_speed_mbps = random.uniform(9, 16)
                            else:  # P1 Outdoor
                                rssi = random.uniform(-79, -88)
                                base_latency = random.uniform(46, 75)
                                base_speed_mbps = random.uniform(5, 12)

                        # Signal level 0-4 mapping for Wi-Fi
                        if rssi >= -55:
                            sig_level = 4
                        elif rssi >= -65:
                            sig_level = 3
                        elif rssi >= -75:
                            sig_level = 2
                        elif rssi >= -85:
                            sig_level = 1
                        else:
                            sig_level = 0
                        signal_reading = round(rssi, 1)

                    else:
                        # Huawei Mobile Data (4G LTE)
                        wifi_freq = -1
                        if loc_id == "P1":  # Outdoor, clear line-of-sight to cell tower
                            cell_dbm = random.uniform(-72, -80)
                            sig_level = 4
                            base_latency = random.uniform(28, 40)
                            base_speed_mbps = random.uniform(24, 38)
                        elif loc_id == "P3":  # Entrance, open
                            cell_dbm = random.uniform(-78, -86)
                            sig_level = 3
                            base_latency = random.uniform(32, 45)
                            base_speed_mbps = random.uniform(18, 28)
                        elif loc_id == "P2":  # Canteen, moderate signal
                            cell_dbm = random.uniform(-84, -94)
                            sig_level = 3 if cell_dbm > -90 else 2
                            base_latency = random.uniform(38, 55)
                            base_speed_mbps = random.uniform(14, 22)
                        elif loc_id == "P5":  # Lab indoor
                            cell_dbm = random.uniform(-92, -102)
                            sig_level = 2
                            base_latency = random.uniform(45, 65)
                            base_speed_mbps = random.uniform(8, 16)
                        else:  # P4 Corridor deep inside concrete building
                            cell_dbm = random.uniform(-104, -114)
                            sig_level = 1
                            base_latency = random.uniform(60, 95)
                            base_speed_mbps = random.uniform(3.5, 8.5)
                        signal_reading = round(cell_dbm, 1)

                    # Apply crowd congestion effect to latency and speed
                    effective_latency = base_latency * crowd_multiplier
                    effective_speed = max(1.5, base_speed_mbps / crowd_multiplier)

                    # Three small probe requests (latency in ms)
                    p1 = max(5.0, round(effective_latency + random.uniform(-4.0, 5.0), 2))
                    p2 = max(5.0, round(effective_latency + random.uniform(-3.5, 4.5), 2))
                    p3 = max(5.0, round(effective_latency + random.uniform(-3.0, 6.0), 2))
                    probe_avg = round((p1 + p2 + p3) / 3.0, 2)

                    # 1 MiB Download calculation
                    # Download speed (Mbps) = (receivedbytes * 8) / (requestduration(seconds) * 1,000,000)
                    # Therefore: requestduration(seconds) = (receivedbytes * 8) / (speed_mbps * 1,000,000)
                    download_bytes = ONE_MIB_BYTES
                    req_duration_sec = (download_bytes * 8.0) / (effective_speed * 1_000_000.0)
                    # Add small TCP slow-start overhead
                    req_duration_sec += random.uniform(0.04, 0.12)
                    req_duration_ms = round(req_duration_sec * 1000.0, 2)
                    actual_speed_mbps = round((download_bytes * 8.0) / (req_duration_sec * 1_000_000.0), 3)

                    outcome = "SUCCESS"
                    notes = ""
                    if is_lunch and loc_id == "P2":
                        notes = "Heavy lunch crowd in canteen; high channel contention"
                    elif loc_id == "P4" and intended_conn == "Cellular":
                        notes = "Indoor corridor; heavy wall attenuation on cellular"

                    rows.append({
                        "session_id": session_id,
                        "date": date_str,
                        "day": day_name,
                        "time_window": time_str,
                        "time_category": time_name,
                        "timestamp": timestamp_iso,
                        "location_id": loc_id,
                        "location_name": loc_name,
                        "location_type": loc_type,
                        "device_model": device,
                        "android_version": android_ver,
                        "intended_connection": intended_conn,
                        "actual_connection": actual_conn,
                        "connection_changed": "false",
                        "wifi_ssid": wifi_ssid,
                        "wifi_frequency_mhz": wifi_freq,
                        "cellular_operator": cell_operator,
                        "cellular_tech": cell_tech,
                        "signal_reading_dbm": signal_reading,
                        "signal_level_0_to_4": sig_level,
                        "probe1_rtt_ms": p1,
                        "probe2_rtt_ms": p2,
                        "probe3_rtt_ms": p3,
                        "probe_avg_rtt_ms": probe_avg,
                        "download_bytes": download_bytes,
                        "download_duration_ms": req_duration_ms,
                        "download_speed_mbps": actual_speed_mbps,
                        "outcome": outcome,
                        "notes": notes
                    })

    return rows

def write_csv(filename, rows):
    if not rows:
        return
    fieldnames = list(rows[0].keys())
    os.makedirs(os.path.dirname(filename), exist_ok=True)
    with open(filename, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)
    print(f"Generated {len(rows)} sessions successfully saved to: {filename}")

if __name__ == "__main__":
    data_rows = generate_session_data()
    write_csv("data/sample_measurements_90_sessions.csv", data_rows)
