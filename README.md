# Disha-Rakshak

Disha-Rakshak is a GPS-less indoor navigation prototype designed to estimate user movement using foot-mounted IMUs and ESP32-based wireless modules.

The system focuses on navigation support in environments where GPS is unavailable, unreliable, or inaccessible, especially during emergencies and indoor navigation scenarios.

## Project Objective

The objective of this project is to build a low-cost GPS-less navigation system that can estimate user movement using inertial sensors.

The system uses foot-mounted IMU modules to detect motion, identify stationary phases, and support dead-reckoning-based path estimation.

## Core Idea

The system uses an MPU6050 IMU connected to an ESP32 foot module.

The IMU captures acceleration and angular velocity data. The ESP32 processes this data to detect whether the foot is moving or stationary.

Stationary foot phases are useful for Zero Velocity Update (ZUPT), which helps reduce drift in inertial navigation systems.

## Current Hardware

* ESP32 DEVKIT V1
* MPU6050 IMU module
* Foot-mounted sensor module
* Belt/hub ESP32 module
* Jumper wires / soldered wires
* USB cable for programming and testing

## Core Technologies

* ESP32
* MPU6050
* Arduino IDE
* I2C communication
* ESP-NOW communication
* IMU-based motion sensing
* Dead reckoning
* Zero Velocity Update (ZUPT)
* Path visualization

## Repository Structure

```text
Disha-Rakshak/
├── README.md
├── docs/
│   ├── problem-statement.md
│   ├── architecture.md
│   ├── hardware-setup.md
│   ├── imu-testing.md
│   ├── calibration-notes.md
│   └── testing-log.md
│
├── hardware/
│   ├── wiring-diagrams/
│   └── components-list.md
│
├── firmware/
│   ├── foot-module/
│   │   ├── i2c-scanner/
│   │   ├── raw-imu-test/
│   │   ├── calibration-test/
│   │   └── foot-zupt-test/
│   │
│   └── belt-module/
│       └── esp-now-receiver/
│
├── software/
│   ├── path-visualization/
│   └── map-processing/
│
├── results/
│   ├── serial-monitor-logs/
│   ├── unit-tests/
│   └── screenshots/
│
└── references/
    └── papers-and-links.md
```

## Current Status

Initial MPU6050 unit testing has been completed successfully.

Confirmed:

* ESP32 detects MPU6050 at I2C address `0x68`
* Accelerometer values change during tilt and shake
* Gyroscope values spike during rotation
* Basic calibration works
* Basic movement/stationary detection works

## Latest Test Result

Still condition:

```text
Accel Mag: 0.980 g | Gyro Mag: 0.88 deg/s | Status: STILL
```

Movement condition:

```text
Accel Mag: 0.906 g | Gyro Mag: 61.52 deg/s | Status: MOVING
```

## Next Steps

* Test the module while mounted on the foot/shoe
* Verify walking movement pattern
* Tune stationary detection thresholds
* Implement ZUPT-based correction
* Add ESP-NOW communication between foot module and belt module
* Integrate movement data with path estimation
* Visualize movement on a map/path interface

## Project Use Case

Disha-Rakshak is intended for environments where GPS cannot be used effectively, such as:

* Indoor buildings
* Emergency evacuation routes
* Smoke-filled or low-visibility environments
* Large campuses
* Disaster-response scenarios
* Underground or signal-blocked areas

## License

This project is currently developed as an academic prototype.
