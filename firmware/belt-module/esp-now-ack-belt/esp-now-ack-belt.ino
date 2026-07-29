#include <Arduino.h>
#include <WiFi.h>
#include <esp_now.h>
#include <esp_wifi.h>
#include <string.h>
#include "config.h"

#define PKT_TYPE_IMU 1
#define PKT_TYPE_ACK 2

static volatile bool ack_pending_left = false;
static volatile uint32_t last_seq_left = 0;

static void setFixedWiFiChannel() {
    esp_wifi_set_promiscuous(true);
    esp_wifi_set_channel(WSN_WIFI_CHANNEL, WIFI_SECOND_CHAN_NONE);
    esp_wifi_set_promiscuous(false);
}

static void sendAck(const uint8_t* dest_mac, uint32_t seq) {
    ack_packet_t ack;
    ack.type = PKT_TYPE_ACK;
    ack.seq = seq;

    esp_err_t result = esp_now_send(dest_mac, reinterpret_cast<uint8_t*>(&ack), sizeof(ack));

    if (result == ESP_OK) {
        Serial.print("[ACK TX] seq=");
        Serial.println(seq);
    } else {
        Serial.print("[ACK TX FAIL] seq=");
        Serial.print(seq);
        Serial.print(" error=");
        Serial.println(result);
    }
}

static void onPacketRecv(const esp_now_recv_info_t* info, const uint8_t* data, int len) {
    if (len < sizeof(imu_packet_t)) {
        Serial.print("[RX] Invalid packet size: ");
        Serial.println(len);
        return;
    }

    const imu_packet_t* pkt = reinterpret_cast<const imu_packet_t*>(data);

    if (pkt->type != PKT_TYPE_IMU) {
        Serial.print("[RX] Unknown packet type: ");
        Serial.println(pkt->type);
        return;
    }

    if (mac_equal(info->src_addr, NODE1_MAC)) {
        last_seq_left = pkt->seq;
        ack_pending_left = true;

        Serial.printf("[RX LEFT] seq=%u ax=%d ay=%d az=%d gx=%d gy=%d gz=%d\n",
                      pkt->seq,
                      pkt->accel[0], pkt->accel[1], pkt->accel[2],
                      pkt->gyro[0], pkt->gyro[1], pkt->gyro[2]);
    } else {
        Serial.println("[RX] Unknown MAC, ignoring");
    }
}

void setup() {
    Serial.begin(115200);
    delay(1500);

    Serial.println();
    Serial.println("Disha-Rakshak Belt Module - ESP-NOW ACK Receiver");
    Serial.println("------------------------------------------------");

    WiFi.mode(WIFI_STA);
    WiFi.disconnect();

    setFixedWiFiChannel();

    Serial.print("Belt ESP32 MAC Address: ");
    Serial.println(WiFi.macAddress());

    Serial.print("ESP-NOW Channel: ");
    Serial.println(WSN_WIFI_CHANNEL);

    if (esp_now_init() != ESP_OK) {
        Serial.println("[COMMS] FATAL: esp_now_init failed");
        while (true) {}
    }

    esp_now_set_pmk(ESP_NOW_PMK);
    esp_now_register_recv_cb(onPacketRecv);

    register_peer(NODE1_MAC, LMK_NODE1);

    Serial.println("[COMMS] Receiver initialised");
}

void loop() {
    if (ack_pending_left) {
        sendAck(NODE1_MAC, last_seq_left);
        ack_pending_left = false;
    }

    delay(10);
}
