#include <Arduino.h>
#include <WiFi.h>
#include <esp_now.h>
#include <esp_wifi.h>
#include "config.h"   // our shared header

// ── State for each foot module ───────────────
static volatile bool ack_pending_left = false;
static volatile bool ack_pending_right = false;
static volatile uint32_t last_seq_left = 0;
static volatile uint32_t last_seq_right = 0;

// ── Callback: when a packet is received ──────
static void onPacketRecv(const esp_now_recv_info_t* info,
                         const uint8_t* data, int len) {
    if (len < sizeof(imu_packet_t)) return;

    const imu_packet_t* pkt = reinterpret_cast<const imu_packet_t*>(data);

    // Identify which foot sent it
    if (mac_equal(info->src_addr, NODE1_MAC)) {
        last_seq_left = pkt->seq;
        ack_pending_left = true;
        Serial.printf("[RX LEFT] seq=%u ax=%d ay=%d az=%d\n",
                      pkt->seq, pkt->accel[0], pkt->accel[1], pkt->accel[2]);
    } else if (mac_equal(info->src_addr, NODE2_MAC)) {
        last_seq_right = pkt->seq;
        ack_pending_right = true;
        Serial.printf("[RX RIGHT] seq=%u ax=%d ay=%d az=%d\n",
                      pkt->seq, pkt->accel[0], pkt->accel[1], pkt->accel[2]);
    } else {
        Serial.println("[RX] Unknown MAC, ignoring");
    }
}

// ── Send ACK back to sender ──────────────────
static void sendAck(const uint8_t* dest_mac, uint32_t seq) {
ack_packet_t ack;
ack.type = 2;
ack.seq = seq;
esp_now_send(dest_mac, reinterpret_cast<uint8_t*>(&ack), sizeof(ack));
}

// ── Setup ────────────────────────────────────
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
    esp_now_register_recv_cb(onPacketRecv);

    // Register peers (left + right foot)
    register_peer(NODE1_MAC, LMK_NODE1);
    register_peer(NODE2_MAC, LMK_NODE2);

    Serial.print("Current Channel: ");
    Serial.println(WiFi.channel());
    Serial.println("[COMMS] Receiver initialised");
}

// ── Main loop ────────────────────────────────
void loop() {
    if (ack_pending_left) {
        sendAck(NODE1_MAC, last_seq_left);
        ack_pending_left = false;
    }
    if (ack_pending_right) {
        sendAck(NODE2_MAC, last_seq_right);
        ack_pending_right = false;
    }

    delay(50); // small delay
}
