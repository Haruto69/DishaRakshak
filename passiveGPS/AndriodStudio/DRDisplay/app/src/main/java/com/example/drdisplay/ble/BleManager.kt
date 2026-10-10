package com.example.drdisplay.ble

import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.content.Context
import androidx.compose.runtime.mutableStateOf
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.os.Handler
import android.os.Looper
import android.bluetooth.BluetoothDevice

class BleManager(
    private val context: Context
) {
    val state = mutableStateOf(
        BleState()
    )
    private val bluetoothManager =
        context.getSystemService(
            BluetoothManager::class.java
        )
    private val bluetoothAdapter =
        bluetoothManager.adapter

    private var beltDevice: BluetoothDevice? = null

    private val bluetoothScanner: BluetoothLeScanner?
        get() = bluetoothAdapter?.bluetoothLeScanner

    private val discovered =
        mutableSetOf<String>()
    private val scanCallback =
        object : ScanCallback() {
            override fun onScanResult(
                callbackType: Int,
                result: ScanResult
            ) {
                val name =
                    result.device.name ?: return
                discovered.add(name)
                if (name == "DishaRakshak-Belt") {
                    beltDevice = result.device
                }
                state.value =
                    state.value.copy(
                        discoveredDevices =
                            discovered.toList(),
                        beltFound =
                            beltDevice != null
                    )
            }
        }

    fun isBluetoothAvailable(): Boolean {
        return bluetoothAdapter != null
    }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    fun connect() {

        state.value = state.value.copy(
            bluetoothAvailable = isBluetoothAvailable(),
            bluetoothEnabled = isBluetoothEnabled(),
            connected = true
        )

    }

    fun disconnect() {

        state.value = BleState(
            bluetoothAvailable = isBluetoothAvailable(),
            bluetoothEnabled = isBluetoothEnabled(),
            connected = false
        )

    }

    fun updateMockData() {

        state.value = state.value.copy(
            sequence = state.value.sequence + 1,
            roll = 3.12f,
            pitch = 8.01f,
            velocityX = 0.204f,
            velocityY = 0.015f,
            velocityZ = 0.000f
        )

    }

    fun startScan() {
        discovered.clear()
        bluetoothScanner?.startScan(
            scanCallback
        )
        state.value =
            state.value.copy(
                isScanning = true
            )
        Handler(
            Looper.getMainLooper()
        ).postDelayed({
            stopScan()
        }, 10000)
    }

    fun stopScan() {
        bluetoothScanner?.stopScan(
            scanCallback
        )
        state.value =
            state.value.copy(
                isScanning = false
            )
    }
}