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
    recordedPath: List<GeoPoint>,
    onPinSelected: (Double, Double, String) -> Unit
) {
    val context = LocalContext.current
    var choosingPin by remember { mutableStateOf(false) }
    var pendingPin by remember { mutableStateOf<GeoPoint?>(null) }
    var pinEvents by remember { mutableStateOf<MapEventsOverlay?>(null) }
    var pinName by remember { mutableStateOf("") }

    var nodeCount by remember { mutableStateOf(0) }
    var edgeCount by remember { mutableStateOf(0) }
    var routePoints by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }
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

    var dynamicRouteOverlay by remember {
        mutableStateOf<Polyline?>(null)
    }
    var walkedPathOverlay by remember { mutableStateOf<Polyline?>(null) }

    var mapCentered by remember {
        mutableStateOf(false)
    }

    var poiMarkersAdded by remember {
        mutableStateOf(false)
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

    val latestLat by rememberUpdatedState(currentLat)
    val latestLon by rememberUpdatedState(currentLon)
    LaunchedEffect(graph, selectedHomeBase) {
        routePoints = emptyList()
        val g = graph ?: return@LaunchedEffect
        val home = selectedHomeBase ?: return@LaunchedEffect
        while (true) {
            val lat = latestLat
            val lon = latestLon
            if (lat != 0.0 && lon != 0.0) {
                val calculated = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    SegmentRouter.route(g, SegmentRouter.Point(lat, lon), SegmentRouter.Point(home.lat, home.lon))
                }
                routePoints = calculated.map { GeoPoint(it.lat, it.lon) }
            }
            kotlinx.coroutines.delay(2000)
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

            if (dynamicRouteOverlay == null) {
                dynamicRouteOverlay = Polyline().apply {
                    outlinePaint.color = android.graphics.Color.BLUE
                    outlinePaint.strokeWidth = 8f
                }
                mapView.overlays.add(dynamicRouteOverlay!!)
            }
            dynamicRouteOverlay?.setPoints(routePoints)

            if (walkedPathOverlay == null) {
                walkedPathOverlay = Polyline().apply {
                    outlinePaint.color = android.graphics.Color.rgb(155, 39, 176)
                    outlinePaint.strokeWidth = 8f
                }
                mapView.overlays.add(walkedPathOverlay!!)
            }
            walkedPathOverlay?.setPoints(recordedPath)

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
    androidx.compose.foundation.layout.Column(modifier = Modifier.align(Alignment.TopCenter).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Button(onClick = { choosingPin = !choosingPin }) {
            Text(if (choosingPin) "Tap map to place pin · Cancel" else "Choose home on map")
        }
        if (recordedPath.isNotEmpty()) {
            androidx.compose.material3.Surface(shape = androidx.compose.material3.MaterialTheme.shapes.small, tonalElevation = 3.dp) {
                Text("Purple: recorded position estimate", modifier = Modifier.padding(8.dp), style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
            }
        }
    }
    }
}