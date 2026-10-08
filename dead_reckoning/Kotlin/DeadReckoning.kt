import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Disha-Rakshak Foot-Mounted IMU Dead Reckoning (Kotlin port of dead_reckoning.py)
 *
 * Input:
 *   ax, ay, az -> acceleration in m/s^2
 *   gx, gy, gz -> angular velocity in deg/s
 *   dt         -> time interval in seconds
 *
 * Output:
 *   Position: x, y, z in meters
 *   Velocity: vx, vy, vz in m/s
 *   Orientation: roll, pitch, heading in degrees
 */

/** One IMU reading: (ax, ay, az, gx, gy, gz). */
data class ImuSample(
    val ax: Double,
    val ay: Double,
    val az: Double,
    val gx: Double,
    val gy: Double,
    val gz: Double
)

/** Snapshot returned by update() / getState(). */
data class DeadReckoningState(
    val x: Double,
    val y: Double,
    val z: Double,
    val vx: Double,
    val vy: Double,
    val vz: Double,
    val roll: Double,
    val pitch: Double,
    val heading: Double,
    val calibrated: Boolean
)

class DeadReckoning {

    companion object {
        const val GRAVITY = 9.81

        // Complementary filter
        const val ACCEL_FILTER_ALPHA = 0.98

        // Acceleration smoothing
        const val ACCEL_SMOOTHING = 0.80

        // ZUPT thresholds
        const val STATIONARY_ACCEL_THRESHOLD = 0.35
        const val STATIONARY_GYRO_THRESHOLD = 5.0

        // Small acceleration values treated as noise
        const val ACCEL_DEADZONE = 0.08

        fun normalizeAngle(angle: Double): Double {
            var a = angle
            while (a > 180.0) {
                a -= 360.0
            }
            while (a < -180.0) {
                a += 360.0
            }
            return a
        }
    }

    // Position
    var x = 0.0
    var y = 0.0
    var z = 0.0

    // Velocity
    var vx = 0.0
    var vy = 0.0
    var vz = 0.0

    // Orientation
    var roll = 0.0
    var pitch = 0.0
    var yaw = 0.0

    // Sensor biases
    private var axBias = 0.0
    private var ayBias = 0.0
    private var azBias = 0.0

    private var gxBias = 0.0
    private var gyBias = 0.0
    private var gzBias = 0.0

    // Filtered acceleration
    private var filteredAx = 0.0
    private var filteredAy = 0.0
    private var filteredAz = 0.0

    var calibrated = false
        private set

    // =========================================================
    // CALIBRATION
    // =========================================================

    /**
     * Calibrate with individual values, or pass [samples] to average a list.
     * If [samples] is non-null, the individual values are ignored (as in Python).
     */
    fun calibrate(
        ax: Double = 0.0,
        ay: Double = 0.0,
        az: Double = 9.81,
        gx: Double = 0.0,
        gy: Double = 0.0,
        gz: Double = 0.0,
        samples: List<ImuSample>? = null
    ) {
        var cax = ax
        var cay = ay
        var caz = az
        var cgx = gx
        var cgy = gy
        var cgz = gz

        if (samples != null) {
            if (samples.isEmpty()) {
                throw IllegalArgumentException("Calibration sample list is empty.")
            }

            var totalAx = 0.0
            var totalAy = 0.0
            var totalAz = 0.0
            var totalGx = 0.0
            var totalGy = 0.0
            var totalGz = 0.0

            for (sample in samples) {
                totalAx += sample.ax
                totalAy += sample.ay
                totalAz += sample.az
                totalGx += sample.gx
                totalGy += sample.gy
                totalGz += sample.gz
            }

            val count = samples.size.toDouble()

            cax = totalAx / count
            cay = totalAy / count
            caz = totalAz / count
            cgx = totalGx / count
            cgy = totalGy / count
            cgz = totalGz / count
        }

        // Accelerometer bias
        axBias = cax
        ayBias = cay
        // Accelerometer should read approximately +9.81 on Z while stationary and level.
        azBias = caz - GRAVITY

        // Gyroscope bias
        gxBias = cgx
        gyBias = cgy
        gzBias = cgz

        calibrated = true

        println("Calibration complete.")
    }

