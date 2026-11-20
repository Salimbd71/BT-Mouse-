package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot

@Composable
fun PrecisionTouchpadSection(
    hapticEnabled: Boolean,
    isLeftPressed: Boolean,
    isMiddlePressed: Boolean,
    isRightPressed: Boolean,
    onMove: (Float, Float) -> Unit,
    onTwoFingerScroll: (Float, Float) -> Unit,
    onWheelStripScroll: (Float) -> Unit,
    onPinchZoom: (Float) -> Unit,
    onLeftClick: () -> Unit,
    onRightClick: () -> Unit,
    onMiddleClick: () -> Unit,
    onLeftHoldChanged: (Boolean) -> Unit,
    onRightHoldChanged: (Boolean) -> Unit,
    onMiddleHoldChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val viewConfig = LocalViewConfiguration.current

    var activeTouchPoint by remember { mutableStateOf<Offset?>(null) }
    var activeFingerCount by remember { mutableStateOf(0) }
    var isTapDragHolding by remember { mutableStateOf(false) }
    var lastSingleTapTime by remember { mutableStateOf(0L) }
    var wheelPhaseOffset by remember { mutableFloatStateOf(0f) }

    val rippleProgress = remember { Animatable(1f) }
    var rippleOrigin by remember { mutableStateOf(Offset.Zero) }
    var rippleIsRightClick by remember { mutableStateOf(false) }

    fun triggerClickHaptic(strong: Boolean = false) {
        if (hapticEnabled) {
            haptic.performHapticFeedback(
                if (strong) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove,
            )
        }
    }

    fun triggerRipple(origin: Offset, rightClick: Boolean) {
        rippleOrigin = origin
        rippleIsRightClick = rightClick
        scope.launch {
            rippleProgress.snapTo(0f)
            rippleProgress.animateTo(1f, animationSpec = tween(durationMillis = 280))
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Touchpad + Tactile Scroll Wheel Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Main Precision Touchpad Surface
            val surfaceColor = MaterialTheme.colorScheme.surface
            val surfaceElevated = MaterialTheme.colorScheme.surfaceVariant
            val primaryColor = MaterialTheme.colorScheme.primary
            val secondaryColor = MaterialTheme.colorScheme.tertiary
            val outlineColor = MaterialTheme.colorScheme.outline
            val gridDotColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.16f)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(surfaceElevated, surfaceColor),
                        ),
                    )
                    .border(
                        width = if (activeFingerCount > 0 || isTapDragHolding) 1.5.dp else 1.dp,
                        color = if (isTapDragHolding) {
                            primaryColor
                        } else if (activeFingerCount > 0) {
                            primaryColor.copy(alpha = 0.55f)
                        } else {
                            outlineColor
                        },
                        shape = RoundedCornerShape(22.dp),
                    )
                    .testTag("precision_touchpad_surface")
                    .pointerInput(Unit) {
                        val touchSlop = viewConfig.touchSlop
                        awaitEachGesture {
                            val firstDown = awaitFirstDown(requireUnconsumed = false)
                            val downTime = System.currentTimeMillis()
                            var maxPointers = 1
                            var totalTravel = 0f
                            var prevCentroid = firstDown.position
                            var prevSpread = 0f
                            var tapDragStarted = false

                            // Check for double-tap-and-drag gesture
                            if (downTime - lastSingleTapTime <= 260L) {
                                tapDragStarted = true
                                isTapDragHolding = true
                                onLeftHoldChanged(true)
                                triggerClickHaptic(strong = true)
                            }

                            activeTouchPoint = firstDown.position
                            activeFingerCount = 1

                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val pressedPointers = event.changes.filter { it.pressed }
                                activeFingerCount = pressedPointers.size

                                if (pressedPointers.isEmpty()) {
                                    break
                                }

                                if (pressedPointers.size > maxPointers) {
                                    maxPointers = pressedPointers.size
                                    // Reset centroid baseline when second finger lands
                                    prevCentroid = pressedPointers
                                        .fold(Offset.Zero) { acc, c -> acc + c.position } /
                                        pressedPointers.size.toFloat()
                                    if (pressedPointers.size >= 2) {
                                        prevSpread = hypot(
                                            pressedPointers[0].position.x - pressedPointers[1].position.x,
                                            pressedPointers[0].position.y - pressedPointers[1].position.y,
                                        )
                                        if (tapDragStarted) {
                                            tapDragStarted = false
                                            isTapDragHolding = false
                                            onLeftHoldChanged(false)
                                        }
                                    }
                                    pressedPointers.forEach { it.consume() }
                                    continue
                                }

                                val centroid = pressedPointers
                                    .fold(Offset.Zero) { acc, c -> acc + c.position } /
                                    pressedPointers.size.toFloat()
                                activeTouchPoint = centroid

                                val dx = centroid.x - prevCentroid.x
                                val dy = centroid.y - prevCentroid.y
                                totalTravel += hypot(dx, dy)

                                if (pressedPointers.size == 1) {
                                    if (pressedPointers[0].positionChanged()) {
                                        if (totalTravel > touchSlop * 0.45f || tapDragStarted) {
                                            onMove(dx, dy)
                                        }
                                        pressedPointers[0].consume()
                                    }
                                } else if (pressedPointers.size >= 2) {
                                    val newSpread = hypot(
                                        pressedPointers[0].position.x - pressedPointers[1].position.x,
                                        pressedPointers[0].position.y - pressedPointers[1].position.y,
                                    )
                                    val spreadRatio = if (prevSpread > 24f) newSpread / prevSpread else 1f
                                    if (abs(spreadRatio - 1f) > 0.045f) {
                                        onPinchZoom(spreadRatio)
                                        prevSpread = newSpread
                                    } else if (abs(dx) > 0.2f || abs(dy) > 0.2f) {
                                        onTwoFingerScroll(dx, dy)
                                    }
                                    pressedPointers.forEach { it.consume() }
                                }

                                prevCentroid = centroid
                            }

                            val upTime = System.currentTimeMillis()
                            val duration = upTime - downTime

                            if (tapDragStarted) {
                                isTapDragHolding = false
                                onLeftHoldChanged(false)
                            } else if (totalTravel < touchSlop && duration < 260L) {
                                if (maxPointers == 1) {
                                    lastSingleTapTime = upTime
                                    onLeftClick()
                                    triggerClickHaptic()
                                    triggerRipple(prevCentroid, rightClick = false)
                                } else if (maxPointers >= 2) {
                                    onRightClick()
                                    triggerClickHaptic(strong = true)
                                    triggerRipple(prevCentroid, rightClick = true)
                                }
                            }

                            activeTouchPoint = null
                            activeFingerCount = 0
                        }
                    },
            ) {
                // Dot-matrix engineering canvas + cursor trail glow + click ripple
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val step = 26.dp.toPx()
                    var x = step * 0.7f
                    while (x < size.width - step * 0.3f) {
                        var y = step * 0.7f
                        while (y < size.height - step * 0.3f) {
                            drawCircle(
                                color = gridDotColor,
                                radius = 1.35.dp.toPx(),
                                center = Offset(x, y),
                            )
                            y += step
                        }
                        x += step
                    }

                    // Active finger glow
                    activeTouchPoint?.let { pos ->
                        val glowRadius = if (activeFingerCount >= 2) 72.dp.toPx() else 56.dp.toPx()
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    primaryColor.copy(alpha = 0.24f),
                                    Color.Transparent,
                                ),
                                center = pos,
                                radius = glowRadius,
                            ),
                            radius = glowRadius,
                            center = pos,
                        )
                        drawCircle(
                            color = primaryColor.copy(alpha = 0.55f),
                            radius = 6.dp.toPx(),
                            center = pos,
                        )
                    }

                    // Tap click ripple
                    val p = rippleProgress.value
                    if (p < 0.99f) {
                        val rippleColor = if (rippleIsRightClick) secondaryColor else primaryColor
                        drawCircle(
                            color = rippleColor.copy(alpha = (1f - p) * 0.55f),
                            radius = (12.dp.toPx() + 44.dp.toPx() * p),
                            center = rippleOrigin,
                        )
                    }
                }

                // Top-left active gesture badge
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.TouchApp,
                        contentDescription = null,
                        tint = if (activeFingerCount > 0) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = when {
                            isTapDragHolding -> "DRAG LOCKED"
                            activeFingerCount >= 2 -> "2-FINGER SCROLL / ZOOM"
                            activeFingerCount == 1 -> "TRACKING"
                            else -> "TOUCHPAD"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (activeFingerCount > 0 || isTapDragHolding) {
                            primaryColor
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                        },
                    )
                }

                // Bottom gesture hint
                Text(
                    text = stringResource(R.string.hint_touchpad),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }

            // Dedicated Tactile Vertical Scroll Wheel Strip
            ScrollWheelStrip(
                hapticEnabled = hapticEnabled,
                phaseOffset = wheelPhaseOffset,
                onScrollDelta = { dy ->
                    wheelPhaseOffset = (wheelPhaseOffset + dy) % 28f
                    onWheelStripScroll(dy)
                },
                modifier = Modifier
                    .width(54.dp)
                    .fillMaxHeight(),
            )
        }

        // Physical-Feel Mouse Buttons Deck (Left / Middle / Right)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MouseHardwareButton(
                label = stringResource(R.string.btn_left_click),
                subLabel = "HOLD / DRAG",
                isPressed = isLeftPressed,
                onPressChanged = { pressed ->
                    if (pressed) triggerClickHaptic()
                    onLeftHoldChanged(pressed)
                },
                modifier = Modifier
                    .weight(1.35f)
                    .fillMaxHeight()
                    .testTag("left_mouse_button"),
            )

            MouseHardwareButton(
                label = stringResource(R.string.btn_middle_click),
                subLabel = "WHEEL",
                isPressed = isMiddlePressed,
                onPressChanged = { pressed ->
                    if (pressed) triggerClickHaptic()
                    onMiddleHoldChanged(pressed)
                },
                modifier = Modifier
                    .weight(0.7f)
                    .fillMaxHeight()
                    .testTag("middle_mouse_button"),
            )

            MouseHardwareButton(
                label = stringResource(R.string.btn_right_click),
                subLabel = "MENU",
                isPressed = isRightPressed,
                onPressChanged = { pressed ->
                    if (pressed) triggerClickHaptic(strong = true)
                    onRightHoldChanged(pressed)
                },
                modifier = Modifier
                    .weight(1.35f)
                    .fillMaxHeight()
                    .testTag("right_mouse_button"),
            )
        }
    }
}

