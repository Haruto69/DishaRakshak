#include <Arduino.h>

#include <WiFi.h>

#include <esp_now.h>

#include <esp_wifi.h>


#include <BLEDevice.h>

#include <BLEServer.h>

#include <BLEUtils.h>

#include <BLE2902.h>

// Shared packet definitions moved before functions so Arduino's
// generated prototypes can resolve these custom types.
enum FootId : uint8_t {

    FOOT_UNKNOWN = 0,

    FOOT_LEFT = 1,

    FOOT_RIGHT = 2

};


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


    uint8_t footId;

    uint8_t isStill;

    uint8_t tiltDirection;

    uint8_t movementDirection;

    uint8_t rotationAxis;

    int8_t rotationSign;

    uint8_t reserved[2];

};


static_assert(sizeof(MotionPacket) == 64, "Unexpected MotionPacket size");


struct FootState {

    MotionPacket packet;

    uint32_t lastReceivedMillis;

    bool hasPacket;

};


struct CombinedMotionPacket {

    uint32_t timestamp;

uint32_t sequence;


    float accelXG;

    float accelYG;

    float accelZG;


    float gyroXDps;

    float gyroYDps;

    float gyroZDps;


    float velocityX;

    float velocityY;

    float velocityZ;


    float rollDeg;

    float pitchDeg;


    bool bothFeetOnline;

};


// ==================================================


#define SERVICE_UUID        "5a0f0a31-1a22-47a2-b001-000000000001"

#define CHARACTERISTIC_UUID "5a0f0a31-1a22-47a2-b001-000000000002"


BLEServer* pServer = nullptr;

BLECharacteristic* pCharacteristic = nullptr;

bool deviceConnected = false;


// ==================================================

// BLE to phone code

// ==================================================


class BeltServerCallbacks : public BLEServerCallbacks {


    void onConnect(BLEServer* server) {

        deviceConnected = true;

        Serial.println("[BLE] Device Connected");

    }


    void onDisconnect(BLEServer* server) {


    deviceConnected = false;


    server->startAdvertising();


    Serial.println(

        "[BLE] Device Disconnected"

    );

}

};


void initializeBLE() {


    BLEDevice::init("DishaRakshak-Belt");


    pServer = BLEDevice::createServer();


    pServer->setCallbacks(

        new BeltServerCallbacks()

    );


    BLEService* service =

        pServer->createService(SERVICE_UUID);


    pCharacteristic =

        service->createCharacteristic(

            CHARACTERISTIC_UUID,

            BLECharacteristic::PROPERTY_READ |

            BLECharacteristic::PROPERTY_NOTIFY

        );


    pCharacteristic->addDescriptor(

        new BLE2902()

    );


    service->start();


    BLEAdvertising* advertising =

        BLEDevice::getAdvertising();


// BLEAdvertising *advertising = pServer->getAdvertising();


advertising->addServiceUUID(

    SERVICE_UUID

);


advertising->setScanResponse(true);


    advertising->start();


    Serial.println("[BLE] Advertising Started");

}


String createBlePayload(

    const CombinedMotionPacket& packet

)

{

    String payload;


    payload += String(packet.sequence);

    payload += ",";


    payload += String(packet.rollDeg, 2);

    payload += ",";


    payload += String(packet.pitchDeg, 2);

    payload += ",";


    payload += String(packet.velocityX, 3);

    payload += ",";


    payload += String(packet.velocityY, 3);

    payload += ",";


    payload += String(packet.velocityZ, 3);


    return payload;

}


// ==================================================

// ESP-NOW configuration

// ==================================================


constexpr uint8_t WIFI_CHANNEL = 6;

constexpr uint32_t PACKET_MAGIC = 0x44524B34; // Must match foot.ino

constexpr uint32_t DISPLAY_INTERVAL_MS = 250;

constexpr uint32_t OFFLINE_TIMEOUT_MS = 1500;


const uint8_t LEFT_FOOT_MAC[6] = {

    0x70, 0x4B, 0xCA, 0x46, 0xE4, 0xC0

};


const uint8_t RIGHT_FOOT_MAC[6] = {

    0x70, 0x4B, 0xCA, 0x47, 0x57, 0x14

};


