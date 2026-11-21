package com.slm.bluetooth

import android.view.KeyEvent

data class HidKeyStroke(
    val modifiers: Int = 0,
    val keyCode: Int,
)

object HidKeyMap {
    // Mouse button bitmasks
    const val MOUSE_LEFT = 0x01
    const val MOUSE_RIGHT = 0x02
    const val MOUSE_MIDDLE = 0x04
    const val MOUSE_BACK = 0x08
    const val MOUSE_FORWARD = 0x10

    // Keyboard modifier bitmasks
    const val MOD_NONE = 0x00
    const val MOD_LEFT_CTRL = 0x01
    const val MOD_LEFT_SHIFT = 0x02
    const val MOD_LEFT_ALT = 0x04
    const val MOD_LEFT_GUI = 0x08 // Windows / Command key
    const val MOD_RIGHT_CTRL = 0x10
    const val MOD_RIGHT_SHIFT = 0x20
    const val MOD_RIGHT_ALT = 0x40
    const val MOD_RIGHT_GUI = 0x80

    // Standard HID Keyboard Usage IDs (Page 0x07)
    const val KEY_NONE = 0x00
    const val KEY_A = 0x04
    const val KEY_C = 0x06
    const val KEY_D = 0x07
    const val KEY_F = 0x09
    const val KEY_V = 0x19
    const val KEY_W = 0x1A
    const val KEY_X = 0x1B
    const val KEY_Z = 0x1D
    const val KEY_ENTER = 0x28
    const val KEY_ESC = 0x29
    const val KEY_BACKSPACE = 0x2A
    const val KEY_TAB = 0x2B
    const val KEY_SPACE = 0x2C
    const val KEY_CAPS_LOCK = 0x39
    const val KEY_F1 = 0x3A
    const val KEY_F2 = 0x3B
    const val KEY_F3 = 0x3C
    const val KEY_F4 = 0x3D
    const val KEY_F5 = 0x3E
    const val KEY_F6 = 0x3F
    const val KEY_F7 = 0x40
    const val KEY_F8 = 0x41
    const val KEY_F9 = 0x42
    const val KEY_F10 = 0x43
    const val KEY_F11 = 0x44
    const val KEY_F12 = 0x45
    const val KEY_PRINT_SCREEN = 0x46
    const val KEY_HOME = 0x4A
    const val KEY_PAGE_UP = 0x4B
    const val KEY_DELETE = 0x4C
    const val KEY_END = 0x4D
    const val KEY_PAGE_DOWN = 0x4E
    const val KEY_RIGHT = 0x4F
    const val KEY_LEFT = 0x50
    const val KEY_DOWN = 0x51
    const val KEY_UP = 0x52

    // Consumer Control Usage IDs (Page 0x0C) for Smart TVs & Windows Media
    const val CONSUMER_POWER = 0x0030
    const val CONSUMER_MENU = 0x0040
    const val CONSUMER_NEXT_TRACK = 0x00B5
    const val CONSUMER_PREV_TRACK = 0x00B6
    const val CONSUMER_STOP = 0x00B7
    const val CONSUMER_PLAY_PAUSE = 0x00CD
    const val CONSUMER_MUTE = 0x00E2
    const val CONSUMER_VOLUME_UP = 0x00E9
    const val CONSUMER_VOLUME_DOWN = 0x00EA
    const val CONSUMER_AC_SEARCH = 0x0221
    const val CONSUMER_AC_HOME = 0x0223
    const val CONSUMER_AC_BACK = 0x0224

