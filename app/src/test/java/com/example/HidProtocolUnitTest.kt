package com.example

import com.example.bluetooth.HidDescriptor
import com.example.bluetooth.HidKeyMap
import com.example.bluetooth.HidMotionEngine
import com.example.wifi.WifiProtocol
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

    @Test
    fun `wifi udp protocol builders encode header and payloads accurately`() {
        val move = WifiProtocol.buildMouseMovePacket(dx = 25, dy = -12, wheelV = 1, wheelH = -1)
        assertEquals(7, move.size)
        assertEquals(WifiProtocol.MAGIC_0, move[0])
        assertEquals(WifiProtocol.MAGIC_1, move[1])
        assertEquals(WifiProtocol.PKT_MOUSE_MOVE, move[2])
        assertEquals(25.toByte(), move[3])
        assertEquals((-12).toByte(), move[4])
        assertEquals(1.toByte(), move[5])
        assertEquals((-1).toByte(), move[6])

        val pinch = WifiProtocol.buildPinchZoomPacket(wheelStep = 1)
        assertEquals(4, pinch.size)
        assertEquals(WifiProtocol.PKT_PINCH_ZOOM, pinch[2])
        assertEquals(1.toByte(), pinch[3])

        val btn = WifiProtocol.buildMouseButtonPacket(seq = 7, buttonMask = HidKeyMap.MOUSE_RIGHT, action = WifiProtocol.BTN_ACTION_CLICK)
        assertEquals(6, btn.size)
        assertEquals(WifiProtocol.PKT_MOUSE_BUTTON, btn[2])
        assertEquals(7.toByte(), btn[3])
        assertEquals(HidKeyMap.MOUSE_RIGHT.toByte(), btn[4])

        val unicode = WifiProtocol.buildUnicodeTextPacket(seq = 9, text = "হ্যালো")
        assertEquals(WifiProtocol.PKT_UNICODE_TEXT, unicode[2])
        assertEquals(9.toByte(), unicode[3])
        val utfLen = ((unicode[4].toInt() and 0xFF) shl 8) or (unicode[5].toInt() and 0xFF)
        assertEquals("হ্যালো".toByteArray(Charsets.UTF_8).size, utfLen)
        assertEquals(6 + utfLen, unicode.size)

        val consumer = WifiProtocol.buildConsumerKeyPacket(seq = 12, usage = HidKeyMap.CONSUMER_VOLUME_UP)
        assertEquals(6, consumer.size)
        assertEquals(WifiProtocol.PKT_CONSUMER_KEY, consumer[2])
        assertEquals((HidKeyMap.CONSUMER_VOLUME_UP and 0xFF).toByte(), consumer[4])
        assertEquals(((HidKeyMap.CONSUMER_VOLUME_UP shr 8) and 0xFF).toByte(), consumer[5])
    }
}
