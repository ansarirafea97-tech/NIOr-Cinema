package com.example.util

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role

private val IosPressEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

/**
 * Provides an ultra-smooth, clearly visible iOS/Infuse-style tactile press animation.
 * When touched or tapped, immediately scales down to [pressedScale] (0.95f) and smoothly
 * returns to 1.0f on release with a fast, fluid deceleration curve without bounce.
 */
fun Modifier.infusePressEffect(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.95f,
    enabled: Boolean = true
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressedScale else 1f,
        animationSpec = tween(
            durationMillis = if (isPressed) 100 else 180,
            easing = IosPressEasing
        ),
        label = "infuse_press_effect"
    )
    val alpha by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.88f else 1f,
        animationSpec = tween(
            durationMillis = if (isPressed) 80 else 160,
            easing = FastOutSlowInEasing
        ),
        label = "infuse_press_alpha"
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
        this.alpha = alpha
    }
}

/**
 * Clickable modifier combined with authentic iOS Infuse visible subtle scale press feedback.
 * Works seamlessly on quick taps and long presses without delaying navigation or click events.
 * Directly integrates with Compose interaction source for zero scroll interference in lists/grids.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.infuseClickable(
    interactionSource: MutableInteractionSource? = null,
    enabled: Boolean = true,
    pressedScale: Float = 0.95f,
    role: Role? = Role.Button,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier = composed {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    val isPressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressedScale else 1f,
        animationSpec = tween(
            durationMillis = if (isPressed) 100 else 200,
            easing = IosPressEasing
        ),
        label = "infuse_clickable_scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.88f else 1f,
        animationSpec = tween(
            durationMillis = if (isPressed) 80 else 160,
            easing = FastOutSlowInEasing
        ),
        label = "infuse_clickable_alpha"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
        }
        .then(
            if (onLongClick != null) {
                Modifier.combinedClickable(
                    interactionSource = source,
                    indication = null,
                    enabled = enabled,
                    role = role,
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    },
                    onClick = onClick
                )
            } else {
                Modifier.clickable(
                    interactionSource = source,
                    indication = null,
                    enabled = enabled,
                    role = role,
                    onClick = onClick
                )
            }
        )
}

