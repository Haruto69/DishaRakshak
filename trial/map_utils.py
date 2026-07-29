import osmnx as ox
import matplotlib.pyplot as plt
import xml.etree.ElementTree as ET

def load_map(osm_file="trial/maps/rnsit.osm"):
    """Load the OSM file into a graph for routing."""
    G = ox.graph_from_xml(osm_file)
    return G

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

def extract_buildings(osm_file="trial/maps/rnsit.osm"):
    """
    Parse the raw OSM XML and extract building centroids.
    Returns a list of (name, lat, lon).
    """
    tree = ET.parse(osm_file)
    root = tree.getroot()

    # Build a dictionary of node_id -> (lat, lon)
    node_coords = {}
    for node in root.findall("node"):
        node_id = node.attrib["id"]
        lat = float(node.attrib["lat"])
        lon = float(node.attrib["lon"])
        node_coords[node_id] = (lat, lon)

    buildings = []
    for way in root.findall("way"):
        tags = {tag.attrib["k"]: tag.attrib["v"] for tag in way.findall("tag")}
        if tags.get("building") == "yes" and "name" in tags:
            coords = []
            for nd in way.findall("nd"):
                ref = nd.attrib["ref"]
                if ref in node_coords:
                    coords.append(node_coords[ref])
            if coords:
                # centroid = average of all node coords
                lat = sum(c[0] for c in coords) / len(coords)
                lon = sum(c[1] for c in coords) / len(coords)
                buildings.append((tags["name"], lat, lon))
    return buildings

def plot_buildings(ax, buildings):
    """Plot building centroids with labels."""
    for name, lat, lon in buildings:
        ax.scatter(lon, lat, c="green", s=70, marker="s", zorder=6)
        ax.text(lon, lat, name, fontsize=8, color="darkgreen")
