# Papers and Links

This file stores useful references for Disha-Rakshak.

## Topics to Study

* Foot-mounted inertial navigation
* Zero Velocity Update
* Dead reckoning
* IMU drift correction
* ESP32 ESP-NOW communication
* MPU6050 calibration
* Indoor navigation systems
* Sensor fusion

## Useful Search Keywords

```text
foot mounted inertial navigation ZUPT
zero velocity update IMU navigation
MPU6050 ESP32 Arduino calibration
ESP32 ESP-NOW sender receiver Arduino
indoor navigation without GPS IMU
dead reckoning using accelerometer gyroscope
```

## Hardware References

Add links here for:

* ESP32 DEVKIT V1 pinout
* MPU6050 datasheet
* MPU6050 Arduino examples
* ESP-NOW documentation
* I2C scanner examples

## Research References

Add papers and notes here as the project develops.

Suggested reference categories:

| Category          | Notes                                          |
| ----------------- | ---------------------------------------------- |
| ZUPT              | Stationary foot detection and drift correction |
| Dead reckoning    | Position estimation from inertial data         |
| IMU calibration   | Offset correction and drift handling           |
| Sensor fusion     | Combining accelerometer and gyroscope readings |
| Indoor navigation | Navigation in GPS-denied environments          |

## Current Notes

The current unit tests show that the MPU6050 hardware is functional.

Future research should focus on:

* Improving ZUPT detection
* Reducing IMU drift
* Improving foot-mounted motion classification
* Testing real walking data
* Integrating ESP-NOW communication
* Building reliable path estimation logic
