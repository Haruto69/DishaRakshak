import java.util.Random

/**
 * Simulated foot-mounted IMU (Kotlin port of imu_simulator.py).
 *
 * Coordinate system:
 *   X = forward, Y = sideways, Z = vertical
 *
 * The simulated foot moves forward, pitches during each step, has small roll
 * and yaw movement, and is stationary during stance.
 */
object ImuSimulator {

    // Python uses the unseeded global `random` module (different noise each run).
    // Kotlin uses a java.util.Random; the default is unseeded to match that behavior.
    // Call setSeed() for a repeatable run.
    private var random = Random()

    fun setSeed(seed: Long) {
        random = Random(seed)
    }

    /** Equivalent of random.gauss(0.0, sigma). */
    private fun gauss(sigma: Double): Double = random.nextGaussian() * sigma

    /** Equivalent of generate_walking_data(t). Returns (ax, ay, az, gx, gy, gz). */
    fun generateWalkingData(t: Double): ImuSample {

        val stepTime = 1.0
        val phase = t % stepTime

        // Default stationary state
        var ax = 0.0
        var ay = 0.0
        var az = 9.81

        var gx = 0.0
        var gy = 0.0
        var gz = 0.0

        // Swing phase
        if (phase < 0.45) {

            // Forward acceleration
            ax = 2.0

            // Very small sideways acceleration
            ay = 0.0

            // Small vertical movement
            az = 9.81 + 0.25

            // Foot pitch
            gy = 35.0

            // Small roll
            gx = 15.0

            // Small heading change
            gz = 8.0
        }

        // Add realistic sensor noise
        ax += gauss(0.03)
        ay += gauss(0.02)
        az += gauss(0.03)

        gx += gauss(0.5)
        gy += gauss(0.5)
        gz += gauss(0.5)

        return ImuSample(ax, ay, az, gx, gy, gz)
    }

    /** Equivalent of main() in imu_simulator.py: a full 10 s walk with a progress table. */
    fun runSimulation(): DeadReckoning {

        val dr = DeadReckoning()

        dr.calibrate(
            ax = 0.0,
            ay = 0.0,
            az = 9.81,
            gx = 0.0,
            gy = 0.0,
            gz = 0.0
        )

        println()
        println("Disha-Rakshak IMU Simulator")
        println("---------------------------")
        println("Simulating straight foot-mounted walking...")
        println()

        val dt = 0.02 // 50 Hz
        val simulationTime = 10.0

        var nextDisplay = 0.0
        var t = 0.0

        while (t <= simulationTime) {

            val s = generateWalkingData(t)

            val state = dr.update(
                ax = s.ax,
                ay = s.ay,
                az = s.az,
                gx = s.gx,
                gy = s.gy,
                gz = s.gz,
                dt = dt
            )

            // Print every 0.5 seconds
            if (t >= nextDisplay) {
                println(
                    String.format(
                        "Time: %5.2fs | X: %7.3f m | Y: %7.3f m | Z: %7.3f m | " +
                            "Heading: %6.2f° | Roll: %6.2f° | Pitch: %6.2f°",
                        t, state.x, state.y, state.z, state.heading, state.roll, state.pitch
                    )
                )
                nextDisplay += 0.5
            }

            t += dt
        }

        return dr
    }
}