    fun fromChar(ch: Char): HidKeyStroke? {
        if (ch in 'a'..'z') {
            return HidKeyStroke(MOD_NONE, 0x04 + (ch - 'a'))
        }
        if (ch in 'A'..'Z') {
            return HidKeyStroke(MOD_LEFT_SHIFT, 0x04 + (ch - 'A'))
        }
        if (ch in '1'..'9') {
            return HidKeyStroke(MOD_NONE, 0x1E + (ch - '1'))
        }
        return when (ch) {
            '0' -> HidKeyStroke(MOD_NONE, 0x27)
            '\n', '\r' -> HidKeyStroke(MOD_NONE, KEY_ENTER)
            '\t' -> HidKeyStroke(MOD_NONE, KEY_TAB)
            ' ' -> HidKeyStroke(MOD_NONE, KEY_SPACE)
            '-' -> HidKeyStroke(MOD_NONE, 0x2D)
            '=' -> HidKeyStroke(MOD_NONE, 0x2E)
            '[' -> HidKeyStroke(MOD_NONE, 0x2F)
            ']' -> HidKeyStroke(MOD_NONE, 0x30)
            '\\' -> HidKeyStroke(MOD_NONE, 0x31)
            ';' -> HidKeyStroke(MOD_NONE, 0x33)
            '\'' -> HidKeyStroke(MOD_NONE, 0x34)
            '`' -> HidKeyStroke(MOD_NONE, 0x35)
            ',' -> HidKeyStroke(MOD_NONE, 0x36)
            '.' -> HidKeyStroke(MOD_NONE, 0x37)
            '/' -> HidKeyStroke(MOD_NONE, 0x38)
            '!' -> HidKeyStroke(MOD_LEFT_SHIFT, 0x1E)
            '@' -> HidKeyStroke(MOD_LEFT_SHIFT, 0x1F)
            '#' -> HidKeyStroke(MOD_LEFT_SHIFT, 0x20)
            '$' -> HidKeyStroke(MOD_LEFT_SHIFT, 0x21)
            '%' -> HidKeyStroke(MOD_LEFT_SHIFT, 0x22)
            '^' -> HidKeyStroke(MOD_LEFT_SHIFT, 0x23)
            '&' -> HidKeyStroke(MOD_LEFT_SHIFT, 0x24)
            '*' -> HidKeyStroke(MOD_LEFT_SHIFT, 0x25)
            '(' -> HidKeyStroke(MOD_LEFT_SHIFT, 0x26)
            ')' -> HidKeyStroke(MOD_LEFT_SHIFT, 0x27)
            '_' -> HidKeyStroke(MOD_LEFT_SHIFT, 0x2D)
            '+' -> HidKeyStroke(MOD_LEFT_SHIFT, 0x2E)
            '{' -> HidKeyStroke(MOD_LEFT_SHIFT, 0x2F)
            '}' -> HidKeyStroke(MOD_LEFT_SHIFT, 0x30)
            '|' -> HidKeyStroke(MOD_LEFT_SHIFT, 0x31)
            ':' -> HidKeyStroke(MOD_LEFT_SHIFT, 0x33)
            '"' -> HidKeyStroke(MOD_LEFT_SHIFT, 0x34)
            '~' -> HidKeyStroke(MOD_LEFT_SHIFT, 0x35)
            '<' -> HidKeyStroke(MOD_LEFT_SHIFT, 0x36)
            '>' -> HidKeyStroke(MOD_LEFT_SHIFT, 0x37)
            '?' -> HidKeyStroke(MOD_LEFT_SHIFT, 0x38)
            else -> null
        }
    }

    fun fromAndroidKeyCode(androidKeyCode: Int): Int? = when (androidKeyCode) {
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> KEY_ENTER
        KeyEvent.KEYCODE_DEL -> KEY_BACKSPACE
        KeyEvent.KEYCODE_FORWARD_DEL -> KEY_DELETE
        KeyEvent.KEYCODE_TAB -> KEY_TAB
        KeyEvent.KEYCODE_SPACE -> KEY_SPACE
        KeyEvent.KEYCODE_ESCAPE -> KEY_ESC
        KeyEvent.KEYCODE_DPAD_UP -> KEY_UP
        KeyEvent.KEYCODE_DPAD_DOWN -> KEY_DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> KEY_LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> KEY_RIGHT
        KeyEvent.KEYCODE_MOVE_HOME -> KEY_HOME
        KeyEvent.KEYCODE_MOVE_END -> KEY_END
        KeyEvent.KEYCODE_PAGE_UP -> KEY_PAGE_UP
        KeyEvent.KEYCODE_PAGE_DOWN -> KEY_PAGE_DOWN
        in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> 0x04 + (androidKeyCode - KeyEvent.KEYCODE_A)
        in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9 -> 0x1E + (androidKeyCode - KeyEvent.KEYCODE_1)
        KeyEvent.KEYCODE_0 -> 0x27
        else -> null
    }
}
