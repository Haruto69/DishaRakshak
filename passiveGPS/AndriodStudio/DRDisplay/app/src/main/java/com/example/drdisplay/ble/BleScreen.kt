package com.example.drdisplay.ble

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BleScreen(
    bleManager: BleManager
) {

    val state by bleManager.state

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Text(
            text = "BLE Monitor",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // LEFT PANEL
            OutlinedCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {

                Column(
                    modifier = Modifier.padding(12.dp)
                ) {

                    Text(
                        "Telemetry",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        "Bluetooth Available: ${state.bluetoothAvailable}"
                    )

                    Text(
                        "Bluetooth Enabled: ${state.bluetoothEnabled}"
                    )

                    Text(
                        "Scanning: ${state.isScanning}"
                    )

                    Text(
                        if (state.beltFound)
                            "BELT FOUND ✅"
                        else
                            "BELT NOT FOUND ❌"
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        if (state.connected)
                            "Connected"
                        else
                            "Disconnected"
                    )

                    Spacer(Modifier.height(8.dp))

                    Text("Sequence: ${state.sequence}")
                    Text("Roll: ${state.roll}")
                    Text("Pitch: ${state.pitch}")
                    Text("Velocity X: ${state.velocityX}")
                    Text("Velocity Y: ${state.velocityY}")
                    Text("Velocity Z: ${state.velocityZ}")
                }
            }

            // RIGHT PANEL
            OutlinedCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {

                    Text(
                        "Devices Found",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        "↓ Scroll ↓",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Spacer(Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {

                        items(state.discoveredDevices) { device ->

                            Text(
                                text = device,
                                modifier = Modifier.padding(
                                    vertical = 4.dp
                                )
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                bleManager.startScan()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start Scan")
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                bleManager.stopScan()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Stop Scan")
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                bleManager.connect()
                bleManager.updateMockData()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Mock Connect")
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                bleManager.disconnect()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Disconnect")
        }
    }
}