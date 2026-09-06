#include <Wire.h>
#include <math.h>

// ============================================================
// DISHA-RAKSHAK
// FOOT MODULE 2 - TILT DIRECTION TEST
//
// ESP32 + MPU6050
//
// SDA -> GPIO 21
// SCL -> GPIO 22
// MPU6050 -> 0x68
// Serial -> 115200
//
// CURRENT TEST:
// MPU6050 is being held in the hand.
// It is NOT mounted on the foot yet.
//
// This program detects:
//
// LEFT
// RIGHT
// UP
// DOWN
// STILL
//
// using calculated ROLL and PITCH angles.
// ============================================================

#define SDA_PIN 21
#define SCL_PIN 22
#define MPU_ADDR 0x68


// ============================================================
// SETTINGS
// ============================================================

// Minimum tilt angle required before a direction is detected.
const float DIRECTION_ANGLE = 8.0;

// Angle below which we consider the board neutral.
const float STILL_ANGLE = 4.0;

// Extra angle required to change from one direction to another.
// This prevents rapid switching near the boundary.
const float HYSTERESIS = 2.0;

// Moving-average filter.
const uint8_t FILTER_SIZE = 8;


// ============================================================
// MPU DATA
// ============================================================

int16_t ax, ay, az;
int16_t gx, gy, gz;


// ============================================================
// FILTER
// ============================================================

float rollBuffer[FILTER_SIZE];
float pitchBuffer[FILTER_SIZE];

uint8_t filterIndex = 0;
bool filterFull = false;

float filteredRoll = 0.0;
float filteredPitch = 0.0;


// ============================================================
// DIRECTION
// ============================================================

enum Direction
{
  DIR_STILL,
  DIR_LEFT,
  DIR_RIGHT,
  DIR_UP,
  DIR_DOWN
};

Direction currentDirection = DIR_STILL;


// ============================================================
// MPU WRITE
// ============================================================

void writeMPU(byte reg, byte value)
{
  Wire.beginTransmission(MPU_ADDR);
  Wire.write(reg);
  Wire.write(value);
  Wire.endTransmission();
}


// ============================================================
// MPU READ
// ============================================================

bool readMPU()
{
  Wire.beginTransmission(MPU_ADDR);
  Wire.write(0x3B);

  if (Wire.endTransmission(false) != 0)
  {
    return false;
  }

  int count = Wire.requestFrom(MPU_ADDR, 14);

  if (count != 14)
  {
    return false;
  }

  // Accelerometer
  ax = (Wire.read() << 8) | Wire.read();
  ay = (Wire.read() << 8) | Wire.read();
  az = (Wire.read() << 8) | Wire.read();

  // Temperature
  Wire.read();
  Wire.read();

  // Gyroscope
  gx = (Wire.read() << 8) | Wire.read();
  gy = (Wire.read() << 8) | Wire.read();
  gz = (Wire.read() << 8) | Wire.read();

  return true;
}


// ============================================================
// CALCULATE ROLL
//
// Roll = rotation around X axis
// ============================================================

float calculateRoll()
{
  return atan2(
           (float)ay,
           (float)az
         ) * 180.0 / PI;
}


// ============================================================
// CALCULATE PITCH
//
// Pitch = rotation around Y axis
// ============================================================

float calculatePitch()
{
  return atan2(
           -(float)ax,
           sqrt(
             (float)ay * ay +
             (float)az * az
           )
         ) * 180.0 / PI;
}


// ============================================================
// UPDATE FILTER
// ============================================================

void updateFilter(float roll, float pitch)
{
  rollBuffer[filterIndex] = roll;
  pitchBuffer[filterIndex] = pitch;

  filterIndex++;

  if (filterIndex >= FILTER_SIZE)
  {
    filterIndex = 0;
    filterFull = true;
  }

  uint8_t count;

  if (filterFull)
  {
    count = FILTER_SIZE;
  }
  else
  {
    count = filterIndex;

    if (count == 0)
    {
      count = 1;
    }
  }

  float rollSum = 0.0;
  float pitchSum = 0.0;

  for (uint8_t i = 0; i < count; i++)
  {
    rollSum += rollBuffer[i];
    pitchSum += pitchBuffer[i];
  }

  filteredRoll = rollSum / count;
  filteredPitch = pitchSum / count;
}


// ============================================================
// INITIALIZE FILTER
// ============================================================

void initializeFilter()
{
  if (!readMPU())
  {
    return;
  }

  float roll = calculateRoll();
  float pitch = calculatePitch();

  for (uint8_t i = 0; i < FILTER_SIZE; i++)
  {
    rollBuffer[i] = roll;
    pitchBuffer[i] = pitch;
  }

  filteredRoll = roll;
  filteredPitch = pitch;

  filterIndex = 0;
  filterFull = true;
}


// ============================================================
// DIRECTION DETECTION
// ============================================================

