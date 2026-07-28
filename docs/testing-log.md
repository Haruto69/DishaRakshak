# Testing Log

This file tracks major hardware and firmware tests for Disha-Rakshak.

## Test Entry 1

| Field     | Details         |
| --------- | --------------- |
| Date      | 2026-07-28      |
| Module    | Foot IMU module |
| Board     | ESP32 DEVKIT V1 |
| Sensor    | MPU6050         |
| Test Type | I2C Scanner     |
| Result    | Passed          |

## Output

```text
I2C device found at address: 0x68
```

## Notes

The ESP32 successfully detected the MPU6050 over I2C.

This confirmed that:

* Power was reaching the IMU
* SDA and SCL wiring were correct
* I2C communication was working
* The detected MPU6050 address was `0x68`

---

## Test Entry 2

| Field     | Details         |
| --------- | --------------- |
| Date      | 2026-07-28      |
| Module    | Foot IMU module |
| Board     | ESP32 DEVKIT V1 |
| Sensor    | MPU6050         |
| Test Type | Raw IMU Reading |
| Result    | Passed          |

## Still Output Sample

```text
Accel(g) -> X: 0.219 | Y: 0.011 | Z: -0.841 || Gyro(deg/s) -> X: -0.43 | Y: -0.12 | Z: -0.92
Accel(g) -> X: 0.211 | Y: 0.011 | Z: -0.854 || Gyro(deg/s) -> X: -0.18 | Y: -0.37 | Z: -1.12
Accel(g) -> X: 0.213 | Y: 0.009 | Z: -0.844 || Gyro(deg/s) -> X: -0.32 | Y: -0.18 | Z: -1.05
```

## Observations

* Accelerometer values changed during tilt
* Accelerometer values spiked during shake
* Gyroscope values spiked during rotation
* Values remained mostly stable when the IMU was still

## Notes

Raw IMU readings are working correctly.

---

## Test Entry 3

| Field     | Details                          |
| --------- | -------------------------------- |
| Date      | 2026-07-28                       |
| Module    | Foot IMU module                  |
| Board     | ESP32 DEVKIT V1                  |
| Sensor    | MPU6050                          |
| Test Type | Calibration + Movement Detection |
| Result    | Passed                           |

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

The MPU6050 hardware and basic firmware tests are successful.

The issue is no longer suspected to be wiring, soldering, power, I2C communication, or a dead IMU.

The next test should be performed with the IMU mounted on the foot or shoe.
