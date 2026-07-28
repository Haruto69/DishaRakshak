#include <Wire.h>
#include <math.h>

#define SDA_PIN 21
#define SCL_PIN 22
#define MPU_ADDR 0x68

const int CALIBRATION_SAMPLES = 1000;

float ax_offset = 0;
float ay_offset = 0;
float az_offset = 0;

float gx_offset = 0;
float gy_offset = 0;
float gz_offset = 0;

void wakeMPU6050() {
Wire.beginTransmission(MPU_ADDR);
Wire.write(0x6B);
Wire.write(0x00);
Wire.endTransmission(true);
}

bool readRawMPU(int16_t &ax, int16_t &ay, int16_t &az, int16_t &gx, int16_t &gy, int16_t &gz) {
Wire.beginTransmission(MPU_ADDR);
Wire.write(0x3B);

if (Wire.endTransmission(false) != 0) {
return false;
}

int bytesReceived = Wire.requestFrom(MPU_ADDR, 14, true);

if (bytesReceived != 14) {
return false;
}

ax = Wire.read() << 8 | Wire.read();
ay = Wire.read() << 8 | Wire.read();
az = Wire.read() << 8 | Wire.read();

Wire.read();
Wire.read();

gx = Wire.read() << 8 | Wire.read();
gy = Wire.read() << 8 | Wire.read();
gz = Wire.read() << 8 | Wire.read();

return true;
}

void calibrateMPU() {
Serial.println();
Serial.println("Keep the foot module completely still.");
Serial.println("Calibration starts in 3 seconds...");
delay(3000);

long ax_sum = 0;
long ay_sum = 0;
long az_sum = 0;

long gx_sum = 0;
long gy_sum = 0;
long gz_sum = 0;

int validSamples = 0;

for (int i = 0; i < CALIBRATION_SAMPLES; i++) {
int16_t ax, ay, az, gx, gy, gz;

```
if (readRawMPU(ax, ay, az, gx, gy, gz)) {
  ax_sum += ax;
  ay_sum += ay;
  az_sum += az;

  gx_sum += gx;
  gy_sum += gy;
  gz_sum += gz;

  validSamples++;
}

delay(2);
```

}

if (validSamples == 0) {
Serial.println("Calibration failed. No valid MPU6050 samples.");
return;
}

ax_offset = ax_sum / (float)validSamples;
ay_offset = ay_sum / (float)validSamples;

// During the successful unit test, the Z axis was negative while still.
// This preserves -1g on the Z axis after calibration.
az_offset = (az_sum / (float)validSamples) + 16384.0;

gx_offset = gx_sum / (float)validSamples;
gy_offset = gy_sum / (float)validSamples;
gz_offset = gz_sum / (float)validSamples;

Serial.println("Calibration complete.");
Serial.println();

Serial.print("Accel offsets: ");
Serial.print(ax_offset);
Serial.print(", ");
Serial.print(ay_offset);
Serial.print(", ");
Serial.println(az_offset);

Serial.print("Gyro offsets: ");
Serial.print(gx_offset);
Serial.print(", ");
Serial.print(gy_offset);
Serial.print(", ");
Serial.println(gz_offset);

Serial.println();
}

void setup() {
Serial.begin(115200);
delay(1500);

Serial.println("Foot IMU Movement + ZUPT Test");
Serial.println("-----------------------------");

Wire.begin(SDA_PIN, SCL_PIN);
Wire.setClock(100000);

wakeMPU6050();
delay(500);

calibrateMPU();
}

void loop() {
int16_t ax_raw, ay_raw, az_raw, gx_raw, gy_raw, gz_raw;

if (!readRawMPU(ax_raw, ay_raw, az_raw, gx_raw, gy_raw, gz_raw)) {
Serial.println("MPU READ FAILED");
delay(100);
return;
}

float ax = (ax_raw - ax_offset) / 16384.0;
float ay = (ay_raw - ay_offset) / 16384.0;
float az = (az_raw - az_offset) / 16384.0;

float gx = (gx_raw - gx_offset) / 131.0;
float gy = (gy_raw - gy_offset) / 131.0;
float gz = (gz_raw - gz_offset) / 131.0;

float accelMag = sqrt(ax * ax + ay * ay + az * az);
float gyroMag = sqrt(gx * gx + gy * gy + gz * gz);

bool stationary = fabs(accelMag - 1.0) < 0.15 && gyroMag < 15.0;

Serial.print("A=");
Serial.print(accelMag, 3);

Serial.print("g | G=");
Serial.print(gyroMag, 2);

Serial.print(" deg/s | ");

if (stationary) {
Serial.println("FOOT STATIONARY / ZUPT POSSIBLE");
} else {
Serial.println("FOOT MOVING");
}

delay(50);
}