@Composable
private fun ScrollWheelStrip(
    hapticEnabled: Boolean,
    phaseOffset: Float,
    onScrollDelta: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    var isDragging by remember { mutableStateOf(false) }
    var hapticAccumulator by remember { mutableFloatStateOf(0f) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = if (isDragging) 1.5.dp else 1.dp,
                color = if (isDragging) primaryColor else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(18.dp),
            )
            .testTag("scroll_wheel_strip")
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = {
                        isDragging = true
                        hapticAccumulator = 0f
                    },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        onScrollDelta(dragAmount)
                        hapticAccumulator += abs(dragAmount)
                        if (hapticEnabled && hapticAccumulator >= 30f) {
                            hapticAccumulator = 0f
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val spacing = 14.dp.toPx()
            val startY = ((phaseOffset % spacing) + spacing) % spacing
            var y = startY
            while (y < size.height - 8.dp.toPx()) {
                val centerFactor = 1f - abs((y / size.height) - 0.5f) * 1.4f
                val lineHalfWidth = (size.width * 0.24f) * centerFactor.coerceIn(0.45f, 1f)
                drawLine(
                    color = if (isDragging) primaryColor.copy(alpha = 0.7f) else tickColor,
                    start = Offset(size.width / 2f - lineHalfWidth, y),
                    end = Offset(size.width / 2f + lineHalfWidth, y),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                y += spacing
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 6.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.SwapVert,
                contentDescription = "Scroll wheel",
                tint = if (isDragging) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(4.dp)
                    .size(16.dp),
            )
        }
    }
}

@Composable
private fun MouseHardwareButton(
    label: String,
    subLabel: String,
    isPressed: Boolean,
    onPressChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (isPressed) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val borderColor = if (isPressed) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    val contentColor = if (isPressed) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onPressChanged(true)
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        if (event.changes.none { it.pressed }) break
                    }
                    onPressChanged(false)
                }
            },
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        border = BorderStroke(if (isPressed) 1.5.dp else 1.dp, borderColor),
        tonalElevation = if (isPressed) 6.dp else 1.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = subLabel,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.65f),
                maxLines = 1,
            )
        }
    }
}
