package com.example.bluetooth

import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sign

data class HidMotionPacket(
    val dx: Int,
    val dy: Int,
    val wheelV: Int,
    val wheelH: Int,
) {
    val isZero: Boolean get() = dx == 0 && dy == 0 && wheelV == 0 && wheelH == 0
}

/**
 * High-frequency sub-pixel motion coalescing & pointer acceleration engine.
 *
 * Accumulates fractional pixel deltas across UI pointer events and emits
 * frame-paced, clamped HID deltas (-127..127) without dropping sub-pixel precision
 * or flooding the Bluetooth L2CAP interrupt channel.
 */
class HidMotionEngine {
    private val lock = Any()
    private var accX = 0f
    private var accY = 0f
    private var accWheelV = 0f
    private var accWheelH = 0f

    fun enqueuePointerDelta(
        rawDx: Float,
        rawDy: Float,
        sensitivity: Float,
        accelerationEnabled: Boolean,
    ) {
        val distance = hypot(rawDx, rawDy)
        if (distance <= 0.001f) return

        val accelGain = if (accelerationEnabled) {
            // Smooth power-assisted acceleration curve: precise at slow drag, fast across 4K TV
            1f + (distance * 0.032f).coerceAtMost(2.6f)
        } else {
            1f
        }
        val scale = sensitivity * accelGain
        synchronized(lock) {
            accX += rawDx * scale
            accY += rawDy * scale
        }
    }

    fun enqueueScrollDelta(
        rawDx: Float,
        rawDy: Float,
        scrollSensitivity: Float,
        naturalScroll: Boolean,
    ) {
        // Standard wheel polarity: positive = scroll up, negative = scroll down
        val polarity = if (naturalScroll) 1f else -1f
        val scale = scrollSensitivity * 0.14f
        synchronized(lock) {
            accWheelV += rawDy * scale * polarity
            accWheelH += rawDx * scale * -polarity
        }
    }

    fun enqueueWheelNotches(verticalNotches: Float, horizontalNotches: Float = 0f) {
        synchronized(lock) {
            accWheelV += verticalNotches
            accWheelH += horizontalNotches
        }
    }

    /**
     * Drains up to one clamped HID mouse packet (-127..127 per axis), preserving
     * any fractional sub-pixel remainder for the next tick.
     */
    fun drainNextPacket(): HidMotionPacket? {
        synchronized(lock) {
            val rawIntX = accX.toInt()
            val rawIntY = accY.toInt()
            val rawIntWv = accWheelV.roundToInt().let {
                if (it == 0 && kotlin.math.abs(accWheelV) >= 0.75f) accWheelV.sign.toInt() else it
            }
            val rawIntWh = accWheelH.roundToInt().let {
                if (it == 0 && kotlin.math.abs(accWheelH) >= 0.75f) accWheelH.sign.toInt() else it
            }

            if (rawIntX == 0 && rawIntY == 0 && rawIntWv == 0 && rawIntWh == 0) {
                return null
            }

            val dx = rawIntX.coerceIn(-127, 127)
            val dy = rawIntY.coerceIn(-127, 127)
            val wv = rawIntWv.coerceIn(-127, 127)
            val wh = rawIntWh.coerceIn(-127, 127)

            accX -= dx
            accY -= dy
            accWheelV -= wv
            accWheelH -= wh

            return HidMotionPacket(dx = dx, dy = dy, wheelV = wv, wheelH = wh)
        }
    }

    fun hasPendingMotion(): Boolean = synchronized(lock) {
        kotlin.math.abs(accX) >= 1f ||
            kotlin.math.abs(accY) >= 1f ||
            kotlin.math.abs(accWheelV) >= 0.75f ||
            kotlin.math.abs(accWheelH) >= 0.75f
    }

    fun reset() {
        synchronized(lock) {
            accX = 0f
            accY = 0f
            accWheelV = 0f
            accWheelH = 0f
        }
    }
}
