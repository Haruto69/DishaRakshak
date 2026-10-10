import math


class DeadReckoning:
    """
    Disha-Rakshak Foot-Mounted IMU Dead Reckoning

    Input:
        ax, ay, az -> acceleration in m/s^2
        gx, gy, gz -> angular velocity in deg/s
        dt          -> time interval in seconds

    Output:
        Position: x, y, z in meters
        Velocity: vx, vy, vz in m/s
        Orientation:
            roll, pitch, heading in degrees
    """

    GRAVITY = 9.81

    # Complementary filter
    ACCEL_FILTER_ALPHA = 0.98

    # Acceleration smoothing
    ACCEL_SMOOTHING = 0.80

    # ZUPT thresholds
    STATIONARY_ACCEL_THRESHOLD = 0.35
    STATIONARY_GYRO_THRESHOLD = 5.0

    # Small acceleration values treated as noise
    ACCEL_DEADZONE = 0.08

    def __init__(self):

        # Position
        self.x = 0.0
        self.y = 0.0
        self.z = 0.0

        # Velocity
        self.vx = 0.0
        self.vy = 0.0
        self.vz = 0.0

        # Orientation
        self.roll = 0.0
        self.pitch = 0.0
        self.yaw = 0.0

        # Sensor biases
        self.ax_bias = 0.0
        self.ay_bias = 0.0
        self.az_bias = 0.0

        self.gx_bias = 0.0
        self.gy_bias = 0.0
        self.gz_bias = 0.0

        # Filtered acceleration
        self.filtered_ax = 0.0
        self.filtered_ay = 0.0
        self.filtered_az = 0.0

        self.calibrated = False

    # =========================================================
    # CALIBRATION
    # =========================================================

    def calibrate(
        self,
        ax=0.0,
        ay=0.0,
        az=9.81,
        gx=0.0,
        gy=0.0,
        gz=0.0,
        samples=None
    ):
        """
        Calibrate the IMU.

        Supports both:

        1. Individual values:
           dr.calibrate(
               ax=0,
               ay=0,
               az=9.81,
               gx=0,
               gy=0,
               gz=0
           )

        2. A list of samples:
           dr.calibrate(samples=[...])
        """

        # -----------------------------------------------------
        # If calibration samples are provided
        # -----------------------------------------------------

        if samples is not None:

            if len(samples) == 0:
                raise ValueError(
                    "Calibration sample list is empty."
                )

            total_ax = 0.0
            total_ay = 0.0
            total_az = 0.0

            total_gx = 0.0
            total_gy = 0.0
            total_gz = 0.0

            for sample in samples:

                sample_ax, sample_ay, sample_az, \
                sample_gx, sample_gy, sample_gz = sample

                total_ax += sample_ax
                total_ay += sample_ay
                total_az += sample_az

                total_gx += sample_gx
                total_gy += sample_gy
                total_gz += sample_gz

            count = len(samples)

            ax = total_ax / count
            ay = total_ay / count
            az = total_az / count

            gx = total_gx / count
            gy = total_gy / count
            gz = total_gz / count

        # -----------------------------------------------------
        # Accelerometer bias
        # -----------------------------------------------------

        self.ax_bias = ax
        self.ay_bias = ay

        # Accelerometer should read approximately +9.81
        # on Z while stationary and level.
        self.az_bias = az - self.GRAVITY

        # -----------------------------------------------------
        # Gyroscope bias
        # -----------------------------------------------------

        self.gx_bias = gx
        self.gy_bias = gy
        self.gz_bias = gz

        self.calibrated = True

        print("Calibration complete.")

    # =========================================================
    # ANGLE NORMALIZATION
    # =========================================================

    @staticmethod
    def normalize_angle(angle):

        while angle > 180.0:
            angle -= 360.0

        while angle < -180.0:
            angle += 360.0

        return angle

    # =========================================================
    # ORIENTATION UPDATE
    # =========================================================

    def update_orientation(
        self,
        ax,
        ay,
        az,
        gx,
        gy,
        gz,
        dt
    ):
        """
        Estimate roll, pitch and yaw.

        Roll/pitch:
            Gyroscope + accelerometer complementary filter.

        Yaw:
            Gyroscope integration.

        Note:
            MPU6050 has no magnetometer, so yaw will
            gradually drift in a real system.
        """

        # -----------------------------------------------------
        # Gyroscope integration
        # -----------------------------------------------------

        gyro_roll = self.roll + gx * dt
        gyro_pitch = self.pitch + gy * dt
        gyro_yaw = self.yaw + gz * dt

        # -----------------------------------------------------
        # Accelerometer magnitude
        # -----------------------------------------------------

        acceleration_magnitude = math.sqrt(
            ax * ax +
            ay * ay +
            az * az
        )

        # -----------------------------------------------------
        # Accelerometer roll/pitch
        # -----------------------------------------------------

        if (
            0.75 * self.GRAVITY
            <
            acceleration_magnitude
            <
            1.25 * self.GRAVITY
        ):

            accel_roll = math.degrees(
                math.atan2(ay, az)
            )

            accel_pitch = math.degrees(
                math.atan2(
                    -ax,
                    math.sqrt(
                        ay * ay +
                        az * az
                    )
                )
            )

            # Complementary filter

            self.roll = (
                self.ACCEL_FILTER_ALPHA * gyro_roll
                +
                (1.0 - self.ACCEL_FILTER_ALPHA)
                * accel_roll
            )

            self.pitch = (
                self.ACCEL_FILTER_ALPHA * gyro_pitch
                +
                (1.0 - self.ACCEL_FILTER_ALPHA)
                * accel_pitch
            )

        else:

            # Strong acceleration:
            # temporarily trust gyro more.

            self.roll = gyro_roll
            self.pitch = gyro_pitch

        self.yaw = self.normalize_angle(
            gyro_yaw
        )

    # =========================================================
    # BODY → WORLD COORDINATE TRANSFORMATION
    # =========================================================

    def body_to_world(self, ax, ay, az):

        roll = math.radians(self.roll)
        pitch = math.radians(self.pitch)
        yaw = math.radians(self.yaw)

        cr = math.cos(roll)
        sr = math.sin(roll)

        cp = math.cos(pitch)
        sp = math.sin(pitch)

        cy = math.cos(yaw)
        sy = math.sin(yaw)

        # Rotation matrix

        r11 = cy * cp
        r12 = cy * sp * sr - sy * cr
        r13 = cy * sp * cr + sy * sr

        r21 = sy * cp
        r22 = sy * sp * sr + cy * cr
        r23 = sy * sp * cr - cy * sr

        r31 = -sp
        r32 = cp * sr
        r33 = cp * cr

        world_x = (
            r11 * ax +
            r12 * ay +
            r13 * az
        )

        world_y = (
            r21 * ax +
            r22 * ay +
            r23 * az
        )

        world_z = (
            r31 * ax +
            r32 * ay +
            r33 * az
        )

        return world_x, world_y, world_z

    # =========================================================
    # GRAVITY REMOVAL
    # =========================================================

    def remove_gravity(self, ax, ay, az):

        world_x, world_y, world_z = (
            self.body_to_world(
                ax,
                ay,
                az
            )
        )

        # Remove gravity from world Z.

        linear_x = world_x
        linear_y = world_y
        linear_z = world_z - self.GRAVITY

        return (
            linear_x,
            linear_y,
            linear_z
        )

    # =========================================================
    # ACCELERATION SMOOTHING
    # =========================================================

    def smooth_acceleration(
        self,
        ax,
        ay,
        az
    ):

        alpha = self.ACCEL_SMOOTHING

        self.filtered_ax = (
            alpha * self.filtered_ax
            +
            (1.0 - alpha) * ax
        )

        self.filtered_ay = (
            alpha * self.filtered_ay
            +
            (1.0 - alpha) * ay
        )

        self.filtered_az = (
            alpha * self.filtered_az
            +
            (1.0 - alpha) * az
        )

        return (
            self.filtered_ax,
            self.filtered_ay,
            self.filtered_az
        )

    # =========================================================
    # DEADZONE
    # =========================================================

    def apply_deadzone(self, value):

        if abs(value) < self.ACCEL_DEADZONE:
            return 0.0

        return value

    # =========================================================
    # STATIONARY DETECTION
    # =========================================================

    def is_stationary(
        self,
        ax,
        ay,
        az,
        gx,
        gy,
        gz
    ):

        acceleration_magnitude = math.sqrt(
            ax * ax +
            ay * ay +
            az * az
        )

        gyro_magnitude = math.sqrt(
            gx * gx +
            gy * gy +
            gz * gz
        )

        acceleration_error = abs(
            acceleration_magnitude
            -
            self.GRAVITY
        )

        stationary_acceleration = (
            acceleration_error
            <
            self.STATIONARY_ACCEL_THRESHOLD
        )

        stationary_gyro = (
            gyro_magnitude
            <
            self.STATIONARY_GYRO_THRESHOLD
        )

        return (
            stationary_acceleration
            and
            stationary_gyro
        )

    # =========================================================
    # ZERO VELOCITY UPDATE
    # =========================================================

    def apply_zupt(self):

        self.vx = 0.0
        self.vy = 0.0
        self.vz = 0.0

    # =========================================================
    # MAIN UPDATE
    # =========================================================

    def update(
        self,
        ax,
        ay,
        az,
        gx,
        gy,
        gz,
        dt
    ):

        if not self.calibrated:

            raise RuntimeError(
                "IMU must be calibrated before update()."
            )

        # -----------------------------------------------------
        # Remove accelerometer bias
        # -----------------------------------------------------

        ax -= self.ax_bias
        ay -= self.ay_bias
        az -= self.az_bias

        # -----------------------------------------------------
        # Remove gyro bias
        # -----------------------------------------------------

        corrected_gx = gx - self.gx_bias
        corrected_gy = gy - self.gy_bias
        corrected_gz = gz - self.gz_bias

        # -----------------------------------------------------
        # Update orientation
        # -----------------------------------------------------

        self.update_orientation(
            ax,
            ay,
            az,
            corrected_gx,
            corrected_gy,
            corrected_gz,
            dt
        )

        # -----------------------------------------------------
        # Remove gravity
        # -----------------------------------------------------

        linear_ax, linear_ay, linear_az = (
            self.remove_gravity(
                ax,
                ay,
                az
            )
        )

        # -----------------------------------------------------
        # Smooth acceleration
        # -----------------------------------------------------

        linear_ax, linear_ay, linear_az = (
            self.smooth_acceleration(
                linear_ax,
                linear_ay,
                linear_az
            )
        )

        # -----------------------------------------------------
        # Deadzone
        # -----------------------------------------------------

        linear_ax = self.apply_deadzone(
            linear_ax
        )

        linear_ay = self.apply_deadzone(
            linear_ay
        )

        linear_az = self.apply_deadzone(
            linear_az
        )

        # -----------------------------------------------------
        # Stationary detection
        # -----------------------------------------------------

        stationary = self.is_stationary(
            ax,
            ay,
            az,
            corrected_gx,
            corrected_gy,
            corrected_gz
        )

        # -----------------------------------------------------
        # ZUPT
        # -----------------------------------------------------

        if stationary:

            self.apply_zupt()

        else:

            # -------------------------------------------------
            # Acceleration → velocity
            # -------------------------------------------------

            self.vx += linear_ax * dt
            self.vy += linear_ay * dt
            self.vz += linear_az * dt

            # -------------------------------------------------
            # Velocity → position
            # -------------------------------------------------

            self.x += self.vx * dt
            self.y += self.vy * dt
            self.z += self.vz * dt

        return self.get_state()

    # =========================================================
    # GET CURRENT STATE
    # =========================================================

    def get_state(self):

        return {
            "x": self.x,
            "y": self.y,
            "z": self.z,

            "vx": self.vx,
            "vy": self.vy,
            "vz": self.vz,

            "roll": self.roll,
            "pitch": self.pitch,
            "heading": self.yaw,

            "calibrated": self.calibrated
        }


# =============================================================
# DIRECT TEST
# =============================================================

if __name__ == "__main__":

    print("Disha-Rakshak Dead Reckoning Module")
    print("------------------------------------")

    dr = DeadReckoning()

    dr.calibrate(
        ax=0.0,
        ay=0.0,
        az=9.81,
        gx=0.0,
        gy=0.0,
        gz=0.0
    )

    print()
    print("Initial state:")
    print(dr.get_state())