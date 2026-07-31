#include <Arduino.h>
#include <WiFi.h>
#include <esp_now.h>
#include <esp_wifi.h>

// --------------------------------------------------
// ESP-NOW configuration
// --------------------------------------------------

constexpr uint8_t WIFI_CHANNEL = 6;

// Must match the value in foot.ino.
constexpr uint32_t PACKET_MAGIC = 0x44524B31;

// --------------------------------------------------
// Packet shared with foot.ino
// --------------------------------------------------

struct ImuPacket {
    uint32_t magic;
    uint32_t sequence;

    int16_t accelX;
    int16_t accelY;
    int16_t accelZ;

    int16_t gyroX;
    int16_t gyroY;
    int16_t gyroZ;
};

static_assert(
    sizeof(ImuPacket) == 20,
    "Unexpected ImuPacket size"
);

// Callback writes here.
// loop() performs Serial printing to avoid doing too much work
// inside the ESP-NOW callback.
volatile bool newPacketAvailable = false;

portMUX_TYPE packetMux = portMUX_INITIALIZER_UNLOCKED;

ImuPacket latestPacket = {};

uint8_t latestSenderMac[6] = {};

// --------------------------------------------------
// ESP-NOW helper functions
// --------------------------------------------------

bool setWiFiChannel() {
    esp_wifi_set_promiscuous(true);

    esp_err_t result = esp_wifi_set_channel(
        WIFI_CHANNEL,
        WIFI_SECOND_CHAN_NONE
    );

    esp_wifi_set_promiscuous(false);

    if (result != ESP_OK) {
        Serial.print("[ESP-NOW] Failed to set channel. Error: ");
        Serial.println(result);
        return false;
    }

    return true;
}

void onDataReceived(
    const esp_now_recv_info_t *receiveInfo,
    const uint8_t *incomingData,
    int dataLength
) {
    if (receiveInfo == nullptr || incomingData == nullptr) {
        return;
    }

    if (dataLength != sizeof(ImuPacket)) {
        return;
    }

    ImuPacket receivedPacket = {};

    memcpy(
        &receivedPacket,
        incomingData,
        sizeof(receivedPacket)
    );

    if (receivedPacket.magic != PACKET_MAGIC) {
        return;
    }

    portENTER_CRITICAL(&packetMux);

    latestPacket = receivedPacket;

    memcpy(
        latestSenderMac,
        receiveInfo->src_addr,
        sizeof(latestSenderMac)
    );

    newPacketAvailable = true;

    portEXIT_CRITICAL(&packetMux);
}

bool initializeEspNow() {
    WiFi.mode(WIFI_STA);
    WiFi.disconnect();

    delay(100);

    Serial.print("[ESP-NOW] Belt ESP32 MAC: ");
    Serial.println(WiFi.macAddress());

    if (!setWiFiChannel()) {
        return false;
    }

    if (esp_now_init() != ESP_OK) {
        Serial.println("[ESP-NOW] Initialization failed");
        return false;
    }

    if (esp_now_register_recv_cb(onDataReceived) != ESP_OK) {
        Serial.println("[ESP-NOW] Failed to register receive callback");
        return false;
    }

    Serial.print("[ESP-NOW] Listening on channel ");
    Serial.println(WIFI_CHANNEL);

    return true;
}

// --------------------------------------------------
// Serial output
// --------------------------------------------------

void printMacAddress(const uint8_t *macAddress) {
    for (int i = 0; i < 6; i++) {
        if (macAddress[i] < 0x10) {
            Serial.print('0');
        }

        Serial.print(macAddress[i], HEX);

        if (i < 5) {
            Serial.print(':');
        }
    }
}

void printPacket(
    const ImuPacket &packet,
    const uint8_t *senderMac
) {
    Serial.print("[RECEIVED] From=");
    printMacAddress(senderMac);

    Serial.print(" | Sequence=");
    Serial.print(packet.sequence);

    Serial.print(" | Accel: X=");
    Serial.print(packet.accelX);

    Serial.print(" Y=");
    Serial.print(packet.accelY);

    Serial.print(" Z=");
    Serial.print(packet.accelZ);

    Serial.print(" | Gyro: X=");
    Serial.print(packet.gyroX);

    Serial.print(" Y=");
    Serial.print(packet.gyroY);

    Serial.print(" Z=");
    Serial.println(packet.gyroZ);
}

// --------------------------------------------------
// Arduino setup and loop
// --------------------------------------------------

void setup() {
    Serial.begin(115200);
    delay(1500);

    Serial.println();
    Serial.println("========================================");
    Serial.println("Disha-Rakshak Belt Module");
    Serial.println("ESP-NOW Raw MPU Data Receiver");
    Serial.println("========================================");

    if (!initializeEspNow()) {
        Serial.println("[FATAL] ESP-NOW initialization failed");

        while (true) {
            delay(1000);
        }
    }

    Serial.println("[READY] Waiting for foot-module packets");
}

void loop() {
    ImuPacket packetCopy = {};
    uint8_t senderMacCopy[6] = {};
    bool shouldPrint = false;

    portENTER_CRITICAL(&packetMux);

    if (newPacketAvailable) {
        packetCopy = latestPacket;

        memcpy(
            senderMacCopy,
            latestSenderMac,
            sizeof(senderMacCopy)
        );

        newPacketAvailable = false;
        shouldPrint = true;
    }

    portEXIT_CRITICAL(&packetMux);

    if (shouldPrint) {
        printPacket(packetCopy, senderMacCopy);
    }

    delay(1);
}