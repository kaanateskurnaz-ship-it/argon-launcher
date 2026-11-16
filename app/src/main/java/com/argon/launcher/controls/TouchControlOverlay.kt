package com.argon.launcher.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Renders the customizable on-screen control buttons over the game surface.
 *
 * - Play mode: buttons send [onButtonDown]/[onButtonUp] for their key code, and the whole
 *   overlay is hidden whenever [visible] is false (driven by [InputModeState]).
 * - Edit mode: buttons can be dragged to reposition them, removed, or added, and the result is
 *   handed back through [onConfigChange] so the caller can persist it.
 */
@Composable
fun TouchControlOverlay(
    config: ControlLayoutConfig,
    visible: Boolean,
    editing: Boolean,
    onConfigChange: (ControlLayoutConfig) -> Unit,
    onButtonDown: (Int) -> Unit = {},
    onButtonUp: (Int) -> Unit = {}
) {
    if (!visible && !editing) return // Fully hidden in play mode; still visible while customizing

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        config.buttons.forEach { button ->
            key(button.id) {
                ControlButton(
                    button = button,
                    editing = editing,
                    widthPx = widthPx,
                    heightPx = heightPx,
                    onMoved = { xFraction, yFraction ->
                        onConfigChange(config.copy(buttons = config.buttons.map {
                            if (it.id == button.id) it.copy(xFraction = xFraction, yFraction = yFraction) else it
                        }))
                    },
                    onRemove = {
                        onConfigChange(config.copy(buttons = config.buttons.filterNot { it.id == button.id }))
                    },
                    onButtonDown = onButtonDown,
                    onButtonUp = onButtonUp
                )
            }
        }

        if (editing) {
            FloatingActionButton(
                onClick = {
                    val newButton = ControlButtonConfig(
                        label = "New",
                        keyCode = android.view.KeyEvent.KEYCODE_UNKNOWN,
                        xFraction = 0.45f,
                        yFraction = 0.45f
                    )
                    onConfigChange(config.copy(buttons = config.buttons + newButton))
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add button")
            }
        }
    }
}

@Composable
private fun ControlButton(
    button: ControlButtonConfig,
    editing: Boolean,
    widthPx: Float,
    heightPx: Float,
    onMoved: (Float, Float) -> Unit,
    onRemove: () -> Unit,
    onButtonDown: (Int) -> Unit,
    onButtonUp: (Int) -> Unit
) {
    var xFraction by remember(button.id) { mutableStateOf(button.xFraction) }
    var yFraction by remember(button.id) { mutableStateOf(button.yFraction) }
    val density = LocalDensity.current.density
    val widthDp = (button.widthFraction * widthPx / density).dp
    val heightDp = (button.heightFraction * heightPx / density).dp

    Box(
        modifier = Modifier
            .offset(
                x = (xFraction * widthPx / density).dp,
                y = (yFraction * heightPx / density).dp
            )
            .size(widthDp, heightDp)
            .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .then(
                if (editing) {
                    Modifier.pointerInput(button.id) {
                        detectDragGestures { _, dragAmount ->
                            xFraction = (xFraction + dragAmount.x / widthPx).coerceIn(0f, 0.92f)
                            yFraction = (yFraction + dragAmount.y / heightPx).coerceIn(0f, 0.92f)
                            onMoved(xFraction, yFraction)
                        }
                    }
                } else {
                    Modifier.pointerInput(button.id) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.any { it.pressed }
                                if (pressed) onButtonDown(button.keyCode) else onButtonUp(button.keyCode)
                            }
                        }
                    }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(button.label, color = Color.White)
        if (editing) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier.align(Alignment.TopEnd).size(20.dp)
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Remove", tint = Color.White)
            }
        }
    }
}

