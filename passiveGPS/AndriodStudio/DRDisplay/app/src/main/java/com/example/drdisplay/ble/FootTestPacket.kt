package com.example.drdisplay.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class FootTestPacket(
    val foot: String, val page: Int, val still: Int, val sequence: Long, val sampleMicros: Long,
    val name1: String, val value1: Float, val name2: String, val value2: Float
)

fun decodeFootTestPacket(data: ByteArray): FootTestPacket? {
    if (data.size != 20 || (data[0].toInt() and 0xff) != 0xD1) return null
    val foot = when (data[1].toInt() and 0xff) { 1 -> "left"; 2 -> "right"; else -> return null }
    val page = data[2].toInt() and 0xff
    val still = data[3].toInt() and 0xff
    if (page !in 0..4 || still !in 0..1) return null
    val input = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
    val sequence = input.getInt(4).toLong() and 0xffffffffL
    val sampleMicros = input.getInt(8).toLong() and 0xffffffffL
    val first = input.getFloat(12)
    val second = input.getFloat(16)
    if (!first.isFinite() || !second.isFinite()) return null
    val names = listOf("accel_x_g" to "accel_y_g", "accel_z_g" to "gyro_x_dps",
        "gyro_y_dps" to "gyro_z_dps", "velocity_x_mps" to "velocity_y_mps",
        "velocity_z_mps" to "yaw_deg")[page]
    return FootTestPacket(foot, page, still, sequence, sampleMicros, names.first, first, names.second, second)
}
