# Problem Statement

GPS-based navigation is unreliable or unavailable in many indoor and emergency environments.

Locations such as buildings, campuses, underground spaces, smoke-filled areas, and disaster-affected zones may not provide stable GPS signals.

In such cases, users may need navigation support without depending on satellite positioning. This creates the need for a GPS-less navigation system that can estimate movement using onboard sensors.

Disha-Rakshak aims to solve this problem by using foot-mounted inertial sensors to estimate user movement and support navigation in GPS-denied environments.

## Problem Summary

The system must estimate user movement without GPS by using sensor data from an IMU attached to the foot.

The movement data can then be processed to support path estimation and indoor navigation.

## Key Challenges

* GPS signals are weak or unavailable indoors
* IMU readings suffer from drift over time
* Foot movement includes both moving and stationary phases
* Sensor calibration is required for usable readings
* Movement detection thresholds must be tuned carefully
* Wireless communication must be stable between modules
* Final path estimation must remain understandable and useful

## Proposed Solution

Disha-Rakshak uses foot-mounted IMU modules with ESP32 microcontrollers.

The IMU captures accelerometer and gyroscope data. The ESP32 processes this data to detect foot movement and stationary phases.

The stationary phases are important because they allow Zero Velocity Update (ZUPT), which can reduce accumulated velocity drift during dead reckoning.

## Expected Outcome

The expected outcome is a working prototype that can:

* Detect foot movement
* Detect stationary foot phases
* Support ZUPT-based correction
* Estimate movement without GPS
* Send sensor data wirelessly
* Support path visualization during testing

## Scope

The current scope focuses on:

* Hardware unit testing
* IMU calibration
* Foot-mounted movement detection
* ESP32-based firmware
* ESP-NOW communication
* Basic path estimation and visualization

Advanced production-level accuracy, commercial deployment, and full-scale indoor mapping are outside the current prototype scope.
