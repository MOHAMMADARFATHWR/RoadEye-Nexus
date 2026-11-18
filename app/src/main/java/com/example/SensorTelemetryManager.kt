package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SensorTelemetryManager(
    context: Context,
    private val stateHolder: TelemetryStateHolder,
    private val roadAnalyzer: RoadConditionAnalyzer = RoadConditionAnalyzer(),
    private val audioTelemetryManager: AudioTelemetryManager = AudioTelemetryManager(context, stateHolder),
) : SensorEventListener {

    private val appContext = context.applicationContext
    private val sensorManager =
        appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(appContext)

    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val magnetometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val gravitySensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
    private val linearAccelSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val rotationVectorSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val barometerSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
    private val lightSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
    private val proximitySensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    @Volatile
    private var isSensorListening = false

    @Volatile
    private var isLocationListening = false

    @Volatile
    private var highRateActive = false

    private val _isCollecting = MutableStateFlow(false)
    val isCollecting: StateFlow<Boolean> = _isCollecting.asStateFlow()

    // Calibration biases (zero-offsets)
    @Volatile
    private var accelBiasX = 0f

    @Volatile
    private var accelBiasY = 0f

    @Volatile
    private var gyroBiasX = 0f

    @Volatile
    private var gyroBiasY = 0f

    @Volatile
    private var gyroBiasZ = 0f

    // Calibration accumulation state
    @Volatile
    private var isCalibrating = false
    private var calAccelXSum = 0.0
    private var calAccelYSum = 0.0
    private var calAccelCount = 0
    private var calGyroXSum = 0.0
    private var calGyroYSum = 0.0
    private var calGyroZSum = 0.0
    private var calGyroCount = 0

    // Low-pass filtered accelerometer & magnetometer state
    private var hasAccelSeed = false
    private var filteredAccelX = 0f
    private var filteredAccelY = 0f
    private var filteredAccelZ = 0f

    private var hasMagSeed = false
    private var magX = 0f
    private var magY = 0f
    private var magZ = 0f

    @Volatile
    private var latestGyroMag = 0f

    // Complementary filter orientation state (radians)
    private var pitchRad = 0f
    private var rollRad = 0f
    private var yawRad = 0f
    private var hasOrientationSeed = false
    private var lastGyroTimestampNs = 0L

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private val quaternionBuffer = FloatArray(4)

    private val locationRequest: LocationRequest =
        LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_INTERVAL_MS)
            .setMinUpdateIntervalMillis(LOCATION_MIN_INTERVAL_MS)
            .setWaitForAccurateLocation(false)
            .build()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            stateHolder.updateGps(
                lat = location.latitude,
                lng = location.longitude,
                altitude = if (location.hasAltitude()) location.altitude else 0.0,
                speedMps = if (location.hasSpeed()) location.speed else 0f,
                bearing = if (location.hasBearing()) location.bearing else 0f,
                accuracyM = if (location.hasAccuracy()) location.accuracy else 0f,
                hasFix = true,
            )
        }
    }

    init {
        stateHolder.setSensorAvailability(
            SensorAvailabilityState(
                accel = accelerometer != null,
                gyro = gyroscope != null,
                mag = magnetometer != null,
                gravity = gravitySensor != null,
                linearAccel = linearAccelSensor != null,
                rotationVector = rotationVectorSensor != null,
                barometer = barometerSensor != null,
                light = lightSensor != null,
                proximity = proximitySensor != null,
                audio = stateHolder.stateFlow.value.sensors.availability.audio,
            ),
        )
    }

    fun setCalibrationOffsets(
        accelBiasX: Float,
        accelBiasY: Float,
        gyroBiasX: Float,
        gyroBiasY: Float,
        gyroBiasZ: Float,
    ) {
        this.accelBiasX = accelBiasX
        this.accelBiasY = accelBiasY
        this.gyroBiasX = gyroBiasX
        this.gyroBiasY = gyroBiasY
        this.gyroBiasZ = gyroBiasZ
    }

    fun beginCalibrationSampleWindow() {
        calAccelXSum = 0.0
        calAccelYSum = 0.0
        calAccelCount = 0
        calGyroXSum = 0.0
        calGyroYSum = 0.0
        calGyroZSum = 0.0
        calGyroCount = 0
        isCalibrating = true
    }

    data class CalibrationResult(
        val accelBiasX: Float,
        val accelBiasY: Float,
        val gyroBiasX: Float,
        val gyroBiasY: Float,
        val gyroBiasZ: Float,
    )

    fun finishCalibrationSampleWindow(): CalibrationResult {
        isCalibrating = false
        val newAccelBiasX = if (calAccelCount > 0) (calAccelXSum / calAccelCount).toFloat() else accelBiasX
        val newAccelBiasY = if (calAccelCount > 0) (calAccelYSum / calAccelCount).toFloat() else accelBiasY
        val newGyroBiasX = if (calGyroCount > 0) (calGyroXSum / calGyroCount).toFloat() else gyroBiasX
        val newGyroBiasY = if (calGyroCount > 0) (calGyroYSum / calGyroCount).toFloat() else gyroBiasY
        val newGyroBiasZ = if (calGyroCount > 0) (calGyroZSum / calGyroCount).toFloat() else gyroBiasZ

        setCalibrationOffsets(
            accelBiasX = newAccelBiasX,
            accelBiasY = newAccelBiasY,
            gyroBiasX = newGyroBiasX,
            gyroBiasY = newGyroBiasY,
            gyroBiasZ = newGyroBiasZ,
        )
        return CalibrationResult(
            accelBiasX = newAccelBiasX,
            accelBiasY = newAccelBiasY,
            gyroBiasX = newGyroBiasX,
            gyroBiasY = newGyroBiasY,
            gyroBiasZ = newGyroBiasZ,
        )
    }

    fun setHighRateMode(highRate: Boolean) {
        highRateActive = highRate
    }

    fun start() {
        startImuSensors()
        startLocationUpdates()
    }

    fun startImuSensors() {
        if (isSensorListening) return
        hasAccelSeed = false
        hasOrientationSeed = false
        lastGyroTimestampNs = 0L

        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
        }
        magnetometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
        }
        gravitySensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        linearAccelSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
        }
        rotationVectorSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        barometerSensor?.let {
            sensorManager.registerListener(this, it, BAROMETER_SAMPLING_PERIOD_US)
        }
        lightSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        proximitySensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        isSensorListening = true
        _isCollecting.value = true
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        if (isLocationListening) return
        if (!hasLocationPermission()) return
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper(),
            )
            isLocationListening = true
        } catch (_: SecurityException) {
            isLocationListening = false
        }
    }

    fun stop() {
        if (isSensorListening) {
            sensorManager.unregisterListener(this)
            isSensorListening = false
        }
        _isCollecting.value = false
        audioTelemetryManager.stop()
        if (isLocationListening) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            isLocationListening = false
        }
    }

    fun startAudioCapture() {
        audioTelemetryManager.start()
    }

    fun stopAudioCapture() {
        audioTelemetryManager.stop()
    }

    fun hasAudioPermission(): Boolean = audioTelemetryManager.hasAudioPermission()

    fun latestSensorTelemetryData(): SensorTelemetryData = stateHolder.snapshot().toSensorTelemetryData()

    fun hasLocationPermission(): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return fineGranted || coarseGranted
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val rawX = event.values[0]
                val rawY = event.values[1]
                val rawZ = event.values[2]
                if (isCalibrating) {
                    calAccelXSum += rawX
                    calAccelYSum += rawY
                    calAccelCount++
                }

                val calX = rawX - accelBiasX
                val calY = rawY - accelBiasY
                val calZ = rawZ

                if (!hasAccelSeed) {
                    filteredAccelX = calX
                    filteredAccelY = calY
                    filteredAccelZ = calZ
                    hasAccelSeed = true
                } else {
                    filteredAccelX = LPF_ALPHA * filteredAccelX + (1f - LPF_ALPHA) * calX
                    filteredAccelY = LPF_ALPHA * filteredAccelY + (1f - LPF_ALPHA) * calY
                    filteredAccelZ = LPF_ALPHA * filteredAccelZ + (1f - LPF_ALPHA) * calZ
                }

                updateOrientationFromAccelAndMag(filteredAccelX, filteredAccelY, filteredAccelZ)
                stateHolder.updateAccelAndOrientation(
                    x = filteredAccelX,
                    y = filteredAccelY,
                    z = filteredAccelZ,
                    pitchDeg = Math.toDegrees(pitchRad.toDouble()).toFloat(),
                    rollDeg = Math.toDegrees(rollRad.toDouble()).toFloat(),
                    yawDeg = normalizeDegrees(Math.toDegrees(yawRad.toDouble()).toFloat()),
                )

                // Fallback road intelligence if device lacks hardware TYPE_LINEAR_ACCELERATION
                if (linearAccelSensor == null) {
                    val snap = stateHolder.stateFlow.value
                    val linZFallback = calZ - SensorManager.GRAVITY_EARTH
                    val road = roadAnalyzer.analyzeSample(
                        linAx = calX,
                        linAy = calY,
                        linAz = linZFallback,
                        gyroMagRad = latestGyroMag,
                        speedMps = snap.sensors.gps.speedMps,
                        audioRmsDbfs = snap.sensors.audio.rmsDbfs,
                        audioActive = snap.sensors.audio.active,
                    )
                    stateHolder.updateLinearAccelAndRoad(calX, calY, linZFallback, road)
                }
            }

            Sensor.TYPE_GYROSCOPE -> {
                val rawGx = event.values[0]
                val rawGy = event.values[1]
                val rawGz = event.values[2]
                if (isCalibrating) {
                    calGyroXSum += rawGx
                    calGyroYSum += rawGy
                    calGyroZSum += rawGz
                    calGyroCount++
                }

                val gx = rawGx - gyroBiasX
                val gy = rawGy - gyroBiasY
                val gz = rawGz - gyroBiasZ
                latestGyroMag = sqrt(gx * gx + gy * gy + gz * gz)

                if (lastGyroTimestampNs != 0L) {
                    val dt = (event.timestamp - lastGyroTimestampNs) * 1e-9f
                    if (dt in 0f..0.2f && hasOrientationSeed) {
                        rollRad += gx * dt
                        pitchRad += gy * dt
                        yawRad -= gz * dt
                    }
                }
                lastGyroTimestampNs = event.timestamp
                stateHolder.updateGyro(gx, gy, gz)
            }

            Sensor.TYPE_MAGNETIC_FIELD -> {
                magX = event.values[0]
                magY = event.values[1]
                magZ = event.values[2]
                hasMagSeed = true
                stateHolder.updateMag(magX, magY, magZ)
            }

            Sensor.TYPE_GRAVITY -> {
                stateHolder.updateGravity(event.values[0], event.values[1], event.values[2])
            }

            Sensor.TYPE_LINEAR_ACCELERATION -> {
                val lx = event.values[0]
                val ly = event.values[1]
                val lz = event.values[2]
                val snap = stateHolder.stateFlow.value
                val road = roadAnalyzer.analyzeSample(
                    linAx = lx,
                    linAy = ly,
                    linAz = lz,
                    gyroMagRad = latestGyroMag,
                    speedMps = snap.sensors.gps.speedMps,
                    audioRmsDbfs = snap.sensors.audio.rmsDbfs,
                    audioActive = snap.sensors.audio.active,
                )
                stateHolder.updateLinearAccelAndRoad(lx, ly, lz, road)
            }

            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getQuaternionFromVector(quaternionBuffer, event.values)
                // Android getQuaternionFromVector returns [w, x, y, z]
                stateHolder.updateRotationVector(
                    x = quaternionBuffer[1],
                    y = quaternionBuffer[2],
                    z = quaternionBuffer[3],
                    w = quaternionBuffer[0],
                )
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                yawRad = orientationAngles[0]
                pitchRad = orientationAngles[1]
                rollRad = orientationAngles[2]
                hasOrientationSeed = true
            }

            Sensor.TYPE_PRESSURE -> {
                val pressureHpa = event.values[0]
                val relAltM = SensorManager.getAltitude(
                    SensorManager.PRESSURE_STANDARD_ATMOSPHERE,
                    pressureHpa,
                )
                stateHolder.updateBarometer(pressureHpa, relAltM)
            }

            Sensor.TYPE_LIGHT -> {
                val lux = event.values[0]
                val condition = when {
                    lux < 10f -> "DARK"
                    lux < 150f -> "DUSK"
                    lux < 2000f -> "DAYLIGHT"
                    else -> "BRIGHT"
                }
                stateHolder.updateAmbientLight(lux, condition)
            }

            Sensor.TYPE_PROXIMITY -> {
                val distCm = event.values[0]
                val maxRange = proximitySensor?.maximumRange ?: 5f
                stateHolder.updateProximity(distCm, isNear = distCm < maxRange && distCm < 5f)
            }
        }
    }

    private fun updateOrientationFromAccelAndMag(ax: Float, ay: Float, az: Float) {
        if (rotationVectorSensor != null && hasOrientationSeed) return
        val norm = sqrt(ax * ax + ay * ay + az * az)
        if (norm < 1f) return

        val accelRoll = atan2(ay.toDouble(), az.toDouble()).toFloat()
        val accelPitch = atan2(-ax.toDouble(), sqrt((ay * ay + az * az).toDouble())).toFloat()

        if (!hasOrientationSeed) {
            rollRad = accelRoll
            pitchRad = accelPitch
            hasOrientationSeed = true
        } else {
            rollRad = COMPLEMENTARY_ALPHA * rollRad + (1f - COMPLEMENTARY_ALPHA) * accelRoll
            pitchRad = COMPLEMENTARY_ALPHA * pitchRad + (1f - COMPLEMENTARY_ALPHA) * accelPitch
        }

        if (hasMagSeed) {
            val cosRoll = cos(rollRad.toDouble())
            val sinRoll = sin(rollRad.toDouble())
            val cosPitch = cos(pitchRad.toDouble())
            val sinPitch = sin(pitchRad.toDouble())

            val xh = magX * cosPitch + magZ * sinPitch
            val yh = magX * sinRoll * sinPitch + magY * cosRoll - magZ * sinRoll * cosPitch
            val magYaw = atan2(-yh, xh).toFloat()
            yawRad = COMPLEMENTARY_ALPHA * yawRad + (1f - COMPLEMENTARY_ALPHA) * magYaw
        }
    }

    private fun normalizeDegrees(deg: Float): Float {
        val mod = deg % 360f
        return if (mod < 0f) mod + 360f else mod
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Accuracy changes do not interrupt telemetry streaming.
    }

    companion object {
        private const val LPF_ALPHA = 0.8f
        private const val COMPLEMENTARY_ALPHA = 0.96f
        private const val LOCATION_INTERVAL_MS = 500L
        private const val LOCATION_MIN_INTERVAL_MS = 250L
        private const val BAROMETER_SAMPLING_PERIOD_US = 200_000 // ~5 Hz
    }
}
