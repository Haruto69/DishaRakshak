package com.example.drdisplay.ble

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.content.Context
import androidx.compose.runtime.mutableStateOf
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.os.Handler
import android.os.Looper
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import androidx.annotation.RequiresPermission
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.sqrt

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
    private var bluetoothGatt: BluetoothGatt? = null
    private var currentX = 0.0
    private var currentY = 0.0

    private var homeX = 0.0
    private var homeY = 0.0

    private var previousTime =
        System.currentTimeMillis()

    private val SERVICE_UUID =
        UUID.fromString(
            "5a0f0a31-1a22-47a2-b001-000000000001"
        )

    private val CHARACTERISTIC_UUID =
        UUID.fromString(
            "5a0f0a31-1a22-47a2-b001-000000000002"
        )

    private val gattCallback =
        object : BluetoothGattCallback() {
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int
            ) {
                if (
                    newState ==
                    BluetoothProfile.STATE_CONNECTED
                ) {
                    bluetoothGatt = gatt
                    state.value =
                        state.value.copy(
                            connected = true
                        )
                    gatt.discoverServices()
                }
                if (
                    newState ==
                    BluetoothProfile.STATE_DISCONNECTED
                ) {
                    state.value =
                        state.value.copy(
                            connected = false
                        )
                    startScan()
                }
            }

            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onServicesDiscovered(
                gatt: BluetoothGatt,
                status: Int
            ) {

                val service =
                    gatt.getService(
                        SERVICE_UUID
                    ) ?: return

                val characteristic =
                    service.getCharacteristic(
                        CHARACTERISTIC_UUID
                    ) ?: return

                gatt.setCharacteristicNotification(
                    characteristic,
                    true
                )
            }

            private var x = 0.0
            private var y = 0.0
            private var z = 0.0

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                val text =
                    String(
                        characteristic.value,
                        Charsets.UTF_8
                    )
                val fields =
                    text.split(",")
                val vx =
                    fields[3].toFloat()
                val vy =
                    fields[4].toFloat()
                val vz =
                    fields[5].toFloat()
                if (fields.size != 6)
                    return
                val now =
                    System.currentTimeMillis()
                val dt =
                    (now - previousTime) / 1000.0
                previousTime = now

                x += vx * dt
                y += vy * dt
                z += vz * dt
                val dx = homeX - x
                val dy = homeY - y
                val distanceToHome =
                    sqrt(
                        dx * dx +
                                dy * dy
                    )
                val bearingToHome =
                    Math.toDegrees(
                        atan2(
                            dy,
                            dx
                        )
                    )
                state.value =
                    state.value.copy(
                        sequence = fields[0].toLong(),
                        roll = fields[1].toFloat(),
                        pitch = fields[2].toFloat(),
                        velocityX = vx,
                        velocityY = vy,
                        velocityZ = vz,
                        posX = x.toFloat(),
                        posY = y.toFloat(),
                        posZ = z.toFloat(),
                        distanceToHome =
                            distanceToHome.toFloat(),
                        bearingToHome =
                            bearingToHome.toFloat()
                    )
            }
        }

    private val bluetoothScanner: BluetoothLeScanner?
        get() = bluetoothAdapter?.bluetoothLeScanner

    private val discovered =
        mutableSetOf<String>()
    private val scanCallback =
        object : ScanCallback() {
            @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN])
            override fun onScanResult(
                callbackType: Int,
                result: ScanResult
            ) {
                val name =
                    result.device.name ?: return
                discovered.add(name)
                if (name == "DishaRakshak-Belt") {
                    beltDevice = result.device
                    if (!state.value.connected) {
                        stopScan()
                        connect()
                    }
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

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect() {
        val device =
            beltDevice ?: return
        bluetoothGatt =
            device.connectGatt(
                context,
                false,
                gattCallback
            )
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        state.value =
            state.value.copy(
                connected = false
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
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopScan() {
        bluetoothScanner?.stopScan(
            scanCallback
        )
        state.value =
            state.value.copy(
                isScanning = false
            )
    }

    fun setHomePosition(
        x: Double,
        y: Double
    ) {
        homeX = x
        homeY = y
    }
}