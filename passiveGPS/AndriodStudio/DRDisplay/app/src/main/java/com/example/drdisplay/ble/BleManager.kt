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
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import androidx.annotation.RequiresPermission
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.sqrt
import android.util.Log
import kotlin.math.cos
import kotlin.math.sin

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

    private var homeX = 0.0
    private var homeY = 0.0

    private val DR_SCALE = 0.02

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
            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int
            ) {

                Log.d(
                    "BLE_FLOW",
                    "CCCD_WRITE_COMPLETE status=$status"
                )
            }
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
                    gatt.requestMtu(185)
                    gatt.discoverServices()
                    bluetoothGatt = gatt
                    state.value =
                        state.value.copy(
                            connected = true
                        )
                    Log.d(
                        "BLE_FLOW",
                        "CONNECTED"
                    )
                    gatt.discoverServices()
                }
                if (
                    newState ==
                    BluetoothProfile.STATE_DISCONNECTED
                ) {

                    beltDevice = null

                    state.value =
                        state.value.copy(
                            connected = false,
                            beltFound = false
                        )

                    startScan()
                }

            }

            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onServicesDiscovered(
                gatt: BluetoothGatt,
                status: Int
            ) {
                Log.d(
                    "BLE_FLOW",
                    "SERVICES_DISCOVERED"
                )

                val service =
                    gatt.getService(
                        SERVICE_UUID
                    ) ?: return

                val characteristic =
                    service.getCharacteristic(
                        CHARACTERISTIC_UUID
                    ) ?: return
                Log.d(
                    "BLE_FLOW",
                    "Characteristic properties = ${characteristic.properties}"
                )
                characteristic.descriptors.forEach {
                    Log.d(
                        "BLE_FLOW",
                        "Descriptor UUID = ${it.uuid}"
                    )
                }

                gatt.setCharacteristicNotification(
                    characteristic,
                    true
                )

                val descriptor =
                    characteristic.getDescriptor(
                        UUID.fromString(
                            "00002902-0000-1000-8000-00805f9b34fb"
                        )
                    )
                if (descriptor != null) {

                    descriptor.value =
                        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE

                    gatt.writeDescriptor(
                        descriptor
                    )

                    Log.d(
                        "BLE_FLOW",
                        "CCCD_WRITE_REQUESTED"
                    )
                }

                Log.d(
                    "BLE_FLOW",
                    "NOTIFICATION_REQUESTED"
                )
            }

            private var x = 0.0
            private var y = 0.0
            private var z = 0.0

            fun resetDeadReckoning() {
                x = 0.0
                y = 0.0
                z = 0.0
            }

            private var gpsAnchorX = 0.0
            private var gpsAnchorY = 0.0

            fun resetDrPosition(
                gpsX: Double,
                gpsY: Double
            ) {
                x = gpsX
                y = gpsY

                gpsAnchorX = gpsX
                gpsAnchorY = gpsY

                Log.d(
                    "GPS_RECOVERY",
                    "DR reset to GPS"
                )
            }


            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                Log.d(
                    "BLE_DEBUG",
                    "NEW_PARSER_RUNNING"
                )
                Log.d(
                    "BLE_FLOW",
                    "PACKET_RECEIVED"
                )
                Log.d(
                    "BLE_PACKET",
                    "rawBytes=${characteristic.value.size}"
                )

                val text =
                    String(
                        characteristic.value,
                        Charsets.UTF_8
                    )
                Log.d(
                    "BLE_PACKET",
                    text
                )
                Log.d(
                    "BLE_PACKET",
                    "length=${text.length} text=[$text]"
                )
                val fields =
                    text.split(",")
                Log.d(
                    "BLE_DEBUG",
                    "fields=${fields.size}"
                )
                Log.d(
                    "BLE_PACKET",
                    fields.joinToString("|")
                )
                if (fields.size != 7) {

                    Log.d(
                        "BLE_DEBUG",
                        "PACKET REJECTED"
                    )

                    return
                }

                val yaw =
                    fields[3].toFloatOrNull() ?: 0f

                val velocityX =
                    fields[4].toFloatOrNull() ?: 0f

                val velocityY =
                    fields[5].toFloatOrNull() ?: 0f

                val velocityZ =
                    fields[6].toFloatOrNull() ?: 0f

                Log.d(
                    "IMU_PACKET",
                    "vx=$velocityX vy=$velocityY vz=$velocityZ"
                )

                val now =
                    System.currentTimeMillis()
                val dt =
                    (now - previousTime) / 1000.0
                previousTime = now

                /*
                x += velocityX * dt * DR_SCALE
                y += velocityY * dt * DR_SCALE
                z += velocityZ * dt * DR_SCALE
                 */
                val yawRad = Math.toRadians(yaw.toDouble())

                val worldVx =
                    velocityX * cos(yawRad) -
                            velocityY * sin(yawRad)

                val worldVy =
                    velocityX * sin(yawRad) +
                            velocityY * cos(yawRad)

                x += worldVx * dt * DR_SCALE
                y += worldVy * dt * DR_SCALE


                z += velocityZ * dt

                Log.d(
                    "VEL_DEBUG",
                    "vx=$velocityX vy=$velocityY vz=$velocityZ dt=$dt"
                )
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
                        yaw = yaw,

                        velocityX = velocityX,
                        velocityY = velocityY,
                        velocityZ = velocityZ,

                        posX = x.toFloat(),
                        posY = y.toFloat(),
                        posZ = z.toFloat(),

                        distanceToHome =
                            distanceToHome.toFloat(),

                        bearingToHome =
                            bearingToHome.toFloat()
                    )

                x += worldVx * dt
                y += worldVy * dt

                Log.d(
                    "VEL_DEBUG",
                    "vx=$velocityX vy=$velocityY vz=$velocityZ"
                )

                Log.d(
                    "DR_DEBUG",
                    "x=$x y=$y z=$z"
                )
            }
            override fun onMtuChanged(
                gatt: BluetoothGatt,
                mtu: Int,
                status: Int
            ) {
                Log.d(
                    "BLE_FLOW",
                    "MTU=$mtu"
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