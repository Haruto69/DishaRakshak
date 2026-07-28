# System Architecture

Disha-Rakshak is designed as a modular GPS-less navigation system.

The system is divided into hardware, firmware, communication, processing, and visualization layers.

## High-Level Architecture

```text
Foot-Mounted IMU Module
        |
        | I2C
        v
ESP32 Foot Module
        |
        | ESP-NOW
        v
ESP32 Belt / Hub Module
        |
        | Serial / Wireless / Processing
        v
Path Estimation Software
        |
        v
Map / Path Visualization
```

## Main Modules

## 1. Foot Module

The foot module contains:

* ESP32 DEVKIT V1
* MPU6050 IMU
* Power source
* Foot/shoe mounting setup

The foot module is responsible for:

* Reading accelerometer data
* Reading gyroscope data
* Detecting movement
* Detecting stationary foot phases
* Sending processed data to the belt module

## 2. Belt / Hub Module

The belt module acts as a receiver and central processing point.

It is responsible for:

* Receiving foot module data
* Combining incoming sensor readings
* Forwarding data to a computer or visualization system
* Supporting integration with dead reckoning logic

## 3. Software / Visualization Module

The software module is responsible for:

* Processing movement data
* Estimating path
* Visualizing movement
* Displaying path output on a map or graph

## Sensor Data Flow

```text
MPU6050
  -> Raw acceleration and gyroscope readings
  -> Calibration
  -> Acceleration magnitude calculation
  -> Gyroscope magnitude calculation
  -> Stationary / moving classification
  -> ZUPT support
  -> Dead reckoning
  -> Path visualization
```

## Communication Flow

```text
Foot ESP32
  -> ESP-NOW packet
  -> Belt ESP32
  -> Serial output / computer processing
  -> Visualization software
```

## Current Development Stage

The project is currently in the unit testing stage.

Completed:

* I2C device detection
* Raw IMU data reading
* Basic calibration
* Basic movement detection

Pending:

* Foot-mounted walking test
* ESP-NOW sender/receiver integration
* Dead reckoning integration
* Path visualization testing
