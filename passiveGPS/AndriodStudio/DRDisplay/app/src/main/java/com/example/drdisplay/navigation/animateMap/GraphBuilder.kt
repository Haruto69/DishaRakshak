package com.example.drdisplay.navigation.animateMap

import android.content.Context
import android.net.Uri
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/*
Take: XML
<node id="1" ... />

<way>
    <nd ref="1"/>
    <nd ref="2"/>
    <nd ref="3"/>
</way>
Produce:
1 -> 2
2 -> 3
adjacency list.
What NetworkX does in Python.
 */

data class RoadNode(
    val id: Long,
    val lat: Double,
    val lon: Double
)

data class OsmWay(val nodes: List<Long>, val tags: Map<String, String>)
data class OsmBounds(val north: Double, val east: Double, val south: Double, val west: Double)

data class RoadGraph(
    val nodes: MutableMap<Long, RoadNode> = mutableMapOf(),
    val adjacency: MutableMap<Long, MutableList<Long>> = mutableMapOf(),
    val buildings: MutableList<List<Long>> = mutableListOf(),
    val ways: MutableList<OsmWay> = mutableListOf(),
    var bounds: OsmBounds? = null
)

object GraphBuilder {

    fun loadGraph(
        context: Context,
        uri: Uri?
    ): RoadGraph {
        val graph = RoadGraph()
        val inputStream = if (uri != null) {
            context.contentResolver.openInputStream(uri)
        } else {
            context.assets.open("tighter_around_rns.osm")
        }
        val parser = XmlPullParserFactory
            .newInstance()
            .newPullParser()
        parser.setInput(inputStream, null)
        var eventType = parser.eventType
        var currentWayNodes = mutableListOf<Long>()
        var isRoad = false
        var isBuilding = false
        var inWay = false
        val wayTags = mutableMapOf<String, String>()
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "bounds" -> {
                            val south = parser.getAttributeValue(null, "minlat")?.toDoubleOrNull()
                            val west = parser.getAttributeValue(null, "minlon")?.toDoubleOrNull()
                            val north = parser.getAttributeValue(null, "maxlat")?.toDoubleOrNull()
                            val east = parser.getAttributeValue(null, "maxlon")?.toDoubleOrNull()
                            if (north != null && south != null && east != null && west != null &&
                                north > south && east > west && north <= 85.0 && south >= -85.0 &&
                                east <= 180.0 && west >= -180.0) {
                                graph.bounds = OsmBounds(north, east, south, west)
                            }
                        }
                        "tag" -> {
                            val k = parser.getAttributeValue(null, "k")
                            val v = parser.getAttributeValue(null, "v")
                            if (inWay && k != null && v != null) wayTags[k] = v

                            if (k == "highway") isRoad = true
                            if (k == "building" && parser.getAttributeValue(null, "v") != "no") isBuilding = true
                        }
                        "node" -> {
                            val id =
                                parser.getAttributeValue(null, "id")
                                    ?.toLongOrNull()
                            val lat =
                                parser.getAttributeValue(null, "lat")
                                    ?.toDoubleOrNull()
                            val lon =
                                parser.getAttributeValue(null, "lon")
                                    ?.toDoubleOrNull()
                            if (
                                id != null &&
                                lat != null &&
                                lon != null
                            ) {
                                graph.nodes[id] =
                                    RoadNode(
                                        id = id,
                                        lat = lat,
                                        lon = lon
                                    )
                            }
                        }
                        "way" -> {
                            currentWayNodes = mutableListOf()
                            inWay = true
                            wayTags.clear()
                            isRoad = false
                            isBuilding = false
                        }
                        "nd" -> {

                            val ref =
                                parser.getAttributeValue(null, "ref")
                                    ?.toLongOrNull()

                            if (ref != null) {
                                currentWayNodes.add(ref)
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "way") {
                        inWay = false
                        graph.ways.add(OsmWay(currentWayNodes.toList(), wayTags.toMap()))
                    }
                    if (parser.name == "way" && isBuilding && currentWayNodes.size >= 4 && currentWayNodes.first() == currentWayNodes.last()) {
                        graph.buildings.add(currentWayNodes.toList())
                    }
                    if (parser.name == "way" && isRoad) {
                        for (i in 0 until currentWayNodes.size - 1) {
                            val a = currentWayNodes[i]
                            val b = currentWayNodes[i + 1]
                            graph.adjacency
                                .getOrPut(a) { mutableListOf() }
                                .add(b)
                            graph.adjacency
                                .getOrPut(b) { mutableListOf() }
                                .add(a)
                        }
                        currentWayNodes.clear()
                    }
                }
            }
            eventType = parser.next()
        }
        inputStream?.close()
        return graph
    }
}