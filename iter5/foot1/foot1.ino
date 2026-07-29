#include <Arduino.h>
#include <WiFi.h>
#include <esp_now.h>
#include <esp_wifi.h>
#include "wsn_config.h"   // shared header with MACs, keys, packet structs

// ── State ───────────────────────────────
static uint32_t seq = 0;          // sequence number for packets
static volatile bool ack_received = false;
static volatile uint32_t ack_seq = 0;

// ── Callback: when packet is sent ───────
static void onDataSent(const wifi_tx_info_t* info, esp_now_send_status_t status) {
    if (status == ESP_NOW_SEND_SUCCESS) {
        Serial.println("[TX] Packet delivered successfully");
    } else {
        Serial.println("[TX] Delivery failed");
    }
}

// ── Callback: when ACK is received ──────
static void onDataRecv(const esp_now_recv_info_t* info,
                       const uint8_t* data, int len) {
    if (len < sizeof(ack_packet_t)) return;
    const ack_packet_t* ack = reinterpret_cast<const ack_packet_t*>(data);
    ack_seq = ack->seq;
    ack_received = true;
    Serial.printf("[ACK] Received for seq=%u\n", ack_seq);
}

// ── Setup ───────────────────────────────
void setup() {
    Serial.begin(115200);

    WiFi.mode(WIFI_STA);
    WiFi.disconnect();

    // Force fixed Wi-Fi channel
    esp_wifi_set_promiscuous(true);
    esp_wifi_set_channel(WSN_WIFI_CHANNEL, WIFI_SECOND_CHAN_NONE);
    esp_wifi_set_promiscuous(false);

    if (esp_now_init() != ESP_OK) {
        Serial.println("[COMMS] FATAL: esp_now_init failed");
        while (true) {}
    }

    esp_now_set_pmk(ESP_NOW_PMK);
    esp_now_register_send_cb(onDataSent);
    esp_now_register_recv_cb(onDataRecv);

    // Register belt module as peer
    register_peer(BASE_MAC, LMK_NODE1);

    Serial.print("Current Channel: ");
    Serial.println(WiFi.channel());
    Serial.println("[COMMS] Foot Module 1 initialised");
}

// ── Main loop ───────────────────────────
void loop() {
    // Build IMU packet (dummy values for now)
    imu_packet_t pkt;
    pkt.type = PKT_TYPE_IMU;
    pkt.seq = seq++;
    pkt.accel[0] = 100;  // dummy X accel
    pkt.accel[1] = 200;  // dummy Y accel
    pkt.accel[2] = 300;  // dummy Z accel
    pkt.gyro[0]  = 10;   // dummy gyro X
    pkt.gyro[1]  = 20;   // dummy gyro Y
    pkt.gyro[2]  = 30;   // dummy gyro Z

    // Send packet
    esp_now_send(BASE_MAC, reinterpret_cast<uint8_t*>(&pkt), sizeof(pkt));
    Serial.printf("[TX] Sent seq=%u ax=%d ay=%d az=%d\n",
                  pkt.seq, pkt.accel[0], pkt.accel[1], pkt.accel[2]);

    // Wait for ACK (up to 300 ms)
    unsigned long start = millis();
    while (!ack_received && millis() - start < 300) {
        // wait for ACK
    }

    if (ack_received && ack_seq == pkt.seq) {
        Serial.printf("[ACK] Confirmed for seq=%u\n", ack_seq);
        ack_received = false; // reset for next packet
    } else {
        Serial.printf("[TX] No ACK for seq=%u, will resend\n", pkt.seq);
        // optional: implement resend logic here
    }

    delay(500); // send every 0.5s for demo
}
