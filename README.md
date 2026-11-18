# RoadEye Nexus — ADAS Mobile Telemetry & OBD-II Bridge

**RoadEye Nexus** is an Android Kotlin and Jetpack Compose telemetry cockpit for the **RoadEye V3 ADAS** platform (Android `minSdk 26`, `targetSdk 36`). It fuses smartphone inertial sensors, 5 Hz barometric pressure, `AudioRecord` ambient road-noise RMS, GNSS positioning, and Bluetooth ELM327 OBD-II powertrain data into checksummed UDP datagrams (`5005`), live animated visualizations, and end-of-session trip analytics.

---

## Key Capabilities

- **Splash Launch & Edge-to-Edge Material 3 UI**:
  - Branded `androidx.core.splashscreen` (`Theme.RoadEyeNexus.Starting` + `installSplashScreen()`), `enableEdgeToEdge()`, safe-area insets, custom dark OLED cockpit theme, and bundled `Chakra Petch` / `JetBrains Mono` / `Inter` typography.
- **High-Frequency Sensor Suite (`SensorTelemetryManager`, `SensorTelemetryData`)**:
  - Captures Accelerometer ($m/s^2$, $\alpha=0.8$ LPF), Gyroscope ($rad/s$), Magnetometer ($\mu T$), Gravity, Linear Acceleration, and Rotation Vector at `SENSOR_DELAY_FASTEST` / `SENSOR_DELAY_GAME`.
  - Samples Barometer (`Sensor.TYPE_PRESSURE` at **5 Hz**, `200 ms`) for atmospheric pressure (`atmosphericPressureHpa`) and relative altitude gradient tracking, plus Ambient Light (`TYPE_LIGHT`) and Proximity (`TYPE_PROXIMITY`).
  - Integrates `AudioRecord` PCM16 capture (in-memory only, no raw audio stored) to compute real-time ambient road-noise RMS (`audioRmsDbfs`), peak amplitude, and zero-crossing rate (`AudioTelemetryManager`).
  - Captures GNSS coordinates (`lat`, `lon`, `alt`), speed, bearing, and horizontal accuracy via `FusedLocationProviderClient` (`500 ms` interval), with lifecycle-aware start/stop in `RoadEyeViewModel`.
- **Interactive Animated Telemetry Dashboard**:
  - Smooth `AnimatedContent` slide-and-fade transitions across **Cockpit**, **Race HUD**, and **Setup** tabs.
  - `rememberInfiniteTransition` pulsing live beacons on UDP `5005`, GNSS fix, and OBD-II connection badges, alongside spring-animated (`animateFloatAsState`) numeric readouts.
  - Custom `Canvas` **2D IMU G-Force Radar Plot** with concentric G-rings, lateral/longitudinal crosshairs, peak-hold vector, and spring physics, paired with rolling **vibration RMS sparklines** and attitude horizon scopes.
  - **Stream Health & Responsive Status Bar**: Live channel tiles (`IMU`, `GNSS`, `OBD2`, `UDP TX`), throughput progress bar, width-aware **auto-collapsing overflow drawer** (`+N` / `LESS`), and **horizontal drag gesture** scrolling (`Modifier.draggable` + `horizontalScroll`) for inspecting extra telemetry pills (`TEMP`, `WIFI`, `BARO`, `MIC`, `ROAD`, `LOG`) on compact screens.
- **Road Condition Intelligence (`RoadConditionAnalyzer`)**:
  - Combines windowed linear acceleration RMS, gyroscope rate, GNSS speed gating, and ambient road noise floor into a `0..100` road `condition_score`, `surface_class` (`SMOOTH`, `MODERATE`, `ROUGH`, `IMPACT_ANOMALY`), and impact detection.
- **ELM327 OBD-II Bluetooth Engine (`Obd2BluetoothManager`)**:
  - Raw SPP RFCOMM (`00001101-0000-1000-8000-00805F9B34FB`) AT/PID engine with automatic exponential-backoff reconnect (`1s` to `15s`) and Mode 01 / Mode 03 (`DTC`) polling.
- **Async UDP Telemetry Bridge (`UdpTelemetryClient`, `UdpTelemetryStreamer`)**:
  - Coroutine-managed `DatagramSocket` sender (`UdpTelemetryClient`) plus monotonic `System.nanoTime()` `10 / 20 / 50 Hz` streamer (`UdpTelemetryStreamer`) backed by `TelemetryForegroundService` (`dataSync`), thermal throttling protection, and `SensorTelemetryJsonSerializer`.
- **End-of-Session Trip Statistics, DataStore Persistence & JSON Export**:
  - `TripStatisticsService` computes trip duration, average/max speed, estimated distance, max altitude change / elevation gain, road-noise exposure (`dB·s` & dosage index), and powertrain peaks at the end of every streaming session, displaying them in a scrollable **`TripSummaryOverlay`**.
  - `TripStatisticsExportRepository` persists completed session statistics and trip history in **Jetpack DataStore** and serializes shareable `.json` reports via `FileProvider` (`Intent.ACTION_SEND`), alongside `.jsonl` raw frame logs (`SessionRecorder`).

