import osmnx as ox
import networkx as nx
import matplotlib.pyplot as plt
import random
from map_utils import load_map, plot_base_map, extract_buildings, plot_buildings

def calculate_bearing(lat1, lon1, lat2, lon2):
    import math
    dLon = math.radians(lon2 - lon1)
    y = math.sin(dLon) * math.cos(math.radians(lat2))
    x = (math.cos(math.radians(lat1)) * math.sin(math.radians(lat2)) -
         math.sin(math.radians(lat1)) * math.cos(math.radians(lat2)) * math.cos(dLon))
    brng = math.degrees(math.atan2(y, x))
    brng = (brng + 360) % 360
    dirs = ["N","NE","E","SE","S","SW","W","NW"]
    idx = round(brng / 45) % 8
    return dirs[idx]

# --- Load map ---
G = load_map(map_name="dummy.osm")  # ✅ use the new load_map function

# --- Define Home Base ---
home_lat, home_lon = 12.8965, 77.5650
home_node = ox.distance.nearest_nodes(G, home_lon, home_lat)   # ✅ use osmnx

# --- Pick random start ---
origin_node = random.choice(list(G.nodes))

# --- Compute shortest path ---
route = nx.shortest_path(G, origin_node, home_node, weight="length")

# --- Plot base map ---
fig, ax = plot_base_map(G, figsize=(15,15))

# --- Highlight Home Base ---
hx, hy = G.nodes[home_node]["x"], G.nodes[home_node]["y"]
ax.scatter(hx, hy, c="blue", s=100, marker="*", zorder=5)
ax.text(hx, hy, "Home Base", fontsize=10, color="blue")

# --- Extract and plot buildings ---
buildings = extract_buildings("dummy.osm")   # ✅ pass file path, not graph
print("Extracted buildings:", buildings)     # Debug print
plot_buildings(ax, buildings)

# --- Animate route ---
for i in range(1, len(route)):
    sub_route = route[:i+1]
    nx.draw_networkx_edges(
        G,
        pos={n:(G.nodes[n]["x"], G.nodes[n]["y"]) for n in sub_route},
        edgelist=[(route[i-1], route[i])],
        edge_color="red",
        width=3,
        ax=ax
    )

    node = route[i]
    lat, lon = G.nodes[node]["y"], G.nodes[node]["x"]

    if i < len(route)-1:
        next_node = route[i+1]
        next_lat, next_lon = G.nodes[next_node]["y"], G.nodes[next_node]["x"]
        direction = calculate_bearing(lat, lon, next_lat, next_lon)
    else:
        direction = "Arrived"

    print(f"Step {i}: Lat={lat:.4f}, Lon={lon:.4f}, Direction={direction}")
    plt.pause(0.5)

plt.show()
