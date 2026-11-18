package com.example

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private val Context.tripExportsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "roadeye_trip_exports",
)

data class TripExportState(
    val latestSummary: TripStatisticsSummary? = null,
    val savedSessions: List<TripStatisticsSummary> = emptyList(),
    val lastExportedFileName: String? = null,
    val lastExportedAtMs: Long = 0L,
    val lastExportedBytes: Long = 0L,
)

data class TripJsonExportResult(
    val file: File,
    val shareUri: Uri,
    val fileName: String,
    val byteSize: Long,
    val jsonContent: String,
)

object TripStatisticsJsonSerializer {

    private const val SCHEMA_VERSION = "1.0"

    fun toJsonObject(
        summary: TripStatisticsSummary,
        exportedAtMs: Long? = null,
    ): JSONObject {
        val speedMotionJson = JSONObject()
            .put("average_speed_kmh", roundFloat(summary.averageSpeedKmh, 2))
            .put("max_speed_kmh", roundFloat(summary.maxSpeedKmh, 2))
            .put("estimated_distance_km", roundDouble(summary.estimatedDistanceKm, 4))
            .put("peak_lateral_g", roundFloat(summary.peakLateralG, 3))
            .put("peak_longitudinal_g", roundFloat(summary.peakLongitudinalG, 3))

        val altitudeProfileJson = JSONObject()
            .put("source", summary.altitudeSource)
            .put("min_altitude_m", roundFloat(summary.minAltitudeM, 2))
            .put("max_altitude_m", roundFloat(summary.maxAltitudeM, 2))
            .put("max_altitude_change_m", roundFloat(summary.maxAltitudeChangeM, 2))
            .put("total_elevation_gain_m", roundFloat(summary.totalElevationGainM, 2))
            .put("net_altitude_change_m", roundFloat(summary.netAltitudeChangeM, 2))
            .put("pressure_min_hpa", roundFloat(summary.pressureMinHpa, 2))
            .put("pressure_max_hpa", roundFloat(summary.pressureMaxHpa, 2))

        val roadNoiseJson = JSONObject()
            .put("has_audio_samples", summary.hasAudioSamples)
            .put("total_exposure_db_sec", roundDouble(summary.totalRoadNoiseExposureDbSec, 2))
            .put("total_energy_dosage", roundDouble(summary.totalNoiseEnergyDosage, 6))
            .put("exposure_index_pct", roundFloat(summary.noiseExposureIndexPct, 2))
            .put("equivalent_level_dbfs", roundFloat(summary.equivalentNoiseDbfs, 2))
            .put("peak_noise_dbfs", roundFloat(summary.peakNoiseDbfs, 2))
            .put("elevated_noise_duration_sec", roundDouble(summary.elevatedNoiseDurationSec, 2))

        val roadPowertrainJson = JSONObject()
            .put("average_road_score", summary.averageRoadScore)
            .put("worst_surface_class", summary.worstSurfaceClass)
            .put("peak_vibration_rms", roundFloat(summary.peakVibrationRms, 3))
            .put("impact_count", summary.impactCount)
            .put("obd2_connected", summary.obd2ConnectedDuringTrip)
            .put("average_rpm", summary.averageRpm)
            .put("peak_rpm", summary.peakRpm)
            .put("max_coolant_temp_c", summary.maxCoolantTempC)

        val root = JSONObject()
            .put("schema_version", SCHEMA_VERSION)
            .put("session_id", summary.sessionId)
            .put("session_label", summary.sessionLabel)
            .put("started_at_ms", summary.startedAtMs)
            .put("ended_at_ms", summary.endedAtMs)
            .put("duration_sec", roundDouble(summary.durationSec, 2))
            .put("sample_count", summary.sampleCount)
            .put("average_stream_hz", roundFloat(summary.averageStreamHz, 2))
            .put("speed_motion", speedMotionJson)
            .put("altitude_profile", altitudeProfileJson)
            .put("road_noise_exposure", roadNoiseJson)
            .put("road_and_powertrain", roadPowertrainJson)

        if (exportedAtMs != null) {
            root.put("exported_at_ms", exportedAtMs)
        }
        return root
    }

    fun toBundleJsonObject(
        sessions: List<TripStatisticsSummary>,
        exportedAtMs: Long = System.currentTimeMillis(),
    ): JSONObject {
        val array = JSONArray()
        sessions.forEach { array.put(toJsonObject(it)) }
        return JSONObject()
            .put("schema_version", SCHEMA_VERSION)
            .put("exported_at_ms", exportedAtMs)
            .put("session_count", sessions.size)
            .put("sessions", array)
    }

    fun toJsonString(
        summary: TripStatisticsSummary,
        exportedAtMs: Long? = null,
        prettyPrint: Boolean = true,
    ): String {
        val obj = toJsonObject(summary, exportedAtMs)
        return if (prettyPrint) obj.toString(2) else obj.toString()
    }

