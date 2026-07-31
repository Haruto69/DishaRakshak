#include <Arduino.h>
#include <Wire.h>
#include <WiFi.h>
#include <esp_now.h>
#include <esp_wifi.h>

constexpr uint8_t MPU6050_ADDRESS = 0x68;
constexpr int SDA_PIN = 21;
constexpr int SCL_PIN = 22;
constexpr uint32_t I2C_CLOCK_HZ = 100000;

constexpr uint8_t WIFI_CHANNEL = 6;
constexpr uint32_t PACKET_MAGIC = 0x44524B31;
constexpr unsigned long SEND_INTERVAL_MS = 100;

const uint8_t BROADCAST_ADDRESS[6] = {
    0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF
};

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

static_assert(sizeof(ImuPacket) == 20, "Unexpected ImuPacket size");

uint32_t sequenceNumber = 0;
unsigned long previousSendTime = 0;

bool writeMpuRegister(uint8_t registerAddress, uint8_t value) {
    Wire.beginTransmission(MPU6050_ADDRESS);
    Wire.write(registerAddress);
    Wire.write(value);
    return Wire.endTransmission(true) == 0;
}

bool readMpuRegister(uint8_t registerAddress, uint8_t &value) {
    Wire.beginTransmission(MPU6050_ADDRESS);
    Wire.write(registerAddress);

    if (Wire.endTransmission(false) != 0) {
        return false;
    }

    size_t received = Wire.requestFrom(
        static_cast<uint16_t>(MPU6050_ADDRESS),
        static_cast<uint8_t>(1),
        true
    );

    if (received != 1) {
        return false;
    }

    value = Wire.read();
    return true;
}

bool initializeMpu6050() {
    Serial.println("[MPU] Initializing MPU6050...");

    uint8_t whoAmI = 0;
    bool detected = false;

    for (int attempt = 1; attempt <= 10; attempt++) {
        if (readMpuRegister(0x75, whoAmI)) {
            Serial.print("[MPU] WHO_AM_I attempt ");
            Serial.print(attempt);
            Serial.print(": 0x");
            Serial.println(whoAmI, HEX);

            if (whoAmI == 0x68 || whoAmI == 0x70) {
                detected = true;
                break;
            }
        } else {
            Serial.print("[MPU] WHO_AM_I read failed on attempt ");
            Serial.println(attempt);
        }

        delay(100);
    }

    if (!detected) {
        Serial.print("[MPU] Unsupported WHO_AM_I value: 0x");
        Serial.println(whoAmI, HEX);
        return false;
    }

    if (whoAmI == 0x68) {
        Serial.println("[MPU] Detected MPU-6050");
    } else {
        Serial.println("[MPU] Detected MPU-6500-compatible sensor");
    }

    if (!writeMpuRegister(0x6B, 0x00)) {
        Serial.println("[MPU] Failed to wake MPU6050");
        return false;
    }

    delay(100);

    if (!writeMpuRegister(0x1C, 0x00)) {
        Serial.println("[MPU] Failed to configure accelerometer");
        return false;
    }

    if (!writeMpuRegister(0x1B, 0x00)) {
        Serial.println("[MPU] Failed to configure gyroscope");
        return false;
    }

    if (!writeMpuRegister(0x1A, 0x03)) {
        Serial.println("[MPU] Failed to configure low-pass filter");
        return false;
    }

    Serial.println("[MPU] IMU initialized successfully");
    return true;
}

bool readMpu6050(ImuPacket &packet) {
    Wire.beginTransmission(MPU6050_ADDRESS);
    Wire.write(0x3B);

    if (Wire.endTransmission(false) != 0) {
        return false;
    }

    constexpr uint8_t BYTES_TO_READ = 14;

    size_t received = Wire.requestFrom(
        static_cast<uint16_t>(MPU6050_ADDRESS),
        BYTES_TO_READ,
        true
    );

    if (received != BYTES_TO_READ) {
        return false;
    }

    packet.accelX = static_cast<int16_t>((Wire.read() << 8) | Wire.read());
    packet.accelY = static_cast<int16_t>((Wire.read() << 8) | Wire.read());
    packet.accelZ = static_cast<int16_t>((Wire.read() << 8) | Wire.read());

    Wire.read();
    Wire.read();

    packet.gyroX = static_cast<int16_t>((Wire.read() << 8) | Wire.read());
    packet.gyroY = static_cast<int16_t>((Wire.read() << 8) | Wire.read());
    packet.gyroZ = static_cast<int16_t>((Wire.read() << 8) | Wire.read());

    return true;
}

