#include <WiFi.h>
#include <esp_now.h>
#include <esp_wifi.h>

#define ESPNOW_CHANNEL 1

uint8_t broadcastAddress[] = {0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF};

typedef struct struct_message {
  int packetId;
  char message[32];
} struct_message;

struct_message outgoingData;

int packetCounter = 0;

void setup() {
  Serial.begin(115200);
  delay(1500);

  Serial.println();
  Serial.println("Disha-Rakshak Foot Module - ESP-NOW Ping Sender");
  Serial.println("-----------------------------------------------");

  WiFi.mode(WIFI_STA);
  WiFi.disconnect();

  esp_wifi_set_channel(ESPNOW_CHANNEL, WIFI_SECOND_CHAN_NONE);

  Serial.print("Foot ESP32 MAC Address: ");
  Serial.println(WiFi.macAddress());

  Serial.print("ESP-NOW Channel: ");
  Serial.println(ESPNOW_CHANNEL);

  if (esp_now_init() != ESP_OK) {
    Serial.println("ESP-NOW initialization failed.");
    return;
  }

  esp_now_peer_info_t peerInfo = {};
  memcpy(peerInfo.peer_addr, broadcastAddress, 6);
  peerInfo.channel = ESPNOW_CHANNEL;
  peerInfo.encrypt = false;

  if (esp_now_add_peer(&peerInfo) != ESP_OK) {
    Serial.println("Failed to add broadcast peer.");
    return;
  }

  Serial.println("Sender ready. Broadcasting packets...");
}

void loop() {
  packetCounter++;

  outgoingData.packetId = packetCounter;
  snprintf(outgoingData.message, sizeof(outgoingData.message), "FOOT_PING #%d", packetCounter);

  esp_err_t result = esp_now_send(broadcastAddress, (uint8_t *)&outgoingData, sizeof(outgoingData));

  if (result == ESP_OK) {
    Serial.print("Sent packet: ");
    Serial.println(outgoingData.message);
  } else {
    Serial.print("Send failed. Error code: ");
    Serial.println(result);
  }

  delay(1000);
}