// ==================================================

// Shared packet definitions

// These must exactly match foot.ino.

// ==================================================


// Runtime state

// ==================================================


portMUX_TYPE packetMux = portMUX_INITIALIZER_UNLOCKED;


FootState leftState = {};

FootState rightState = {};


volatile uint32_t validPacketCount = 0;

volatile uint32_t invalidLengthCount = 0;

volatile uint32_t invalidMagicCount = 0;

volatile uint32_t unknownSenderCount = 0;


uint32_t lastDisplayMillis = 0;


// ==================================================

// Helpers

// ==================================================


bool macEqual(const uint8_t *a, const uint8_t *b) {

    return memcmp(a, b, 6) == 0;

}


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

    if (axis == ROTATION_X) return sign >= 0 ? "X+" : "X-";

    if (axis == ROTATION_Y) return sign >= 0 ? "Y+" : "Y-";

    if (axis == ROTATION_Z) return sign >= 0 ? "Z+" : "Z-";

    return "UNKNOWN";

}


// ==================================================

// ESP-NOW callback

// ==================================================


void onDataReceived(

    const esp_now_recv_info_t *receiveInfo,

    const uint8_t *incomingData,

    int dataLength

) {

    if (receiveInfo == nullptr || incomingData == nullptr) {

        return;

    }


    if (dataLength != sizeof(MotionPacket)) {

        invalidLengthCount++;

        return;

    }


    MotionPacket received = {};

    memcpy(&received, incomingData, sizeof(received));


    if (received.magic != PACKET_MAGIC) {

        invalidMagicCount++;

        return;

    }


    const uint32_t receivedAt = millis();


    portENTER_CRITICAL(&packetMux);


    if (macEqual(receiveInfo->src_addr, LEFT_FOOT_MAC)) {

        received.footId = FOOT_LEFT;

        leftState.packet = received;

        leftState.lastReceivedMillis = receivedAt;

        leftState.hasPacket = true;

        validPacketCount++;

    } else if (macEqual(receiveInfo->src_addr, RIGHT_FOOT_MAC)) {

        received.footId = FOOT_RIGHT;

        rightState.packet = received;

        rightState.lastReceivedMillis = receivedAt;

        rightState.hasPacket = true;

        validPacketCount++;

    } else {

        unknownSenderCount++;

    }


    portEXIT_CRITICAL(&packetMux);

}


// ==================================================

// ESP-NOW setup

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


    Serial.print("[ESP-NOW] Belt MAC: ");

    Serial.println(WiFi.macAddress());


    if (!setWiFiChannel()) {

        Serial.println("[ESP-NOW] Failed to set Wi-Fi channel");

        return false;

    }


    if (esp_now_init() != ESP_OK) {

        Serial.println("[ESP-NOW] Initialization failed");

        return false;

    }


    if (esp_now_register_recv_cb(onDataReceived) != ESP_OK) {

        Serial.println("[ESP-NOW] Receive callback registration failed");

        return false;

    }


    Serial.print("[ESP-NOW] Listening on channel ");

    Serial.println(WIFI_CHANNEL);


    return true;

}


// ==================================================

// Output

// ==================================================


void printFootState(

    const char *label,

    const FootState &state,

    uint32_t nowMillis

) {

    Serial.print(label);

    Serial.print(" | ");


    if (

        !state.hasPacket ||

        nowMillis - state.lastReceivedMillis > OFFLINE_TIMEOUT_MS

    ) {

        Serial.println("OFFLINE / NO RECENT PACKETS");

        return;

    }


    const MotionPacket &packet = state.packet;


    Serial.print("#");

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

    Serial.println(")");

}


bool isFootOnline(

    const FootState &state,

    uint32_t nowMillis

) {

    return state.hasPacket &&

           (nowMillis - state.lastReceivedMillis) <= OFFLINE_TIMEOUT_MS;

}