Direction calculateDirection()
{
  float roll = filteredRoll;
  float pitch = filteredPitch;

  float absRoll = fabs(roll);
  float absPitch = fabs(pitch);


  // ----------------------------------------------------------
  // STILL
  // ----------------------------------------------------------

  if (
    absRoll < STILL_ANGLE &&
    absPitch < STILL_ANGLE
  )
  {
    return DIR_STILL;
  }


  // ----------------------------------------------------------
  // ROLL AXIS
  //
  // Positive roll = one side
  // Negative roll = opposite side
  //
  // We will verify which one is LEFT/RIGHT during testing.
  // ----------------------------------------------------------

  if (
    absRoll >= DIRECTION_ANGLE &&
    absRoll > absPitch + HYSTERESIS
  )
  {
    if (roll > 0)
    {
      return DIR_RIGHT;
    }
    else
    {
      return DIR_LEFT;
    }
  }


  // ----------------------------------------------------------
  // PITCH AXIS
  //
  // Positive pitch = one direction
  // Negative pitch = opposite direction
  //
  // We will verify which one is UP/DOWN during testing.
  // ----------------------------------------------------------

  if (
    absPitch >= DIRECTION_ANGLE &&
    absPitch > absRoll + HYSTERESIS
  )
  if (pitch > 0)
{
  return DIR_DOWN;
}
else
{
  return DIR_UP;
}


  // ----------------------------------------------------------
  // If the movement is between two axes, keep previous state.
  // ----------------------------------------------------------

  return currentDirection;
}


// ============================================================
// DIRECTION STRING
// ============================================================

const char* directionToString(Direction direction)
{
  switch (direction)
  {
    case DIR_LEFT:
      return "LEFT";

    case DIR_RIGHT:
      return "RIGHT";

    case DIR_UP:
      return "UP";

    case DIR_DOWN:
      return "DOWN";

    case DIR_STILL:
      return "STILL";

    default:
      return "UNKNOWN";
  }
}


// ============================================================
// SETUP
// ============================================================

void setup()
{
  Serial.begin(115200);

  delay(1500);


  Serial.println();
  Serial.println();
  Serial.println("================================================");
  Serial.println("       DISHA-RAKSHAK FOOT MODULE 2");
  Serial.println("          TILT DIRECTION TEST");
  Serial.println("================================================");

  Serial.println();

  // ----------------------------------------------------------
  // I2C
  // ----------------------------------------------------------

  Wire.begin(
    SDA_PIN,
    SCL_PIN
  );

  delay(200);


  Serial.println("[I2C] SDA = GPIO 21");
  Serial.println("[I2C] SCL = GPIO 22");


  // ----------------------------------------------------------
  // CHECK MPU
  // ----------------------------------------------------------

  Wire.beginTransmission(MPU_ADDR);

  if (Wire.endTransmission() != 0)
  {
    Serial.println();
    Serial.println("[ERROR] MPU6050 NOT FOUND at 0x68!");

    while (true)
    {
      delay(1000);
    }
  }


  Serial.println("[SUCCESS] MPU6050 found at 0x68");


  // ----------------------------------------------------------
  // WAKE MPU
  // ----------------------------------------------------------

  writeMPU(
    0x6B,
    0x00
  );

  delay(100);


  // ----------------------------------------------------------
  // ACCELEROMETER ±2g
  // ----------------------------------------------------------

  writeMPU(
    0x1C,
    0x00
  );


  // ----------------------------------------------------------
  // GYROSCOPE ±250 deg/s
  // ----------------------------------------------------------

  writeMPU(
    0x1B,
    0x00
  );


  // ----------------------------------------------------------
  // MPU DIGITAL LOW PASS FILTER
  // ----------------------------------------------------------

  writeMPU(
    0x1A,
    0x03
  );


  delay(500);


  // ----------------------------------------------------------
  // FILTER INITIALIZATION
  // ----------------------------------------------------------

  initializeFilter();


  // ----------------------------------------------------------
  // STARTING MESSAGE
  // ----------------------------------------------------------

  Serial.println();
  Serial.println("================================================");
  Serial.println("              READY FOR TEST");
  Serial.println("================================================");

  Serial.println();

  Serial.println("Hold the MPU6050 in your hand.");
  Serial.println();

  Serial.println("Keep the SAME orientation during all tests.");
  Serial.println();

  Serial.println("Test:");
  Serial.println("  1. STILL");
  Serial.println("  2. RIGHT");
  Serial.println("  3. LEFT");
  Serial.println("  4. UP");
  Serial.println("  5. DOWN");

  Serial.println();

  Serial.println("Do NOT quickly lift or shake the module.");
  Serial.println("Slowly TILT it.");
  Serial.println();

  delay(2000);
}


// ============================================================
// LOOP
// ============================================================

void loop()
{
  // ----------------------------------------------------------
  // READ MPU
  // ----------------------------------------------------------

  if (!readMPU())
  {
    Serial.println("[ERROR] MPU read failed.");
    delay(200);
    return;
  }


  // ----------------------------------------------------------
  // CALCULATE ANGLES
  // ----------------------------------------------------------

  float roll = calculateRoll();
  float pitch = calculatePitch();


  // ----------------------------------------------------------
  // FILTER
  // ----------------------------------------------------------

  updateFilter(
    roll,
    pitch
  );


  // ----------------------------------------------------------
  // DETERMINE DIRECTION
  // ----------------------------------------------------------

  Direction newDirection =
    calculateDirection();


  currentDirection =
    newDirection;


  // ----------------------------------------------------------
  // OUTPUT
  // ----------------------------------------------------------

  Serial.print("Roll: ");

  Serial.print(
    filteredRoll,
    1
  );

  Serial.print(" deg");


  Serial.print(" | Pitch: ");

  Serial.print(
    filteredPitch,
    1
  );

  Serial.print(" deg");


  Serial.print(" | Direction: ");

  Serial.println(
    directionToString(currentDirection)
  );


  delay(100);
}