package com.example.bluetooth

/**
 * Standard Composite Bluetooth HID Descriptor supporting:
 * - Report ID 1: 5-button Mouse + X/Y relative movement + Vertical Wheel + Horizontal AC Pan
 * - Report ID 2: Standard 104-key Keyboard (8 modifier bits + reserved + 6 key rollover)
 * - Report ID 3: Consumer Control (Media & Smart TV Remote usages: Play/Pause, Vol+/-, Mute, Home, Back)
 */
object HidDescriptor {
    const val REPORT_ID_MOUSE: Int = 1
    const val REPORT_ID_KEYBOARD: Int = 2
    const val REPORT_ID_CONSUMER: Int = 3

    val COMPOSITE_DESCRIPTOR: ByteArray = byteArrayOf(
        // ==================== REPORT ID 1: MOUSE ====================
        0x05.toByte(), 0x01.toByte(), // Usage Page (Generic Desktop)
        0x09.toByte(), 0x02.toByte(), // Usage (Mouse)
        0xA1.toByte(), 0x01.toByte(), // Collection (Application)
        0x85.toByte(), REPORT_ID_MOUSE.toByte(), // Report ID (1)
        0x09.toByte(), 0x01.toByte(), // Usage (Pointer)
        0xA1.toByte(), 0x00.toByte(), // Collection (Physical)

        // 5 Mouse Buttons (Left, Right, Middle, Back, Forward)
        0x05.toByte(), 0x09.toByte(), // Usage Page (Button)
        0x19.toByte(), 0x01.toByte(), // Usage Minimum (Button 1)
        0x29.toByte(), 0x05.toByte(), // Usage Maximum (Button 5)
        0x15.toByte(), 0x00.toByte(), // Logical Minimum (0)
        0x25.toByte(), 0x01.toByte(), // Logical Maximum (1)
        0x95.toByte(), 0x05.toByte(), // Report Count (5)
        0x75.toByte(), 0x01.toByte(), // Report Size (1)
        0x81.toByte(), 0x02.toByte(), // Input (Data, Variable, Absolute)

        // 3 Padding bits
        0x95.toByte(), 0x01.toByte(), // Report Count (1)
        0x75.toByte(), 0x03.toByte(), // Report Size (3)
        0x81.toByte(), 0x03.toByte(), // Input (Constant, Variable, Absolute)

        // X, Y, Vertical Wheel (-127..127)
        0x05.toByte(), 0x01.toByte(), // Usage Page (Generic Desktop)
        0x09.toByte(), 0x30.toByte(), // Usage (X)
        0x09.toByte(), 0x31.toByte(), // Usage (Y)
        0x09.toByte(), 0x38.toByte(), // Usage (Wheel)
        0x15.toByte(), 0x81.toByte(), // Logical Minimum (-127)
        0x25.toByte(), 0x7F.toByte(), // Logical Maximum (127)
        0x75.toByte(), 0x08.toByte(), // Report Size (8)
        0x95.toByte(), 0x03.toByte(), // Report Count (3)
        0x81.toByte(), 0x06.toByte(), // Input (Data, Variable, Relative)

        // Horizontal Wheel / AC Pan (-127..127)
        0x05.toByte(), 0x0C.toByte(), // Usage Page (Consumer)
        0x0A.toByte(), 0x38.toByte(), 0x02.toByte(), // Usage (AC Pan)
        0x15.toByte(), 0x81.toByte(), // Logical Minimum (-127)
        0x25.toByte(), 0x7F.toByte(), // Logical Maximum (127)
        0x75.toByte(), 0x08.toByte(), // Report Size (8)
        0x95.toByte(), 0x01.toByte(), // Report Count (1)
        0x81.toByte(), 0x06.toByte(), // Input (Data, Variable, Relative)

        0xC0.toByte(), // End Collection (Physical)
        0xC0.toByte(), // End Collection (Application)

        // ==================== REPORT ID 2: KEYBOARD ====================
        0x05.toByte(), 0x01.toByte(), // Usage Page (Generic Desktop)
        0x09.toByte(), 0x06.toByte(), // Usage (Keyboard)
        0xA1.toByte(), 0x01.toByte(), // Collection (Application)
        0x85.toByte(), REPORT_ID_KEYBOARD.toByte(), // Report ID (2)

        // Modifier Byte (Left/Right Ctrl, Shift, Alt, GUI)
        0x05.toByte(), 0x07.toByte(), // Usage Page (Key Codes)
        0x19.toByte(), 0xE0.toByte(), // Usage Minimum (224)
        0x29.toByte(), 0xE7.toByte(), // Usage Maximum (231)
        0x15.toByte(), 0x00.toByte(), // Logical Minimum (0)
        0x25.toByte(), 0x01.toByte(), // Logical Maximum (1)
        0x75.toByte(), 0x01.toByte(), // Report Size (1)
        0x95.toByte(), 0x08.toByte(), // Report Count (8)
        0x81.toByte(), 0x02.toByte(), // Input (Data, Variable, Absolute)

        // Reserved Byte
        0x95.toByte(), 0x01.toByte(), // Report Count (1)
        0x75.toByte(), 0x08.toByte(), // Report Size (8)
        0x81.toByte(), 0x01.toByte(), // Input (Constant)

        // 6 Key Codes (6-KRO)
        0x95.toByte(), 0x06.toByte(), // Report Count (6)
        0x75.toByte(), 0x08.toByte(), // Report Size (8)
        0x15.toByte(), 0x00.toByte(), // Logical Minimum (0)
        0x25.toByte(), 0xE7.toByte(), // Logical Maximum (231)
        0x05.toByte(), 0x07.toByte(), // Usage Page (Key Codes)
        0x19.toByte(), 0x00.toByte(), // Usage Minimum (0)
        0x29.toByte(), 0xE7.toByte(), // Usage Maximum (231)
        0x81.toByte(), 0x00.toByte(), // Input (Data, Array)

        0xC0.toByte(), // End Collection (Application)

        // ==================== REPORT ID 3: CONSUMER CONTROL ====================
        0x05.toByte(), 0x0C.toByte(), // Usage Page (Consumer)
        0x09.toByte(), 0x01.toByte(), // Usage (Consumer Control)
        0xA1.toByte(), 0x01.toByte(), // Collection (Application)
        0x85.toByte(), REPORT_ID_CONSUMER.toByte(), // Report ID (3)
        0x15.toByte(), 0x00.toByte(), // Logical Minimum (0)
        0x26.toByte(), 0xFF.toByte(), 0x03.toByte(), // Logical Maximum (1023)
        0x19.toByte(), 0x00.toByte(), // Usage Minimum (0)
        0x2A.toByte(), 0xFF.toByte(), 0x03.toByte(), // Usage Maximum (1023)
        0x75.toByte(), 0x10.toByte(), // Report Size (16)
        0x95.toByte(), 0x01.toByte(), // Report Count (1)
        0x81.toByte(), 0x00.toByte(), // Input (Data, Array)
        0xC0.toByte(), // End Collection (Application)
    )

    fun buildMouseReport(
        buttonsMask: Int,
        dx: Int,
        dy: Int,
        wheelV: Int,
        wheelH: Int,
    ): ByteArray = byteArrayOf(
        (buttonsMask and 0x1F).toByte(),
        dx.coerceIn(-127, 127).toByte(),
        dy.coerceIn(-127, 127).toByte(),
        wheelV.coerceIn(-127, 127).toByte(),
        wheelH.coerceIn(-127, 127).toByte(),
    )

    fun buildKeyboardReport(
        modifiersMask: Int,
        keyCode1: Int = 0,
        keyCode2: Int = 0,
    ): ByteArray = byteArrayOf(
        (modifiersMask and 0xFF).toByte(),
        0x00,
        (keyCode1 and 0xFF).toByte(),
        (keyCode2 and 0xFF).toByte(),
        0x00,
        0x00,
        0x00,
        0x00,
    )

    fun buildConsumerReport(usage: Int): ByteArray = byteArrayOf(
        (usage and 0xFF).toByte(),
        ((usage shr 8) and 0xFF).toByte(),
    )
}
