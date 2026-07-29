#include <Arduino.h>
#include <WiFi.h>
#include <esp_now.h>
#include <esp_wifi.h>
#include <Wire.h>
#include <string.h>
#include "config.h"

#define SDA_PIN 21
#define SCL_PIN 22
#define MPU_ADDR 0x68

#define PKT_TYPE_IMU 1
#define PKT_TYPE_ACK 2

#define SEND_INTERVAL_MS 100
#define ACK_TIMEOUT_MS 80
#define MAX_RETRIES 5

static uint32_t seqCounter = 0;

static volatile bool ackReceived = false;
static volatile uint32_t receivedAckSeq = 0;

static void setFixedWiFiChannel() {
esp_wifi_set_promiscuous(true);
esp_wifi_set_channel(WSN_WIFI_CHANNEL, WIFI_SECOND_CHAN_NONE);
esp_wifi_set_promiscuous(false);
}

static void wakeMPU6050() {
Wire.beginTransmission(MPU_ADDR);
Wire.write(0x6B);
Wire.write(0x00);
Wire.endTransmission(true);
}

static bool readRawMPU(int16_t accel[3], int16_t gyro[3]) {
Wire.beginTransmission(MPU_ADDR);
Wire.write(0x3B);

```
if (Wire.endTransmission(false) != 0) {
    return false;
}

int bytesReceived = Wire.requestFrom(MPU_ADDR, 14, true);

if (bytesReceived != 14) {
    return false;
}

accel[0] = Wire.read() << 8 | Wire.read();
accel[1] = Wire.read() << 8 | Wire.read();
accel[2] = Wire.read() << 8 | Wire.read();

Wire.read();
Wire.read();

gyro[0] = Wire.read() << 8 | Wire.read();
gyro[1] = Wire.read() << 8 | Wire.read();
gyro[2] = Wire.read() << 8 | Wire.read();

return true;
```

}

static void printMacAddress(const uint8_t *mac) {
for (int i = 0; i < 6; i++) {
if (mac[i] < 16) {
Serial.print("0");
}

```
    Serial.print(mac[i], HEX);

    if (i < 5) {
        Serial.print(":");
    }
}
```

}

static void onPacketSent(const uint8_t *mac_addr, esp_now_send_status_t status) {
Serial.print("[SEND STATUS] To ");
printMacAddress(mac_addr);
Serial.print(" -> ");

```
if (status == ESP_NOW_SEND_SUCCESS) {
    Serial.println("DELIVERED");
} else {
    Serial.println("FAILED");
}
```

}

static void onPacketRecv(const esp_now_recv_info_t* info, const uint8_t* data, int len) {
if (!mac_equal(info->src_addr, BASE_MAC)) {
Serial.println("[ACK RX] Unknown sender, ignoring");
return;
}

```
if (len < sizeof(ack_packet_t)) {
    Serial.println("[ACK RX] Invalid ACK size");
    return;
}

ack_packet_t ack;
memcpy(&ack, data, sizeof(ack));

receivedAckSeq = ack.seq;
ackReceived = true;

Serial.print("[ACK RX] seq=");
Serial.println(ack.seq);
```

}

static bool sendImuPacketWithAck(const imu_packet_t &packet) {
for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
ackReceived = false;
receivedAckSeq = 0;

```
    esp_err_t result = esp_now_send(BASE_MAC, reinterpret_cast<const uint8_t*>(&packet), sizeof(packet));

    if (result != ESP_OK) {
        Serial.print("[TX] esp_now_send failed. Error: ");
        Serial.println(result);
        delay(20);
        continue;
    }

    unsigned long startTime = millis();

    while (millis() - startTime < ACK_TIMEOUT_MS) {
        if (ackReceived && receivedAckSeq == packet.seq) {
            Serial.print("[TX OK] seq=");
            Serial.print(packet.seq);
            Serial.print(" attempt=");
            Serial.println(attempt);
            return true;
        }

        delay(1);
    }

    Serial.print("[TX RETRY] seq=");
    Serial.print(packet.seq);
    Serial.print(" attempt=");
    Serial.println(attempt);
}

Serial.print("[TX FAIL] No ACK for seq=");
Serial.println(packet.seq);
return false;
```

}

void setup() {
Serial.begin(115200);
delay(1500);

```
Serial.println();
Serial.println("Disha-Rakshak Foot Module - MPU6050 ESP-NOW ACK Sender");
Serial.println("------------------------------------------------------");

Wire.begin(SDA_PIN, SCL_PIN);
Wire.setClock(100000);

wakeMPU6050();
delay(500);

WiFi.mode(WIFI_STA);
WiFi.disconnect();

setFixedWiFiChannel();

Serial.print("Foot ESP32 MAC Address: ");
Serial.println(WiFi.macAddress());

Serial.print("Target Belt MAC Address: ");
printMacAddress(BASE_MAC);
Serial.println();

Serial.print("ESP-NOW Channel: ");
Serial.println(WSN_WIFI_CHANNEL);

if (esp_now_init() != ESP_OK) {
    Serial.println("[COMMS] FATAL: esp_now_init failed");
    while (true) {}
}

esp_now_set_pmk(ESP_NOW_PMK);

register_peer(BASE_MAC, LMK_NODE1);

esp_now_register_send_cb(onPacketSent);
esp_now_register_recv_cb(onPacketRecv);

Serial.println("[COMMS] Foot sender initialised");
```

}

void loop() {
imu_packet_t packet;

```
packet.type = PKT_TYPE_IMU;
packet.seq = ++seqCounter;

bool readOk = readRawMPU(packet.accel, packet.gyro);

if (!readOk) {
    Serial.println("[IMU] Failed to read MPU6050");
    delay(SEND_INTERVAL_MS);
    return;
}

Serial.print("[IMU TX] seq=");
Serial.print(packet.seq);

Serial.print(" ax=");
Serial.print(packet.accel[0]);

Serial.print(" ay=");
Serial.print(packet.accel[1]);

Serial.print(" az=");
Serial.print(packet.accel[2]);

Serial.print(" gx=");
Serial.print(packet.gyro[0]);

Serial.print(" gy=");
Serial.print(packet.gyro[1]);

Serial.print(" gz=");
Serial.println(packet.gyro[2]);

sendImuPacketWithAck(packet);

delay(SEND_INTERVAL_MS);
```

}
