package com.example.drdisplay.navigation.animateMap

/*
Implement Dijkstra or A*
Input:
Current Node
Home Base Node
Output:
List<Node>
representing the route
 */

import java.util.PriorityQueue
import android.util.Log

object RouteFinder {

    fun haversine(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {

        val R = 6371000.0

        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a =
            Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                    Math.cos(Math.toRadians(lat1)) *
                    Math.cos(Math.toRadians(lat2)) *
                    Math.sin(dLon / 2) *
                    Math.sin(dLon / 2)

        val c =
            2 * Math.atan2(
                Math.sqrt(a),
                Math.sqrt(1 - a)
            )

        return R * c
    }
    /*
    private fun distance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {

        val dx = lat1 - lat2
        val dy = lon1 - lon2

        return dx * dx + dy * dy
    }
     */

    fun nearestNode(
        graph: RoadGraph,
        lat: Double,
        lon: Double
    ): Long {
        var nearestId = -1L
        var bestDistance = Double.MAX_VALUE
        for ((id, node) in graph.nodes) {
            if (!graph.adjacency.containsKey(id))
                continue
            val d =
                haversine(
                    lat,
                    lon,
                    node.lat,
                    node.lon
                )
            if (d < bestDistance) {

                bestDistance = d
                nearestId = id
            }
        }
        Log.d(
            "ROUTE",
            "start in adjacency = ${
                graph.adjacency.containsKey(nearestId)
            }"
        )
        return nearestId
    }

    fun shortestPath(
        graph: RoadGraph,
        start: Long,
        goal: Long
    ): List<Long> {
        val distances = mutableMapOf<Long, Double>()
        val previous = mutableMapOf<Long, Long?>()
        val queue =
            PriorityQueue<Pair<Long, Double>>(
                compareBy { it.second }
            )
        graph.nodes.keys.forEach {
            distances[it] = Double.POSITIVE_INFINITY
            previous[it] = null
        }
        distances[start] = 0.0
        queue.add(start to 0.0)
        while (queue.isNotEmpty()) {
            val current =
                queue.poll().first
            if (current == goal) {
                break
            }
            val neighbors =
                graph.adjacency[current]
                    ?: continue
            for (neighbor in neighbors) {
                val currentNode =
                    graph.nodes[current]
                        ?: continue
                val neighborNode =
                    graph.nodes[neighbor]
                        ?: continue
                val edgeCost =
                    haversine(
                        currentNode.lat,
                        currentNode.lon,
                        neighborNode.lat,
                        neighborNode.lon
                    )
                val newDistance =
                    distances[current]!! + edgeCost
                if (
                    newDistance <
                    distances[neighbor]!!
                ) {
                    distances[neighbor] =
                        newDistance
                    previous[neighbor] =
                        current
                    queue.add(
                        neighbor to newDistance
                    )
                }
            }
        }

        Log.d(
            "ROUTE",
            "Goal predecessor = ${previous[goal]}"
        )

        if (
            start != goal &&
            previous[goal] == null
        ) {
            Log.d(
                "ROUTE",
                "No path found"
            )
            Log.d(
                "ROUTE",
                "Neighbors at start = ${graph.adjacency[start]?.size}"
            )
            Log.d(
                "ROUTE",
                "Neighbors at goal = ${graph.adjacency[goal]?.size}"
            )
            Log.d(
                "ROUTE",
                "start exists = ${graph.adjacency.containsKey(start)}"
            )

            Log.d(
                "ROUTE",
                "goal exists = ${graph.adjacency.containsKey(goal)}"
            )

            Log.d(
                "ROUTE_ONCE",
                "Route generated"
            )

            return emptyList()
        }
        val route = mutableListOf<Long>()
        var current: Long? = goal
        while (current != null) {
            route.add(current)
            current =
                previous[current]
        }
        route.reverse()
        return route
    }
}
