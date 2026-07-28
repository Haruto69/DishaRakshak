# Hardware Setup

This document describes the current hardware setup used for Disha-Rakshak unit testing.

## Main Components

* ESP32 DEVKIT V1
* MPU6050 IMU module
* Jumper wires / soldered wires
* USB cable
* Computer with Arduino IDE

## ESP32 to MPU6050 Wiring

| MPU6050 Pin | ESP32 DEVKIT V1 Pin |
| ----------- | ------------------- |
| VCC         | 3V3                 |
| GND         | GND                 |
| SDA         | GPIO 21             |
| SCL         | GPIO 22             |
| AD0 / ADO   | GND                 |
| INT         | Not connected       |
| XDA         | Not connected       |
| XCL         | Not connected       |

## Important Notes

* Use `3V3`, not `VIN`, during testing.
* Connect ESP32 GND and MPU6050 GND together.
* Connect AD0/ADO to GND to force I2C address `0x68`.
* Keep wires short during testing.
* Do not connect XDA or XCL.
* INT is not required for basic polling-based IMU tests.

## Arduino IDE Settings

Recommended settings:

* Board: ESP32 Dev Module / DOIT ESP32 DEVKIT V1
* Baud rate: `115200`
* I2C SDA pin: `21`
* I2C SCL pin: `22`

## I2C Address

The MPU6050 was detected at:

```text
0x68
```

This confirms that the ESP32 can communicate with the IMU over I2C.

## Current Hardware Status

| Test                           | Status  |
| ------------------------------ | ------- |
| ESP32 powered through USB      | Working |
| MPU6050 powered from ESP32 3V3 | Working |
| I2C detection                  | Passed  |
| Raw accelerometer reading      | Passed  |
| Raw gyroscope reading          | Passed  |
| Basic calibration              | Passed  |
| Movement detection             | Passed  |
| Foot-mounted walking test      | Pending |

## Debugging Checklist

If the MPU6050 is not detected:

1. Check whether VCC is connected to `3V3`.
2. Check whether GND is common between ESP32 and MPU6050.
3. Check whether SDA is connected to GPIO `21`.
4. Check whether SCL is connected to GPIO `22`.
5. Check whether AD0/ADO is connected to GND.
6. Run the I2C scanner before testing any full project code.
7. Check solder joints and wire continuity using a multimeter.
8. Try the second MPU6050 module only after wiring and pins are verified.
