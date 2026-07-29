import osmnx as ox
import matplotlib.pyplot as plt
import xml.etree.ElementTree as ET
import os

def load_map(map_name):
    # Get absolute path to project root (one level up from trial/)
    project_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    maps_dir = os.path.join(project_root, "trial/maps")
    full_path = os.path.join(maps_dir, map_name)

    if not os.path.exists(full_path):
        raise FileNotFoundError(f"Map file not found: {full_path}")

    return ox.graph_from_xml(full_path)

def plot_base_map(G, figsize=(10,10)):
    """Plot the base map with a lighter background."""
    fig, ax = ox.plot_graph(
        G,
        bgcolor="lightgray",
        node_color="black",
        edge_color="gray",
        figsize=figsize,
        show=False,
        close=False
    )
    return fig, ax

def extract_buildings(map_name):
    # Resolve path relative to project root
    project_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    maps_dir = os.path.join(project_root, "trial/maps")
    full_path = os.path.join(maps_dir, map_name)

    if not os.path.exists(full_path):
        raise FileNotFoundError(f"Map file not found: {full_path}")

    tree = ET.parse(full_path)
    root = tree.getroot()

    buildings = []
    for way in root.findall("way"):
        tags = {tag.get("k"): tag.get("v") for tag in way.findall("tag")}
        if tags.get("building"):
            nodes = [nd.get("ref") for nd in way.findall("nd")]
            buildings.append({"id": way.get("id"), "nodes": nodes, "tags": tags})
    return buildings

def plot_buildings(ax, buildings):
    """Plot building centroids with labels."""
    for name, lat, lon in buildings:
        ax.scatter(lon, lat, c="green", s=70, marker="s", zorder=6)
        ax.text(lon, lat, name, fontsize=8, color="darkgreen")