bool setWiFiChannel() {
    esp_wifi_set_promiscuous(true);

    esp_err_t result = esp_wifi_set_channel(
        WIFI_CHANNEL,
        WIFI_SECOND_CHAN_NONE
    );

    esp_wifi_set_promiscuous(false);

    if (result != ESP_OK) {
        Serial.print("[ESP-NOW] Failed to set Wi-Fi channel. Error: ");
        Serial.println(result);
        return false;
    }

    return true;
}

bool initializeEspNow() {
    WiFi.mode(WIFI_STA);
    WiFi.disconnect();
    delay(100);

    Serial.print("[ESP-NOW] Foot ESP32 MAC: ");
    Serial.println(WiFi.macAddress());

    if (!setWiFiChannel()) {
        return false;
    }

    if (esp_now_init() != ESP_OK) {
        Serial.println("[ESP-NOW] Initialization failed");
        return false;
    }

    esp_now_peer_info_t peerInfo = {};
    memcpy(
        peerInfo.peer_addr,
        BROADCAST_ADDRESS,
        sizeof(BROADCAST_ADDRESS)
    );

    peerInfo.channel = WIFI_CHANNEL;
    peerInfo.encrypt = false;
    peerInfo.ifidx = WIFI_IF_STA;

    esp_err_t addPeerResult = esp_now_add_peer(&peerInfo);

    if (addPeerResult != ESP_OK && addPeerResult != ESP_ERR_ESPNOW_EXIST) {
        Serial.print("[ESP-NOW] Failed to add broadcast peer. Error: ");
        Serial.println(addPeerResult);
        return false;
    }

    Serial.print("[ESP-NOW] Initialized on channel ");
    Serial.println(WIFI_CHANNEL);
    return true;
}

void printPacket(const ImuPacket &packet) {
    Serial.print("[SENT] Sequence=");
    Serial.print(packet.sequence);

    Serial.print(" | Accel X=");
    Serial.print(packet.accelX);
    Serial.print(" Y=");
    Serial.print(packet.accelY);
    Serial.print(" Z=");
    Serial.print(packet.accelZ);

    Serial.print(" | Gyro X=");
    Serial.print(packet.gyroX);
    Serial.print(" Y=");
    Serial.print(packet.gyroY);
    Serial.print(" Z=");
    Serial.println(packet.gyroZ);
}

void setup() {
    Serial.begin(115200);
    Serial.println("FOOT FIRMWARE BUILD 2 LOADED");
    delay(1500);

    Serial.println();
    Serial.println("========================================");
    Serial.println("Disha-Rakshak Foot Module");
    Serial.println("IMU ESP-NOW Raw Data Sender - BUILD 2");
    Serial.println("========================================");

    Wire.begin(SDA_PIN, SCL_PIN);
    Wire.setClock(I2C_CLOCK_HZ);
    delay(250);

    if (!initializeMpu6050()) {
        Serial.println("[FATAL] MPU6050 initialization failed");

        while (true) {
            delay(1000);
        }
    }

    if (!initializeEspNow()) {
        Serial.println("[FATAL] ESP-NOW initialization failed");

        while (true) {
            delay(1000);
        }
    }

    Serial.println("[READY] Foot module is transmitting MPU data");
}

void loop() {
    unsigned long currentTime = millis();

    if (currentTime - previousSendTime < SEND_INTERVAL_MS) {
        delay(1);
        return;
    }

    previousSendTime = currentTime;

    ImuPacket packet = {};
    packet.magic = PACKET_MAGIC;
    packet.sequence = ++sequenceNumber;

    if (!readMpu6050(packet)) {
        Serial.println("[MPU] Failed to read sensor data");
        return;
    }

    esp_err_t sendResult = esp_now_send(
        BROADCAST_ADDRESS,
        reinterpret_cast<const uint8_t *>(&packet),
        sizeof(packet)
    );

    if (sendResult != ESP_OK) {
        Serial.print("[SEND ERROR] esp_now_send returned: ");
        Serial.println(sendResult);
        return;
    }

    printPacket(packet);
}
