#pragma once
#include <stdint.h>
#define PKT_TYPE_IMU 0x01
#define PKT_TYPE_ACK 0x02

// ── Wi-Fi channel ───────────────────────────
#define WSN_WIFI_CHANNEL 6

// ── MAC addresses ───────────────────────────
static const uint8_t BASE_MAC[6]  = {0x70, 0x4B, 0xCA, 0x47, 0x57, 0x14};
static const uint8_t NODE1_MAC[6] = {0x70, 0x4B, 0xCA, 0x47, 0x6D, 0x3C};
static const uint8_t NODE2_MAC[6] = { /* right foot MAC */ };

// ── Keys ────────────────────────────────────
// Shared network key (same on all devices)
static const uint8_t ESP_NOW_PMK[16] = {
    0x2B, 0x7E, 0x15, 0x16,
    0x28, 0xAE, 0xD2, 0xA6,
    0xAB, 0xF7, 0x15, 0x88,
    0x09, 0xCF, 0x4F, 0x3C
};

// Local Master Key for Foot Module 1 ↔ Belt
static const uint8_t LMK_NODE1[16] = {
    0x60, 0x3D, 0xEB, 0x10,
    0x15, 0xCA, 0x71, 0xBE,
    0x2B, 0x73, 0xAE, 0xF0,
    0x85, 0x7D, 0x77, 0x81
};

// Local Master Key for Foot Module 2 ↔ Belt
static const uint8_t LMK_NODE2[16] = {
    0x1F, 0x35, 0x2C, 0x07,
    0x3B, 0x61, 0x08, 0xD7,
    0x2D, 0x98, 0x10, 0xA3,
    0x09, 0x14, 0xDF, 0xF4
};

// ── Packet structures ───────────────────────
typedef struct {
    uint8_t type;       // e.g. PKT_TYPE_IMU
    uint32_t seq;
    int16_t accel[3];
    int16_t gyro[3];
} imu_packet_t;

typedef struct {
    uint8_t type;       // e.g. PKT_TYPE_ACK
    uint32_t seq;
} ack_packet_t;

// ── Utility functions ───────────────────────
static bool mac_equal(const uint8_t* a, const uint8_t* b) {
    for (int i = 0; i < 6; i++) if (a[i] != b[i]) return false;
    return true;
}

static void register_peer(const uint8_t* mac, const uint8_t* lmk) {
    esp_now_peer_info_t peerInfo;
    memset(&peerInfo, 0, sizeof(peerInfo));
    memcpy(peerInfo.peer_addr, mac, 6);
    peerInfo.channel = WSN_WIFI_CHANNEL;
    peerInfo.encrypt = true;
    memcpy(peerInfo.lmk, lmk, 16);
    esp_now_add_peer(&peerInfo);
}
