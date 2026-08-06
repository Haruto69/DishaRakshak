import serial
import time

def gps_stream(port="COM3", baudrate=9600):
    """
    Read NMEA sentences directly from a GPS receiver module.
    Adjust 'port' for your setup:
      - Windows: COM3, COM4, etc.
      - Raspberry Pi: /dev/ttyAMA0 or /dev/ttyUSB0
    """
    try:
        ser = serial.Serial(port, baudrate, timeout=1)
        print(f"Connected to GPS on {port} at {baudrate} baud")
    except Exception as e:
        print("Could not open GPS port:", e)
        return

    while True:
        line = ser.readline().decode("ascii", errors="replace").strip()
        if line.startswith("$GPGGA"):  # focus on GPGGA sentences
            parts = line.split(",")
            fix_flag = parts[6] if len(parts) > 6 else "0"
            if fix_flag == "1":
                print("GPS OK → logging coordinates:", line)
            else:
                print("GPS LOST → switching to IMU fallback")
        time.sleep(0.5)

if __name__ == "__main__":
    gps_stream()
