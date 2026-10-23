#include <Arduino.h>
#include <Wire.h>
#include <math.h>

// ============================================================
// Disha-Rakshak
// Foot IMU Test 02 - Guided Physical Axis Mapping
//
// COMMANDS:
//   f = forward
//   b = backward
//   l = left
//   r = right
//   u = up
//   d = down
//   c = clockwise rotation
//   a = anticlockwise rotation
//
// PROCEDURE:
//   1. Enter command in Serial Monitor.
//   2. Keep module still during baseline.
//   3. Wait for countdown.
//   4. Perform ONE deliberate movement at "MOVE NOW!".
//   5. Firmware records for 2 seconds.
//   6. Results remain on screen.
// ============================================================


// ============================================================
// Configuration
// ============================================================

constexpr uint8_t IMU_ADDRESS = 0x68;

constexpr int SDA_PIN = 21;
constexpr int SCL_PIN = 22;

constexpr uint32_t I2C_CLOCK_HZ = 100000;

constexpr float ACCEL_SCALE = 16384.0f;
constexpr float GYRO_SCALE = 131.0f;

constexpr uint32_t SAMPLE_INTERVAL_US = 10000; // 100 Hz

constexpr int BASELINE_SAMPLES = 100;          // ~1 second

constexpr uint32_t TEST_DURATION_MS = 2000;    // 2 seconds


// ============================================================
// Data structures
// ============================================================

struct RawImu {
    int16_t ax;
    int16_t ay;
    int16_t az;

    int16_t gx;
    int16_t gy;
    int16_t gz;
};


struct ImuSample {
    float ax;
    float ay;
    float az;

    float gx;
    float gy;
    float gz;
};


// ============================================================
// Explicit function declarations
//
// These avoid Arduino's automatic prototype generator causing
// problems with custom structures such as ImuSample.
// ============================================================

bool writeRegister(
    uint8_t reg,
    uint8_t value
);

bool readRawImu(
    RawImu &raw
);

bool initializeImu();

bool getSample(
    ImuSample &sample
);

ImuSample measureBaseline();

const char *dominantAxis(
    float x,
    float y,
    float z
);

const char *commandName(
    char command
);

bool validCommand(
    char command
);

void runMotionTest(
    char command
);

void printHelp();


// ============================================================
// I2C helpers
// ============================================================

bool writeRegister(
    uint8_t reg,
    uint8_t value
) {
    Wire.beginTransmission(IMU_ADDRESS);

    Wire.write(reg);
    Wire.write(value);

    return Wire.endTransmission(true) == 0;
}


bool readRawImu(
    RawImu &raw
) {
    Wire.beginTransmission(IMU_ADDRESS);

    // ACCEL_XOUT_H
    Wire.write(0x3B);

    if (Wire.endTransmission(false) != 0) {
        return false;
    }

    constexpr uint8_t BYTE_COUNT = 14;

    if (
        Wire.requestFrom(
            static_cast<uint16_t>(IMU_ADDRESS),
            BYTE_COUNT,
            true
        ) != BYTE_COUNT
    ) {
        return false;
    }

    raw.ax =
        static_cast<int16_t>(
            (Wire.read() << 8) |
            Wire.read()
        );

    raw.ay =
        static_cast<int16_t>(
            (Wire.read() << 8) |
            Wire.read()
        );

    raw.az =
        static_cast<int16_t>(
            (Wire.read() << 8) |
            Wire.read()
        );

    // Skip temperature registers
    Wire.read();
    Wire.read();

    raw.gx =
        static_cast<int16_t>(
            (Wire.read() << 8) |
            Wire.read()
        );

    raw.gy =
        static_cast<int16_t>(
            (Wire.read() << 8) |
            Wire.read()
        );

    raw.gz =
        static_cast<int16_t>(
            (Wire.read() << 8) |
            Wire.read()
        );

    return true;
}


// ============================================================
// IMU initialization
// ============================================================

bool initializeImu() {

    // Wake sensor
    if (!writeRegister(0x6B, 0x00)) {
        return false;
    }

    delay(100);

    // Gyroscope +/-250 degrees/sec
    if (!writeRegister(0x1B, 0x00)) {
        return false;
    }

    // Accelerometer +/-2 g
    if (!writeRegister(0x1C, 0x00)) {
        return false;
    }

    // Digital low-pass filter
    if (!writeRegister(0x1A, 0x03)) {
        return false;
    }

    delay(100);

    return true;
}


