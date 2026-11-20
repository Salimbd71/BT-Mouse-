package com.example

import com.example.bluetooth.HidDescriptor
import com.example.bluetooth.HidKeyMap
import com.example.bluetooth.HidMotionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HidProtocolUnitTest {

    @Test
    fun `sub-pixel motion accumulates without dropping slow movement`() {
        val engine = HidMotionEngine()
        // Send three 0.4px movements -> sum = 1.2px -> drains 1px and keeps 0.2px remainder
        engine.enqueuePointerDelta(0.4f, 0f, sensitivity = 1.0f, accelerationEnabled = false)
        assertNull(engine.drainNextPacket())
        engine.enqueuePointerDelta(0.4f, 0f, sensitivity = 1.0f, accelerationEnabled = false)
        assertNull(engine.drainNextPacket())
        engine.enqueuePointerDelta(0.4f, 0f, sensitivity = 1.0f, accelerationEnabled = false)

        val packet = engine.drainNextPacket()
        assertNotNull(packet)
        assertEquals(1, packet!!.dx)
        assertEquals(0, packet.dy)
    }

    @Test
    fun `large fast flick splits cleanly into 127px HID chunks`() {
        val engine = HidMotionEngine()
        engine.enqueuePointerDelta(300f, 0f, sensitivity = 1.0f, accelerationEnabled = false)

        val first = engine.drainNextPacket()
        val second = engine.drainNextPacket()
        val third = engine.drainNextPacket()

        assertEquals(127, first?.dx)
        assertEquals(127, second?.dx)
        assertEquals(46, third?.dx)
        assertNull(engine.drainNextPacket())
    }

    @Test
    fun `key map encodes lowercase uppercase and shifted symbols`() {
        val lowerA = HidKeyMap.fromChar('a')
        assertNotNull(lowerA)
        assertEquals(HidKeyMap.MOD_NONE, lowerA!!.modifiers)
        assertEquals(HidKeyMap.KEY_A, lowerA.keyCode)

        val upperA = HidKeyMap.fromChar('A')
        assertNotNull(upperA)
        assertEquals(HidKeyMap.MOD_LEFT_SHIFT, upperA!!.modifiers)
        assertEquals(HidKeyMap.KEY_A, upperA.keyCode)

        val atSign = HidKeyMap.fromChar('@')
        assertNotNull(atSign)
        assertEquals(HidKeyMap.MOD_LEFT_SHIFT, atSign!!.modifiers)
    }

    @Test
    fun `hid report builders produce exact byte sizes`() {
        val mouse = HidDescriptor.buildMouseReport(HidKeyMap.MOUSE_LEFT, 10, -5, 1, 0)
        assertEquals(5, mouse.size)
        assertEquals(1.toByte(), mouse[0])
        assertEquals(10.toByte(), mouse[1])
        assertEquals((-5).toByte(), mouse[2])

        val keyboard = HidDescriptor.buildKeyboardReport(HidKeyMap.MOD_LEFT_CTRL, HidKeyMap.KEY_C)
        assertEquals(8, keyboard.size)
        assertEquals(HidKeyMap.MOD_LEFT_CTRL.toByte(), keyboard[0])
        assertEquals(HidKeyMap.KEY_C.toByte(), keyboard[2])

        val consumer = HidDescriptor.buildConsumerReport(HidKeyMap.CONSUMER_AC_HOME)
        assertEquals(2, consumer.size)
        assertTrue(HidDescriptor.COMPOSITE_DESCRIPTOR.isNotEmpty())
    }
}
