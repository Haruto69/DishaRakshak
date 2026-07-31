#include <Arduino.h>
#include <Wire.h>
#include <WiFi.h>
#include <esp_now.h>
#include <esp_wifi.h>
#include <math.h>

// ==================================================
// Hardware and communication configuration
// ==================================================

constexpr uint8_t IMU_ADDRESS = 0x68;
constexpr int SDA_PIN = 21;
constexpr int SCL_PIN = 22;
constexpr uint32_t I2C_CLOCK_HZ = 100000;

constexpr uint8_t WIFI_CHANNEL = 6;
constexpr uint32_t PACKET_MAGIC = 0x44524B32;  // "DRK2"
constexpr uint32_t SAMPLE_INTERVAL_US = 20000; // 50 Hz
constexpr uint32_t PRINT_INTERVAL_MS = 200;

const uint8_t BROADCAST_ADDRESS[6] = {
    0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF
};

// MPU-6050/6500 scales for +/-2 g and +/-250 dps.
constexpr float ACCEL_SCALE = 16384.0f;
constexpr float GYRO_SCALE = 131.0f;
constexpr float GRAVITY_MS2 = 9.80665f;

// Calibration and detection settings.
constexpr int CALIBRATION_SAMPLES = 800;
constexpr float COMPLEMENTARY_ALPHA = 0.98f;
constexpr float STILL_ACCEL_TOLERANCE_G = 0.08f;
constexpr float STILL_GYRO_THRESHOLD_DPS = 3.0f;
constexpr int STILL_REQUIRED_SAMPLES = 10;
constexpr float TILT_THRESHOLD_DEG = 8.0f;
constexpr float ROTATION_THRESHOLD_DPS = 5.0f;
constexpr float MOVEMENT_THRESHOLD_MS = 0.10f;
constexpr float LINEAR_ACCEL_DEADBAND_MS2 = 0.12f;

// ==================================================
// Packet definitions
// ==================================================

enum TiltDirection : uint8_t {
    TILT_LEVEL = 0,
    TILT_PITCH_POSITIVE = 1,
    TILT_PITCH_NEGATIVE = 2,
    TILT_ROLL_POSITIVE = 3,
    TILT_ROLL_NEGATIVE = 4
};

enum MovementDirection : uint8_t {
    MOVE_STILL = 0,
    MOVE_X_POSITIVE = 1,
    MOVE_X_NEGATIVE = 2,
    MOVE_Y_POSITIVE = 3,
    MOVE_Y_NEGATIVE = 4,
    MOVE_UP = 5,
    MOVE_DOWN = 6
};

enum RotationAxis : uint8_t {
    ROTATION_NONE = 0,
    ROTATION_X = 1,
    ROTATION_Y = 2,
    ROTATION_Z = 3
};

struct MotionPacket {
    uint32_t magic;
    uint32_t sequence;

    float accelXG;
    float accelYG;
    float accelZG;

    float gyroXDps;
    float gyroYDps;
    float gyroZDps;

    float rollDeg;
    float pitchDeg;
    float yawDeg;

    float velocityX;
    float velocityY;
    float velocityZ;

    uint8_t isStill;
    uint8_t tiltDirection;
    uint8_t movementDirection;
    uint8_t rotationAxis;
    int8_t rotationSign;
    uint8_t reserved[3];
};

static_assert(sizeof(MotionPacket) == 64, "Unexpected MotionPacket size");

struct RawImu {
    int16_t ax;
    int16_t ay;
    int16_t az;
    int16_t gx;
    int16_t gy;
    int16_t gz;
};

// ==================================================
// Runtime state
// ==================================================

float gyroBiasX = 0.0f;
float gyroBiasY = 0.0f;
float gyroBiasZ = 0.0f;

float baselineRollDeg = 0.0f;
float baselinePitchDeg = 0.0f;
float baselineGravityG = 1.0f;

float filteredRollDeg = 0.0f;
float filteredPitchDeg = 0.0f;
float yawDeg = 0.0f;

float velocityX = 0.0f;
float velocityY = 0.0f;
float velocityZ = 0.0f;

bool isStill = true;
int stillCounter = STILL_REQUIRED_SAMPLES;

uint32_t sequenceNumber = 0;
uint32_t lastSampleMicros = 0;
uint32_t lastPrintMillis = 0;

// ==================================================
// Utility functions
// ==================================================

float clampDeadband(float value, float deadband) {
    return fabsf(value) < deadband ? 0.0f : value;
}

