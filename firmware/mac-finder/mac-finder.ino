#include <Arduino.h>
#include <WiFi.h>

void setup() {
    Serial.begin(115200);
    delay(1500);

    WiFi.mode(WIFI_STA);
    WiFi.disconnect();
    delay(200);

    Serial.println();
    Serial.println("========================================");
    Serial.println("Disha-Rakshak ESP32 MAC Address Finder");
    Serial.println("========================================");

    Serial.print("Wi-Fi STA MAC: ");
    Serial.println(WiFi.macAddress());

    Serial.print("Chip model: ");
    Serial.println(ESP.getChipModel());

    Serial.print("Chip revision: ");
    Serial.println(ESP.getChipRevision());

    Serial.println();
    Serial.println("Copy the Wi-Fi STA MAC shown above.");
}

void loop() {
    delay(5000);

    Serial.print("Wi-Fi STA MAC: ");
    Serial.println(WiFi.macAddress());
}
