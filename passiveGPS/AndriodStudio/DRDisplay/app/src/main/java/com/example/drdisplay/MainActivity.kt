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
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.zIndex
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import kotlinx.coroutines.flow.map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowCompat
import com.example.drdisplay.ui.theme.DRDisplayTheme
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

    private var permissionVersion by mutableIntStateOf(0)

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionVersion++
    }

    override fun onResume() {
        super.onResume()
        permissionVersion++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ask for GPS + storage permission
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            ),
            1
        )

        setContent {
            val appearanceFlow = remember { homeBaseDataStore.data.map { it[AppearancePrefs.MODE] ?: "system" } }
            val appearance by appearanceFlow.collectAsState(initial = "system")
            val darkTheme = when (appearance) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            val scope = rememberCoroutineScope()
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
                @Suppress("DEPRECATION")
                window.statusBarColor = if (darkTheme) android.graphics.Color.rgb(18, 20, 24) else android.graphics.Color.WHITE
                @Suppress("DEPRECATION")
                window.navigationBarColor = if (darkTheme) android.graphics.Color.rgb(18, 20, 24) else android.graphics.Color.WHITE
            }
            DRDisplayTheme(darkTheme = darkTheme) {
                AppLayout(permissionVersion, appearance) { mode ->
                    scope.launch { homeBaseDataStore.edit { it[AppearancePrefs.MODE] = mode } }
                }
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

object AppearancePrefs {
    val MODE = stringPreferencesKey("appearance_mode")
}

object HomeBasePrefs {
    val HOME_BASES = stringPreferencesKey("home_bases")
}

object NavPrefs {
    val LAST_GPS_LAT =
        stringPreferencesKey("last_gps_lat")

    val LAST_GPS_LON =
        stringPreferencesKey("last_gps_lon")
}

@Composable
fun AppLayout(
    permissionVersion: Int,
    appearance: String,
    onAppearanceChange: (String) -> Unit
) {
    var showAppearance by remember { mutableStateOf(false) }

    var selectedScreen by remember { mutableStateOf("SearchOSM") }
    var activeOsmUri by remember {
        mutableStateOf<Uri?>(null)
    }
    var gpsText by remember { mutableStateOf("Waiting for GPS...") }
    var gpsAccuracy by remember { mutableStateOf<Float?>(null) }
    var gpsFixElapsedNs by remember { mutableStateOf<Long?>(null) }
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
    SideEffect {
        bleManager.packetLogger.navigationSnapshot = com.example.drdisplay.ble.NavigationSnapshot(
            if (gpsFixElapsedNs != null) gpsLat else null, if (gpsFixElapsedNs != null) gpsLon else null,
            gpsAccuracy, gpsFixElapsedNs,
            if (navLat != 0.0 && navLon != 0.0) navLat else null,
            if (navLat != 0.0 && navLon != 0.0) navLon else null,
            selectedHomeBase?.lat, selectedHomeBase?.lon
        )
    }

    val locationManager = remember { context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager }
    var baseX by remember { mutableFloatStateOf(0f) }
    var baseY by remember { mutableFloatStateOf(0f) }
    var gpsHealthy by remember { mutableStateOf(false) }
    var providerEnabled by remember { mutableStateOf(false) }
    var satellitesUsed by remember { mutableIntStateOf(0) }
    var locationError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(permissionVersion) {
        val listener = object : android.location.LocationListener {
            override fun onLocationChanged(location: android.location.Location) {
                gpsLat = location.latitude
                gpsLon = location.longitude
                gpsAccuracy = if (location.hasAccuracy()) location.accuracy else null
                gpsFixElapsedNs = location.elapsedRealtimeNanos
                providerEnabled = true
                val ageNs = android.os.SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos
                if (location.hasAccuracy() && location.accuracy <= 20f && ageNs in 0..15_000_000_000L) {
                    originLat = location.latitude
                    originLon = location.longitude
                    baseX = bleManager.state.value.posX
                    baseY = bleManager.state.value.posY
                    navLat = originLat
                    navLon = originLon
                    gpsHealthy = true
                    CoroutineScope(Dispatchers.IO).launch {
                        context.homeBaseDataStore.edit { prefs ->
                            prefs[NavPrefs.LAST_GPS_LAT] = location.latitude.toString()
                            prefs[NavPrefs.LAST_GPS_LON] = location.longitude.toString()
                        }
                    }
                }
            }
            override fun onProviderEnabled(provider: String) { providerEnabled = true }
            override fun onProviderDisabled(provider: String) { providerEnabled = false; gpsHealthy = false }
        }
        val gnssCallback = object : android.location.GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: android.location.GnssStatus) {
                satellitesUsed = (0 until status.satelliteCount).count { status.usedInFix(it) }
            }
            override fun onStopped() { satellitesUsed = 0 }
        }
        providerEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
        locationError = null
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                locationManager.requestLocationUpdates(android.location.LocationManager.GPS_PROVIDER, 1000L, 0f, listener, Looper.getMainLooper())
                locationManager.registerGnssStatusCallback(gnssCallback, android.os.Handler(Looper.getMainLooper()))
                locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)?.let { cached ->
                    val age = android.os.SystemClock.elapsedRealtimeNanos() - cached.elapsedRealtimeNanos
                    if (age in 0..15_000_000_000L) listener.onLocationChanged(cached)
                }
            } catch (e: Exception) {
                locationError = "GPS unavailable: ${e.message}"
            }
        } else gpsText = "Precise location permission required"
        onDispose {
            locationManager.removeUpdates(listener)
            locationManager.unregisterGnssStatusCallback(gnssCallback)
        }
    }

    LaunchedEffect(Unit) {
        val prefs = context.homeBaseDataStore.data.first()
        val savedLat = prefs[NavPrefs.LAST_GPS_LAT]?.toDoubleOrNull()
        val savedLon = prefs[NavPrefs.LAST_GPS_LON]?.toDoubleOrNull()
        if (!gpsHealthy && originLat == 0.0 && savedLat != null && savedLon != null) {
            originLat = savedLat
            originLon = savedLon
            baseX = bleState.posX
            baseY = bleState.posY
        }
    }

    LaunchedEffect(gpsFixElapsedNs, gpsAccuracy, providerEnabled, permissionVersion, bleState.connected, satellitesUsed, locationError) {
        while (true) {
            val age = gpsFixElapsedNs?.let { android.os.SystemClock.elapsedRealtimeNanos() - it }
            gpsHealthy = providerEnabled && age != null && age in 0..15_000_000_000L && (gpsAccuracy ?: Float.MAX_VALUE) <= 20f
            val permission = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            gpsText = when {
                !permission -> "Precise location permission required"
                locationError != null -> locationError!!
                gpsHealthy -> "GPS · ${"%.6f".format(java.util.Locale.US, gpsLat)}, ${"%.6f".format(java.util.Locale.US, gpsLon)} · ±${gpsAccuracy} m"
                providerEnabled && age != null && age in 0..15_000_000_000L && originLat == 0.0 -> "GPS fix too weak (±${gpsAccuracy ?: "?"} m) · $satellitesUsed satellites used"
                originLat != 0.0 && bleState.connected -> "GPS weak or off · belt estimate"
                originLat != 0.0 -> "GPS weak or off · last position (belt disconnected)"
                !providerEnabled -> "GPS is off · enable location"
                else -> "Waiting for GPS fix · $satellitesUsed satellites used"
            }
            kotlinx.coroutines.delay(1000)
        }
    }

    LaunchedEffect(bleState.posX, bleState.posY, gpsHealthy, originLat, originLon, baseX, baseY) {
        if (!gpsHealthy && originLat != 0.0 && originLon != 0.0) {
            navLat = originLat + (bleState.posY - baseY) / 111111.0
            navLon = originLon + (bleState.posX - baseX) / (111111.0 * kotlin.math.cos(Math.toRadians(originLat)))
        }
    }

    if (showAppearance) {
        AlertDialog(
            onDismissRequest = { showAppearance = false },
            title = { Text("Appearance") },
            text = {
                Column {
                    listOf("system" to "System default", "light" to "Light", "dark" to "Dark").forEach { (mode, label) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .combinedClickable(onClick = { onAppearanceChange(mode); showAppearance = false }),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = appearance == mode, onClick = { onAppearanceChange(mode); showAppearance = false })
                            Text(label, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAppearance = false }) { Text("Close") } }
        )
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth().zIndex(1f)) {
            Column {
                Row(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Disha Rakshak", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { showAppearance = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Appearance settings")
                    }
                }
                NavigationBar {
                    NavigationBarItem(selected = selectedScreen == "SearchOSM", onClick = { selectedScreen = "SearchOSM" }, icon = { Icon(Icons.Filled.Home, contentDescription = null) }, label = { Text("Home") })
                    NavigationBarItem(selected = selectedScreen == "Animation", onClick = { selectedScreen = "Animation" }, icon = { Icon(Icons.Filled.Map, contentDescription = null) }, label = { Text("Map") })
                    NavigationBarItem(selected = selectedScreen == "BLE", onClick = { selectedScreen = "BLE" }, icon = { Icon(Icons.Filled.Bluetooth, contentDescription = null) }, label = { Text("Belt") })
                }
            }
        }

        // Main content area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
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
                                "selectedHomeBase NOW = ${it?.name}"
                            )
                        }
                    )


                "Animation" ->
                    key(activeOsmUri) { AnimationScreen(
                        gpsText = gpsText,
                        mapUri = activeOsmUri,
                        gpsLat = gpsLat,
                        gpsLon = gpsLon,
                        currentLat = navLat,
                        currentLon = navLon,
                        selectedHomeBase = selectedHomeBase,
                        onPinSelected = { lat, lon, name ->
                            val mapId = activeOsmUri?.let { getFileName(context, it) } ?: "default"
                            val base = HomeBase(name = name, lat = lat, lon = lon, mapId = mapId, isSelected = true)
                            CoroutineScope(Dispatchers.IO).launch {
                                context.homeBaseDataStore.edit { prefs ->
                                    val type = object : TypeToken<Map<String, List<HomeBase>>>() {}.type
                                    val existing: Map<String, List<HomeBase>> = Gson().fromJson(prefs[HomeBasePrefs.HOME_BASES] ?: "{}", type)
                                    val updated = existing.toMutableMap()
                                    val entries = existing[mapId].orEmpty().map { it.copy(isSelected = false) }.toMutableList()
                                    entries.add(base)
                                    updated[mapId] = entries
                                    prefs[HomeBasePrefs.HOME_BASES] = Gson().toJson(updated)
                                }
                                kotlinx.coroutines.withContext(Dispatchers.Main) { selectedHomeBase = base }
                            }
                        }
                    ) }

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
            text = if (selectedScreen == "Animation") "$gpsText\n© OpenStreetMap contributors" else gpsText,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            textAlign = TextAlign.Center
        )
    }
    }
}

