/**
 * Kotlin test for DeadReckoning using ImuSimulator.
 *
 * Mirrors the API test setup: 500 samples at dt = 0.02 s (50 Hz, 10 s of walking).
 * Every number printed is computed by running the Kotlin algorithm; nothing is hardcoded.
 *
 * Usage:
 *   DeadReckoningTestKt            -> random noise (like Python)
 *   DeadReckoningTestKt <seed>     -> repeatable noise
 */
fun main(args: Array<String>) {

    if (args.isNotEmpty()) {
        ImuSimulator.setSeed(args[0].toLong())
    }

    val dr = DeadReckoning()

    // Calibration (same values as the Python module)
    dr.calibrate(
        ax = 0.0,
        ay = 0.0,
        az = 9.81,
        gx = 0.0,
        gy = 0.0,
        gz = 0.0
    )

    println()
    println("Disha-Rakshak Kotlin Dead Reckoning Test")
    println("----------------------------------------")
    println("Running 500 samples at dt = 0.02 s (50 Hz)...")
    println()

    val dt = 0.02
    val totalSamples = 500

    var sampleCount = 0

    for (i in 0 until totalSamples) {

        val t = i * dt

        val s = ImuSimulator.generateWalkingData(t)

        val state = dr.update(
            ax = s.ax,
            ay = s.ay,
            az = s.az,
            gx = s.gx,
            gy = s.gy,
            gz = s.gz,
            dt = dt
        )

        sampleCount++

        // Print every 25 samples
        if (sampleCount % 25 == 0) {
            println(
                String.format(
                    "Sample %4d | X=%6.2f m | Y=%6.2f m | Z=%6.2f m | Heading=%6.2f°",
                    sampleCount, state.x, state.y, state.z, state.heading
                )
            )
        }
    }

    val finalState = dr.getState()

    println()
    println("Final position:")
    println(String.format("X = %.3f m", finalState.x))
    println(String.format("Y = %.3f m", finalState.y))
    println(String.format("Z = %.3f m", finalState.z))

    println()
    println("Final velocity:")
    println(String.format("VX = %.3f m/s", finalState.vx))
    println(String.format("VY = %.3f m/s", finalState.vy))
    println(String.format("VZ = %.3f m/s", finalState.vz))

    println()
    println("Final orientation:")
    println(String.format("Roll    = %.2f°", finalState.roll))
    println(String.format("Pitch   = %.2f°", finalState.pitch))
    println(String.format("Heading = %.2f°", finalState.heading))

    // Extra checks of the remaining API surface (calibration from samples, reset)
    println()
    println("Checks:")

    val sampleCal = DeadReckoning()
    sampleCal.calibrate(
        samples = listOf(
            ImuSample(0.1, -0.1, 9.91, 0.2, -0.2, 0.0),
            ImuSample(-0.1, 0.1, 9.71, -0.2, 0.2, 0.0)
        )
    )
    println("  calibrate(samples) -> calibrated = ${sampleCal.getState().calibrated}")

    dr.reset()
    val afterReset = dr.getState()
    val resetOk = afterReset.x == 0.0 && afterReset.y == 0.0 && afterReset.z == 0.0 &&
        afterReset.vx == 0.0 && afterReset.vy == 0.0 && afterReset.vz == 0.0 &&
        afterReset.roll == 0.0 && afterReset.pitch == 0.0 && afterReset.heading == 0.0
    println("  reset()            -> state cleared = $resetOk")

    println()
    println("----------------------------------------")
    println("Kotlin test completed. Samples processed: $sampleCount")
    println()
}
