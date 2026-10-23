#include <Arduino.h>
#include <Wire.h>
#include <math.h>

// ============================================================
// Disha-Rakshak
// Foot IMU Test 01B - Sensor Integrity Test
//
// PURPOSE:
// Check whether stationary IMU samples are physically plausible
// before attempting calibration.
//
// Keep the module COMPLETELY STILL on a hard surface.
// ============================================================

constexpr uint8_t IMU_ADDRESS = 0x68;

constexpr int SDA_PIN = 21;
constexpr int SCL_PIN = 22;

constexpr uint32_t I2C_CLOCK_HZ = 100000;

constexpr float ACCEL_SCALE = 16384.0f;
constexpr float GYRO_SCALE = 131.0f;

constexpr uint32_t SAMPLE_INTERVAL_US = 10000; // 100 Hz
constexpr uint32_t WINDOW_SAMPLES = 500;       // ~5 seconds

struct RawImu {
    int16_t ax;
    int16_t ay;
    int16_t az;

    int16_t gx;
    int16_t gy;
    int16_t gz;
};

// ============================================================
// Window statistics
// ============================================================

uint32_t sampleCount = 0;
uint32_t readFailures = 0;

uint32_t accelRailHits = 0;
uint32_t gyroRailHits = 0;

int16_t minAx = INT16_MAX;
int16_t maxAx = INT16_MIN;

int16_t minAy = INT16_MAX;
int16_t maxAy = INT16_MIN;

int16_t minAz = INT16_MAX;
int16_t maxAz = INT16_MIN;

int16_t minGx = INT16_MAX;
int16_t maxGx = INT16_MIN;

int16_t minGy = INT16_MAX;
int16_t maxGy = INT16_MIN;

int16_t minGz = INT16_MAX;
int16_t maxGz = INT16_MIN;

double sumAx = 0;
double sumAy = 0;
double sumAz = 0;

double sumGx = 0;
double sumGy = 0;
double sumGz = 0;

double sumAccelMagnitude = 0;

uint32_t lastSampleMicros = 0;
uint32_t windowNumber = 0;

// ============================================================
// I2C helpers
// ============================================================

bool writeRegister(uint8_t reg, uint8_t value) {
    Wire.beginTransmission(IMU_ADDRESS);

    Wire.write(reg);
    Wire.write(value);

    return Wire.endTransmission(true) == 0;
}

