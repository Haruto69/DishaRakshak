#include <Arduino.h>
#include <Wire.h>
#include <math.h>

// ============================================================
// Disha-Rakshak
// Foot IMU Test 01 - Raw Sensor Characterization
//
// PURPOSE:
//   Measure the raw behaviour of the IMU before applying
//   calibration, filtering, orientation estimation or ZUPT.
//
// WHAT THIS TEST TELLS US:
//   1. WHO_AM_I value
//   2. Raw accelerometer behaviour
//   3. Raw gyroscope behaviour
//   4. Accelerometer magnitude
//   5. Mean stationary gyro bias
//   6. Accelerometer and gyroscope noise
//
// DO NOT MOVE THE SENSOR during the statistics window.
// ============================================================

constexpr uint8_t IMU_ADDRESS = 0x68;

constexpr int SDA_PIN = 21;
constexpr int SCL_PIN = 22;

constexpr uint32_t I2C_CLOCK_HZ = 100000;

// MPU6050/compatible default ranges:
//
// Accelerometer: +/-2 g
// Gyroscope:     +/-250 degrees/second
constexpr float ACCEL_SCALE = 16384.0f;
constexpr float GYRO_SCALE = 131.0f;

// 100 Hz
constexpr uint32_t SAMPLE_INTERVAL_US = 10000;

// Print raw sample every 100 ms so Serial Monitor remains readable.
constexpr uint32_t PRINT_INTERVAL_MS = 100;

// Calculate stationary statistics across 1000 samples.
// At 100 Hz this is approximately 10 seconds.
constexpr int STAT_SAMPLE_COUNT = 1000;

// ============================================================
// Raw IMU structure
// ============================================================

struct RawImu {
    int16_t ax;
    int16_t ay;
    int16_t az;

    int16_t gx;
    int16_t gy;
    int16_t gz;
};

// ============================================================
// Running statistics
//
// Uses Welford's algorithm so we do not need to store every
// sample in memory.
// ============================================================

struct RunningStats {
    uint32_t count = 0;

    double mean = 0.0;
    double m2 = 0.0;

    float minimum = INFINITY;
    float maximum = -INFINITY;

    void reset() {
        count = 0;
        mean = 0.0;
        m2 = 0.0;

        minimum = INFINITY;
        maximum = -INFINITY;
    }

    void add(float value) {

        count++;

        const double delta =
            static_cast<double>(value) - mean;

        mean += delta / count;

        const double delta2 =
            static_cast<double>(value) - mean;

        m2 += delta * delta2;

        if (value < minimum) {
            minimum = value;
        }

        if (value > maximum) {
            maximum = value;
        }
    }

    float standardDeviation() const {

        if (count < 2) {
            return 0.0f;
        }

        return sqrt(
            m2 /
            static_cast<double>(count - 1)
        );
    }
};

// ============================================================
// Statistics
// ============================================================

RunningStats axStats;
RunningStats ayStats;
RunningStats azStats;

RunningStats gxStats;
RunningStats gyStats;
RunningStats gzStats;

RunningStats accelMagnitudeStats;

// ============================================================
// Timing
// ============================================================

uint32_t lastSampleMicros = 0;
uint32_t lastPrintMillis = 0;

int statisticsSamples = 0;

// ============================================================
// I2C helpers
// ============================================================

bool writeRegister(
    uint8_t registerAddress,
    uint8_t value
) {
    Wire.beginTransmission(IMU_ADDRESS);

    Wire.write(registerAddress);
    Wire.write(value);

    return Wire.endTransmission(true) == 0;
}

bool readRegister(
    uint8_t registerAddress,
    uint8_t &value
) {
    Wire.beginTransmission(IMU_ADDRESS);

    Wire.write(registerAddress);

    if (Wire.endTransmission(false) != 0) {
        return false;
    }

    if (
        Wire.requestFrom(
            static_cast<uint16_t>(IMU_ADDRESS),
            static_cast<uint8_t>(1),
            true
        ) != 1
    ) {
        return false;
    }

    value = Wire.read();

    return true;
}

// ============================================================
// Read accelerometer and gyroscope
// ============================================================

bool readRawImu(RawImu &raw) {

    Wire.beginTransmission(IMU_ADDRESS);

    Wire.write(0x3B);

    if (Wire.endTransmission(false) != 0) {
        return false;
    }

    constexpr uint8_t BYTE_COUNT = 14;

    if (
        Wire.requestFrom(
            static_cast<uint16_t>(IMU_ADDRESS),
            BYTE_COUNT,
            true
        ) != BYTE_COUNT
    ) {
        return false;
    }

    raw.ax =
        static_cast<int16_t>(
            (Wire.read() << 8) |
            Wire.read()
        );

    raw.ay =
        static_cast<int16_t>(
            (Wire.read() << 8) |
            Wire.read()
        );

    raw.az =
        static_cast<int16_t>(
            (Wire.read() << 8) |
            Wire.read()
        );

    // Skip temperature.
    Wire.read();
    Wire.read();

    raw.gx =
        static_cast<int16_t>(
            (Wire.read() << 8) |
            Wire.read()
        );

    raw.gy =
        static_cast<int16_t>(
            (Wire.read() << 8) |
            Wire.read()
        );

    raw.gz =
        static_cast<int16_t>(
            (Wire.read() << 8) |
            Wire.read()
        );

    return true;
}

// ============================================================
// IMU initialization
// ============================================================

