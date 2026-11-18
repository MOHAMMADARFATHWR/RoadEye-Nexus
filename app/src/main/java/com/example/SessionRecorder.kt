package com.example

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RecordingStatus(
    val isRecording: Boolean = false,
    val currentFileName: String = "",
    val framesRecorded: Long = 0L,
    val bytesWritten: Long = 0L,
    val lastSavedFileName: String? = null,
)

class SessionRecorder(context: Context) {
    private val appContext = context.applicationContext
    private val recordingsDir = File(appContext.filesDir, "recordings").apply { mkdirs() }

    private val _status = MutableStateFlow(RecordingStatus())
    val status: StateFlow<RecordingStatus> = _status.asStateFlow()

    @Volatile
    private var writer: BufferedWriter? = null

    @Volatile
    private var activeFile: File? = null

    @Volatile
    private var framesWritten = 0L

    @Volatile
    private var bytesWritten = 0L

    @Synchronized
    fun startNewSession(): File {
        stopSession()
        recordingsDir.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(recordingsDir, "roadeye_session_$stamp.jsonl")
        writer = BufferedWriter(FileWriter(file, true), 16 * 1024)
        activeFile = file
        framesWritten = 0L
        bytesWritten = 0L
        _status.value = RecordingStatus(
            isRecording = true,
            currentFileName = file.name,
            framesRecorded = 0L,
            bytesWritten = 0L,
            lastSavedFileName = _status.value.lastSavedFileName,
        )
        return file
    }

    @Synchronized
    fun appendLine(jsonLine: String) {
        val currentWriter = writer ?: return
        try {
            currentWriter.write(jsonLine)
            currentWriter.newLine()
            framesWritten++
            bytesWritten += jsonLine.length + 1
            if (framesWritten % 10L == 0L) {
                currentWriter.flush()
            }
            _status.value = _status.value.copy(
                framesRecorded = framesWritten,
                bytesWritten = bytesWritten,
            )
        } catch (_: Exception) {
            stopSession()
        }
    }

    @Synchronized
    fun stopSession() {
        val previousName = activeFile?.name
        try {
            writer?.flush()
            writer?.close()
        } catch (_: Exception) {
            // Ignore close errors.
        }
        writer = null
        activeFile = null
        _status.value = RecordingStatus(
            isRecording = false,
            currentFileName = "",
            framesRecorded = framesWritten,
            bytesWritten = bytesWritten,
            lastSavedFileName = if (framesWritten > 0L) previousName else _status.value.lastSavedFileName,
        )
    }

    fun getLatestShareUri(): Uri? {
        val files = recordingsDir.listFiles()?.filter { it.isFile && it.length() > 0L }
            ?.sortedByDescending { it.lastModified() }
            ?: return null
        val latest = files.firstOrNull() ?: return null
        return FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            latest,
        )
    }
}
