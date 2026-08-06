import time
import random

def generate_nmea(lat, lon, fix=True):
    """
    Generate a fake NMEA GPGGA sentence.
    lat, lon = coordinates
    fix = True means GPS fix, False means no fix
    """
    lat_deg = int(lat)
    lat_min = (lat - lat_deg) * 60
    lon_deg = int(lon)
    lon_min = (lon - lon_deg) * 60

    fix_flag = "1" if fix else "0"  # 1 = GPS fix, 0 = no fix

    # Example GPGGA sentence
    nmea = f"$GPGGA,{time.strftime('%H%M%S')},{lat_deg:02d}{lat_min:07.4f},N,{lon_deg:03d}{lon_min:07.4f},E,{fix_flag},08,0.9,{random.uniform(800,820):.1f},M,0.0,M,,*47"
    return nmea

def gps_stream():
    lat, lon = 12.9716, 77.5946  # Start near Bengaluru
    while True:
        # Simulate walking east
        lon += 0.0001

        # Randomly drop GPS fix (simulate loss)
        fix = random.choice([True, True, True, False])  # mostly True, sometimes False

        nmea = generate_nmea(lat, lon, fix)

        if fix:
            print("GPS OK → logging coordinates:", nmea)
        else:
            print("GPS LOST → switching to IMU fallback")

        time.sleep(1)

if __name__ == "__main__":
    gps_stream()