// ============================================================
// Read converted IMU sample
// ============================================================

bool getSample(
    ImuSample &sample
) {
    RawImu raw = {};

    if (!readRawImu(raw)) {
        return false;
    }

    sample.ax =
        static_cast<float>(raw.ax) /
        ACCEL_SCALE;

    sample.ay =
        static_cast<float>(raw.ay) /
        ACCEL_SCALE;

    sample.az =
        static_cast<float>(raw.az) /
        ACCEL_SCALE;


    sample.gx =
        static_cast<float>(raw.gx) /
        GYRO_SCALE;

    sample.gy =
        static_cast<float>(raw.gy) /
        GYRO_SCALE;

    sample.gz =
        static_cast<float>(raw.gz) /
        GYRO_SCALE;


    return true;
}


// ============================================================
// Measure stationary baseline
// ============================================================

ImuSample measureBaseline() {

    ImuSample baseline = {};

    double sumAx = 0.0;
    double sumAy = 0.0;
    double sumAz = 0.0;

    double sumGx = 0.0;
    double sumGy = 0.0;
    double sumGz = 0.0;

    int validSamples = 0;


    while (
        validSamples <
        BASELINE_SAMPLES
    ) {

        ImuSample sample = {};

        if (!getSample(sample)) {
            delay(10);
            continue;
        }

        sumAx += sample.ax;
        sumAy += sample.ay;
        sumAz += sample.az;

        sumGx += sample.gx;
        sumGy += sample.gy;
        sumGz += sample.gz;

        validSamples++;

        delay(10);
    }


    baseline.ax =
        static_cast<float>(
            sumAx /
            validSamples
        );

    baseline.ay =
        static_cast<float>(
            sumAy /
            validSamples
        );

    baseline.az =
        static_cast<float>(
            sumAz /
            validSamples
        );


    baseline.gx =
        static_cast<float>(
            sumGx /
            validSamples
        );

    baseline.gy =
        static_cast<float>(
            sumGy /
            validSamples
        );

    baseline.gz =
        static_cast<float>(
            sumGz /
            validSamples
        );


    return baseline;
}


// ============================================================
// Axis helpers
// ============================================================

const char *dominantAxis(
    float x,
    float y,
    float z
) {

    const float absX = fabsf(x);
    const float absY = fabsf(y);
    const float absZ = fabsf(z);


    if (
        absX >= absY &&
        absX >= absZ
    ) {
        return "X";
    }


    if (
        absY >= absX &&
        absY >= absZ
    ) {
        return "Y";
    }


    return "Z";
}


const char *commandName(
    char command
) {

    switch (command) {

        case 'f':
            return "FORWARD";

        case 'b':
            return "BACKWARD";

        case 'l':
            return "LEFT";

        case 'r':
            return "RIGHT";

        case 'u':
            return "UP";

        case 'd':
            return "DOWN";

        case 'c':
            return "CLOCKWISE ROTATION";

        case 'a':
            return "ANTICLOCKWISE ROTATION";

        default:
            return "UNKNOWN";
    }
}


bool validCommand(
    char command
) {

    return
        command == 'f' ||
        command == 'b' ||
        command == 'l' ||
        command == 'r' ||
        command == 'u' ||
        command == 'd' ||
        command == 'c' ||
        command == 'a';
}


// ============================================================
// Guided motion test
// ============================================================

