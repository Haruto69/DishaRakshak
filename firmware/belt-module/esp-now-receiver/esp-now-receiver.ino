#include <WiFi.h>
#include <esp_now.h>
#include <esp_wifi.h>
#include <esp_idf_version.h>

#define ESPNOW_CHANNEL 1

typedef struct struct_message {
  int packetId;
  char message[32];
} struct_message;

struct_message incomingData;

void printMacAddress(const uint8_t *mac) {
  for (int i = 0; i < 6; i++) {
    if (mac[i] < 16) {
      Serial.print("0");
    }

    Serial.print(mac[i], HEX);

    if (i < 5) {
      Serial.print(":");
    }
  }
}

#if ESP_IDF_VERSION >= ESP_IDF_VERSION_VAL(5, 0, 0)
void onDataRecv(const esp_now_recv_info_t *info, const uint8_t *data, int len) {
  const uint8_t *mac = info->src_addr;
#else
void onDataRecv(const uint8_t *mac, const uint8_t *data, int len) {
#endif

  if (len != sizeof(incomingData)) {
    Serial.print("Received packet with unexpected size: ");
    Serial.println(len);
    return;
  }

  memcpy(&incomingData, data, sizeof(incomingData));

  Serial.println();
  Serial.println("Packet received from foot ESP32");

  Serial.print("Sender MAC: ");
  printMacAddress(mac);
  Serial.println();

  Serial.print("Packet ID: ");
  Serial.println(incomingData.packetId);

  Serial.print("Message: ");
  Serial.println(incomingData.message);
}

void setup() {
  Serial.begin(115200);
  delay(1500);

  Serial.println();
  Serial.println("Disha-Rakshak Belt Module - ESP-NOW Receiver");
  Serial.println("--------------------------------------------");

  WiFi.mode(WIFI_STA);
  WiFi.disconnect();

  esp_wifi_set_channel(ESPNOW_CHANNEL, WIFI_SECOND_CHAN_NONE);

  Serial.print("Belt ESP32 MAC Address: ");
  Serial.println(WiFi.macAddress());

  Serial.print("ESP-NOW Channel: ");
  Serial.println(ESPNOW_CHANNEL);

  if (esp_now_init() != ESP_OK) {
    Serial.println("ESP-NOW initialization failed.");
    return;
  }

  esp_now_register_recv_cb(onDataRecv);

  Serial.println("Receiver ready. Waiting for foot module packets...");
}

void loop() {
  delay(1000);
}