# Calibration Notes

Calibration is required because raw IMU values contain offset errors. These offsets can affect movement detection and dead-reckoning accuracy.

## Why Calibration Is Needed

The MPU6050 may produce non-zero readings even when it is completely still.

For example:

* Accelerometer axes may not read perfect `0g`, `0g`, and `1g`
* Gyroscope axes may not read exactly `0 deg/s`
* Small errors can accumulate over time
* Dead reckoning is sensitive to drift

## Current Calibration Method

The current calibration method:

1. Keeps the IMU completely still
2. Collects multiple raw samples
3. Averages accelerometer and gyroscope readings
4. Calculates offsets
5. Subtracts offsets from future readings

## Calibration Procedure

During startup:

1. Place the module flat and still.
2. Do not touch it.
3. Wait for calibration to complete.
4. Begin movement testing only after calibration.

## Current Calibration Result

After calibration, still readings were approximately:

```text
Accel Mag: 0.972 g to 0.986 g
Gyro Mag: mostly below 2 deg/s
Status: STILL
```

This is acceptable for the current unit testing stage.

## Stationary Detection Logic

Current basic stationary condition:

```cpp
bool stationary = abs(accelMag - 1.0) < 0.15 && gyroMag < 15.0;
```

This means the foot is considered stationary when:

* Acceleration magnitude is close to `1g`
* Gyroscope magnitude is low

## Why Stationary Detection Matters

In foot-mounted inertial navigation, the foot repeatedly alternates between:

```text
stationary on ground
moving through air
stationary on ground
moving through air
```

The stationary phase is useful for Zero Velocity Update.

## ZUPT Relevance

ZUPT stands for Zero Velocity Update.

When the foot is stationary on the ground, its velocity should be approximately zero. This known zero-velocity condition can be used to correct drift in the velocity estimate.

## Threshold Tuning

The current thresholds are intentionally loose because the project is still in unit testing.

Current condition:

```cpp
abs(accelMag - 1.0) < 0.15 && gyroMag < 15.0
```

Possible stricter condition after testing:

```cpp
abs(accelMag - 1.0) < 0.08 && gyroMag < 5.0
```

The stricter condition should only be used after collecting foot-mounted walking data.

## Next Calibration Improvements

Future improvements may include:

* Longer calibration sampling
* Saving offsets permanently
* Testing different foot orientations
* Separate calibration for left and right foot modules
* Dynamic threshold tuning
* Filtering noisy IMU data
* Adding moving average or low-pass filtering