bool initializeImu() {

    uint8_t whoAmI = 0;

    if (!readRegister(0x75, whoAmI)) {

        Serial.println(
            "[ERROR] Could not read WHO_AM_I"
        );

        return false;
    }

    Serial.print("[IMU] WHO_AM_I = 0x");

    if (whoAmI < 0x10) {
        Serial.print("0");
    }

    Serial.println(whoAmI, HEX);

    if (
        whoAmI != 0x68 &&
        whoAmI != 0x70
    ) {
        Serial.println(
            "[WARNING] Unexpected WHO_AM_I value"
        );
    }

    // Wake sensor.
    if (!writeRegister(0x6B, 0x00)) {
        return false;
    }

    delay(100);

    // +/-2 g
    if (!writeRegister(0x1C, 0x00)) {
        return false;
    }

    // +/-250 degrees/second
    if (!writeRegister(0x1B, 0x00)) {
        return false;
    }

    // Digital low-pass filtering.
    if (!writeRegister(0x1A, 0x03)) {
        return false;
    }

    return true;
}

// ============================================================
// Statistics
// ============================================================

void resetStatistics() {

    axStats.reset();
    ayStats.reset();
    azStats.reset();

    gxStats.reset();
    gyStats.reset();
    gzStats.reset();

    accelMagnitudeStats.reset();

    statisticsSamples = 0;
}

void printStatsLine(
    const char *name,
    const RunningStats &stats,
    const char *unit
) {
    Serial.print(name);

    Serial.print(" mean=");
    Serial.print(stats.mean, 6);

    Serial.print(" std=");
    Serial.print(
        stats.standardDeviation(),
        6
    );

    Serial.print(" min=");
    Serial.print(stats.minimum, 6);

    Serial.print(" max=");
    Serial.print(stats.maximum, 6);

    Serial.print(" ");
    Serial.println(unit);
}

void printStatistics() {

    Serial.println();
    Serial.println(
        "========================================"
    );

    Serial.println(
        "[STATISTICS] Stationary measurement"
    );

    Serial.println(
        "========================================"
    );

    printStatsLine(
        "Accel X:",
        axStats,
        "g"
    );

    printStatsLine(
        "Accel Y:",
        ayStats,
        "g"
    );

    printStatsLine(
        "Accel Z:",
        azStats,
        "g"
    );

    printStatsLine(
        "|Accel|:",
        accelMagnitudeStats,
        "g"
    );

    Serial.println();

    printStatsLine(
        "Gyro X:",
        gxStats,
        "deg/s"
    );

    printStatsLine(
        "Gyro Y:",
        gyStats,
        "deg/s"
    );

    printStatsLine(
        "Gyro Z:",
        gzStats,
        "deg/s"
    );

    Serial.println();

    Serial.println(
        "[INFO] Starting another 10-second statistics window."
    );

    Serial.println(
        "[INFO] Keep the module completely still."
    );

    Serial.println(
        "========================================"
    );

    Serial.println();

    resetStatistics();
}

// ============================================================
// Setup
// ============================================================

void setup() {

    Serial.begin(115200);

    delay(1500);

    Serial.println();
    Serial.println(
        "========================================"
    );

    Serial.println(
        "Disha-Rakshak Foot IMU Test 01"
    );

    Serial.println(
        "RAW SENSOR CHARACTERIZATION"
    );

    Serial.println(
        "========================================"
    );

    Serial.println();

    Wire.begin(
        SDA_PIN,
        SCL_PIN
    );

    Wire.setClock(
        I2C_CLOCK_HZ
    );

    delay(250);

    if (!initializeImu()) {

        Serial.println(
            "[FATAL] IMU initialization failed."
        );

        while (true) {
            delay(1000);
        }
    }

    resetStatistics();

    lastSampleMicros = micros();

    Serial.println();
    Serial.println(
        "[READY]"
    );

    Serial.println(
        "Place the foot module on a solid flat surface."
    );

    Serial.println(
        "Do not touch it for at least 10 seconds."
    );

    Serial.println();
}

// ============================================================
// Main loop
// ============================================================

void loop() {

    const uint32_t nowMicros = micros();

    if (
        nowMicros - lastSampleMicros <
        SAMPLE_INTERVAL_US
    ) {
        return;
    }

    lastSampleMicros += SAMPLE_INTERVAL_US;

    RawImu raw = {};

    if (!readRawImu(raw)) {

        Serial.println(
            "[ERROR] IMU read failed"
        );

        return;
    }

    const float ax =
        raw.ax / ACCEL_SCALE;

    const float ay =
        raw.ay / ACCEL_SCALE;

    const float az =
        raw.az / ACCEL_SCALE;

    const float gx =
        raw.gx / GYRO_SCALE;

    const float gy =
        raw.gy / GYRO_SCALE;

    const float gz =
        raw.gz / GYRO_SCALE;

    const float accelMagnitude =
        sqrtf(
            ax * ax +
            ay * ay +
            az * az
        );

    axStats.add(ax);
    ayStats.add(ay);
    azStats.add(az);

    gxStats.add(gx);
    gyStats.add(gy);
    gzStats.add(gz);

    accelMagnitudeStats.add(
        accelMagnitude
    );

    statisticsSamples++;

    const uint32_t nowMillis = millis();

    if (
        nowMillis - lastPrintMillis >=
        PRINT_INTERVAL_MS
    ) {

        lastPrintMillis = nowMillis;

        Serial.print("A=(");

        Serial.print(ax, 4);
        Serial.print(", ");

        Serial.print(ay, 4);
        Serial.print(", ");

        Serial.print(az, 4);

        Serial.print(") g");

        Serial.print("  |A|=");
        Serial.print(
            accelMagnitude,
            4
        );

        Serial.print(" g");

        Serial.print("  G=(");

        Serial.print(gx, 3);
        Serial.print(", ");

        Serial.print(gy, 3);
        Serial.print(", ");

        Serial.print(gz, 3);

        Serial.println(") deg/s");
    }

    if (
        statisticsSamples >=
        STAT_SAMPLE_COUNT
    ) {
        printStatistics();
    }
}