// Placeholder for Search OSM screen
@Composable
fun SearchOSMScreen(
    gpsText: String,
    activeOsmUri: Uri?,
    onMapSelected: (Uri?) -> Unit,
    onHomeBaseSelected: (HomeBase?) -> Unit
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

        onHomeBaseSelected(homeBasesByMap[currentMapId]?.firstOrNull { it.isSelected })
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
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Your map", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fillMaxWidth())
        Text("Choose a map and a home base for your route.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp))
        Text("Map source", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
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

        Text("Find a home base", style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth())
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
                val list = updated.getOrPut(mapId) { mutableListOf() }.toMutableList()
                if (list.none { it.name == hb.name }) list.add(hb)
                updated[mapId] = list.map { it.copy(isSelected = it.name == hb.name) }.toMutableList()

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
                val selectedBase = updated.getValue(mapId).first { it.isSelected }

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
        Text("Saved home bases", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))

        val mapId = activeOsmUri?.let { getFileName(context, it) } ?: "default"
        homeBasesByMap[mapId]?.forEachIndexed { index, hb ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    .background(
                        if (hb.isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        MaterialTheme.shapes.medium
                    ).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
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
                            updated[mapId]?.mapIndexed { i, item ->
                                item.copy(isSelected = i == index && !hb.isSelected)
                            }?.toMutableList()

                        if (list != null) {

                            updated[mapId] = list
                            homeBasesByMap = updated

                            val selectedBase = list.firstOrNull { it.isSelected }

                            onHomeBaseSelected(selectedBase)

                            Log.d(
                                "HOMEBASE",
                                "selectedHomeBase callback = ${selectedBase?.name}"
                            )

                            CoroutineScope(Dispatchers.IO).launch {
                                saveHomeBases(context, updated)
                            }
                        }
                    }
                ){
                    Text(if (hb.isSelected) "Selected" else "Select")
                }
                Column(
                    modifier = Modifier.weight(1f).combinedClickable(
                        onClick = { },
                        onLongClick = {
                            selectedIndexForDialog = index
                            showOptionsDialog = true
                        }
                    )
                ) {
                    Text(hb.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = String.format(java.util.Locale.US, "%.6f, %.6f", hb.lat, hb.lon),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
                    OutlinedButton(
                        onClick = {
                            query = suggestion
                            onItemSelected(suggestion)
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text(
                            text = suggestion,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Start
                        )
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