void runMotionTest(
    char command
) {

    Serial.println();
    Serial.println(
        "===================================================="
    );

    Serial.print("TEST: ");
    Serial.println(
        commandName(command)
    );

    Serial.println(
        "===================================================="
    );


    // --------------------------------------------------------
    // Baseline
    // --------------------------------------------------------

    Serial.println();
    Serial.println(
        "KEEP THE MODULE COMPLETELY STILL."
    );

    Serial.println(
        "Measuring baseline..."
    );


    const ImuSample baseline =
        measureBaseline();


    Serial.println(
        "Baseline complete."
    );


    Serial.print(
        "Baseline accel: X="
    );

    Serial.print(
        baseline.ax,
        4
    );

    Serial.print(
        " Y="
    );

    Serial.print(
        baseline.ay,
        4
    );

    Serial.print(
        " Z="
    );

    Serial.println(
        baseline.az,
        4
    );


    Serial.print(
        "Baseline gyro : X="
    );

    Serial.print(
        baseline.gx,
        3
    );

    Serial.print(
        " Y="
    );

    Serial.print(
        baseline.gy,
        3
    );

    Serial.print(
        " Z="
    );

    Serial.println(
        baseline.gz,
        3
    );


    // --------------------------------------------------------
    // Countdown
    // --------------------------------------------------------

    Serial.println();
    Serial.println(
        "Get ready."
    );


    delay(1000);


    Serial.println("3");
    delay(1000);

    Serial.println("2");
    delay(1000);

    Serial.println("1");
    delay(1000);


    Serial.println();
    Serial.println(
        ">>>>>>>> MOVE NOW! <<<<<<<<"
    );


    // --------------------------------------------------------
    // Peak storage
    // --------------------------------------------------------

    float maxAccelX = 0.0f;
    float maxAccelY = 0.0f;
    float maxAccelZ = 0.0f;

    float maxGyroX = 0.0f;
    float maxGyroY = 0.0f;
    float maxGyroZ = 0.0f;


    float signedAccelX = 0.0f;
    float signedAccelY = 0.0f;
    float signedAccelZ = 0.0f;

    float signedGyroX = 0.0f;
    float signedGyroY = 0.0f;
    float signedGyroZ = 0.0f;


    uint32_t recordedSamples = 0;

    const uint32_t startMillis =
        millis();

    uint32_t lastSampleMicros =
        micros();


    // --------------------------------------------------------
    // Record
    // --------------------------------------------------------

    while (
        millis() - startMillis <
        TEST_DURATION_MS
    ) {

        const uint32_t nowMicros =
            micros();


        if (
            static_cast<uint32_t>(
                nowMicros -
                lastSampleMicros
            ) <
            SAMPLE_INTERVAL_US
        ) {
            continue;
        }


        lastSampleMicros =
            nowMicros;


        ImuSample sample = {};


        if (!getSample(sample)) {
            continue;
        }


        // Difference from stationary baseline
        const float deltaAx =
            sample.ax -
            baseline.ax;

        const float deltaAy =
            sample.ay -
            baseline.ay;

        const float deltaAz =
            sample.az -
            baseline.az;


        const float deltaGx =
            sample.gx -
            baseline.gx;

        const float deltaGy =
            sample.gy -
            baseline.gy;

        const float deltaGz =
            sample.gz -
            baseline.gz;


        // ----------------------------------------------------
        // Acceleration peaks
        // ----------------------------------------------------

        if (
            fabsf(deltaAx) >
            maxAccelX
        ) {

            maxAccelX =
                fabsf(deltaAx);

            signedAccelX =
                deltaAx;
        }


        if (
            fabsf(deltaAy) >
            maxAccelY
        ) {

            maxAccelY =
                fabsf(deltaAy);

            signedAccelY =
                deltaAy;
        }


        if (
            fabsf(deltaAz) >
            maxAccelZ
        ) {

            maxAccelZ =
                fabsf(deltaAz);

            signedAccelZ =
                deltaAz;
        }


        // ----------------------------------------------------
        // Gyroscope peaks
        // ----------------------------------------------------

        if (
            fabsf(deltaGx) >
            maxGyroX
        ) {

            maxGyroX =
                fabsf(deltaGx);

            signedGyroX =
                deltaGx;
        }


        if (
            fabsf(deltaGy) >
            maxGyroY
        ) {

            maxGyroY =
                fabsf(deltaGy);

            signedGyroY =
                deltaGy;
        }


        if (
            fabsf(deltaGz) >
            maxGyroZ
        ) {

            maxGyroZ =
                fabsf(deltaGz);

            signedGyroZ =
                deltaGz;
        }


        recordedSamples++;
    }


    // --------------------------------------------------------
    // Results
    // --------------------------------------------------------

    Serial.println(
        "<<<<<< STOP >>>>>>"
    );


    Serial.println();
    Serial.println(
        "================ RESULTS ================="
    );


    Serial.print(
        "Recorded samples: "
    );

    Serial.println(
        recordedSamples
    );


    Serial.println();
    Serial.println(
        "ACCELERATION PEAK CHANGE FROM BASELINE"
    );


    Serial.print(
        "X = "
    );

    Serial.print(
        signedAccelX,
        4
    );

    Serial.print(
        " g    magnitude="
    );

    Serial.println(
        maxAccelX,
        4
    );


    Serial.print(
        "Y = "
    );

    Serial.print(
        signedAccelY,
        4
    );

    Serial.print(
        " g    magnitude="
    );

    Serial.println(
        maxAccelY,
        4
    );


    Serial.print(
        "Z = "
    );

    Serial.print(
        signedAccelZ,
        4
    );

    Serial.print(
        " g    magnitude="
    );

    Serial.println(
        maxAccelZ,
        4
    );


    Serial.print(
        "DOMINANT ACCEL AXIS: "
    );

    Serial.println(
        dominantAxis(
            maxAccelX,
            maxAccelY,
            maxAccelZ
        )
    );


    Serial.println();
    Serial.println(
        "GYROSCOPE PEAK CHANGE FROM BASELINE"
    );


    Serial.print(
        "X = "
    );

    Serial.print(
        signedGyroX,
        2
    );

    Serial.print(
        " deg/s    magnitude="
    );

    Serial.println(
        maxGyroX,
        2
    );


    Serial.print(
        "Y = "
    );

    Serial.print(
        signedGyroY,
        2
    );

    Serial.print(
        " deg/s    magnitude="
    );

    Serial.println(
        maxGyroY,
        2
    );


    Serial.print(
        "Z = "
    );

    Serial.print(
        signedGyroZ,
        2
    );

    Serial.print(
        " deg/s    magnitude="
    );

    Serial.println(
        maxGyroZ,
        2
    );


    Serial.print(
        "DOMINANT GYRO AXIS: "
    );

    Serial.println(
        dominantAxis(
            maxGyroX,
            maxGyroY,
            maxGyroZ
        )
    );


    Serial.println(
        "=========================================="
    );


    Serial.println();
    Serial.println(
        "Results will stay here."
    );

    Serial.println(
        "Enter another command when ready."
    );

    Serial.println();
}


