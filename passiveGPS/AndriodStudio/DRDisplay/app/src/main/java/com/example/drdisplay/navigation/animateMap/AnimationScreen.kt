package com.example.drdisplay.navigation.animateMap

import android.annotation.SuppressLint
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.drdisplay.R
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.example.drdisplay.HomeBase
import com.example.drdisplay.parseOsmNames
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Polyline

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun AnimationScreen(
    gpsText: String,
    mapUri: Uri?,
    gpsLat: Double,
    gpsLon: Double,
    currentLat: Double,
    currentLon: Double,
    selectedHomeBase: HomeBase?
) {
    val context = LocalContext.current

    var nodeCount by remember { mutableStateOf(0) }
    var edgeCount by remember { mutableStateOf(0) }
    var startNode by remember { mutableLongStateOf(-1L) }
    var routeLength by remember { mutableIntStateOf(0) }
    var routePreview by remember { mutableStateOf("") }
    var routeNodes by remember {
        mutableStateOf<List<Long>>(emptyList())
    }
    var roadsDrawn by remember {
        mutableStateOf(false)
    }
    var graph by remember {
        mutableStateOf<RoadGraph?>(null)
    }

    var poiList by remember {
        mutableStateOf<List<POI>>(emptyList())
    }

    var imuMarker by remember {
        mutableStateOf<Marker?>(null)
    }

    var gpsMarker by remember {
        mutableStateOf<Marker?>(null)
    }

    var homeMarker by remember {
        mutableStateOf<Marker?>(null)
    }

    var routeOverlay by remember {
        mutableStateOf<Polyline?>(null)
    }

    var poiMarkersAdded by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(mapUri) {
        try {
            graph = GraphBuilder.loadGraph(
                context = context,
                uri = mapUri
            )
            roadsDrawn = false
            poiMarkersAdded = false

            graph?.let { g ->
                nodeCount = g.nodes.size
                edgeCount = g.adjacency.size
            }
            poiList =
                PoiLoader.loadPOIs(
                    context,
                    mapUri
                )

            Log.d(
                "POI",
                "poiList size = ${poiList.size}"
            )

            val names =
                parseOsmNames(
                    context,
                    mapUri
                )

            Log.d(
                "OSM_NAMES",
                "Count = ${names.size}"
            )

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    LaunchedEffect(
        graph,
        selectedHomeBase
    )
    {
        Log.d(
            "ROUTE_ONCE",
            "Generating route"
        )
        Log.d(
            "ANIMATION",
            "HomeBase = $selectedHomeBase"
        )
        val g = graph ?: return@LaunchedEffect
        Log.d("ROUTE", "Entered Route Calculation")
        val goalNode =
            selectedHomeBase?.let {
                RouteFinder.nearestNode(
                    g,
                    it.lat,
                    it.lon
                )
            } ?: return@LaunchedEffect
        Log.d(
            "ROUTE",
            "Goal node = $goalNode"
        )
        startNode =
            RouteFinder.nearestNode(
                g,
                currentLat,
                currentLon
            )
        Log.d(
            "ROUTE",
            "Start node = $startNode"
        )
        val route =
            RouteFinder.shortestPath(
                g,
                startNode,
                goalNode
            )
        routeNodes = route
        Log.d(
            "ROUTE",
            "Route size = ${route.size}"
        )

        Log.d(
            "ROUTE",
            "Start coords = $currentLat,$currentLon"
        )
        Log.d(
            "ROUTE",
            "Goal coords = ${selectedHomeBase?.lat},${selectedHomeBase?.lon}"
        )

        Log.d(
            "ROUTE",
            "Goal snapped to = ${g.nodes[goalNode]}"
        )
        Log.d(
            "ROUTE",
            "Start snapped to = ${g.nodes[startNode]}"
        )

        try {
            routeLength = route.size
            routePreview =
                route.take(5)
                    .mapNotNull {
                        g.nodes[it]
                    }
                    .joinToString("\n") {
                        "${it.lat}, ${it.lon}"
                    }
            Log.d(
                "ROUTE",
                "start=$startNode goal=$goalNode length=$routeLength"
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                top = 100.dp,
                bottom = 120.dp
            ),
        factory = { ctx ->
            MapView(ctx).apply {
                setMultiTouchControls(true)
                controller.setCenter(
                    GeoPoint(
                        currentLat,
                        currentLon
                    )
                )
                controller.setZoom(17.0)
            }
        },
        update = { mapView ->
            Log.d(
                "MARKER",
                "Updating marker"
            )
            if (imuMarker == null) {

                imuMarker = Marker(mapView)

                imuMarker!!.icon =
                    ContextCompat.getDrawable(
                        context,
                        R.drawable.blue_dot
                    )

                mapView.overlays.add(
                    imuMarker!!
                )
            }

            imuMarker!!.position =
                GeoPoint(
                    currentLat,
                    currentLon
                )

            if (gpsMarker == null) {

                gpsMarker = Marker(mapView)

                gpsMarker!!.icon =
                    ContextCompat.getDrawable(
                        context,
                        R.drawable.gps_green
                    )

                mapView.overlays.add(
                    gpsMarker!!
                )
            }

            gpsMarker!!.position =
                GeoPoint(
                    gpsLat,
                    gpsLon
                )

            selectedHomeBase?.let { home ->

                if (homeMarker == null) {

                    homeMarker = Marker(mapView)

                    homeMarker!!.icon =
                        ContextCompat.getDrawable(
                            context,
                            R.drawable.house_icon
                        )

                    mapView.overlays.add(
                        homeMarker!!
                    )
                }

                homeMarker!!.position =
                    GeoPoint(
                        home.lat,
                        home.lon
                    )
            }

            if (!roadsDrawn) {
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
                roadsDrawn = true
            }

            graph?.let { g ->
                if (routeOverlay == null) {

                    routeOverlay = Polyline()

                    routeOverlay!!.outlinePaint.color =
                        android.graphics.Color.RED

                    routeOverlay!!.outlinePaint.strokeWidth =
                        8f

                    mapView.overlays.add(
                        routeOverlay!!
                    )
                }

                routeOverlay!!.setPoints(
                    routeNodes.mapNotNull { nodeId ->
                        g.nodes[nodeId]?.let { node ->
                            GeoPoint(node.lat, node.lon)
                        }
                    }
                )
            }
            if (!poiMarkersAdded) {

                poiList.forEach { poi ->
                    val marker = Marker(mapView)

                    marker.position =
                        GeoPoint(
                            poi.lat,
                            poi.lon
                        )

                    marker.title = poi.name

                    marker.icon =
                        ContextCompat.getDrawable(
                            context,
                            R.drawable.poi_dot
                        )

                    marker.setOnMarkerClickListener { marker, _ ->
                        marker.showInfoWindow()
                        true
                    }

                    mapView.overlays.add(marker)
                }

                poiMarkersAdded = true
            }

            mapView.invalidate()

            Log.d("POI", "Loaded ${poiList.size} POIs")
        }
    )
}