    fun fromJson(rawJson: String): TripStatisticsSummary {
        val root = JSONObject(rawJson)
        val speed = root.optJSONObject("speed_motion")
        val alt = root.optJSONObject("altitude_profile")
        val noise = root.optJSONObject("road_noise_exposure")
        val road = root.optJSONObject("road_and_powertrain")

        return TripStatisticsSummary(
            sessionId = root.optString("session_id", TelemetrySnapshot.DEFAULT_DEVICE_ID),
            sessionLabel = root.optString("session_label", "UDP Telemetry Session"),
            startedAtMs = root.optLong("started_at_ms", 0L),
            endedAtMs = root.optLong("ended_at_ms", 0L),
            durationSec = root.optDouble("duration_sec", 0.0),
            sampleCount = root.optLong("sample_count", 0L),
            averageSpeedKmh = speed?.optDouble("average_speed_kmh", 0.0)?.toFloat() ?: 0f,
            maxSpeedKmh = speed?.optDouble("max_speed_kmh", 0.0)?.toFloat() ?: 0f,
            estimatedDistanceKm = speed?.optDouble("estimated_distance_km", 0.0) ?: 0.0,
            minAltitudeM = alt?.optDouble("min_altitude_m", 0.0)?.toFloat() ?: 0f,
            maxAltitudeM = alt?.optDouble("max_altitude_m", 0.0)?.toFloat() ?: 0f,
            maxAltitudeChangeM = alt?.optDouble("max_altitude_change_m", 0.0)?.toFloat() ?: 0f,
            totalElevationGainM = alt?.optDouble("total_elevation_gain_m", 0.0)?.toFloat() ?: 0f,
            netAltitudeChangeM = alt?.optDouble("net_altitude_change_m", 0.0)?.toFloat() ?: 0f,
            altitudeSource = alt?.optString("source", "Unavailable") ?: "Unavailable",
            pressureMinHpa = alt?.optDouble("pressure_min_hpa", 0.0)?.toFloat() ?: 0f,
            pressureMaxHpa = alt?.optDouble("pressure_max_hpa", 0.0)?.toFloat() ?: 0f,
            totalRoadNoiseExposureDbSec = noise?.optDouble("total_exposure_db_sec", 0.0) ?: 0.0,
            totalNoiseEnergyDosage = noise?.optDouble("total_energy_dosage", 0.0) ?: 0.0,
            noiseExposureIndexPct = noise?.optDouble("exposure_index_pct", 0.0)?.toFloat() ?: 0f,
            equivalentNoiseDbfs = noise?.optDouble("equivalent_level_dbfs", -90.0)?.toFloat() ?: -90f,
            peakNoiseDbfs = noise?.optDouble("peak_noise_dbfs", -90.0)?.toFloat() ?: -90f,
            elevatedNoiseDurationSec = noise?.optDouble("elevated_noise_duration_sec", 0.0) ?: 0.0,
            hasAudioSamples = noise?.optBoolean("has_audio_samples", false) ?: false,
            averageRoadScore = road?.optInt("average_road_score", 100) ?: 100,
            worstSurfaceClass = road?.optString("worst_surface_class", "SMOOTH") ?: "SMOOTH",
            peakVibrationRms = road?.optDouble("peak_vibration_rms", 0.0)?.toFloat() ?: 0f,
            impactCount = road?.optInt("impact_count", 0) ?: 0,
            peakLateralG = speed?.optDouble("peak_lateral_g", 0.0)?.toFloat() ?: 0f,
            peakLongitudinalG = speed?.optDouble("peak_longitudinal_g", 0.0)?.toFloat() ?: 0f,
            obd2ConnectedDuringTrip = road?.optBoolean("obd2_connected", false) ?: false,
            peakRpm = road?.optInt("peak_rpm", 0) ?: 0,
            averageRpm = road?.optInt("average_rpm", 0) ?: 0,
            maxCoolantTempC = road?.optInt("max_coolant_temp_c", 0) ?: 0,
            averageStreamHz = root.optDouble("average_stream_hz", 0.0).toFloat(),
        )
    }

    private fun roundFloat(value: Float, decimals: Int): Double {
        if (!value.isFinite()) return 0.0
        val factor = when (decimals) {
            1 -> 10.0
            2 -> 100.0
            3 -> 1000.0
            else -> 100.0
        }
        return (value.toDouble() * factor).roundToLong() / factor
    }

    private fun roundDouble(value: Double, decimals: Int): Double {
        if (!value.isFinite()) return 0.0
        val factor = when (decimals) {
            2 -> 100.0
            4 -> 10000.0
            6 -> 1000000.0
            else -> 100.0
        }
        return (value * factor).roundToLong() / factor
    }
}

class TripStatisticsExportRepository(context: Context) {
    private val appContext = context.applicationContext
    private val dataStore = appContext.tripExportsDataStore
    private val exportsDir = File(appContext.filesDir, "exports").apply { mkdirs() }

