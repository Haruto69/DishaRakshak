import requests
import time

from imu_simulator import generate_walking_data


API_URL = "http://127.0.0.1:5000/imu"


print()
print("Disha-Rakshak API Test")
print("----------------------")
print("Sending simulated IMU data to Flask API...")
print()


sample_count = 0

# 50 Hz simulation
dt = 0.02

# 10 seconds of simulated walking
total_samples = 500


for i in range(total_samples):

    # Current simulation time
    t = i * dt

    # Generate one simulated IMU sample
    ax, ay, az, gx, gy, gz = generate_walking_data(t)

    payload = {
        "ax": ax,
        "ay": ay,
        "az": az,
        "gx": gx,
        "gy": gy,
        "gz": gz,
        "dt": dt
    }

    try:

        response = requests.post(
            API_URL,
            json=payload
        )

        response.raise_for_status()

        result = response.json()

        sample_count += 1

        # Print every 25 samples
        if sample_count % 25 == 0:

            position = result["position"]
            velocity = result["velocity"]
            orientation = result["orientation"]

            print(
                f"Sample {sample_count:4d} | "
                f"X={position['x']:6.2f} m | "
                f"Y={position['y']:6.2f} m | "
                f"Z={position['z']:6.2f} m | "
                f"Heading={orientation['heading']:6.2f}°"
            )

        # Simulate real-time 50 Hz data
        time.sleep(dt)

    except requests.exceptions.ConnectionError:

        print()
        print("ERROR: Could not connect to the Flask API.")
        print("Make sure api.py is running.")
        break

    except requests.exceptions.RequestException as error:

        print()
        print("ERROR while sending IMU data:")
        print(error)
        break


print()
print("----------------------")
print("API test completed.")
print(f"Samples sent: {sample_count}")
print()
