#include <Arduino.h>
#include <WiFi.h>
#include <esp_now.h>
#include <esp_wifi.h>

// ==================================================
// ESP-NOW configuration
// ==================================================

constexpr uint8_t WIFI_CHANNEL = 6;
constexpr uint32_t PACKET_MAGIC = 0x44524B32; // Must match foot.ino

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

portMUX_TYPE packetMux = portMUX_INITIALIZER_UNLOCKED;

volatile bool newPacketAvailable = false;
MotionPacket latestPacket = {};
uint8_t latestSenderMac[6] = {};

// ==================================================
// Labels
// ==================================================

const char *tiltLabel(uint8_t value) {
    switch (value) {
        case TILT_LEVEL: return "LEVEL";
        case TILT_PITCH_POSITIVE: return "PITCH+";
        case TILT_PITCH_NEGATIVE: return "PITCH-";
        case TILT_ROLL_POSITIVE: return "ROLL+";
        case TILT_ROLL_NEGATIVE: return "ROLL-";
        default: return "UNKNOWN";
    }
}

const char *movementLabel(uint8_t value) {
    switch (value) {
        case MOVE_STILL: return "STILL";
        case MOVE_X_POSITIVE: return "X+";
        case MOVE_X_NEGATIVE: return "X-";
        case MOVE_Y_POSITIVE: return "Y+";
        case MOVE_Y_NEGATIVE: return "Y-";
        case MOVE_UP: return "UP";
        case MOVE_DOWN: return "DOWN";
        default: return "UNKNOWN";
    }
}

const char *rotationLabel(uint8_t axis, int8_t sign) {
    if (axis == ROTATION_NONE) return "NONE";

    if (axis == ROTATION_X) {
        return sign >= 0 ? "X+" : "X-";
    }

    if (axis == ROTATION_Y) {
        return sign >= 0 ? "Y+" : "Y-";
    }

    if (axis == ROTATION_Z) {
        return sign >= 0 ? "Z+" : "Z-";
    }

    return "UNKNOWN";
}

// ==================================================
// ESP-NOW callback and setup
// ==================================================

void onDataReceived(
    const esp_now_recv_info_t *receiveInfo,
    const uint8_t *incomingData,
    int dataLength
) {
    if (
        receiveInfo == nullptr ||
        incomingData == nullptr ||
        dataLength != sizeof(MotionPacket)
    ) {
        return;
    }

    MotionPacket received = {};
    memcpy(&received, incomingData, sizeof(received));

    if (received.magic != PACKET_MAGIC) {
        return;
    }

    portENTER_CRITICAL(&packetMux);

    latestPacket = received;
    memcpy(latestSenderMac, receiveInfo->src_addr, 6);
    newPacketAvailable = true;

    portEXIT_CRITICAL(&packetMux);
}

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

    Serial.print("[ESP-NOW] Belt MAC: ");
    Serial.println(WiFi.macAddress());

    if (!setWiFiChannel()) return false;
    if (esp_now_init() != ESP_OK) return false;

    return esp_now_register_recv_cb(onDataReceived) == ESP_OK;
}

// ==================================================
// Output
// ==================================================

void printPacket(const MotionPacket &packet) {
    Serial.print("[RX] #");
    Serial.print(packet.sequence);

    Serial.print(" | ");
    Serial.print(packet.isStill ? "STILL" : "MOVING");

    Serial.print(" | Tilt=");
    Serial.print(tiltLabel(packet.tiltDirection));

    Serial.print(" | Move=");
    Serial.print(movementLabel(packet.movementDirection));

    Serial.print(" | Rotation=");
    Serial.print(rotationLabel(
        packet.rotationAxis,
        packet.rotationSign
    ));

    Serial.print(" | RPY=(");
    Serial.print(packet.rollDeg, 1);
    Serial.print(",");
    Serial.print(packet.pitchDeg, 1);
    Serial.print(",");
    Serial.print(packet.yawDeg, 1);
    Serial.print(")");

    Serial.print(" | Vel=(");
    Serial.print(packet.velocityX, 2);
    Serial.print(",");
    Serial.print(packet.velocityY, 2);
    Serial.print(",");
    Serial.print(packet.velocityZ, 2);
    Serial.print(")");

    Serial.print(" | Accel=(");
    Serial.print(packet.accelXG, 2);
    Serial.print(",");
    Serial.print(packet.accelYG, 2);
    Serial.print(",");
    Serial.print(packet.accelZG, 2);
    Serial.print(")");

    Serial.print(" | Gyro=(");
    Serial.print(packet.gyroXDps, 1);
    Serial.print(",");
    Serial.print(packet.gyroYDps, 1);
    Serial.print(",");
    Serial.print(packet.gyroZDps, 1);
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
    Serial.println("Disha-Rakshak Belt Module - BUILD 3");
    Serial.println("Motion Packet Receiver");
    Serial.println("========================================");

    if (!initializeEspNow()) {
        Serial.println("[FATAL] ESP-NOW initialization failed");
        while (true) delay(1000);
    }

    Serial.println("[READY] Waiting for motion packets");
}

void loop() {
    MotionPacket packetCopy = {};
    bool shouldPrint = false;

    portENTER_CRITICAL(&packetMux);

    if (newPacketAvailable) {
        packetCopy = latestPacket;
        newPacketAvailable = false;
        shouldPrint = true;
    }

    portEXIT_CRITICAL(&packetMux);

    if (shouldPrint) {
        printPacket(packetCopy);
    }

    delay(1);
}
