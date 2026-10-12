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

data class RoadGraph(
    val nodes: MutableMap<Long, RoadNode> = mutableMapOf(),
    val adjacency: MutableMap<Long, MutableList<Long>> = mutableMapOf()
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
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
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