package com.example.drdisplay

import com.example.drdisplay.navigation.animateMap.RoadGraph
import com.example.drdisplay.navigation.animateMap.RoadNode
import com.example.drdisplay.navigation.animateMap.SegmentRouter
import org.junit.Assert.*
import org.junit.Test

class SegmentRouterTest {
    private fun graph() = RoadGraph(
        nodes = mutableMapOf(
            1L to RoadNode(1, 12.0, 77.0),
            2L to RoadNode(2, 12.0, 77.001),
            3L to RoadNode(3, 12.001, 77.001)
        ),
        adjacency = mutableMapOf(1L to mutableListOf(2L), 2L to mutableListOf(1L, 3L), 3L to mutableListOf(2L))
    )

    @Test fun positionsOnSameRoadStartAtTheirProjections() {
        val route = SegmentRouter.route(graph(), SegmentRouter.Point(12.00001, 77.0004), SegmentRouter.Point(12.0, 77.0008))
        assertEquals(2, route.size)
        assertEquals(77.0004, route.first().lon, 0.000001)
        assertEquals(77.0008, route.last().lon, 0.000001)
    }

    @Test fun changingRoadUsesIntersection() {
        val route = SegmentRouter.route(graph(), SegmentRouter.Point(12.0005, 77.001), SegmentRouter.Point(12.0, 77.0004))
        assertEquals(3, route.size)
        assertEquals(12.0005, route.first().lat, 0.000001)
        assertEquals(77.001, route[1].lon, 0.000001)
        assertEquals(12.0, route[1].lat, 0.000001)
    }

    @Test fun disconnectedRoadsHaveNoRoute() {
        val g = graph()
        g.nodes[4L] = RoadNode(4, 12.01, 77.01)
        g.nodes[5L] = RoadNode(5, 12.01, 77.011)
        g.adjacency[4L] = mutableListOf(5L)
        g.adjacency[5L] = mutableListOf(4L)
        assertTrue(SegmentRouter.route(g, SegmentRouter.Point(12.0, 77.0004), SegmentRouter.Point(12.01, 77.0105)).isEmpty())
    }
}
