#include <Wire.h>

#define SDA_PIN 21
#define SCL_PIN 22

void setup() {
Serial.begin(115200);
delay(1500);

Serial.println();
Serial.println("ESP32 I2C Scanner for MPU6050");
Serial.println("--------------------------------");

Wire.begin(SDA_PIN, SCL_PIN);
Wire.setClock(100000);

Serial.print("SDA pin: ");
Serial.println(SDA_PIN);

Serial.print("SCL pin: ");
Serial.println(SCL_PIN);

Serial.println("Starting scan...");
}

void loop() {
byte error;
byte address;
int deviceCount = 0;

Serial.println();
Serial.println("Scanning I2C bus...");

for (address = 1; address < 127; address++) {
Wire.beginTransmission(address);
error = Wire.endTransmission();

```
if (error == 0) {
  Serial.print("I2C device found at address: 0x");

  if (address < 16) {
    Serial.print("0");
  }

  Serial.println(address, HEX);
  deviceCount++;
} 
else if (error == 4) {
  Serial.print("Unknown error at address: 0x");

  if (address < 16) {
    Serial.print("0");
  }

  Serial.println(address, HEX);
}
```

}

if (deviceCount == 0) {
Serial.println("No I2C devices found.");
Serial.println("Check wiring: VCC, GND, SDA=21, SCL=22, AD0=GND.");
} else {
Serial.print("Scan complete. Devices found: ");
Serial.println(deviceCount);
}

delay(3000);
}
