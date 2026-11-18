package com.example

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

class RoadConditionAnalyzer {
    private val sqWindow = ArrayDeque<Float>(WINDOW_SIZE)
    private var sqSum = 0f

    @Synchronized
    fun analyzeSample(
        linAx: Float,
        linAy: Float,
        linAz: Float,
        gyroMagRad: Float,
        speedMps: Float,
        audioRmsDbfs: Float,
        audioActive: Boolean,
    ): RoadIntelligenceState {
        val mag = sqrt(linAx * linAx + linAy * linAy + linAz * linAz)
        val sq = mag * mag
        if (sqWindow.size >= WINDOW_SIZE) {
            sqSum = max(0f, sqSum - sqWindow.removeFirst())
        }
        sqWindow.addLast(sq)
        sqSum += sq

        val rms = sqrt((sqSum / sqWindow.size.coerceAtLeast(1)).toDouble()).toFloat()
        val verticalSpike = abs(linAz)

        // Gate impact/roughness scoring if device is nearly stationary (< 1.5 m/s) unless a strong shock occurs
        val moving = speedMps >= 1.5f
        val impactDetected = verticalSpike >= IMPACT_THRESHOLD_MPS2 || (moving && mag >= 6.5f)

        val vibPenalty = (rms * 14f).coerceIn(0f, 70f)
        val gyroPenalty = if (moving) (gyroMagRad * 8f).coerceIn(0f, 15f) else 0f
        val audioPenalty = if (audioActive && moving && audioRmsDbfs > -28f) {
            ((audioRmsDbfs + 28f) * 0.8f).coerceIn(0f, 15f)
        } else {
            0f
        }

        val rawScore = (100f - vibPenalty - gyroPenalty - audioPenalty).roundToInt().coerceIn(0, 100)
        val surfaceClass = when {
            impactDetected -> "IMPACT_ANOMALY"
            rawScore >= 80 -> "SMOOTH"
            rawScore >= 55 -> "MODERATE"
            else -> "ROUGH"
        }

        return RoadIntelligenceState(
            conditionScore = rawScore,
            surfaceClass = surfaceClass,
            vibrationRms = rms,
            impactDetected = impactDetected,
        )
    }

    companion object {
        private const val WINDOW_SIZE = 32
        private const val IMPACT_THRESHOLD_MPS2 = 7.5f
    }
}
