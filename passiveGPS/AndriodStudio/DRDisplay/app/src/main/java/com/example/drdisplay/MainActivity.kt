package com.example.drdisplay

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.example.drdisplay.ui.theme.DRDisplayTheme
import com.google.android.gms.location.*
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.OpenableColumns
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Bluetooth
import com.example.drdisplay.navigation.animateMap.AnimationScreen
import com.example.drdisplay.navigation.SimulatorScreen
import android.util.Log
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import java.util.Collections.list

class MainActivity : ComponentActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ask for GPS + storage permission
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            ),
            1
        )

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setContent {
            DRDisplayTheme {
                AppLayout(fusedLocationClient)
            }
        }
    }
}

// Define your model at the top of MainActivity.kt
data class HomeBase(
    var name: String,
    val lat: Double,
    val lon: Double,
    val mapId: String,
    val isSelected: Boolean = false
)

data class SearchLocation(
    val name: String,
    val lat: Double,
    val lon: Double
)

// DataStore setup
val Context.homeBaseDataStore: androidx.datastore.core.DataStore<Preferences>
        by preferencesDataStore(name = "home_bases")

object HomeBasePrefs {
    val HOME_BASES = stringPreferencesKey("home_bases")
}

@Composable
fun AppLayout(fusedLocationClient: FusedLocationProviderClient) {

    var selectedScreen by remember { mutableStateOf("SearchOSM") }
    var activeOsmUri by remember {
        mutableStateOf<Uri?>(null)
    }
    var gpsText by remember { mutableStateOf("Waiting for GPS...") }
    var gpsLat by remember {
        mutableDoubleStateOf(0.0)
    }
    var gpsLon by remember {
        mutableDoubleStateOf(0.0)
    }
    var navLat by remember {
        mutableDoubleStateOf(0.0)
    }
    var navLon by remember {
        mutableDoubleStateOf(0.0)
    }
    var originLat by remember {
        mutableDoubleStateOf(0.0)
    }
    var originLon by remember {
        mutableDoubleStateOf(0.0)
    }

    var selectedHomeBase by remember {
        mutableStateOf<HomeBase?>(null)
    }

    val context = LocalContext.current

    val bleManager = remember {
        com.example.drdisplay.ble.BleManager(context)
    }
    val bleState by bleManager.state

    LaunchedEffect(
        bleState.posX,
        bleState.posY,
        gpsLat,
        gpsLon
    ) {
        if (gpsLat == 0.0 || gpsLon == 0.0)
            return@LaunchedEffect
        navLat =
            originLat +
                    bleState.posY /
                    111111.0
        navLon =
            originLon +
                    bleState.posX /
                    (
                            111111.0 *
                                    kotlin.math.cos(
                                        Math.toRadians(originLat)
                                    )
                            )
        Log.d(
            "NAV_POSITION",
            "lat=$navLat lon=$navLon"
        )
        Log.d(
            "NAV_POSITION",
            "nav=$navLat,$navLon origin=$originLat,$originLon"
        )
    }

    // Passive GPS updates
    LaunchedEffect(Unit) {

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 5000
            ).build()

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                object : LocationCallback() {
                    override fun onLocationResult(locationResult: LocationResult) {
                        for (location in locationResult.locations) {
                            gpsLat = location.latitude
                            gpsLon = location.longitude
                            if (originLat == 0.0 && originLon == 0.0) {

                                originLat = gpsLat
                                originLon = gpsLon

                                navLat = gpsLat
                                navLon = gpsLon
                            }

                            gpsText =
                                "Lat: ${location.latitude}, " +
                                        "Lon: ${location.longitude}, " +
                                        "Acc: ${location.accuracy}m"
                        }
                    }

                    override fun onLocationAvailability(availability: LocationAvailability) {
                        if (!availability.isLocationAvailable) {
                            gpsText = "GPS signal lost"
                        }
                    }
                },
                Looper.getMainLooper()
            )
        } else {
            gpsText = "GPS permission not granted"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 48.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top row of icons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { selectedScreen = "SearchOSM" }) {
                Icon(Icons.Filled.Home, contentDescription = "Search OSM")
            }
            IconButton(onClick = { selectedScreen = "Animation" }) {
                Icon(Icons.Filled.Map, contentDescription = "Animation")
            }
            IconButton(onClick = { selectedScreen = "BLE" }) {
                Icon(
                    Icons.Filled.Bluetooth, contentDescription = "BLE Monitor"
                )
            }
        }

        // Main content area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(
                    top = 8.dp,
                    bottom = 8.dp
                )
        ) {
            when (selectedScreen) {

                "SearchOSM" ->
                    SearchOSMScreen(
                        gpsText = gpsText,
                        activeOsmUri = activeOsmUri,
                        onMapSelected = {
                            activeOsmUri = it
                        },
                        onHomeBaseSelected = {
                            selectedHomeBase = it
                            Log.d(
                                "HOMEBASE",
                                "selectedHomeBase NOW = ${it.name}"
                            )
                        }
                    )


                "Animation" ->
                    AnimationScreen(
                        gpsText = gpsText,
                        mapUri = activeOsmUri,
                        gpsLat = gpsLat,
                        gpsLon = gpsLon,
                        currentLat = navLat,
                        currentLon = navLon,
                        selectedHomeBase = selectedHomeBase
                    )

                "BLE" ->
                    com.example.drdisplay.ble.BleScreen(
                        bleManager = bleManager
                    )

                "DeadReckoning" -> SimulatorScreen()

                else ->
                    Text("Main content goes here")
            }
        }

        // Persistent GPS footer
        Text(
            text = gpsText,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            textAlign = TextAlign.Center
        )
    }
}