bool buildCombinedPacket(

    CombinedMotionPacket &combined,

    const FootState &left,

    const FootState &right,

    uint32_t nowMillis

) {

    if (!isFootOnline(left, nowMillis)) {

        return false;

    }


    if (!isFootOnline(right, nowMillis)) {

        return false;

    }


    combined.timestamp = nowMillis;


combined.sequence =

    (left.packet.sequence > right.packet.sequence)

        ? left.packet.sequence

        : right.packet.sequence;


    combined.accelXG =

        (left.packet.accelXG + right.packet.accelXG) * 0.5f;


    combined.accelYG =

        (left.packet.accelYG + right.packet.accelYG) * 0.5f;


    combined.accelZG =

        (left.packet.accelZG + right.packet.accelZG) * 0.5f;


    combined.gyroXDps =

        (left.packet.gyroXDps + right.packet.gyroXDps) * 0.5f;


    combined.gyroYDps =

        (left.packet.gyroYDps + right.packet.gyroYDps) * 0.5f;


    combined.gyroZDps =

        (left.packet.gyroZDps + right.packet.gyroZDps) * 0.5f;


    combined.velocityX =

        (left.packet.velocityX + right.packet.velocityX) * 0.5f;


    combined.velocityY =

        (left.packet.velocityY + right.packet.velocityY) * 0.5f;


    combined.velocityZ =

        (left.packet.velocityZ + right.packet.velocityZ) * 0.5f;


    combined.rollDeg =

        (left.packet.rollDeg + right.packet.rollDeg) * 0.5f;


    combined.pitchDeg =

        (left.packet.pitchDeg + right.packet.pitchDeg) * 0.5f;


    combined.bothFeetOnline = true;


    return true;

}


void printCombinedPacket(

    const CombinedMotionPacket &packet

) {

    Serial.print("[COMBINED] ");


    Serial.print("#");

    Serial.print(packet.sequence);


    Serial.print(" RP=(");

    Serial.print(packet.rollDeg, 1);

    Serial.print(",");

    Serial.print(packet.pitchDeg, 1);

    Serial.print(")");


    Serial.print(" Vel=(");

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

    Serial.println("Disha-Rakshak Belt Module - BUILD 6 BLE");

    Serial.println("Dual Foot Motion Receiver");

    Serial.println("========================================");


    if (!initializeEspNow()) {

        Serial.println("[FATAL] ESP-NOW initialization failed");

        while (true) {

            delay(1000);

        }

    }


initializeBLE();


    Serial.println("[READY] Waiting for LEFT and RIGHT foot packets");

}


void loop() {

    const uint32_t nowMillis = millis();


    if (nowMillis - lastDisplayMillis < DISPLAY_INTERVAL_MS) {

        delay(1);

        return;

    }


    lastDisplayMillis = nowMillis;


    FootState leftCopy = {};

    FootState rightCopy = {};


    uint32_t validCopy = 0;

    uint32_t invalidLengthCopy = 0;

    uint32_t invalidMagicCopy = 0;

    uint32_t unknownSenderCopy = 0;


    portENTER_CRITICAL(&packetMux);


    leftCopy = leftState;

    rightCopy = rightState;


    validCopy = validPacketCount;

    invalidLengthCopy = invalidLengthCount;

    invalidMagicCopy = invalidMagicCount;

    unknownSenderCopy = unknownSenderCount;


    portEXIT_CRITICAL(&packetMux);


    Serial.println("----------------------------------------");

    printFootState("LEFT ", leftCopy, nowMillis);

    printFootState("RIGHT", rightCopy, nowMillis);


CombinedMotionPacket combined = {};


if (

    buildCombinedPacket(

        combined,

        leftCopy,

        rightCopy,

        nowMillis

    )

) {


    printCombinedPacket(combined);


    if (deviceConnected) {


        String payload =

            createBlePayload(combined);


        pCharacteristic->setValue(

            payload.c_str()

        );


        pCharacteristic->notify();

    }


}

else {


    Serial.println(

        "[COMBINED] Waiting for both foot modules"

    );


}


    Serial.print("[DEBUG] valid=");

    Serial.print(validCopy);

    Serial.print(" badLength=");

    Serial.print(invalidLengthCopy);

    Serial.print(" badMagic=");

    Serial.print(invalidMagicCopy);

    Serial.print(" unknownSender=");

    Serial.println(unknownSenderCopy);

}
