package com.example.drdisplay.navigation.animateMap

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.drdisplay.loadHomeBases
import com.example.drdisplay.HomeBase
import android.net.Uri
import com.example.drdisplay.getFileName


import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Polyline

@Composable
fun AnimationScreen(
    gpsText: String,
    mapUri: Uri?,
    currentLat: Double,
    currentLon: Double
) {
    val context = LocalContext.current

    var nodeCount by remember { mutableStateOf(0) }
    var edgeCount by remember { mutableStateOf(0) }
    var startNode by remember { mutableLongStateOf(-1L) }
    var homeNode by remember { mutableLongStateOf(-1L) }
    var routeLength by remember { mutableIntStateOf(0) }
    var routePreview by remember { mutableStateOf("") }
    var routeNodes by remember {
        mutableStateOf<List<Long>>(emptyList())
    }
    var graph by remember {
        mutableStateOf<RoadGraph?>(null)
    }
    var homeBase by remember { mutableStateOf<HomeBase?>(null) }


    LaunchedEffect(
        mapUri,
        currentLat,
        currentLon
    )
    {
        try {
            graph = GraphBuilder.loadGraph(
                context = context,
                uri = mapUri
            )
            val g = graph ?: return@LaunchedEffect
            val homeBases =
                loadHomeBases(context)

            val mapId =
                mapUri?.let {
                    getFileName(context, it)
                } ?: "default"
            homeBase =
                homeBases[mapId]
                    ?.firstOrNull {
                        it.isSelected
                    }
                    ?: homeBases[mapId]
                        ?.firstOrNull()
            if (homeBase != null) {
                homeNode =
                    RouteFinder.nearestNode(
                        g,
                        homeBase!!.lat,
                        homeBase!!.lon
                    )
            } else {
                println("No home base selected")
                return@LaunchedEffect
            }

            startNode =
                RouteFinder.nearestNode(
                    g,
                    currentLat,
                    currentLon
                )
            nodeCount = g.nodes.size
            edgeCount = g.adjacency.size

            val route =
                RouteFinder.shortestPath(
                    g,
                    startNode,
                    homeNode
                )
            routeNodes = route
            routeLength = route.size
            println("Start Node = $startNode")
            println("Home Node = $homeNode")
            println("Route Length = $routeLength")
            println("Home Base = $homeBase")
            println("HomeBase Lat = ${homeBase?.lat}")
            println("HomeBase Lon = ${homeBase?.lon}")
            println(
                "Start Position = $currentLat, $currentLon"
            )
            routePreview =
                route.take(5)
                    .mapNotNull {
                        g.nodes[it]
                    }
                    .joinToString("\n") {
                        "${it.lat}, ${it.lon}"
                    }
            println("Nodes = ${g.nodes.size}")
            println("Adjacency = ${g.adjacency.size}")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 50.dp, bottom = 50.dp),
        factory = { ctx ->
            MapView(ctx).apply {
                setMultiTouchControls(true)
                controller.setZoom(15.0)
            }
        },
        update = { mapView ->
            println("UPDATE CALLED")
            println("Route Nodes = ${routeNodes.size}")
            mapView.overlays.clear()
            val currentMarker = Marker(mapView)
            currentMarker.position =
                GeoPoint(
                    currentLat,
                    currentLon
                )
            currentMarker.title = "Current Position"
            mapView.overlays.add(currentMarker)

            homeBase?.let {
                val homeMarker = Marker(mapView)
                homeMarker.position =
                    GeoPoint(
                        it.lat,
                        it.lon
                    )
                homeMarker.title =
                    "Home Base"
                mapView.overlays.add(homeMarker)
            }
            graph?.let { g ->
                g.adjacency.forEach { (fromId, neighbors) ->
                    val fromNode = g.nodes[fromId]
                        ?: return@forEach
                    neighbors.forEach { neighborId ->
                        val toNode = g.nodes[neighborId]
                            ?: return@forEach
                        val road = Polyline()
                        road.outlinePaint.color =
                            android.graphics.Color.GRAY
                        road.outlinePaint.strokeWidth = 4f
                        road.setPoints(
                            listOf(
                                GeoPoint(
                                    fromNode.lat,
                                    fromNode.lon
                                ),
                                GeoPoint(
                                    toNode.lat,
                                    toNode.lon
                                )
                            )
                        )
                        mapView.overlays.add(road)
                    }
                }
            }

            graph?.let { g ->
                val routeLine = Polyline()
                routeLine.outlinePaint.color =
                    android.graphics.Color.RED
                routeLine.outlinePaint.strokeWidth =
                    15f
                routeLine.setPoints(
                    routeNodes.mapNotNull { nodeId ->
                        g.nodes[nodeId]?.let { node ->
                            GeoPoint(
                                node.lat,
                                node.lon
                            )
                        }
                    }
                )
                mapView.overlays.add(routeLine)
            }

            homeBase?.let {
                mapView.controller.setCenter(
                    GeoPoint(
                        currentLat,
                        currentLon
                    )
                )
                mapView.controller.setZoom(17.0)
            }
            mapView.invalidate()
        }
    )
}