// Placeholder for Search OSM screen
@Composable
fun SearchOSMScreen(
    gpsText: String,
    activeOsmUri: Uri?,
    onMapSelected: (Uri?) -> Unit,
    onHomeBaseSelected: (HomeBase) -> Unit
) {
    val context = LocalContext.current

    var mode by remember { mutableStateOf("name") }
    var areaName by remember { mutableStateOf("") }
    var selectedName by remember { mutableStateOf<String?>(null) }

    var lat by remember { mutableStateOf("") }
    var lon by remember { mutableStateOf("") }

    var errorText by remember { mutableStateOf<String?>(null) }

    // Home base state
    var homeBasesByMap by remember { mutableStateOf(mutableMapOf<String, MutableList<HomeBase>>()) }

    // Reset counter for AutoCompleteTextField
    var resetCounter by remember { mutableStateOf(0) }

    // Load persisted bases on startup
    LaunchedEffect(activeOsmUri) {
        Log.d(
            "HOMEBASE",
            "URI = $activeOsmUri"
        )
        Log.d("HOMEBASE", "LOAD EFFECT FIRED")

        val currentMapId =
            activeOsmUri?.let {
                getFileName(context, it)
            } ?: "default"

        homeBasesByMap = loadHomeBases(context)
        homeBasesByMap[currentMapId]
            ?.forEach {
                Log.d(
                    "HOMEBASE",
                    "LOADED ${it.name} = ${it.isSelected}"
                )
                Log.d(
                    "DOUBLE_ROAD_LOAD",
                    "${it.name} ${it.lat} ${it.lon}"
                )
            }

        homeBasesByMap[currentMapId]
            ?.firstOrNull { it.isSelected }
            ?.let {
                onHomeBaseSelected(it)
            }
        Log.d("HOMEBASE", "Current Map Id = $currentMapId")
    }


    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null)
                onMapSelected(uri)
        }
    )

    val availableLocations =
        remember(activeOsmUri) {
            parseOsmLocations(
                context,
                activeOsmUri
            )
        }
    val availableNames =
        availableLocations.map {
            it.name
        }

    // State for dialogs (declare these at the top of SearchOSMScreen)
    var showOptionsDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var selectedIndexForDialog by remember { mutableStateOf(-1) }

    Column(
        modifier = Modifier.fillMaxSize()
            .padding(32.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Select Map Source", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        Row {
            Button(
                onClick = {
                    onMapSelected(null)
                }
            ) { Text("Use Default Map") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { launcher.launch(arrayOf("application/xml","text/xml","*/*")) }) {
                Text("Load Custom Map")
            }
        }

        Spacer(Modifier.height(8.dp))

        // 🔑 Confirmation of selected map
        if (activeOsmUri != null) {
            val fileName = getFileName(context, activeOsmUri!!)
            Text(
                text = "Custom map selected: $fileName",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            Text(
                text = "Default map selected",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        Spacer(Modifier.height(16.dp))

        Text("🔍 Search Home Base", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        if (mode == "name") {
            AutoCompleteTextField(
                label = "Enter place name",
                allItems = availableNames,
                onItemSelected = { selectedName = it; areaName = it },
                resetKey = resetCounter   // 🔑 pass resetCounter here
            )
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            if (mode == "name" && selectedName != null) {
                val mapId = activeOsmUri?.let { getFileName(context, it) } ?: "default"
                val selectedLocation =
                    availableLocations.firstOrNull {
                        it.name == selectedName
                    }
                Log.d(
                    "HOMEBASE_DEBUG",
                    "Selected = ${selectedLocation?.name} lat=${selectedLocation?.lat} lon=${selectedLocation?.lon}"
                )
                val hb =
                    HomeBase(
                        name = selectedLocation!!.name,
                        lat = selectedLocation.lat,
                        lon = selectedLocation.lon,
                        mapId = mapId
                    )

                val updated = homeBasesByMap.toMutableMap()
                val list = updated.getOrPut(mapId) { mutableListOf() }
                if (
                    list.none {
                        it.name == hb.name
                    }
                ) {
                    list.add(hb)
                }

                homeBasesByMap = updated
                updated[mapId]?.forEach {
                    Log.d(
                        "HOMEBASE",
                        "${it.name} -> ${it.isSelected}"
                    )
                }

                areaName = ""
                selectedName = null
                resetCounter++

                CoroutineScope(Dispatchers.IO).launch {
                    Log.d(
                        "HOMEBASE",
                        "SAVING TO DATASTORE"
                    )
                    saveHomeBases(context, updated)
                    Log.d(
                        "HOMEBASE",
                        "SAVE COMPLETE"
                    )
                }
                val selectedBase =
                    hb.copy(
                        isSelected = true
                    )

                onHomeBaseSelected(selectedBase)

                Log.d(
                    "HOMEBASE",
                    "APP STATE UPDATE -> ${selectedBase.name}"
                )
            }
        }) {
            Text("Select Home Base")
        }

        if (errorText != null) {
            Spacer(Modifier.height(8.dp))
            Text(errorText!!, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(16.dp))
        Text("Saved Home Bases:")
        Spacer(Modifier.height(16.dp))

        val mapId = activeOsmUri?.let { getFileName(context, it) } ?: "default"
        homeBasesByMap[mapId]?.forEachIndexed { index, hb ->
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Log.d(
                    "HOMEBASE",
                    "${hb.name} selected=${hb.isSelected}"
                )
                Button(
                    onClick = {

                        val updated = homeBasesByMap.toMutableMap()

                        val list =
                            updated[mapId]?.mapIndexed { i, hb ->
                                hb.copy(isSelected = i == index)
                            }?.toMutableList()

                        if (list != null) {

                            updated[mapId] = list
                            homeBasesByMap = updated

                            val selectedBase = list[index]

                            onHomeBaseSelected(selectedBase)

                            Log.d(
                                "HOMEBASE",
                                "selectedHomeBase callback = ${selectedBase.name}"
                            )

                            CoroutineScope(Dispatchers.IO).launch {
                                saveHomeBases(context, updated)
                            }
                        }
                    }
                ){
                    Text("Select")
                }
                Text(
                    text = "${hb.name} (${hb.lat}, ${hb.lon})",
                    modifier = Modifier.combinedClickable(
                        onClick = { },
                        onLongClick = {
                            selectedIndexForDialog = index
                            showOptionsDialog = true
                        }
                    )
                )
            }
        }

// Options dialog
        if (showOptionsDialog) {
            AlertDialog(
                onDismissRequest = { showOptionsDialog = false },
                title = { Text("Manage Home Base") },
                text = { Text("Rename or delete this home base?") },
                confirmButton = {
                    Button(onClick = {
                        showOptionsDialog = false
                        showRenameDialog = true

                        val mapId = activeOsmUri?.let { getFileName(context, it) } ?: "default"
                        renameText = homeBasesByMap[mapId]?.get(selectedIndexForDialog)?.name ?: ""
                    }) { Text("Rename") }
                },
                dismissButton = {
                    Button(onClick = {
                        val mapId = activeOsmUri?.let { getFileName(context, it) } ?: "default"
                        val updated = homeBasesByMap.toMutableMap()
                        val list = updated[mapId] ?: mutableListOf()

                        list.removeAt(selectedIndexForDialog)
                        updated[mapId] = list

                        homeBasesByMap = updated
                        updated[mapId]?.forEach {
                            Log.d(
                                "HOMEBASE",
                                "${it.name} -> ${it.isSelected}"
                            )
                        }
                        CoroutineScope(Dispatchers.IO).launch {
                            saveHomeBases(context, updated)
                        }
                        showOptionsDialog = false
                    }) { Text("Delete") }
                }
            )
        }

// Rename dialog
        if (showRenameDialog) {
            AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                title = { Text("Rename Home Base") },
                text = {
                    TextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        label = { Text("New name") }
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        val mapId = activeOsmUri?.let { getFileName(context, it) } ?: "default"
                        val updated = homeBasesByMap.toMutableMap()
                        val list = updated[mapId] ?: mutableListOf()

                        list[selectedIndexForDialog] =
                            list[selectedIndexForDialog].copy(name = renameText)

                        updated[mapId] = list
                        homeBasesByMap = updated
                        updated[mapId]?.forEach {
                            Log.d(
                                "HOMEBASE",
                                "${it.name} -> ${it.isSelected}"
                            )
                        }
                        CoroutineScope(Dispatchers.IO).launch {
                            saveHomeBases(context, updated)
                        }
                        showRenameDialog = false
                    }) { Text("OK") }
                },
                dismissButton = {
                    Button(onClick = { showRenameDialog = false }) { Text("Cancel") }
                }
            )
        }

    }
}