// ============================================================
// Help
// ============================================================

void printHelp() {

    Serial.println();
    Serial.println(
        "================ COMMANDS ================"
    );

    Serial.println(
        "f = move FORWARD"
    );

    Serial.println(
        "b = move BACKWARD"
    );

    Serial.println(
        "l = move LEFT"
    );

    Serial.println(
        "r = move RIGHT"
    );

    Serial.println(
        "u = move UP"
    );

    Serial.println(
        "d = move DOWN"
    );

    Serial.println(
        "c = rotate CLOCKWISE"
    );

    Serial.println(
        "a = rotate ANTICLOCKWISE"
    );

    Serial.println(
        "=========================================="
    );

    Serial.println();
}


// ============================================================
// Arduino setup
// ============================================================

void setup() {

    Serial.begin(
        115200
    );


    delay(
        1500
    );


    Serial.println();
    Serial.println(
        "=========================================="
    );

    Serial.println(
        "Disha-Rakshak Foot IMU Test 02"
    );

    Serial.println(
        "GUIDED PHYSICAL AXIS MAPPING"
    );

    Serial.println(
        "=========================================="
    );


    Wire.begin(
        SDA_PIN,
        SCL_PIN
    );


    Wire.setClock(
        I2C_CLOCK_HZ
    );


    delay(
        250
    );


    if (!initializeImu()) {

        Serial.println(
            "[FATAL] IMU initialization failed"
        );

        while (true) {
            delay(1000);
        }
    }


    printHelp();


    Serial.println(
        "Enter a command in Serial Monitor"
    );

    Serial.println(
        "and press Send / Enter."
    );

    Serial.println();
}


// ============================================================
// Arduino loop
// ============================================================

void loop() {

    if (!Serial.available()) {
        return;
    }


    char command =
        Serial.read();


    // Uppercase -> lowercase
    if (
        command >= 'A' &&
        command <= 'Z'
    ) {

        command =
            static_cast<char>(
                command -
                'A' +
                'a'
            );
    }


    if (
        validCommand(
            command
        )
    ) {

        runMotionTest(
            command
        );
    }


    // Remove newline / carriage-return characters
    while (
        Serial.available()
    ) {
        Serial.read();
    }
}