float normalizeAngle180(float angle) {
    while (angle > 180.0f) angle -= 360.0f;
    while (angle < -180.0f) angle += 360.0f;
    return angle;
}

bool writeImuRegister(uint8_t registerAddress, uint8_t value) {
    Wire.beginTransmission(IMU_ADDRESS);
    Wire.write(registerAddress);
    Wire.write(value);
    return Wire.endTransmission(true) == 0;
}

bool readImuRegister(uint8_t registerAddress, uint8_t &value) {
    Wire.beginTransmission(IMU_ADDRESS);
    Wire.write(registerAddress);

    if (Wire.endTransmission(false) != 0) {
        return false;
    }

    if (Wire.requestFrom(
            static_cast<uint16_t>(IMU_ADDRESS),
            static_cast<uint8_t>(1),
            true
        ) != 1) {
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

    constexpr uint8_t BYTES_TO_READ = 14;

    if (Wire.requestFrom(
            static_cast<uint16_t>(IMU_ADDRESS),
            BYTES_TO_READ,
            true
        ) != BYTES_TO_READ) {
        return false;
    }

    raw.ax = static_cast<int16_t>((Wire.read() << 8) | Wire.read());
    raw.ay = static_cast<int16_t>((Wire.read() << 8) | Wire.read());
    raw.az = static_cast<int16_t>((Wire.read() << 8) | Wire.read());

    Wire.read();
    Wire.read();

    raw.gx = static_cast<int16_t>((Wire.read() << 8) | Wire.read());
    raw.gy = static_cast<int16_t>((Wire.read() << 8) | Wire.read());
    raw.gz = static_cast<int16_t>((Wire.read() << 8) | Wire.read());

    return true;
}

// ==================================================
// IMU initialization and calibration
// ==================================================

bool initializeImu() {
    uint8_t whoAmI = 0;

    for (int attempt = 1; attempt <= 10; attempt++) {
        if (readImuRegister(0x75, whoAmI)) {
            Serial.print("[IMU] WHO_AM_I: 0x");
            Serial.println(whoAmI, HEX);

            if (whoAmI == 0x68 || whoAmI == 0x70) {
                break;
            }
        }

        if (attempt == 10) {
            Serial.println("[IMU] Unsupported or unavailable sensor");
            return false;
        }

        delay(100);
    }

    if (!writeImuRegister(0x6B, 0x00)) return false;
    delay(100);

    if (!writeImuRegister(0x1C, 0x00)) return false; // +/-2 g
    if (!writeImuRegister(0x1B, 0x00)) return false; // +/-250 dps
    if (!writeImuRegister(0x1A, 0x03)) return false; // low-pass filter

    return true;
}

bool calibrateImu() {
    Serial.println();
    Serial.println("[CALIBRATION] Keep the foot module completely still.");
    Serial.println("[CALIBRATION] Sampling for about 4 seconds...");

    double sumAx = 0.0;
    double sumAy = 0.0;
    double sumAz = 0.0;
    double sumGx = 0.0;
    double sumGy = 0.0;
    double sumGz = 0.0;

    int validSamples = 0;

    while (validSamples < CALIBRATION_SAMPLES) {
        RawImu raw = {};

        if (!readRawImu(raw)) {
            delay(5);
            continue;
        }

        sumAx += raw.ax / ACCEL_SCALE;
        sumAy += raw.ay / ACCEL_SCALE;
        sumAz += raw.az / ACCEL_SCALE;

        sumGx += raw.gx / GYRO_SCALE;
        sumGy += raw.gy / GYRO_SCALE;
        sumGz += raw.gz / GYRO_SCALE;

        validSamples++;

        if (validSamples % 100 == 0) {
            Serial.print(".");
        }

        delay(5);
    }

    Serial.println();

    const float avgAx = static_cast<float>(sumAx / validSamples);
    const float avgAy = static_cast<float>(sumAy / validSamples);
    const float avgAz = static_cast<float>(sumAz / validSamples);

    gyroBiasX = static_cast<float>(sumGx / validSamples);
    gyroBiasY = static_cast<float>(sumGy / validSamples);
    gyroBiasZ = static_cast<float>(sumGz / validSamples);

    baselineGravityG = sqrtf(
        avgAx * avgAx +
        avgAy * avgAy +
        avgAz * avgAz
    );

    baselineRollDeg = atan2f(avgAy, avgAz) * RAD_TO_DEG;
    baselinePitchDeg = atan2f(
        -avgAx,
        sqrtf(avgAy * avgAy + avgAz * avgAz)
    ) * RAD_TO_DEG;

    filteredRollDeg = baselineRollDeg;
    filteredPitchDeg = baselinePitchDeg;
    yawDeg = 0.0f;

    velocityX = 0.0f;
    velocityY = 0.0f;
    velocityZ = 0.0f;

    Serial.println("[CALIBRATION] Complete");
    Serial.print("[CALIBRATION] Gyro bias dps: X=");
    Serial.print(gyroBiasX, 3);
    Serial.print(" Y=");
    Serial.print(gyroBiasY, 3);
    Serial.print(" Z=");
    Serial.println(gyroBiasZ, 3);

    Serial.print("[CALIBRATION] Baseline roll=");
    Serial.print(baselineRollDeg, 2);
    Serial.print(" pitch=");
    Serial.print(baselinePitchDeg, 2);
    Serial.print(" gravity=");
    Serial.print(baselineGravityG, 3);
    Serial.println(" g");

    return baselineGravityG > 0.75f && baselineGravityG < 1.25f;
}

// ==================================================
// ESP-NOW initialization
// ==================================================

bool setWiFiChannel() {
    esp_wifi_set_promiscuous(true);

    const esp_err_t result = esp_wifi_set_channel(
        WIFI_CHANNEL,
        WIFI_SECOND_CHAN_NONE
    );

    esp_wifi_set_promiscuous(false);
    return result == ESP_OK;
}

bool initializeEspNow() {
    WiFi.mode(WIFI_STA);
    WiFi.disconnect();
    delay(100);

    Serial.print("[ESP-NOW] Foot MAC: ");
    Serial.println(WiFi.macAddress());

    if (!setWiFiChannel()) return false;
    if (esp_now_init() != ESP_OK) return false;

    esp_now_peer_info_t peerInfo = {};
    memcpy(peerInfo.peer_addr, BROADCAST_ADDRESS, 6);
    peerInfo.channel = WIFI_CHANNEL;
    peerInfo.encrypt = false;
    peerInfo.ifidx = WIFI_IF_STA;

    const esp_err_t result = esp_now_add_peer(&peerInfo);

    return result == ESP_OK || result == ESP_ERR_ESPNOW_EXIST;
}

// ==================================================
// Motion processing
// ==================================================

void updateStillness(
    float accelMagnitudeG,
    float gxDps,
    float gyDps,
    float gzDps
) {
    const float gyroMagnitude = sqrtf(
        gxDps * gxDps +
        gyDps * gyDps +
        gzDps * gzDps
    );

    const bool stillCandidate =
        fabsf(accelMagnitudeG - baselineGravityG) <
            STILL_ACCEL_TOLERANCE_G &&
        gyroMagnitude < STILL_GYRO_THRESHOLD_DPS;

    if (stillCandidate) {
        if (stillCounter < STILL_REQUIRED_SAMPLES) {
            stillCounter++;
        }
    } else {
        stillCounter = 0;
    }

    isStill = stillCounter >= STILL_REQUIRED_SAMPLES;
}

TiltDirection detectTilt(float relativeRoll, float relativePitch) {
    if (
        fabsf(relativeRoll) < TILT_THRESHOLD_DEG &&
        fabsf(relativePitch) < TILT_THRESHOLD_DEG
    ) {
        return TILT_LEVEL;
    }

    if (fabsf(relativePitch) >= fabsf(relativeRoll)) {
        return relativePitch >= 0.0f
            ? TILT_PITCH_POSITIVE
            : TILT_PITCH_NEGATIVE;
    }

    return relativeRoll >= 0.0f
        ? TILT_ROLL_POSITIVE
        : TILT_ROLL_NEGATIVE;
}

RotationAxis detectRotationAxis(
    float gxDps,
    float gyDps,
    float gzDps,
    int8_t &rotationSign
) {
    const float absX = fabsf(gxDps);
    const float absY = fabsf(gyDps);
    const float absZ = fabsf(gzDps);

    rotationSign = 0;

    if (
        absX < ROTATION_THRESHOLD_DPS &&
        absY < ROTATION_THRESHOLD_DPS &&
        absZ < ROTATION_THRESHOLD_DPS
    ) {
        return ROTATION_NONE;
    }

    if (absX >= absY && absX >= absZ) {
        rotationSign = gxDps >= 0.0f ? 1 : -1;
        return ROTATION_X;
    }

    if (absY >= absX && absY >= absZ) {
        rotationSign = gyDps >= 0.0f ? 1 : -1;
        return ROTATION_Y;
    }

    rotationSign = gzDps >= 0.0f ? 1 : -1;
    return ROTATION_Z;
}

MovementDirection detectMovementDirection() {
    if (isStill) return MOVE_STILL;

    const float absX = fabsf(velocityX);
    const float absY = fabsf(velocityY);
    const float absZ = fabsf(velocityZ);

    if (
        absX < MOVEMENT_THRESHOLD_MS &&
        absY < MOVEMENT_THRESHOLD_MS &&
        absZ < MOVEMENT_THRESHOLD_MS
    ) {
        return MOVE_STILL;
    }

    if (absX >= absY && absX >= absZ) {
        return velocityX >= 0.0f
            ? MOVE_X_POSITIVE
            : MOVE_X_NEGATIVE;
    }

    if (absY >= absX && absY >= absZ) {
        return velocityY >= 0.0f
            ? MOVE_Y_POSITIVE
            : MOVE_Y_NEGATIVE;
    }

    return velocityZ >= 0.0f ? MOVE_UP : MOVE_DOWN;
}

void transformAccelerationToWorld(
    float axG,
    float ayG,
    float azG,
    float rollDeg,
    float pitchDeg,
    float yawAngleDeg,
    float &linearAx,
    float &linearAy,
    float &linearAz
) {
    const float roll = rollDeg * DEG_TO_RAD;
    const float pitch = pitchDeg * DEG_TO_RAD;
    const float yaw = yawAngleDeg * DEG_TO_RAD;

    const float cr = cosf(roll);
    const float sr = sinf(roll);
    const float cp = cosf(pitch);
    const float sp = sinf(pitch);
    const float cy = cosf(yaw);
    const float sy = sinf(yaw);

    const float worldX =
        cp * cy * axG +
        (sr * sp * cy - cr * sy) * ayG +
        (cr * sp * cy + sr * sy) * azG;

    const float worldY =
        cp * sy * axG +
        (sr * sp * sy + cr * cy) * ayG +
        (cr * sp * sy - sr * cy) * azG;

    const float worldZ =
        -sp * axG +
        sr * cp * ayG +
        cr * cp * azG;

    linearAx = worldX * GRAVITY_MS2;
    linearAy = worldY * GRAVITY_MS2;
    linearAz = (worldZ - baselineGravityG) * GRAVITY_MS2;

    linearAx = clampDeadband(linearAx, LINEAR_ACCEL_DEADBAND_MS2);
    linearAy = clampDeadband(linearAy, LINEAR_ACCEL_DEADBAND_MS2);
    linearAz = clampDeadband(linearAz, LINEAR_ACCEL_DEADBAND_MS2);
}

bool buildMotionPacket(MotionPacket &packet, float dtSeconds) {
    RawImu raw = {};

    if (!readRawImu(raw)) {
        return false;
    }

    const float axG = raw.ax / ACCEL_SCALE;
    const float ayG = raw.ay / ACCEL_SCALE;
    const float azG = raw.az / ACCEL_SCALE;

    const float gxDps = raw.gx / GYRO_SCALE - gyroBiasX;
    const float gyDps = raw.gy / GYRO_SCALE - gyroBiasY;
    const float gzDps = raw.gz / GYRO_SCALE - gyroBiasZ;

    const float accelRollDeg =
        atan2f(ayG, azG) * RAD_TO_DEG;

    const float accelPitchDeg =
        atan2f(-axG, sqrtf(ayG * ayG + azG * azG)) *
        RAD_TO_DEG;

    filteredRollDeg =
        COMPLEMENTARY_ALPHA *
            (filteredRollDeg + gxDps * dtSeconds) +
        (1.0f - COMPLEMENTARY_ALPHA) * accelRollDeg;

    filteredPitchDeg =
        COMPLEMENTARY_ALPHA *
            (filteredPitchDeg + gyDps * dtSeconds) +
        (1.0f - COMPLEMENTARY_ALPHA) * accelPitchDeg;

    yawDeg = normalizeAngle180(yawDeg + gzDps * dtSeconds);

    const float accelMagnitudeG =
        sqrtf(axG * axG + ayG * ayG + azG * azG);

    updateStillness(
        accelMagnitudeG,
        gxDps,
        gyDps,
        gzDps
    );

    float linearAx = 0.0f;
    float linearAy = 0.0f;
    float linearAz = 0.0f;

    transformAccelerationToWorld(
        axG,
        ayG,
        azG,
        filteredRollDeg,
        filteredPitchDeg,
        yawDeg,
        linearAx,
        linearAy,
        linearAz
    );

    // Basic strapdown integration with ZUPT.
    if (isStill) {
        velocityX = 0.0f;
        velocityY = 0.0f;
        velocityZ = 0.0f;
    } else {
        velocityX += linearAx * dtSeconds;
        velocityY += linearAy * dtSeconds;
        velocityZ += linearAz * dtSeconds;
    }

    const float relativeRoll =
        normalizeAngle180(filteredRollDeg - baselineRollDeg);

    const float relativePitch =
        normalizeAngle180(filteredPitchDeg - baselinePitchDeg);

    int8_t rotationSign = 0;

    packet.magic = PACKET_MAGIC;
    packet.sequence = ++sequenceNumber;

    packet.accelXG = axG;
    packet.accelYG = ayG;
    packet.accelZG = azG;

    packet.gyroXDps = gxDps;
    packet.gyroYDps = gyDps;
    packet.gyroZDps = gzDps;

    packet.rollDeg = relativeRoll;
    packet.pitchDeg = relativePitch;
    packet.yawDeg = yawDeg;

    packet.velocityX = velocityX;
    packet.velocityY = velocityY;
    packet.velocityZ = velocityZ;

    packet.isStill = isStill ? 1 : 0;
    packet.tiltDirection = detectTilt(relativeRoll, relativePitch);
    packet.movementDirection = detectMovementDirection();
    packet.rotationAxis = detectRotationAxis(
        gxDps,
        gyDps,
        gzDps,
        rotationSign
    );
    packet.rotationSign = rotationSign;

    packet.reserved[0] = 0;
    packet.reserved[1] = 0;
    packet.reserved[2] = 0;

    return true;
}

void printLocalStatus(const MotionPacket &packet) {
    Serial.print("[TX] #");
    Serial.print(packet.sequence);

    Serial.print(" Still=");
    Serial.print(packet.isStill ? "YES" : "NO");

    Serial.print(" R=");
    Serial.print(packet.rollDeg, 1);

    Serial.print(" P=");
    Serial.print(packet.pitchDeg, 1);

    Serial.print(" Y=");
    Serial.print(packet.yawDeg, 1);

    Serial.print(" V=(");
    Serial.print(packet.velocityX, 2);
    Serial.print(",");
    Serial.print(packet.velocityY, 2);
    Serial.print(",");
    Serial.print(packet.velocityZ, 2);
    Serial.println(")");
}

// ==================================================
// Arduino setup and loop
// ==================================================

void setup() {
    Serial.begin(115200);
    delay(1500);

    Serial.println();
    Serial.println("========================================");
    Serial.println("Disha-Rakshak Foot Module - BUILD 3");
    Serial.println("Calibration + Orientation + Basic ZUPT");
    Serial.println("========================================");

    Wire.begin(SDA_PIN, SCL_PIN);
    Wire.setClock(I2C_CLOCK_HZ);
    delay(250);

    if (!initializeImu()) {
        Serial.println("[FATAL] IMU initialization failed");
        while (true) delay(1000);
    }

    if (!calibrateImu()) {
        Serial.println("[FATAL] Calibration failed");
        while (true) delay(1000);
    }

    if (!initializeEspNow()) {
        Serial.println("[FATAL] ESP-NOW initialization failed");
        while (true) delay(1000);
    }

    lastSampleMicros = micros();

    Serial.println("[READY] Motion packets are being transmitted");
}

void loop() {
    const uint32_t nowMicros = micros();

    if (nowMicros - lastSampleMicros < SAMPLE_INTERVAL_US) {
        delay(1);
        return;
    }

    const float dtSeconds =
        (nowMicros - lastSampleMicros) / 1000000.0f;

    lastSampleMicros = nowMicros;

    MotionPacket packet = {};

    if (!buildMotionPacket(packet, dtSeconds)) {
        Serial.println("[IMU] Read failed");
        return;
    }

    const esp_err_t sendResult = esp_now_send(
        BROADCAST_ADDRESS,
        reinterpret_cast<const uint8_t *>(&packet),
        sizeof(packet)
    );

    if (sendResult != ESP_OK) {
        Serial.print("[ESP-NOW] Send failed: ");
        Serial.println(sendResult);
        return;
    }

    const uint32_t nowMillis = millis();

    if (nowMillis - lastPrintMillis >= PRINT_INTERVAL_MS) {
        lastPrintMillis = nowMillis;
        printLocalStatus(packet);
    }
}
