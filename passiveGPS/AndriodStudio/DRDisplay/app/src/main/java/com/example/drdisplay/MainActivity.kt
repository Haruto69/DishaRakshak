package com.example.drdisplay

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Looper
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.example.drdisplay.ui.theme.DRDisplayTheme
import com.google.android.gms.location.*
import java.io.File

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
                Icon(Icons.Filled.Download, contentDescription = "Search OSM")
            }
            IconButton(onClick = { selectedScreen = "Home" }) {
                Icon(Icons.Filled.Home, contentDescription = "Home Base")
            }
            IconButton(onClick = { selectedScreen = "Animation" }) {
                Icon(Icons.Filled.Animation, contentDescription = "Animation")
            }
        }

        // Main content area
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            when (selectedScreen) {
                "SearchOSM" -> SearchOSMScreen(gpsText)
                "Home" -> HomeBaseScreen(gpsText)
                "Animation" -> AnimationScreen(gpsText)
                else -> Text("Main content goes here")
            }
        }
    }
}

// Placeholder for Search OSM screen
@Composable
fun SearchOSMScreen(gpsText: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text("🔍 Search OSM Files", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        Text("Here you’ll select an OSM file, parse regions, and set a home base.")
        Spacer(Modifier.height(16.dp))
        Text(gpsText)
    }
}

// Placeholder for Home Base screen
@Composable
fun HomeBaseScreen(gpsText: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🏠 Home Base Screen")
        Spacer(Modifier.height(16.dp))
        Text(gpsText)
    }
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
        Text(gpsText)
    }
}