    val exportStateFlow: Flow<TripExportState> = dataStore.data.map { prefs ->
        val latest = prefs[KEY_LAST_SUMMARY_JSON]?.let { raw ->
            runCatching { TripStatisticsJsonSerializer.fromJson(raw) }.getOrNull()
        }
        val history = prefs[KEY_SESSION_HISTORY_JSON]?.let { raw ->
            parseHistoryArray(raw)
        } ?: emptyList()

        TripExportState(
            latestSummary = latest ?: history.firstOrNull(),
            savedSessions = history,
            lastExportedFileName = prefs[KEY_LAST_EXPORTED_FILE],
            lastExportedAtMs = prefs[KEY_LAST_EXPORTED_AT_MS] ?: 0L,
            lastExportedBytes = prefs[KEY_LAST_EXPORTED_BYTES] ?: 0L,
        )
    }

    suspend fun saveCompletedSession(summary: TripStatisticsSummary) {
        val summaryJson = TripStatisticsJsonSerializer.toJsonString(summary, prettyPrint = false)
        dataStore.edit { prefs ->
            prefs[KEY_LAST_SUMMARY_JSON] = summaryJson
            val existing = prefs[KEY_SESSION_HISTORY_JSON]?.let { parseHistoryArray(it) } ?: emptyList()
            val deduped = existing.filterNot {
                it.startedAtMs == summary.startedAtMs &&
                    it.endedAtMs == summary.endedAtMs &&
                    it.sessionId == summary.sessionId
            }
            val updated = (listOf(summary) + deduped).take(MAX_SAVED_SESSIONS)
            val jsonArray = JSONArray()
            updated.forEach { jsonArray.put(TripStatisticsJsonSerializer.toJsonObject(it)) }
            prefs[KEY_SESSION_HISTORY_JSON] = jsonArray.toString()
        }
    }

    suspend fun exportSummaryToJsonFile(
        summary: TripStatisticsSummary? = null,
    ): TripJsonExportResult? {
        val targetSummary = summary ?: exportStateFlow.first().latestSummary ?: return null
        val exportedAtMs = System.currentTimeMillis()
        val jsonContent = TripStatisticsJsonSerializer.toJsonString(
            summary = targetSummary,
            exportedAtMs = exportedAtMs,
            prettyPrint = true,
        )
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(exportedAtMs))
        val fileName = "roadeye_trip_stats_$stamp.json"
        return writeExportFile(fileName = fileName, jsonContent = jsonContent, exportedAtMs = exportedAtMs)
    }

    suspend fun exportAllSavedSessionsToJsonFile(): TripJsonExportResult? {
        val state = exportStateFlow.first()
        val sessions = state.savedSessions.ifEmpty {
            listOfNotNull(state.latestSummary)
        }
        if (sessions.isEmpty()) return null

        val exportedAtMs = System.currentTimeMillis()
        val jsonContent = TripStatisticsJsonSerializer.toBundleJsonObject(
            sessions = sessions,
            exportedAtMs = exportedAtMs,
        ).toString(2)
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(exportedAtMs))
        val fileName = "roadeye_trip_history_$stamp.json"
        return writeExportFile(fileName = fileName, jsonContent = jsonContent, exportedAtMs = exportedAtMs)
    }

    fun getLatestExportedShareUri(): Uri? {
        val files = exportsDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".json") && it.length() > 0L }
            ?.sortedByDescending { it.lastModified() }
            ?: return null
        val latest = files.firstOrNull() ?: return null
        return FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            latest,
        )
    }

    private suspend fun writeExportFile(
        fileName: String,
        jsonContent: String,
        exportedAtMs: Long,
    ): TripJsonExportResult = withContext(Dispatchers.IO) {
        exportsDir.mkdirs()
        val targetFile = File(exportsDir, fileName)
        targetFile.writeText(jsonContent, Charsets.UTF_8)
        val bytes = targetFile.length()

        dataStore.edit { prefs ->
            prefs[KEY_LAST_EXPORTED_FILE] = fileName
            prefs[KEY_LAST_EXPORTED_AT_MS] = exportedAtMs
            prefs[KEY_LAST_EXPORTED_BYTES] = bytes
        }

        val uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            targetFile,
        )
        TripJsonExportResult(
            file = targetFile,
            shareUri = uri,
            fileName = fileName,
            byteSize = bytes,
            jsonContent = jsonContent,
        )
    }

    private fun parseHistoryArray(raw: String): List<TripStatisticsSummary> {
        return runCatching {
            val array = JSONArray(raw)
            buildList(array.length()) {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    add(TripStatisticsJsonSerializer.fromJson(obj.toString()))
                }
            }
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val MAX_SAVED_SESSIONS = 20
        val KEY_LAST_SUMMARY_JSON = stringPreferencesKey("last_summary_json")
        val KEY_SESSION_HISTORY_JSON = stringPreferencesKey("session_history_json")
        val KEY_LAST_EXPORTED_FILE = stringPreferencesKey("last_exported_file")
        val KEY_LAST_EXPORTED_AT_MS = longPreferencesKey("last_exported_at_ms")
        val KEY_LAST_EXPORTED_BYTES = longPreferencesKey("last_exported_bytes")
    }
}
