package com.example.drdisplay

import com.example.drdisplay.ble.decodeFootTestPacket
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FootTestPacketTest {
    @Test fun decodesUnsignedCountersAndValues() {
        val bytes = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN)
            .put(0xD1.toByte()).put(2.toByte()).put(3.toByte()).put(1.toByte())
            .putInt(-1).putInt(-2).putFloat(1.25f).putFloat(-2.5f).array()
        val packet = decodeFootTestPacket(bytes)!!
        assertEquals("right", packet.foot)
        assertEquals(4294967295L, packet.sequence)
        assertEquals(4294967294L, packet.sampleMicros)
        assertEquals("velocity_x_mps", packet.name1)
        assertEquals(-2.5f, packet.value2, 0f)
    }

    @Test fun rejectsInvalidOrTruncatedFrames() {
        assertNull(decodeFootTestPacket(byteArrayOf(0xD1.toByte())))
        val bytes = ByteArray(20)
        bytes[0] = 0xD1.toByte()
        bytes[1] = 1
        bytes[2] = 5
        assertNull(decodeFootTestPacket(bytes))
    }
}
