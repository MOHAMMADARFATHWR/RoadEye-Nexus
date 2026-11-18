# ==============================================================================
# RoadEye Nexus — ProGuard / R8 Release Optimization & Shrinking Rules
# ==============================================================================

# Preserve source file names, line numbers, generic signatures, and annotations
# for readable crash stack traces and Kotlin/AndroidX runtime compatibility.
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod,*Annotation*
-renamesourcefileattribute SourceFile

# Strip verbose and debug logs in release builds while keeping Log.w / Log.e
# for field telemetry diagnostics.
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}

# Preserve Kotlin enum entries for state enums (e.g., Obd2ConnectionStatus, HudMode)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ------------------------------------------------------------------------------
# 1. Critical JSON Serialization & Telemetry State Models
# ------------------------------------------------------------------------------
-keep class com.example.TelemetrySnapshot { *; }
-keep class com.example.TelemetrySnapshot$* { *; }
-keep class com.example.SensorTelemetryData { *; }
-keep class com.example.SensorTelemetryData$* { *; }
-keep class com.example.SensorTelemetryJsonSerializer { *; }
-keep class com.example.TelemetryStateHolder { *; }
-keep class com.example.SensorsState { *; }
-keep class com.example.Vector3State { *; }
-keep class com.example.QuaternionState { *; }
-keep class com.example.OrientationState { *; }
-keep class com.example.BarometerState { *; }
-keep class com.example.AmbientLightState { *; }
-keep class com.example.ProximityState { *; }
-keep class com.example.AudioTelemetryState { *; }
-keep class com.example.DeviceHealthState { *; }
-keep class com.example.RoadIntelligenceState { *; }
-keep class com.example.SensorAvailabilityState { *; }
-keep class com.example.GpsState { *; }
-keep class com.example.Obd2State { *; }
-keep class com.example.Obd2ConnectionStatus { *; }
-keep class com.example.PacketInspectionEntry { *; }
-keep class com.example.StreamerStatus { *; }
-keep class com.example.RecordingStatus { *; }
-keep class com.example.TelemetrySettings { *; }

# Keep OBD2 parsing structures & Bluetooth device descriptors
-keep class com.example.PairedBluetoothDevice { *; }
-keep class com.example.PidParseResult { *; }
-keep class com.example.PidParseResult$* { *; }
-keep class com.example.Obd2PidParser { *; }

# Keep Android org.json calls intact
-keepclassmembers class org.json.JSONObject { *; }
-keepclassmembers class org.json.JSONArray { *; }

# ------------------------------------------------------------------------------
# 2. Sensor Listeners, Hardware Telemetry Managers & Location Callbacks
# ------------------------------------------------------------------------------
-keep class com.example.SensorTelemetryManager { *; }
-keep class com.example.SensorTelemetryManager$* { *; }
-keep class com.example.RoadConditionAnalyzer { *; }
-keep class com.example.AudioTelemetryManager { *; }
-keep class com.example.DeviceDiagnosticsManager { *; }
-keep class com.example.Obd2BluetoothManager { *; }
-keep class com.example.Obd2BluetoothManager$* { *; }
-keep class com.example.UdpTelemetryStreamer { *; }
-keep class com.example.UdpTelemetryClient { *; }
-keep class com.example.UdpTelemetryClient$* { *; }
-keep class com.example.UdpClientStats { *; }
-keep class com.example.SessionRecorder { *; }
-keep class com.example.TripStatisticsService { *; }
-keep class com.example.TripStatisticsSummary { *; }
-keep class com.example.TripStatisticsExportRepository { *; }
-keep class com.example.TripStatisticsJsonSerializer { *; }
-keep class com.example.TripExportState { *; }
-keep class com.example.TripJsonExportResult { *; }
-keep class com.example.TripJsonSharePayload { *; }
-keep class com.example.TelemetrySessionController { *; }
-keep class com.example.TelemetryPreferencesRepository { *; }

# Keep all SensorEventListener implementations and Android hardware callbacks
-keepclassmembers class * implements android.hardware.SensorEventListener {
    public void onSensorChanged(android.hardware.SensorEvent);
    public void onAccuracyChanged(android.hardware.Sensor, int);
}

# Keep FusedLocationProvider LocationCallback subclasses and callbacks
-keep class * extends com.google.android.gms.location.LocationCallback {
    <init>(...);
    public void onLocationResult(com.google.android.gms.location.LocationResult);
    public void onLocationAvailability(com.google.android.gms.location.LocationAvailability);
}

# ------------------------------------------------------------------------------
# 3. Android Entry Points, Foreground Service, ViewModel & FileProvider
# ------------------------------------------------------------------------------
-keep class com.example.MainActivity { *; }
-keep class com.example.TelemetryForegroundService { *; }
-keep class com.example.RoadEyeViewModel {
    public <init>(android.app.Application);
}
-keep class * extends androidx.lifecycle.AndroidViewModel {
    public <init>(android.app.Application);
}
-keep class * extends androidx.lifecycle.ViewModel {
    public <init>(...);
}
-keep class androidx.core.content.FileProvider { *; }
-keep class androidx.core.splashscreen.** { *; }

# ------------------------------------------------------------------------------
# 4. AndroidX DataStore & Coroutines
# ------------------------------------------------------------------------------
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}
-dontwarn kotlinx.coroutines.debug.**
