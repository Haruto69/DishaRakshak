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
    val beltFound: Boolean = false,

    val x: Double = 0.0,
    val y: Double = 0.0,
    val z: Double = 0.0,
    val posX: Float = 0f,
    val posY: Float = 0f,
    val posZ: Float = 0f,

    val curX: Float = 0f,
    val curY: Float = 0f,

    val homeX: Float = 0f,
    val homeY: Float = 0f,

    val distanceToHome: Float = 0f,
    val bearingToHome: Float = 0f
)