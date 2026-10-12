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

object RouteFinder {
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

    fun nearestNode(
        graph: RoadGraph,
        lat: Double,
        lon: Double
    ): Long {
        var nearestId = -1L
        var bestDistance = Double.MAX_VALUE
        println("Searching through ${graph.nodes.size} nodes")
        for ((id, node) in graph.nodes) {
            val d =
                distance(
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
        println("Nearest Node Found = $nearestId")
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
                    distance(
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