bool readRegister(uint8_t reg, uint8_t &value) {
    Wire.beginTransmission(IMU_ADDRESS);

    Wire.write(reg);

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

bool readRawImu(RawImu &raw) {
    Wire.beginTransmission(IMU_ADDRESS);

    Wire.write(0x3B);

    if (Wire.endTransmission(false) != 0) {
        return false;
    }

    if (
        Wire.requestFrom(
            static_cast<uint16_t>(IMU_ADDRESS),
            static_cast<uint8_t>(14),
            true
        ) != 14
    ) {
        return false;
    }

    raw.ax = static_cast<int16_t>(
        (Wire.read() << 8) | Wire.read()
    );

    raw.ay = static_cast<int16_t>(
        (Wire.read() << 8) | Wire.read()
    );

    raw.az = static_cast<int16_t>(
        (Wire.read() << 8) | Wire.read()
    );

    // Skip temperature.
    Wire.read();
    Wire.read();

    raw.gx = static_cast<int16_t>(
        (Wire.read() << 8) | Wire.read()
    );

    raw.gy = static_cast<int16_t>(
        (Wire.read() << 8) | Wire.read()
    );

    raw.gz = static_cast<int16_t>(
        (Wire.read() << 8) | Wire.read()
    );

    return true;
}

// ============================================================
// Configuration
// ============================================================

bool configureImu() {
    uint8_t who = 0;

    if (!readRegister(0x75, who)) {
        Serial.println("[FAIL] Cannot read WHO_AM_I");
        return false;
    }

    Serial.print("WHO_AM_I = 0x");
    Serial.println(who, HEX);

    // Wake up.
    if (!writeRegister(0x6B, 0x00)) {
        return false;
    }

    delay(100);

    // +/- 250 dps
    if (!writeRegister(0x1B, 0x00)) {
        return false;
    }

    // +/- 2 g
    if (!writeRegister(0x1C, 0x00)) {
        return false;
    }

    // DLPF
    if (!writeRegister(0x1A, 0x03)) {
        return false;
    }

    delay(100);

    // Read configuration back.
    uint8_t gyroConfig = 0;
    uint8_t accelConfig = 0;
    uint8_t dlpfConfig = 0;
    uint8_t powerConfig = 0;

    readRegister(0x1B, gyroConfig);
    readRegister(0x1C, accelConfig);
    readRegister(0x1A, dlpfConfig);
    readRegister(0x6B, powerConfig);

    Serial.println();
    Serial.println("REGISTER READBACK");

    Serial.print("PWR_MGMT_1  = 0x");
    Serial.println(powerConfig, HEX);

    Serial.print("CONFIG      = 0x");
    Serial.println(dlpfConfig, HEX);

    Serial.print("GYRO_CONFIG = 0x");
    Serial.println(gyroConfig, HEX);

    Serial.print("ACCEL_CONFIG= 0x");
    Serial.println(accelConfig, HEX);

    Serial.println();

    return true;
}

// ============================================================
// Reset measurement window
// ============================================================

void resetWindow() {
    sampleCount = 0;
    readFailures = 0;

    accelRailHits = 0;
    gyroRailHits = 0;

    minAx = minAy = minAz = INT16_MAX;
    maxAx = maxAy = maxAz = INT16_MIN;

    minGx = minGy = minGz = INT16_MAX;
    maxGx = maxGy = maxGz = INT16_MIN;

    sumAx = 0;
    sumAy = 0;
    sumAz = 0;

    sumGx = 0;
    sumGy = 0;
    sumGz = 0;

    sumAccelMagnitude = 0;
}

// ============================================================
// Process sample
// ============================================================

void processSample(const RawImu &raw) {
    minAx = min(minAx, raw.ax);
    maxAx = max(maxAx, raw.ax);

    minAy = min(minAy, raw.ay);
    maxAy = max(maxAy, raw.ay);

    minAz = min(minAz, raw.az);
    maxAz = max(maxAz, raw.az);

    minGx = min(minGx, raw.gx);
    maxGx = max(maxGx, raw.gx);

    minGy = min(minGy, raw.gy);
    maxGy = max(maxGy, raw.gy);

    minGz = min(minGz, raw.gz);
    maxGz = max(maxGz, raw.gz);

    // Anything extremely close to signed 16-bit limits is suspicious
    // while the sensor is stationary.
    if (
        abs(static_cast<int32_t>(raw.ax)) > 32000 ||
        abs(static_cast<int32_t>(raw.ay)) > 32000 ||
        abs(static_cast<int32_t>(raw.az)) > 32000
    ) {
        accelRailHits++;
    }

    if (
        abs(static_cast<int32_t>(raw.gx)) > 32000 ||
        abs(static_cast<int32_t>(raw.gy)) > 32000 ||
        abs(static_cast<int32_t>(raw.gz)) > 32000
    ) {
        gyroRailHits++;
    }

    const float ax =
        static_cast<float>(raw.ax) / ACCEL_SCALE;

    const float ay =
        static_cast<float>(raw.ay) / ACCEL_SCALE;

    const float az =
        static_cast<float>(raw.az) / ACCEL_SCALE;

    const float gx =
        static_cast<float>(raw.gx) / GYRO_SCALE;

    const float gy =
        static_cast<float>(raw.gy) / GYRO_SCALE;

    const float gz =
        static_cast<float>(raw.gz) / GYRO_SCALE;

    sumAx += ax;
    sumAy += ay;
    sumAz += az;

    sumGx += gx;
    sumGy += gy;
    sumGz += gz;

    sumAccelMagnitude += sqrtf(
        ax * ax +
        ay * ay +
        az * az
    );

    sampleCount++;
}

// ============================================================
// Print window
// ============================================================

void printWindow() {
    windowNumber++;

    Serial.println();
    Serial.println(
        "================================================"
    );

    Serial.print("INTEGRITY WINDOW ");
    Serial.println(windowNumber);

    Serial.println(
        "================================================"
    );

    Serial.print("Samples      : ");
    Serial.println(sampleCount);

    Serial.print("Read failures: ");
    Serial.println(readFailures);

    Serial.print("Accel rail hits: ");
    Serial.println(accelRailHits);

    Serial.print("Gyro rail hits : ");
    Serial.println(gyroRailHits);

    Serial.println();

    if (sampleCount > 0) {
        Serial.println("MEANS");

        Serial.print("Accel = (");
        Serial.print(sumAx / sampleCount, 5);
        Serial.print(", ");
        Serial.print(sumAy / sampleCount, 5);
        Serial.print(", ");
        Serial.print(sumAz / sampleCount, 5);
        Serial.println(") g");

        Serial.print("|Accel| = ");
        Serial.print(
            sumAccelMagnitude / sampleCount,
            5
        );
        Serial.println(" g");

        Serial.print("Gyro = (");
        Serial.print(sumGx / sampleCount, 4);
        Serial.print(", ");
        Serial.print(sumGy / sampleCount, 4);
        Serial.print(", ");
        Serial.print(sumGz / sampleCount, 4);
        Serial.println(") deg/s");
    }

    Serial.println();

    Serial.println("RAW MIN / MAX");

    Serial.print("AX: ");
    Serial.print(minAx);
    Serial.print(" / ");
    Serial.println(maxAx);

    Serial.print("AY: ");
    Serial.print(minAy);
    Serial.print(" / ");
    Serial.println(maxAy);

    Serial.print("AZ: ");
    Serial.print(minAz);
    Serial.print(" / ");
    Serial.println(maxAz);

    Serial.print("GX: ");
    Serial.print(minGx);
    Serial.print(" / ");
    Serial.println(maxGx);

    Serial.print("GY: ");
    Serial.print(minGy);
    Serial.print(" / ");
    Serial.println(maxGy);

    Serial.print("GZ: ");
    Serial.print(minGz);
    Serial.print(" / ");
    Serial.println(maxGz);

    Serial.println(
        "================================================"
    );

    resetWindow();
}

// ============================================================
// Setup
// ============================================================

void setup() {
    Serial.begin(115200);

    delay(1500);

    Serial.println();
    Serial.println(
        "Disha-Rakshak Foot IMU - SENSOR INTEGRITY TEST"
    );

    Wire.begin(SDA_PIN, SCL_PIN);
    Wire.setClock(I2C_CLOCK_HZ);

    delay(250);

    if (!configureImu()) {
        Serial.println("[FATAL] IMU setup failed");

        while (true) {
            delay(1000);
        }
    }

    resetWindow();

    lastSampleMicros = micros();

    Serial.println(
        "Keep module completely still."
    );
}

// ============================================================
// Loop
// ============================================================

void loop() {
    const uint32_t now = micros();

    if (
        static_cast<uint32_t>(
            now - lastSampleMicros
        ) < SAMPLE_INTERVAL_US
    ) {
        return;
    }

    lastSampleMicros = now;

    RawImu raw = {};

    if (!readRawImu(raw)) {
        readFailures++;
        return;
    }

    processSample(raw);

    if (sampleCount >= WINDOW_SAMPLES) {
        printWindow();
    }
}