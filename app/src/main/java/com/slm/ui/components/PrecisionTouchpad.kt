package com.slm.ui.components

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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import com.slm.R
import com.slm.data.MouseButtonsPosition
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot

@Composable
fun PrecisionTouchpadSection(
    hapticEnabled: Boolean,
    buttonsPosition: MouseButtonsPosition = MouseButtonsPosition.BOTTOM,
    leftHandedMode: Boolean = false,
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
    var pendingSingleTapJob by remember { mutableStateOf<Job?>(null) }
    var wheelPhaseOffset by remember { mutableFloatStateOf(0f) }

    val currentOnLeftClick by rememberUpdatedState(onLeftClick)
    val currentOnRightClick by rememberUpdatedState(onRightClick)
    val currentOnLeftHoldChanged by rememberUpdatedState(onLeftHoldChanged)
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnTwoFingerScroll by rememberUpdatedState(onTwoFingerScroll)
    val currentOnPinchZoom by rememberUpdatedState(onPinchZoom)

    DisposableEffect(Unit) {
        onDispose {
            pendingSingleTapJob?.cancel()
            if (isTapDragHolding) {
                currentOnLeftHoldChanged(false)
            }
        }
    }

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

    val mouseButtonsDeck: @Composable () -> Unit = {
        val leftSlotIsRightClick = leftHandedMode
        val firstLabel = stringResource(if (leftSlotIsRightClick) R.string.btn_right_click else R.string.btn_left_click)
        val firstSub = if (leftSlotIsRightClick) "MENU" else "HOLD / DRAG"
        val firstPressed = if (leftSlotIsRightClick) isRightPressed else isLeftPressed
        val firstHandler: (Boolean) -> Unit = { pressed ->
            if (pressed) triggerClickHaptic(strong = leftSlotIsRightClick)
            if (leftSlotIsRightClick) onRightHoldChanged(pressed) else onLeftHoldChanged(pressed)
        }
        val firstTag = if (leftSlotIsRightClick) "right_mouse_button" else "left_mouse_button"

        val secondLabel = stringResource(if (leftSlotIsRightClick) R.string.btn_left_click else R.string.btn_right_click)
        val secondSub = if (leftSlotIsRightClick) "HOLD / DRAG" else "MENU"
        val secondPressed = if (leftSlotIsRightClick) isLeftPressed else isRightPressed
        val secondHandler: (Boolean) -> Unit = { pressed ->
            if (pressed) triggerClickHaptic(strong = !leftSlotIsRightClick)
            if (leftSlotIsRightClick) onLeftHoldChanged(pressed) else onRightHoldChanged(pressed)
        }
        val secondTag = if (leftSlotIsRightClick) "left_mouse_button" else "right_mouse_button"

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MouseHardwareButton(
                label = firstLabel,
                subLabel = firstSub,
                isPressed = firstPressed,
                onPressChanged = firstHandler,
                modifier = Modifier
                    .weight(1.35f)
                    .fillMaxHeight()
                    .testTag(firstTag),
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
                label = secondLabel,
                subLabel = secondSub,
                isPressed = secondPressed,
                onPressChanged = secondHandler,
                modifier = Modifier
                    .weight(1.35f)
                    .fillMaxHeight()
                    .testTag(secondTag),
            )
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (buttonsPosition == MouseButtonsPosition.TOP) {
            mouseButtonsDeck()
        }

        // Touchpad + Tactile Scroll Wheel Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (leftHandedMode) {
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
                            var hasCrossedMoveSlop = false
                            var prevCentroid = firstDown.position
                            var prevSpread = 0f
                            var smoothCarryX = 0f
                            var smoothCarryY = 0f

                            // Smart laptop-style tap-drag state:
                            // Cancel pending single tap click if 2nd finger lands quickly so Windows does not
                            // interpret Tap-1 + Down-2 as WM_NCLBUTTONDBLCLK (maximize) on title bars.
                            val pendingWasWaiting = pendingSingleTapJob?.isActive == true
                            val withinDoubleTapWindow = (downTime - lastSingleTapTime) <= 290L
                            val isTapDragCandidate = pendingWasWaiting || withinDoubleTapWindow
                            if (isTapDragCandidate) {
                                pendingSingleTapJob?.cancel()
                                pendingSingleTapJob = null
                            }

                            var tapDragEngaged = false
                            var holdTimerJob: Job? = null

                            fun engageTapDrag() {
                                if (!tapDragEngaged) {
                                    tapDragEngaged = true
                                    isTapDragHolding = true
                                    currentOnLeftHoldChanged(true)
                                    triggerClickHaptic(strong = true)
                                }
                            }

                            if (isTapDragCandidate) {
                                holdTimerJob = scope.launch {
                                    delay(165L)
                                    if (!tapDragEngaged && activeFingerCount == 1) {
                                        engageTapDrag()
                                    }
                                }
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
                                    holdTimerJob?.cancel()
                                    prevCentroid = pressedPointers
                                        .fold(Offset.Zero) { acc, c -> acc + c.position } /
                                        pressedPointers.size.toFloat()
                                    if (pressedPointers.size >= 2) {
                                        prevSpread = hypot(
                                            pressedPointers[0].position.x - pressedPointers[1].position.x,
                                            pressedPointers[0].position.y - pressedPointers[1].position.y,
                                        )
                                        if (tapDragEngaged) {
                                            tapDragEngaged = false
                                            isTapDragHolding = false
                                            currentOnLeftHoldChanged(false)
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
                                val stepDist = hypot(dx, dy)
                                totalTravel += stepDist

                                if (pressedPointers.size == 1) {
                                    if (pressedPointers[0].positionChanged()) {
                                        if (!hasCrossedMoveSlop && totalTravel > touchSlop * 0.38f) {
                                            hasCrossedMoveSlop = true
                                            smoothCarryX = 0f
                                            smoothCarryY = 0f
                                            if (isTapDragCandidate) {
                                                holdTimerJob?.cancel()
                                                engageTapDrag()
                                            }
                                        } else if (hasCrossedMoveSlop || tapDragEngaged) {
                                            // Residual-conserving EMA smoothing eliminates micro-tremor without losing travel
                                            val alpha = when {
                                                stepDist < 1.2f -> 0.48f
                                                stepDist < 5.0f -> 0.66f
                                                else -> 0.85f
                                            }
                                            val sendX = (dx + smoothCarryX) * alpha
                                            val sendY = (dy + smoothCarryY) * alpha
                                            smoothCarryX = (dx + smoothCarryX) - sendX
                                            smoothCarryY = (dy + smoothCarryY) - sendY
                                            currentOnMove(sendX, sendY)
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
                                        currentOnPinchZoom(spreadRatio)
                                        prevSpread = newSpread
                                    } else if (abs(dx) > 0.2f || abs(dy) > 0.2f) {
                                        currentOnTwoFingerScroll(dx, dy)
                                    }
                                    pressedPointers.forEach { it.consume() }
                                }

                                prevCentroid = centroid
                            }

                            holdTimerJob?.cancel()
                            // Flush any tiny remaining smoothed fractional delta cleanly when drag ends
                            if (hasCrossedMoveSlop && (abs(smoothCarryX) > 0.25f || abs(smoothCarryY) > 0.25f)) {
                                currentOnMove(smoothCarryX, smoothCarryY)
                            }

                            val upTime = System.currentTimeMillis()
                            val duration = upTime - downTime

                            if (tapDragEngaged) {
                                isTapDragHolding = false
                                currentOnLeftHoldChanged(false)
                                lastSingleTapTime = 0L
                            } else if (totalTravel < touchSlop && duration < 280L) {
                                if (maxPointers == 1) {
                                    if (isTapDragCandidate) {
                                        // User tapped twice quickly without dragging -> execute genuine double-click
                                        lastSingleTapTime = 0L
                                        triggerRipple(prevCentroid, rightClick = false)
                                        triggerClickHaptic()
                                        if (pendingWasWaiting) {
                                            currentOnLeftClick()
                                            scope.launch {
                                                delay(55L)
                                                currentOnLeftClick()
                                            }
                                        } else {
                                            currentOnLeftClick()
                                        }
                                    } else {
                                        // Wait 195ms before firing Click 1 so double-tap-drag can intercept cleanly
                                        lastSingleTapTime = upTime
                                        triggerRipple(prevCentroid, rightClick = false)
                                        pendingSingleTapJob = scope.launch {
                                            delay(195L)
                                            currentOnLeftClick()
                                            triggerClickHaptic()
                                        }
                                    }
                                } else if (maxPointers >= 2) {
                                    lastSingleTapTime = 0L
                                    currentOnRightClick()
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

            if (!leftHandedMode) {
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
        }

        if (buttonsPosition == MouseButtonsPosition.BOTTOM) {
            mouseButtonsDeck()
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
