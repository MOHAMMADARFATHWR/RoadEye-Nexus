package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AudioTelemetryManager(
    context: Context,
    private val stateHolder: TelemetryStateHolder,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var recordJob: Job? = null

    fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (recordJob?.isActive == true) return
        if (!hasAudioPermission()) {
            stateHolder.updateAudioAvailability(false)
            return
        }

        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuf <= 0) {
            stateHolder.updateAudioAvailability(false)
            return
        }

        val bufferSize = maxOf(minBuf, FRAME_SAMPLES * 2)
        stateHolder.updateAudioAvailability(true)

        recordJob = scope.launch {
            var recorder: AudioRecord? = null
            try {
                recorder = AudioRecord(
                    MediaRecorder.AudioSource.UNPROCESSED,
                    SAMPLE_RATE_HZ,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize,
                )
                if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                    recorder.release()
                    recorder = AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        SAMPLE_RATE_HZ,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize,
                    )
                }
                if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                    stateHolder.updateAudio(AudioTelemetryState(active = false))
                    return@launch
                }

                val samples = ShortArray(FRAME_SAMPLES)
                recorder.startRecording()

                while (isActive) {
                    val read = recorder.read(samples, 0, samples.size)
                    if (read > 0) {
                        val features = extractAcousticFeatures(samples, read, SAMPLE_RATE_HZ)
                        stateHolder.updateAudio(features)
                    }
                }
            } catch (_: SecurityException) {
                stateHolder.updateAudioAvailability(false)
                stateHolder.updateAudio(AudioTelemetryState(active = false))
            } catch (_: Exception) {
                stateHolder.updateAudio(AudioTelemetryState(active = false))
            } finally {
                try {
                    recorder?.stop()
                } catch (_: Exception) {
                    // Ignore stop state error.
                }
                try {
                    recorder?.release()
                } catch (_: Exception) {
                    // Ignore release error.
                }
            }
        }
    }

    fun stop() {
        recordJob?.cancel()
        recordJob = null
        stateHolder.updateAudio(AudioTelemetryState(active = false))
    }

    fun shutdown() {
        stop()
        scope.cancel()
    }

    companion object {
        private const val SAMPLE_RATE_HZ = 16000
        private const val FRAME_SAMPLES = 1600 // 100ms window

        fun extractAcousticFeatures(
            samples: ShortArray,
            length: Int,
            sampleRateHz: Int,
        ): AudioTelemetryState {
            if (length <= 1) return AudioTelemetryState(active = true)
            var sumSq = 0.0
            var peak = 0
            var zeroCrossings = 0
            var prev = samples[0].toInt()

            for (i in 0 until length) {
                val s = samples[i].toInt()
                val absVal = abs(s)
                if (absVal > peak) peak = absVal
                sumSq += s.toDouble() * s.toDouble()
                if ((s >= 0 && prev < 0) || (s < 0 && prev >= 0)) {
                    zeroCrossings++
                }
                prev = s
            }

            val rmsNorm = sqrt(sumSq / length.toDouble()) / 32768.0
            val rmsDbfs = if (rmsNorm > 1e-6) {
                (20.0 * log10(rmsNorm)).toFloat().coerceIn(-90f, 0f)
            } else {
                -90f
            }
            val peakNorm = (peak / 32768.0).toFloat().coerceIn(0f, 1f)
            val durationSec = length.toFloat() / sampleRateHz.toFloat()
            val zcrHz = (zeroCrossings / 2f) / durationSec

            return AudioTelemetryState(
                active = true,
                rmsDbfs = rmsDbfs,
                peakAmp = peakNorm,
                zcrHz = zcrHz,
            )
        }
    }
}
