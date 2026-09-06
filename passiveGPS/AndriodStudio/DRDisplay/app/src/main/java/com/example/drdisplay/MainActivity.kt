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

class MainActivity : ComponentActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ask for GPS + storage permission
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.READ_EXTERNAL_STORAGE
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
    val mapId: String // "default" or custom filename
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
    var gpsText by remember { mutableStateOf("Waiting for GPS...") }

    val context = LocalContext.current

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
                            gpsText =
                                "Lat: ${location.latitude}, Lon: ${location.longitude}, Acc: ${location.accuracy}m"
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
        }

        // Main content area
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            when (selectedScreen) {
                "SearchOSM" -> SearchOSMScreen(gpsText)
                "Animation" -> AnimationScreen(gpsText)
                else -> Text("Main content goes here")
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
fun SearchOSMScreen(gpsText: String) {
    val context = LocalContext.current

    var activeOsmUri by remember { mutableStateOf<Uri?>(null) }
    var mode by remember { mutableStateOf("name") }
    var areaName by remember { mutableStateOf("") }
    var selectedName by remember { mutableStateOf<String?>(null) }

    var lat by remember { mutableStateOf("") }
    var lon by remember { mutableStateOf("") }

    var errorText by remember { mutableStateOf<String?>(null) }

    // Home base state
    var homeBasesByMap by remember { mutableStateOf(mutableMapOf<String, MutableList<HomeBase>>()) }
    var activeIndex by remember { mutableStateOf(-1) }

    // Reset counter for AutoCompleteTextField
    var resetCounter by remember { mutableStateOf(0) }

    // Load persisted bases on startup
    LaunchedEffect(Unit) {
        homeBasesByMap = loadHomeBases(context)
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> if (uri != null) activeOsmUri = uri }
    )

    val availableNames = remember(activeOsmUri) { parseOsmNames(context, activeOsmUri) }
    val availableLats = remember(activeOsmUri) { parseOsmLatitudes(context, activeOsmUri) }
    val availableLons = remember(activeOsmUri) { parseOsmLongitudes(context, activeOsmUri) }

    // State for dialogs (declare these at the top of SearchOSMScreen)
    var showOptionsDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var selectedIndexForDialog by remember { mutableStateOf(-1) }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Select Map Source", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        Row {
            Button(onClick = { activeOsmUri = null }) { Text("Use Default Map") }
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

        Row {
            Button(onClick = { mode = "name"; errorText = null }) { Text("Search by Name") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { mode = "coords"; errorText = null }) { Text("Search by Coordinates") }
        }

        Spacer(Modifier.height(16.dp))

        if (mode == "name") {
            AutoCompleteTextField(
                label = "Enter place name",
                allItems = availableNames,
                onItemSelected = { selectedName = it; areaName = it },
                resetKey = resetCounter   // 🔑 pass resetCounter here
            )
        } else {
            Column {
                AutoCompleteTextField(
                    label = "Latitude",
                    allItems = availableLats,
                    onItemSelected = { lat = it },
                    resetKey = resetCounter
                )
                Spacer(Modifier.height(8.dp))
                AutoCompleteTextField(
                    label = "Longitude",
                    allItems = availableLons,
                    onItemSelected = { lon = it },
                    resetKey = resetCounter
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            if (mode == "name" && selectedName != null) {
                val mapId = activeOsmUri?.let { getFileName(context, it) } ?: "default"
                val index = availableNames.indexOf(selectedName!!)
                val hb = HomeBase(
                    name = selectedName!!,
                    lat = availableLats.getOrNull(index)?.toDoubleOrNull() ?: 0.0,
                    lon = availableLons.getOrNull(index)?.toDoubleOrNull() ?: 0.0,
                    mapId = mapId
                )

                val updated = homeBasesByMap.toMutableMap()
                val list = updated.getOrPut(mapId) { mutableListOf() }
                list.add(hb)

                homeBasesByMap = updated

                areaName = ""
                selectedName = null
                resetCounter++

                CoroutineScope(Dispatchers.IO).launch {
                    saveHomeBases(context, updated)
                }
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

        val mapId = activeOsmUri?.let { getFileName(context, it) } ?: "default"
        homeBasesByMap[mapId]?.forEachIndexed { index, hb ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.combinedClickable(
                    onClick = { activeIndex = index },
                    onLongClick = {
                        selectedIndexForDialog = index
                        showOptionsDialog = true
                    }
                )
            ) {
                RadioButton(
                    selected = index == activeIndex,
                    onClick = { activeIndex = index }
                )
                Text("${hb.name} (${hb.lat}, ${hb.lon})")
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

fun parseOsmLatitudes(context: Context, uri: Uri?): List<String> {
    val lats = mutableListOf<String>()
    try {
        val inputStream = if (uri != null) {
            context.contentResolver.openInputStream(uri)
        } else {
            context.assets.open("tighter_around_rns.osm")
        }
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(inputStream, null)

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "node") {
                val lat = parser.getAttributeValue(null, "lat")
                if (lat != null) lats.add(lat)
            }
            eventType = parser.next()
        }
        inputStream?.close()
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return lats.distinct()
}

fun parseOsmLongitudes(context: Context, uri: Uri?): List<String> {
    val lons = mutableListOf<String>()
    try {
        val inputStream = if (uri != null) {
            context.contentResolver.openInputStream(uri)
        } else {
            context.assets.open("tighter_around_rns.osm")
        }
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(inputStream, null)

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "node") {
                val lon = parser.getAttributeValue(null, "lon")
                if (lon != null) lons.add(lon)
            }
            eventType = parser.next()
        }
        inputStream?.close()
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return lons.distinct()
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

// Placeholder for Animation screen
@Composable
fun AnimationScreen(gpsText: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🎞️ Animation Screen")
        Spacer(Modifier.height(16.dp))
    }
}