    // =========================================================
    // ANGLE NORMALIZATION
    // =========================================================

    fun normalizeAngle(angle: Double): Double = Companion.normalizeAngle(angle)

    // =========================================================
    // ORIENTATION UPDATE
    // =========================================================

    /**
     * Roll/pitch: gyroscope + accelerometer complementary filter.
     * Yaw: gyroscope integration only (MPU6050 has no magnetometer, so yaw drifts).
     */
    fun updateOrientation(
        ax: Double,
        ay: Double,
        az: Double,
        gx: Double,
        gy: Double,
        gz: Double,
        dt: Double
    ) {
        // Gyroscope integration
        val gyroRoll = roll + gx * dt
        val gyroPitch = pitch + gy * dt
        val gyroYaw = yaw + gz * dt

        // Accelerometer magnitude
        val accelerationMagnitude = sqrt(ax * ax + ay * ay + az * az)

        if (accelerationMagnitude > 0.75 * GRAVITY && accelerationMagnitude < 1.25 * GRAVITY) {

            val accelRoll = Math.toDegrees(atan2(ay, az))

            val accelPitch = Math.toDegrees(
                atan2(-ax, sqrt(ay * ay + az * az))
            )

            // Complementary filter
            roll = ACCEL_FILTER_ALPHA * gyroRoll + (1.0 - ACCEL_FILTER_ALPHA) * accelRoll
            pitch = ACCEL_FILTER_ALPHA * gyroPitch + (1.0 - ACCEL_FILTER_ALPHA) * accelPitch

        } else {
            // Strong acceleration: temporarily trust gyro more.
            roll = gyroRoll
            pitch = gyroPitch
        }

        yaw = normalizeAngle(gyroYaw)
    }

    // =========================================================
    // BODY -> WORLD COORDINATE TRANSFORMATION
    // =========================================================

    fun bodyToWorld(ax: Double, ay: Double, az: Double): Triple<Double, Double, Double> {
        val rollRad = Math.toRadians(roll)
        val pitchRad = Math.toRadians(pitch)
        val yawRad = Math.toRadians(yaw)

        val cr = cos(rollRad)
        val sr = sin(rollRad)

        val cp = cos(pitchRad)
        val sp = sin(pitchRad)

        val cy = cos(yawRad)
        val sy = sin(yawRad)

        // Rotation matrix
        val r11 = cy * cp
        val r12 = cy * sp * sr - sy * cr
        val r13 = cy * sp * cr + sy * sr

        val r21 = sy * cp
        val r22 = sy * sp * sr + cy * cr
        val r23 = sy * sp * cr - cy * sr

        val r31 = -sp
        val r32 = cp * sr
        val r33 = cp * cr

        val worldX = r11 * ax + r12 * ay + r13 * az
        val worldY = r21 * ax + r22 * ay + r23 * az
        val worldZ = r31 * ax + r32 * ay + r33 * az

        return Triple(worldX, worldY, worldZ)
    }

    // =========================================================
    // GRAVITY REMOVAL
    // =========================================================

    fun removeGravity(ax: Double, ay: Double, az: Double): Triple<Double, Double, Double> {
        val (worldX, worldY, worldZ) = bodyToWorld(ax, ay, az)

        // Remove gravity from world Z.
        return Triple(worldX, worldY, worldZ - GRAVITY)
    }

    // =========================================================
    // ACCELERATION SMOOTHING
    // =========================================================

