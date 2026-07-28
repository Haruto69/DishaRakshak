# IMU Testing Notes

This document records the MPU6050 unit tests performed for the Disha-Rakshak foot module.

## Test 1: I2C Scanner

## Objective

Check whether the ESP32 can detect the MPU6050 on the I2C bus.

## Wiring Used

| MPU6050 Pin | ESP32 DEVKIT V1 Pin |
| ----------- | ------------------- |
| VCC         | 3V3                 |
| GND         | GND                 |
| SDA         | GPIO 21             |
| SCL         | GPIO 22             |
| AD0 / ADO   | GND                 |

## Result

Passed.

Observed output:

```text
I2C device found at address: 0x68
```

## Conclusion

The ESP32 successfully detected the MPU6050.

This confirms:

* Power is reaching the IMU
* SDA and SCL wiring is correct
* I2C communication is working
* MPU6050 address is `0x68`
* The IMU is not dead

---

## Test 2: Raw Accelerometer and Gyroscope Test

## Objective

Check whether the MPU6050 returns changing accelerometer and gyroscope values.

## Still Output Sample

```text
Accel(g) -> X: 0.219 | Y: 0.011 | Z: -0.841 || Gyro(deg/s) -> X: -0.43 | Y: -0.12 | Z: -0.92
Accel(g) -> X: 0.211 | Y: 0.011 | Z: -0.854 || Gyro(deg/s) -> X: -0.18 | Y: -0.37 | Z: -1.12
Accel(g) -> X: 0.213 | Y: 0.009 | Z: -0.844 || Gyro(deg/s) -> X: -0.32 | Y: -0.18 | Z: -1.05
```

## Observations

When the IMU was still:

* Accelerometer values remained mostly stable
* Gyroscope values remained low

When the IMU was tilted:

* Accelerometer values changed

When the IMU was shaken:

* Accelerometer values spiked

When the IMU was rotated:

* Gyroscope values spiked

## Conclusion

The MPU6050 accelerometer and gyroscope are functional.

---

## Test 3: Calibration and Movement Detection

## Objective

Calibrate the MPU6050 and classify movement as either `STILL` or `MOVING`.

## Still Output Sample

```text
Accel Mag: 0.980 g | Gyro Mag: 0.88 deg/s | Status: STILL
Accel Mag: 0.983 g | Gyro Mag: 0.42 deg/s | Status: STILL
Accel Mag: 0.977 g | Gyro Mag: 1.98 deg/s | Status: STILL
```

## Movement Output Sample

```text
Accel Mag: 0.906 g | Gyro Mag: 61.52 deg/s | Status: MOVING
```

## Conclusion

The calibration and movement detection test passed.

This confirms:

* Basic calibration works
* Acceleration magnitude is close to `1g` when still
* Gyroscope magnitude is low when still
* Movement is detected when the IMU is shaken or rotated

## Diagnosis

The hardware is working correctly.

Remaining problems are likely in:

* Main project firmware
* Movement threshold logic
* ZUPT detection logic
* Axis assumptions after foot mounting
* Integration with ESP-NOW communication
* Dead reckoning logic

## Next Test

The next test should be performed with the IMU mounted on the foot or shoe.

Expected walking pattern:

```text
FOOT STATIONARY / ZUPT POSSIBLE
FOOT STATIONARY / ZUPT POSSIBLE
FOOT MOVING
FOOT MOVING
FOOT STATIONARY / ZUPT POSSIBLE
FOOT MOVING
FOOT MOVING
FOOT STATIONARY / ZUPT POSSIBLE
```
