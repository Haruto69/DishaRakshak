package com.example.drdisplay.navigation.animateMap

import java.util.PriorityQueue
import kotlin.math.*

/** Route between projections onto road segments, including the remaining part of each segment. */
object SegmentRouter {
    data class Point(val lat: Double, val lon: Double)
    private data class Snap(val a: Long, val b: Long, val point: Point, val distance: Double)

    private fun distance(a: Point, b: Point): Double {
        val lat = Math.toRadians(b.lat - a.lat)
        val lon = Math.toRadians(b.lon - a.lon)
        val h = sin(lat / 2).pow(2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(lon / 2).pow(2)
        return 6371000.0 * 2 * atan2(sqrt(h.coerceIn(0.0, 1.0)), sqrt((1.0 - h).coerceIn(0.0, 1.0)))
    }

    private fun snap(graph: RoadGraph, target: Point): Snap? {
        var best: Snap? = null
        val scale = cos(Math.toRadians(target.lat))
        for ((id, adjacent) in graph.adjacency) {
            val a = graph.nodes[id] ?: continue
            for (other in adjacent) {
                if (id >= other) continue
                val b = graph.nodes[other] ?: continue
                val dx = (b.lon - a.lon) * scale
                val dy = b.lat - a.lat
                val denom = dx * dx + dy * dy
                if (denom <= 0.0) continue
                val t = (((target.lon - a.lon) * scale * dx + (target.lat - a.lat) * dy) / denom).coerceIn(0.0, 1.0)
                val projected = Point(a.lat + t * (b.lat - a.lat), a.lon + t * (b.lon - a.lon))
                val d = distance(target, projected)
                if (best == null || d < best.distance) best = Snap(id, other, projected, d)
            }
        }
        return best
    }

    fun route(graph: RoadGraph, start: Point, destination: Point): List<Point> {
        val from = snap(graph, start) ?: return emptyList()
        val to = snap(graph, destination) ?: return emptyList()
        var first = Long.MIN_VALUE
        while (graph.nodes.containsKey(first)) first++
        var last = first + 1
        while (graph.nodes.containsKey(last)) last++
        val points = graph.nodes.mapValues { Point(it.value.lat, it.value.lon) }.toMutableMap()
        points[first] = from.point
        points[last] = to.point
        val extra = mutableMapOf<Long, MutableList<Long>>()
        fun connect(a: Long, b: Long) {
            extra.getOrPut(a) { mutableListOf() }.add(b)
            extra.getOrPut(b) { mutableListOf() }.add(a)
        }
        connect(first, from.a); connect(first, from.b)
        connect(last, to.a); connect(last, to.b)
        if (from.a == to.a && from.b == to.b) connect(first, last)
        val distances = mutableMapOf(first to 0.0)
        val previous = mutableMapOf<Long, Long>()
        val queue = PriorityQueue(compareBy<Pair<Long, Double>> { it.second })
        queue.add(first to 0.0)
        while (queue.isNotEmpty()) {
            val (id, cost) = queue.poll()
            if (cost > (distances[id] ?: Double.POSITIVE_INFINITY)) continue
            if (id == last) break
            val p = points[id] ?: continue
            val neighbors = graph.adjacency[id].orEmpty() + extra[id].orEmpty()
            for (next in neighbors) {
                val q = points[next] ?: continue
                val candidate = cost + distance(p, q)
                if (candidate < (distances[next] ?: Double.POSITIVE_INFINITY)) {
                    distances[next] = candidate
                    previous[next] = id
                    queue.add(next to candidate)
                }
            }
        }
        if (last !in distances) return emptyList()
        val result = mutableListOf<Point>()
        var current = last
        while (true) {
            result.add(points.getValue(current))
            if (current == first) break
            current = previous[current] ?: return emptyList()
        }
        return result.asReversed()
    }
}
