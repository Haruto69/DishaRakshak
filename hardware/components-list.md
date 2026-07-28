# Components List

This document lists the hardware components used in the Disha-Rakshak prototype.

## Main Components

| Component                     |    Quantity | Purpose                                   |
| ----------------------------- | ----------: | ----------------------------------------- |
| ESP32 DEVKIT V1               |          1+ | Microcontroller for foot and belt modules |
| MPU6050 IMU                   |          1+ | Accelerometer and gyroscope readings      |
| Jumper wires / soldered wires |   As needed | Electrical connections                    |
| USB cable                     |           1 | Programming and serial monitor testing    |
| Computer with Arduino IDE     |           1 | Firmware upload and debugging             |
| Breadboard / perfboard        |    Optional | Temporary or stable prototyping           |
| Battery source                | Later stage | Portable field testing                    |

## Current Foot Module Components

| Component         | Status    |
| ----------------- | --------- |
| ESP32 DEVKIT V1   | Available |
| MPU6050 IMU       | Available |
| I2C wiring        | Tested    |
| Soldering         | Done      |
| Foot mounting     | Pending   |
| Battery operation | Pending   |

## MPU6050 to ESP32 DEVKIT V1 Wiring

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

## Notes

* MPU6050 default I2C address is `0x68` when AD0/ADO is connected to GND.
* Use 3.3V for safer ESP32 testing.
* Keep jumper wires short during debugging.
* If I2C detection becomes unstable, check SDA/SCL wiring and solder joints.
* If the MPU6050 is not detected, run the I2C scanner before testing any full project code.

## Optional Components for Later Testing

| Component                 | Purpose                                  |
| ------------------------- | ---------------------------------------- |
| 4.7kΩ resistors           | I2C pull-ups if required                 |
| Velcro strap / shoe mount | Stable foot mounting                     |
| Battery pack              | Portable testing                         |
| Enclosure                 | Protect electronics during walking tests |
| Multimeter                | Voltage and continuity checks            |
| Second ESP32              | Belt/hub receiver module                 |
