package com.example.drdisplay.navigation.animateMap

import android.annotation.SuppressLint
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.views.overlay.MapEventsOverlay
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
import org.osmdroid.views.overlay.Polygon
import androidx.compose.ui.draw.clipToBounds
import org.osmdroid.util.BoundingBox

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun AnimationScreen(
    gpsText: String,
    mapUri: Uri?,
    gpsLat: Double,
    gpsLon: Double,
    currentLat: Double,
    currentLon: Double,
    selectedHomeBase: HomeBase?,
    onPinSelected: (Double, Double, String) -> Unit
) {
    val context = LocalContext.current
    var choosingPin by remember { mutableStateOf(false) }
    var pendingPin by remember { mutableStateOf<GeoPoint?>(null) }
    var pinEvents by remember { mutableStateOf<MapEventsOverlay?>(null) }
    var pinName by remember { mutableStateOf("") }

    var nodeCount by remember { mutableStateOf(0) }
    var edgeCount by remember { mutableStateOf(0) }
    var startNode by remember { mutableLongStateOf(-1L) }
    var routeLength by remember { mutableIntStateOf(0) }
    var routePreview by remember { mutableStateOf("") }

    var staticRouteNodes by remember {
        mutableStateOf<List<Long>>(emptyList())
    }

    var dynamicRouteNodes by remember {
        mutableStateOf<List<Long>>(emptyList())
    }
    var roadsDrawn by remember {
        mutableStateOf(false)
    }
    var graph by remember {
        mutableStateOf<RoadGraph?>(null)
    }
    val pinHandler by rememberUpdatedState<(GeoPoint) -> Boolean> { point ->
        if (!choosingPin) false else {
            pendingPin = point
            pinName = "Pinned home base"
            true
        }
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

    var staticRouteOverlay by remember {
        mutableStateOf<Polyline?>(null)
    }

    var dynamicRouteOverlay by remember {
        mutableStateOf<Polyline?>(null)
    }

    var mapCentered by remember {
        mutableStateOf(false)
    }

    var poiMarkersAdded by remember {
        mutableStateOf(false)
    }

    var lastRerouteTime by remember {
        mutableLongStateOf(0L)
    }

    LaunchedEffect(mapUri) {
        Log.d(
            "ANIMATION_SCREEN",
            "ENTERED"
        )
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
        selectedHomeBase,
        currentLat != 0.0 && currentLon != 0.0
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
        staticRouteNodes = emptyList()
        dynamicRouteNodes = emptyList()
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
        if (
            currentLat == 0.0 ||
            currentLon == 0.0
        ) {
            return@LaunchedEffect
        }

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
        val staticRoute =
            RouteFinder.shortestPath(
                g,
                startNode,
                goalNode
            )

        staticRouteNodes = staticRoute
        dynamicRouteNodes = staticRoute

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
            routeLength = staticRoute.size
            routePreview =
                staticRoute.take(5)
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


    if (pendingPin != null) {
        AlertDialog(
            onDismissRequest = { pendingPin = null },
            title = { Text("Save home base") },
            text = {
                androidx.compose.foundation.layout.Column {
                    Text("${"%.6f".format(java.util.Locale.US, pendingPin!!.latitude)}, ${"%.6f".format(java.util.Locale.US, pendingPin!!.longitude)}")
                    OutlinedTextField(value = pinName, onValueChange = { pinName = it }, label = { Text("Name") }, singleLine = true)
                }
            },
            confirmButton = { TextButton(enabled = pinName.isNotBlank(), onClick = {
                pendingPin?.let { onPinSelected(it.latitude, it.longitude, pinName.trim()) }
                pendingPin = null
                choosingPin = false
            }) { Text("Save and select") } },
            dismissButton = { TextButton(onClick = { pendingPin = null }) { Text("Cancel") } }
        )
    }

    Box(Modifier.fillMaxSize()) {
    AndroidView(
        modifier = Modifier
            .fillMaxSize().clipToBounds(),
        factory = { ctx ->
            MapView(ctx).apply {
                setUseDataConnection(false)
                overlayManager.tilesOverlay.isEnabled = false
                setBackgroundColor(android.graphics.Color.rgb(242, 239, 233))

                Log.d(
                    "MAP_DEBUG",
                    "MAP CREATED"
                )

                setMultiTouchControls(true)
                pinEvents = MapEventsOverlay(object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: GeoPoint): Boolean = pinHandler(p)
                    override fun longPressHelper(p: GeoPoint): Boolean = false
                })
                overlays.add(pinEvents)

                /*
                controller.setCenter(
                    GeoPoint(
                        12.9009952,
                        77.5183405
                    )
                )
                 */

                controller.setZoom(17.0)
            }
        },
        update = { mapView ->

            val nodes = graph?.nodes?.values
            if (!mapCentered && !nodes.isNullOrEmpty()) {
                val bounds = graph?.bounds ?: OsmBounds(
                    nodes.maxOf { it.lat }, nodes.maxOf { it.lon },
                    nodes.minOf { it.lat }, nodes.minOf { it.lon }
                )
                mapCentered = true
                mapView.post {
                    if (bounds.north > bounds.south && bounds.east > bounds.west && mapView.width > 0 && mapView.height > 0) {
                        val box = BoundingBox(bounds.north, bounds.east, bounds.south, bounds.west)
                        mapView.zoomToBoundingBox(box, false, 32)
                        mapView.setMinZoomLevel(mapView.zoomLevelDouble)
                        mapView.setScrollableAreaLimitDouble(box)
                    } else {
                        mapView.controller.setCenter(GeoPoint(bounds.north, bounds.east))
                        mapView.controller.setZoom(17.0)
                    }
                }
            }

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

            val dynamicRoutePoints =
                dynamicRouteNodes.mapNotNull {
                    graph?.nodes?.get(it)?.let {
                        GeoPoint(
                            it.lat,
                            it.lon
                        )
                    }
                }

            fun isOffRoute(
                position: GeoPoint,
                route: List<GeoPoint>
            ): Boolean {

                if (route.isEmpty()) {
                    return false
                }

                val nearest =
                    route.minOf {
                        position.distanceToAsDouble(it)
                    }

                return nearest > 12.0
            }

            val offRoute =
                isOffRoute(
                    GeoPoint(
                        currentLat,
                        currentLon
                    ),
                    dynamicRoutePoints
                )
            if (offRoute) {

                val now = System.currentTimeMillis()

                if (now - lastRerouteTime > 5000) {

                    lastRerouteTime = now

                    val g = graph ?: return@AndroidView

                    val goalNode =
                        selectedHomeBase?.let {
                            RouteFinder.nearestNode(
                                g,
                                it.lat,
                                it.lon
                            )
                        } ?: return@AndroidView

                    val imuStartNode =
                        RouteFinder.nearestNode(
                            g,
                            currentLat,
                            currentLon
                        )

                    Log.d(
                        "NAV_REROUTE",
                        "REROUTING NOW"
                    )

                    dynamicRouteNodes =
                        RouteFinder.shortestPath(
                            g,
                            imuStartNode,
                            goalNode
                        )
                }
            }

            if (
                gpsLat != 0.0 &&
                gpsLon != 0.0
            ) {

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
            }

            if (selectedHomeBase == null) {
                homeMarker?.let { mapView.overlays.remove(it) }
                homeMarker = null
            }
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

            if (!roadsDrawn && graph != null) {
                mapView.overlays.add(0, OsmMapOverlay(graph!!))
                roadsDrawn = true
            }

            if (staticRouteOverlay == null) {

                staticRouteOverlay = Polyline()

                staticRouteOverlay!!
                    .outlinePaint.color =
                    android.graphics.Color.RED

                staticRouteOverlay!!
                    .outlinePaint.strokeWidth = 8f

                mapView.overlays.add(
                    staticRouteOverlay!!
                )
            }
            graph?.let { g ->

                staticRouteOverlay?.setPoints(
                    staticRouteNodes.mapNotNull { nodeId ->
                        g.nodes[nodeId]?.let { node ->
                            GeoPoint(
                                node.lat,
                                node.lon
                            )
                        }
                    }
                )
            }
            if (dynamicRouteOverlay == null) {

                dynamicRouteOverlay = Polyline()

                dynamicRouteOverlay!!
                    .outlinePaint.color =
                    android.graphics.Color.BLUE

                dynamicRouteOverlay!!
                    .outlinePaint.strokeWidth = 6f

                mapView.overlays.add(
                    dynamicRouteOverlay!!
                )
            }
            graph?.let { g ->

                dynamicRouteOverlay?.setPoints(
                    dynamicRouteNodes.mapNotNull {
                        g.nodes[it]?.let { node ->
                            GeoPoint(
                                node.lat,
                                node.lon
                            )
                        }
                    }
                )
            }

            if (!poiMarkersAdded && graph != null) {

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

            pinEvents?.let { overlay ->
                mapView.overlays.remove(overlay)
                mapView.overlays.add(overlay)
            }
            mapView.invalidate()

            Log.d("POI", "Loaded ${poiList.size} POIs")
        }
    )
    Button(onClick = { choosingPin = !choosingPin }, modifier = Modifier.align(Alignment.TopCenter).padding(12.dp)) {
        Text(if (choosingPin) "Tap map to place pin · Cancel" else "Choose home on map")
    }
    }
}