    fun smoothAcceleration(ax: Double, ay: Double, az: Double): Triple<Double, Double, Double> {
        val alpha = ACCEL_SMOOTHING

        filteredAx = alpha * filteredAx + (1.0 - alpha) * ax
        filteredAy = alpha * filteredAy + (1.0 - alpha) * ay
        filteredAz = alpha * filteredAz + (1.0 - alpha) * az

        return Triple(filteredAx, filteredAy, filteredAz)
    }

    // =========================================================
    // DEADZONE
    // =========================================================

    fun applyDeadzone(value: Double): Double {
        if (abs(value) < ACCEL_DEADZONE) {
            return 0.0
        }
        return value
    }

    // =========================================================
    // STATIONARY DETECTION
    // =========================================================

    fun isStationary(
        ax: Double,
        ay: Double,
        az: Double,
        gx: Double,
        gy: Double,
        gz: Double
    ): Boolean {
        val accelerationMagnitude = sqrt(ax * ax + ay * ay + az * az)
        val gyroMagnitude = sqrt(gx * gx + gy * gy + gz * gz)

        val accelerationError = abs(accelerationMagnitude - GRAVITY)

        val stationaryAcceleration = accelerationError < STATIONARY_ACCEL_THRESHOLD
        val stationaryGyro = gyroMagnitude < STATIONARY_GYRO_THRESHOLD

        return stationaryAcceleration && stationaryGyro
    }

    // =========================================================
    // ZERO VELOCITY UPDATE
    // =========================================================

    fun applyZupt() {
        vx = 0.0
        vy = 0.0
        vz = 0.0
    }

    // =========================================================
    // MAIN UPDATE
    // =========================================================

    fun update(
        ax: Double,
        ay: Double,
        az: Double,
        gx: Double,
        gy: Double,
        gz: Double,
        dt: Double
    ): DeadReckoningState {

        if (!calibrated) {
            throw IllegalStateException("IMU must be calibrated before update().")
        }

        // Remove accelerometer bias
        val cax = ax - axBias
        val cay = ay - ayBias
        val caz = az - azBias

        // Remove gyro bias
        val correctedGx = gx - gxBias
        val correctedGy = gy - gyBias
        val correctedGz = gz - gzBias

        // Update orientation
        updateOrientation(cax, cay, caz, correctedGx, correctedGy, correctedGz, dt)

        // Remove gravity
        val linear = removeGravity(cax, cay, caz)

        // Smooth acceleration
        val smoothed = smoothAcceleration(linear.first, linear.second, linear.third)

        // Deadzone
        val linearAx = applyDeadzone(smoothed.first)
        val linearAy = applyDeadzone(smoothed.second)
        val linearAz = applyDeadzone(smoothed.third)

        // Stationary detection
        val stationary = isStationary(cax, cay, caz, correctedGx, correctedGy, correctedGz)

        if (stationary) {
            // ZUPT
            applyZupt()
        } else {
            // Acceleration -> velocity
            vx += linearAx * dt
            vy += linearAy * dt
            vz += linearAz * dt

            // Velocity -> position
            x += vx * dt
            y += vy * dt
            z += vz * dt
        }

        return getState()
    }

    // =========================================================
    // GET CURRENT STATE
    // =========================================================

    fun getState(): DeadReckoningState = DeadReckoningState(
        x = x,
        y = y,
        z = z,
        vx = vx,
        vy = vy,
        vz = vz,
        roll = roll,
        pitch = pitch,
        heading = yaw,
        calibrated = calibrated
    )

    // =========================================================
    // RESET
    // =========================================================

    /**
     * Same behavior as POST /reset in api.py: clears position, velocity and
     * orientation. Calibration biases and the acceleration filter state are
     * left untouched, exactly as the Flask endpoint does.
     */
    fun reset() {
        x = 0.0
        y = 0.0
        z = 0.0

        vx = 0.0
        vy = 0.0
        vz = 0.0

        roll = 0.0
        pitch = 0.0
        yaw = 0.0
    }
}