fun getFileName(context: Context, uri: Uri): String {
    var name = "Unknown file"
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    cursor?.use {
        val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex != -1 && it.moveToFirst()) {
            name = it.getString(nameIndex)
        }
    }
    return name
}

@Composable
fun AutoCompleteTextField(
    label: String,
    allItems: List<String>,
    onItemSelected: (String) -> Unit,
    resetKey: Any? = null
) {
    // Reset query whenever resetKey changes
    var query by remember(resetKey) { mutableStateOf("") }

    // Suggestions recompute whenever query OR allItems changes
    val suggestions = remember(query, allItems) {
        allItems.filter { it.startsWith(query, ignoreCase = true) }
    }

    Column {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(label) },
            placeholder = { Text("Type to search…") },
            modifier = Modifier.fillMaxWidth()
        )

        if (suggestions.isNotEmpty() && query.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
            ) {
                items(suggestions) { suggestion ->
                    TextButton(
                        onClick = {
                            query = suggestion
                            onItemSelected(suggestion)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(suggestion)
                    }
                }
            }
        }
    }
}
// --- OSM Parsers ---
fun parseOsmNames(context: Context, uri: Uri?): List<String> {
    val names = mutableListOf<String>()
    val nodeMap = mutableMapOf<String, Pair<Double, Double>>() // id → (lat, lon)

    try {
        val inputStream = if (uri != null) {
            context.contentResolver.openInputStream(uri)
        } else {
            context.assets.open("tighter_around_rns.osm")
        }
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(inputStream, null)

        var eventType = parser.eventType
        var currentName: String? = null
        var currentLat: String? = null
        var currentLon: String? = null
        val currentWayNodes = mutableListOf<Pair<Double, Double>>()

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "node" -> {
                            val id = parser.getAttributeValue(null, "id")
                            val lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                            val lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                            if (id != null && lat != null && lon != null) {
                                nodeMap[id] = lat to lon
                                currentLat = lat.toString()
                                currentLon = lon.toString()
                            }
                        }
                        "nd" -> {
                            val ref = parser.getAttributeValue(null, "ref")
                            nodeMap[ref]?.let { currentWayNodes.add(it) }
                        }
                        "tag" -> {
                            val k = parser.getAttributeValue(null, "k")
                            val v = parser.getAttributeValue(null, "v")
                            if (k == "name" && v != null) {
                                currentName = v
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "node" -> {
                            if (currentName != null && currentLat != null && currentLon != null) {
                                names.add("$currentName ($currentLat,$currentLon)")
                            }
                            currentName = null
                            currentLat = null
                            currentLon = null
                        }
                        "way" -> {
                            if (currentName != null && currentWayNodes.isNotEmpty()) {
                                val avgLat = currentWayNodes.map { it.first }.average()
                                val avgLon = currentWayNodes.map { it.second }.average()
                                names.add("$currentName ($avgLat,$avgLon)")
                            }
                            currentName = null
                            currentWayNodes.clear()
                        }
                        "relation" -> {
                            // For simplicity, treat relation like way: centroid of member nodes
                            if (currentName != null && currentWayNodes.isNotEmpty()) {
                                val avgLat = currentWayNodes.map { it.first }.average()
                                val avgLon = currentWayNodes.map { it.second }.average()
                                names.add("$currentName ($avgLat,$avgLon)")
                            }
                            currentName = null
                            currentWayNodes.clear()
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        inputStream?.close()
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return names.distinct()
}

fun parseOsmLocations(
    context: Context,
    uri: Uri?
): List<SearchLocation> {
    val locations =
        mutableListOf<SearchLocation>()
    val nodeMap = mutableMapOf<String, Pair<Double, Double>>() // id → (lat, lon)

    try {
        val inputStream = if (uri != null) {
            context.contentResolver.openInputStream(uri)
        } else {
            context.assets.open("tighter_around_rns.osm")
        }
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(inputStream, null)

        var eventType = parser.eventType
        var currentName: String? = null
        var currentLat: String? = null
        var currentLon: String? = null
        val currentWayNodes = mutableListOf<Pair<Double, Double>>()

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "node" -> {
                            val id = parser.getAttributeValue(null, "id")
                            val lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                            val lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                            if (id != null && lat != null && lon != null) {
                                nodeMap[id] = lat to lon
                                currentLat = lat.toString()
                                currentLon = lon.toString()
                            }
                        }
                        "nd" -> {
                            val ref = parser.getAttributeValue(null, "ref")
                            nodeMap[ref]?.let { currentWayNodes.add(it) }
                        }
                        "tag" -> {
                            val k = parser.getAttributeValue(null, "k")
                            val v = parser.getAttributeValue(null, "v")
                            if (k == "name" && v != null) {
                                currentName = v
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "node" -> {
                            if (currentName != null && currentLat != null && currentLon != null) {
                                locations.add(
                                    SearchLocation(
                                        currentName,
                                        currentLat.toDouble(),
                                        currentLon.toDouble()
                                    )
                                )
                            }
                            currentName = null
                            currentLat = null
                            currentLon = null
                        }
                        "way" -> {
                            if (currentName != null && currentWayNodes.isNotEmpty()) {
                                val avgLat = currentWayNodes.map { it.first }.average()
                                val avgLon = currentWayNodes.map { it.second }.average()
                                locations.add(
                                    SearchLocation(
                                        currentName,
                                        avgLat,
                                        avgLon
                                    )
                                )

                            }
                            currentName = null
                            currentWayNodes.clear()
                        }
                        "relation" -> {
                            // For simplicity, treat relation like way: centroid of member nodes
                            if (currentName != null && currentWayNodes.isNotEmpty()) {
                                val avgLat = currentWayNodes.map { it.first }.average()
                                val avgLon = currentWayNodes.map { it.second }.average()
                                locations.add(
                                    SearchLocation(
                                        currentName,
                                        avgLat,
                                        avgLon
                                    )
                                )

                            }
                            currentName = null
                            currentWayNodes.clear()
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        inputStream?.close()
    } catch (e: Exception) {
        e.printStackTrace()
    }
    locations
        .filter {
            it.name == "Double Road"
        }
        .forEach {

            Log.d(
                "DOUBLE_ROAD",
                "${it.lat},${it.lon}"
            )
        }
    return locations.distinctBy {
        it.name
    }
}


//Saving home base
suspend fun saveHomeBases(context: Context, bases: Map<String, List<HomeBase>>) {
    val json = Gson().toJson(bases)
    context.homeBaseDataStore.edit { prefs ->
        prefs[HomeBasePrefs.HOME_BASES] = json
    }
}

suspend fun loadHomeBases(context: Context): MutableMap<String, MutableList<HomeBase>> {
    val prefs = context.homeBaseDataStore.data.first()
    val json = prefs[HomeBasePrefs.HOME_BASES] ?: "{}"
    val type = object : TypeToken<Map<String, List<HomeBase>>>() {}.type
    val loaded: Map<String, List<HomeBase>> = Gson().fromJson(json, type)
    return loaded.mapValues { it.value.toMutableList() }.toMutableMap()
}
