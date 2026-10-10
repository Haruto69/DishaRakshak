package com.example.drdisplay.ble

data class BleState(

    val bluetoothAvailable: Boolean = false,
    val bluetoothEnabled: Boolean = false,

    val connected: Boolean = false,

    val sequence: Long = 0,

    val roll: Float = 0f,
    val pitch: Float = 0f,

    val velocityX: Float = 0f,
    val velocityY: Float = 0f,
    val velocityZ: Float = 0f,

    val isScanning: Boolean = false,
    val discoveredDevices: List<String> = emptyList(),
    val beltFound: Boolean = false
)