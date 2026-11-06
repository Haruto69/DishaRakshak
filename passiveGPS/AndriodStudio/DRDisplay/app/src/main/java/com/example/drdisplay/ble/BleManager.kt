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
    val packetLogger = PacketLogger(context.applicationContext)
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

    private var previousTime = 0L

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
                    previousTime = 0L

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


            @Suppress("DEPRECATION")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                handlePacket(characteristic.uuid, characteristic.value.copyOf())
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                handlePacket(characteristic.uuid, value.copyOf())
            }

            private fun handlePacket(uuid: UUID, value: ByteArray) {
                packetLogger.record(value, uuid.toString())
                if (uuid != CHARACTERISTIC_UUID) return
                if (value.size == 20 && (value[0].toInt() and 0xff) == 0xD1) return
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
                    "rawBytes=${value.size}"
                )

                val text =
                    String(
                        value,
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
                    text.trim().split(",").map { it.trim() }
                Log.d(
                    "BLE_DEBUG",
                    "fields=${fields.size}"
                )
                Log.d(
                    "BLE_PACKET",
                    fields.joinToString("|")
                )
                if (fields.size != 7 || fields[0].toLongOrNull() == null ||
                    fields.drop(1).any { it.toFloatOrNull()?.isFinite() != true }) {

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

                val now = android.os.SystemClock.elapsedRealtime()
                val interval = now - previousTime
                val dt = if (previousTime != 0L && interval in 1..2000) interval / 1000.0 else 0.0
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
                val name = result.scanRecord?.deviceName ?: result.device.name
                val hasService = result.scanRecord?.serviceUuids?.any { it.uuid == SERVICE_UUID } == true
                if (name != null) discovered.add(name)
                if (name == "DishaRakshak-Belt" || hasService) {
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

            override fun onScanFailed(errorCode: Int) {
                Handler(Looper.getMainLooper()).post {
                    state.value = state.value.copy(isScanning = false, scanError = "Bluetooth scan failed (code $errorCode). Try again.")
                }
            }
        }


    fun isBluetoothAvailable(): Boolean {
        return bluetoothAdapter != null
    }

    fun isBluetoothEnabled(): Boolean {
        return try { bluetoothAdapter?.isEnabled == true } catch (_: SecurityException) { false }
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
        if (state.value.isScanning) return
        if (android.os.Build.VERSION.SDK_INT >= 31 &&
            (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != android.content.pm.PackageManager.PERMISSION_GRANTED ||
             androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED)) {
            state.value = state.value.copy(scanError = "Allow Nearby devices permission in app settings.")
            return
        }
        if (android.os.Build.VERSION.SDK_INT < 31 && !(context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager).isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
            state.value = state.value.copy(scanError = "This Android version requires Location to be on for Bluetooth scanning.")
            return
        }
        try {
            val scanner = bluetoothScanner
            if (scanner == null) {
                state.value = state.value.copy(scanError = "Turn on Bluetooth before scanning.")
                return
            }
            discovered.clear()
            state.value = state.value.copy(isScanning = true, scanError = null, discoveredDevices = emptyList())
            scanner.startScan(scanCallback)
        } catch (e: SecurityException) {
            state.value = state.value.copy(isScanning = false, scanError = "Bluetooth permission is missing. Check app settings.")
        } catch (e: IllegalStateException) {
            state.value = state.value.copy(isScanning = false, scanError = "Bluetooth is unavailable. Turn it on and try again.")
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopScan() {
        try { bluetoothScanner?.stopScan(scanCallback) } catch (_: SecurityException) { }
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