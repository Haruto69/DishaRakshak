import math
import random

from dead_reckoning import DeadReckoning


# ============================================================
# SIMULATED FOOT-MOUNTED IMU
# ============================================================

def generate_walking_data(t):
    """
    Generate simulated IMU data for a person walking forward.

    Coordinate system:

        X = forward
        Y = sideways
        Z = vertical

    The simulated foot:
        - moves forward
        - pitches during each step
        - has small roll movement
        - has small yaw movement
        - becomes stationary during stance
    """

    step_time = 1.0
    phase = t % step_time

    # --------------------------------------------------------
    # Default stationary state
    # --------------------------------------------------------

    ax = 0.0
    ay = 0.0
    az = 9.81

    gx = 0.0
    gy = 0.0
    gz = 0.0

    # --------------------------------------------------------
    # Swing phase
    # --------------------------------------------------------

    if phase < 0.45:

        # Forward acceleration
        ax = 2.0

        # Very small sideways acceleration
        ay = 0.0

        # Small vertical movement
        az = 9.81 + 0.25

        # Foot pitch
        gy = 35.0

        # Small roll
        gx = 15.0

        # Small heading change
        gz = 8.0

    # --------------------------------------------------------
    # Add realistic sensor noise
    # --------------------------------------------------------

    ax += random.gauss(0.0, 0.03)
    ay += random.gauss(0.0, 0.02)
    az += random.gauss(0.0, 0.03)

    gx += random.gauss(0.0, 0.5)
    gy += random.gauss(0.0, 0.5)
    gz += random.gauss(0.0, 0.5)

    return ax, ay, az, gx, gy, gz


# ============================================================
# MAIN
# ============================================================

def main():

    dr = DeadReckoning()

    # --------------------------------------------------------
    # Calibration
    # --------------------------------------------------------

    dr.calibrate(
        ax=0.0,
        ay=0.0,
        az=9.81,
        gx=0.0,
        gy=0.0,
        gz=0.0
    )

    print()
    print("Disha-Rakshak IMU Simulator")
    print("---------------------------")
    print("Simulating straight foot-mounted walking...")
    print()

    # --------------------------------------------------------
    # Simulation settings
    # --------------------------------------------------------

    dt = 0.02          # 50 Hz
    simulation_time = 10.0

    next_display = 0.0

    # --------------------------------------------------------
    # Run simulation
    # --------------------------------------------------------

    t = 0.0

    while t <= simulation_time:

        ax, ay, az, gx, gy, gz = generate_walking_data(t)

        state = dr.update(
            ax=ax,
            ay=ay,
            az=az,
            gx=gx,
            gy=gy,
            gz=gz,
            dt=dt
        )

        # Print every 0.5 seconds
        if t >= next_display:

            print(
                f"Time: {t:5.2f}s | "
                f"X: {state['x']:7.3f} m | "
                f"Y: {state['y']:7.3f} m | "
                f"Z: {state['z']:7.3f} m | "
                f"Heading: {state['heading']:6.2f}° | "
                f"Roll: {state['roll']:6.2f}° | "
                f"Pitch: {state['pitch']:6.2f}°"
            )

            next_display += 0.5

        t += dt

    # --------------------------------------------------------
    # Final result
    # --------------------------------------------------------

    print()
    print("Simulation complete.")
    print()

    print("Final position:")
    print(f"X = {dr.x:.3f} m")
    print(f"Y = {dr.y:.3f} m")
    print(f"Z = {dr.z:.3f} m")

    print()
    print("Final velocity:")
    print(f"VX = {dr.vx:.3f} m/s")
    print(f"VY = {dr.vy:.3f} m/s")
    print(f"VZ = {dr.vz:.3f} m/s")

    print()
    print("Final orientation:")
    print(f"Roll    = {dr.roll:.2f}°")
    print(f"Pitch   = {dr.pitch:.2f}°")
    print(f"Heading = {dr.yaw:.2f}°")


# ============================================================
# RUN
# ============================================================

if __name__ == "__main__":
    main()