---

## Architecture Overview

| Layer | Components |
| --- | --- |
| **UI & Visualizations** | `MainActivity`, `CockpitComponents`, `CockpitTelemetryVisuals`, `CockpitV2Cards`, `CockpitSecondaryScreens`, `TripSummaryOverlay` |
| **State & Lifecycle** | `RoadEyeViewModel` (`DefaultLifecycleObserver`), `TelemetryState`, `TelemetrySessionController` |
| **Sensors & Diagnostics** | `SensorTelemetryManager`, `SensorTelemetryData`, `AudioTelemetryManager`, `DeviceDiagnosticsManager`, `RoadConditionAnalyzer`, `Obd2BluetoothManager` |
| **Network, Stats & Storage** | `UdpTelemetryClient`, `UdpTelemetryStreamer`, `TelemetryForegroundService`, `TripStatisticsService`, `TripStatisticsExportRepository`, `TelemetryPreferencesRepository`, `SessionRecorder` |

---

## UDP JSON Payload Schema (Default Port `5005`)

Each UDP datagram sent by `UdpTelemetryStreamer` is a single UTF-8 JSON object (`SensorTelemetryData` also supports compact single-packet serialization via `SensorTelemetryJsonSerializer`):

```json
{
  "device_id": "RoadEye_Nexus_Node01",
  "seq": 1024,
  "timestamp": 1731800000.123,
  "stream_hz": 20,
  "thermal_status": "NOMINAL",
  "sensors": {
    "accel": { "x": 0.12, "y": 9.81, "z": -0.45 },
    "gyro": { "x": 0.01, "y": -0.02, "z": 0.05 },
    "mag": { "x": 25.1, "y": -12.3, "z": 45.0 },
    "gravity": { "x": 0.05, "y": 9.80, "z": 0.11 },
    "linear_accel": { "x": 0.22, "y": -0.14, "z": 0.85 },
    "rotation_vector": { "x": 0.012, "y": -0.008, "z": 0.707, "w": 0.707 },
    "orientation": { "pitch_deg": 1.5, "roll_deg": -0.8, "yaw_deg": 182.4 },
    "barometer": { "pressure_hpa": 910.25, "rel_altitude_m": 895.4 },
    "ambient_light": { "lux": 1240.5, "condition": "DAYLIGHT" },
    "proximity": { "distance_cm": 5.0, "is_near": false },
    "audio": { "active": true, "rms_dbfs": -34.2, "peak_amp": 0.125, "zcr_hz": 420.5 },
    "gps": {
      "lat": 13.134,
      "lng": 77.962,
      "altitude_m": 920.0,
      "speed_mps": 14.5,
      "bearing": 182.4,
      "accuracy_m": 4.2,
      "has_fix": true
    },
    "availability": {
      "accel": true,
      "gyro": true,
      "mag": true,
      "gravity": true,
      "linear_accel": true,
      "rotation_vector": true,
      "barometer": true,
      "light": true,
      "proximity": true,
      "audio": true
    }
  },
  "device_health": {
    "battery_pct": 82,
    "battery_temp_c": 33.4,
    "charging": true,
    "wifi_rssi_dbm": -54,
    "wifi_link_mbps": 433
  },
  "road_intelligence": {
    "condition_score": 88,
    "surface_class": "SMOOTH",
    "vibration_rms": 0.885,
    "impact_detected": false
  },
  "obd2": {
    "connected": true,
    "protocol": "ISO 15765-4 (CAN 11/500)",
    "speed_kmh": 52,
    "rpm": 2400,
    "throttle_pos": 18.5,
    "coolant_temp_c": 88,
    "engine_load": 34.2,
    "intake_temp_c": 29,
    "maf_gps": 14.25,
    "timing_advance_deg": 12.5,
    "fuel_level_pct": 68.0,
    "dtc_count": 0,
    "dtcs": []
  },
  "crc32": "A1B2C3D4"
}
```

### Verifying `crc32` on the Python Receiver
`crc32` is computed as an uppercase 8-character hex CRC-32 (`zlib.crc32`) over the compact UTF-8 JSON string of the object **without** the `"crc32"` field.

```python
import json, socket, zlib

sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
sock.bind(("0.0.0.0", 5005))

while True:
    data, addr = sock.recvfrom(4096)
    pkt = json.loads(data.decode("utf-8"))
    received_crc = pkt.pop("crc32", None)
    calc_crc = f"{zlib.crc32(json.dumps(pkt, separators=(',', ':')).encode('utf-8')) & 0xFFFFFFFF:08X}"
    print(f"seq={pkt['seq']} rpm={pkt['obd2']['rpm']} speed={pkt['obd2']['speed_kmh']} crc_match={received_crc == calc_crc}")
```

---

## Build & Local Verification

```bash
# Build debug APK
gradle :app:assembleDebug

# Run JVM unit tests & Robolectric Compose UI test suite
gradle :app:testDebugUnitTest
```
