#include <Wire.h>

#define SDA_PIN 21
#define SCL_PIN 22
#define MPU_ADDR 0x68

void setup() {
Serial.begin(115200);
delay(1500);

Serial.println();
Serial.println("MPU6050 Raw Accelerometer + Gyroscope Test");
Serial.println("------------------------------------------");

Wire.begin(SDA_PIN, SCL_PIN);
Wire.setClock(100000);

Wire.beginTransmission(MPU_ADDR);
Wire.write(0x6B);
Wire.write(0x00);
byte error = Wire.endTransmission(true);

if (error == 0) {
Serial.println("MPU6050 wake-up successful.");
} else {
Serial.print("MPU6050 wake-up failed. Error: ");
Serial.println(error);
}

delay(500);
}

void loop() {
Wire.beginTransmission(MPU_ADDR);
Wire.write(0x3B);
byte error = Wire.endTransmission(false);

if (error != 0) {
Serial.print("Failed to select register. Error: ");
Serial.println(error);
delay(500);
return;
}

int bytesReceived = Wire.requestFrom(MPU_ADDR, 14, true);

if (bytesReceived == 14) {
int16_t ax = Wire.read() << 8 | Wire.read();
int16_t ay = Wire.read() << 8 | Wire.read();
int16_t az = Wire.read() << 8 | Wire.read();

```
int16_t tempRaw = Wire.read() << 8 | Wire.read();

int16_t gx = Wire.read() << 8 | Wire.read();
int16_t gy = Wire.read() << 8 | Wire.read();
int16_t gz = Wire.read() << 8 | Wire.read();

float ax_g = ax / 16384.0;
float ay_g = ay / 16384.0;
float az_g = az / 16384.0;

float gx_dps = gx / 131.0;
float gy_dps = gy / 131.0;
float gz_dps = gz / 131.0;

Serial.print("Accel(g) -> ");
Serial.print("X: ");
Serial.print(ax_g, 3);
Serial.print(" | Y: ");
Serial.print(ay_g, 3);
Serial.print(" | Z: ");
Serial.print(az_g, 3);

Serial.print(" || Gyro(deg/s) -> ");
Serial.print("X: ");
Serial.print(gx_dps, 2);
Serial.print(" | Y: ");
Serial.print(gy_dps, 2);
Serial.print(" | Z: ");
Serial.println(gz_dps, 2);
```

} else {
Serial.print("Failed to read 14 bytes. Bytes received: ");
Serial.println(bytesReceived);
}

delay(300);
}
