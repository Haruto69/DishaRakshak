package com.example.drdisplay.ble

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun BleScreen(bleManager: BleManager) {
    val state by bleManager.state
    val log by bleManager.packetLogger.state
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) bleManager.packetLogger.export(uri)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Belt test monitor", style = MaterialTheme.typography.headlineSmall)
            Text("Phone receipt times and raw BLE notifications", style = MaterialTheme.typography.bodyMedium)
        }
        item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Connection", style = MaterialTheme.typography.titleMedium)
                    Text(when {
                        state.connected -> "Connected"
                        state.isScanning -> "Scanning for belt…"
                        state.beltFound -> "Belt found"
                        else -> "Not connected"
                    })
                    Text("Bluetooth: " + if (!bleManager.isBluetoothAvailable()) "unavailable" else if (bleManager.isBluetoothEnabled()) "on" else "off")
                    state.scanError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { bleManager.startScan() }, enabled = !state.isScanning && !state.connected) { Text("Start scan") }
                        OutlinedButton(onClick = @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN) { bleManager.stopScan() }, enabled = state.isScanning) { Text("Stop scan") }
                    }
                    if (state.discoveredDevices.isNotEmpty()) Text("Devices: " + state.discoveredDevices.joinToString())
                }
            }
        }
        item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Latest decoded telemetry", style = MaterialTheme.typography.titleMedium)
                    Text("Sequence: ${state.sequence}  •  Yaw: ${"%.2f".format(Locale.US, state.yaw)}°")
                    Text("Roll: ${"%.2f".format(Locale.US, state.roll)}°  •  Pitch: ${"%.2f".format(Locale.US, state.pitch)}°")
                    Text("Velocity X / Y / Z: ${"%.3f".format(Locale.US, state.velocityX)} / ${"%.3f".format(Locale.US, state.velocityY)} / ${"%.3f".format(Locale.US, state.velocityZ)}")
                    Text("Values come from the belt; units depend on its firmware.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Packet recording", style = MaterialTheme.typography.titleMedium)
                    Text(if (log.recording) "Recording is on" else "Recording is off")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { if (log.recording) bleManager.packetLogger.stop() else bleManager.packetLogger.start() }) {
                            Text(if (log.recording) "Stop log" else "Start log")
                        }
                        OutlinedButton(
                            onClick = { export.launch("belt_log.csv") },
                            enabled = !log.recording && log.filePath != null
                        ) { Text("Export CSV") }
                    }
                    if (log.message.isNotEmpty()) Text(log.message, style = MaterialTheme.typography.bodySmall)
                    if (log.filePath != null) Text("Saved locally: ${log.filePath}", style = MaterialTheme.typography.bodySmall)
                    Text("Times are phone receipt times, in UTC. Export after stopping.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Text("Recent packets (${log.total} received; last 50 shown)", style = MaterialTheme.typography.titleMedium)
        }
        if (log.recent.isEmpty()) item { Text("No notifications received yet.") }
        items(log.recent) { packet ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(packet.time + if (packet.valid) "  •  Valid" else "  •  Unrecognized", style = MaterialTheme.typography.labelMedium)
                    Text(packet.payload.ifEmpty { "(empty packet)" }, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
