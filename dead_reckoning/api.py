from flask import Flask, request, jsonify

from dead_reckoning import DeadReckoning


app = Flask(__name__)


# ============================================================
# DEAD RECKONING ENGINE
# ============================================================

dr = DeadReckoning()

dr.calibrate(
    ax=0.0,
    ay=0.0,
    az=9.81,
    gx=0.0,
    gy=0.0,
    gz=0.0
)


# ============================================================
# HOME / STATUS
# ============================================================

@app.route("/", methods=["GET"])
def home():

    return jsonify({
        "project": "Disha-Rakshak",
        "module": "IMU Dead Reckoning",
        "status": "running"
    })


# ============================================================
# GET CURRENT POSITION
# ============================================================

@app.route("/position", methods=["GET"])
def get_position():

    state = dr.get_state()

    return jsonify({
        "x": round(state["x"], 4),
        "y": round(state["y"], 4),
        "z": round(state["z"], 4),
        "vx": round(state["vx"], 4),
        "vy": round(state["vy"], 4),
        "vz": round(state["vz"], 4),
        "roll": round(state["roll"], 2),
        "pitch": round(state["pitch"], 2),
        "heading": round(state["heading"], 2)
    })


# ============================================================
# PROCESS IMU DATA
# ============================================================

@app.route("/imu", methods=["POST"])
def process_imu():

    data = request.get_json()

    if data is None:
        return jsonify({
            "error": "Request must contain JSON data."
        }), 400

    required_fields = [
        "ax",
        "ay",
        "az",
        "gx",
        "gy",
        "gz",
        "dt"
    ]

    missing_fields = [
        field
        for field in required_fields
        if field not in data
    ]

    if missing_fields:
        return jsonify({
            "error": "Missing IMU fields.",
            "missing": missing_fields
        }), 400

    try:

        ax = float(data["ax"])
        ay = float(data["ay"])
        az = float(data["az"])

        gx = float(data["gx"])
        gy = float(data["gy"])
        gz = float(data["gz"])

        dt = float(data["dt"])

        if dt <= 0:
            return jsonify({
                "error": "dt must be greater than 0."
            }), 400

        state = dr.update(
            ax=ax,
            ay=ay,
            az=az,
            gx=gx,
            gy=gy,
            gz=gz,
            dt=dt
        )

        return jsonify({
            "status": "success",

            "position": {
                "x": round(state["x"], 4),
                "y": round(state["y"], 4),
                "z": round(state["z"], 4)
            },

            "velocity": {
                "x": round(state["vx"], 4),
                "y": round(state["vy"], 4),
                "z": round(state["vz"], 4)
            },

            "orientation": {
                "roll": round(state["roll"], 2),
                "pitch": round(state["pitch"], 2),
                "heading": round(state["heading"], 2)
            }
        })

    except (TypeError, ValueError):

        return jsonify({
            "error": "IMU values must be numeric."
        }), 400


# ============================================================
# RESET POSITION
# ============================================================

@app.route("/reset", methods=["POST"])
def reset_position():

    dr.x = 0.0
    dr.y = 0.0
    dr.z = 0.0

    dr.vx = 0.0
    dr.vy = 0.0
    dr.vz = 0.0

    dr.roll = 0.0
    dr.pitch = 0.0
    dr.yaw = 0.0

    return jsonify({
        "status": "success",
        "message": "Dead reckoning position reset."
    })


# ============================================================
# START SERVER
# ============================================================

if __name__ == "__main__":

    print()
    print("Disha-Rakshak Dead Reckoning API")
    print("---------------------------------")
    print("Server starting...")
    print()
    print("API: http://127.0.0.1:5000")
    print()

    app.run(
        host="0.0.0.0",
        port=5000,
        debug=True
    )
