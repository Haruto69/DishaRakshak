# Belt hardware trial firmware

These are testing copies of the firmware. Flash `foot_test/foot_test.ino` to **both** foot ESP32s and `belt_test/belt_test.ino` to the belt, together with the Android app from this branch. Foot identification still uses the MAC addresses in the original sketches. Keep both foot modules still during their startup calibration.

The foot-to-belt packet has changed from 64 to 68 bytes. Do not mix a test belt with the original foot firmware or vice versa. Test firmware retains the seven-field combined BLE notification used by the app for navigation.

## Build configuration

Both sketches compile with Arduino ESP32 core 3.3.12 for the generic ESP32 Dev Module. The belt binary exceeds the default 1.2 MB application partition. For a compatible 4 MB ESP32, the `No FS 4MB (2MB APP x2)` partition option compiles successfully. Confirm the actual board and flash size before uploading; changing the partition layout affects existing flash data and removes the filesystem partition. The sketches have not yet been tested on physical devices.

## Trial

1. Power the belt and both feet; wait for calibration. Connect in the app's Belt screen.
2. Choose a home base (including a map pin). Measure the actual start and end points independently.
3. Start the log, remain still briefly, walk the measured path, and stand still at the end. Stop the log and wait for “Log saved” before exporting.
4. Check the CSV for `foot_test_v1` rows for both feet, all five page numbers, increasing sample counters and times, and `valid` combined rows. Record separately the measured distance, trial ID, mounting orientation and ground-truth endpoint.

Each foot samples at approximately 50 Hz, but the belt currently snapshots the **latest** packet from each foot once per 250 ms, so the phone receives approximately 4 snapshots per foot per second. The test stream does not contain every sample. Each snapshot is split across five independent BLE notifications; match rows by foot, sequence and sample time and verify all pages are present. BLE notifications are not acknowledged, so missing pages can occur. Sequence gaps between snapshots are expected and cannot measure foot-to-belt packet loss.

The 32-bit `foot_sample_us` counter wraps about every 71.6 minutes and resets when that foot restarts. The left, right and phone clocks are independent and are not synchronized. Phone timestamps are receipt times, not sample times. GPS and navigation columns are the latest app snapshot before receipt, not a reference measurement.

## Test record format v1

Each BLE test notification is exactly 20 bytes, little endian: byte 0 = 0xD1, byte 1 = foot ID (1 left, 2 right), byte 2 = page (0–4), byte 3 = still flag, bytes 4–7 = foot sequence, bytes 8–11 = foot sample `micros()`, bytes 12–19 = two IEEE-754 float32 values.

| Page | First value | Second value |
|---|---|---|
| 0 | acceleration X (g) | acceleration Y (g) |
| 1 | acceleration Z (g) | gyro X (degrees/s) |
| 2 | gyro Y (degrees/s) | gyro Z (degrees/s) |
| 3 | velocity X (m/s) | velocity Y (m/s) |
| 4 | velocity Z (m/s) | yaw (degrees) |

Acceleration values are sensor readings in sensor axes; gyro values have calibration bias subtracted. Foot velocity is computed in each foot's locally initialized estimated world frame. Each foot initializes its yaw independently, so those frames are not guaranteed to align with each other or north. This test protocol does not change the navigation algorithm or validate its accuracy.

These sketches require compilation and real-device testing with your actual ESP32 board and Arduino ESP32 core before relying on the results.


[System Notification] Background shell session 'shell-1' (`android run .`) finished with exit status 1. Full output was saved to `C:\Users\eng22\.android\studio\agent\conversations\DishaRakshak-e6ed58d0a644394e1f867550664e0f\8dd71c61-b498-4cef-934d-14dca96d082d\session\agent\shell-logs\shell-1.log`. Call `interact_shell` with `sessionId="shell-1"` and `action="status"` if you need to inspect its final output.