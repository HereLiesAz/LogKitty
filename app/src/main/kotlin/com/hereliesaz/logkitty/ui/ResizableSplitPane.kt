package com.hereliesaz.logkitty.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

@Composable
fun ResizableSplitPane(
    modifier: Modifier = Modifier,
    initialSplitFraction: Float = 0.5f,
    leftContent: @Composable BoxScope.() -> Unit,
    rightContent: @Composable BoxScope.() -> Unit
) {
    var splitFraction by remember { mutableStateOf(initialSplitFraction.coerceIn(0.1f, 0.9f)) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    
    val animatedFraction by animateFloatAsState(
        targetValue = splitFraction,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "SplitFractionAnimation"
    )

    Row(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                containerSize = coordinates.size
            }
    ) {
        Box(
            modifier = Modifier
                .weight(animatedFraction)
                .fillMaxHeight()
        ) {
            leftContent()
        }

        // Draggable Divider
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(8.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        if (containerSize.width > 0) {
                            val dragFraction = dragAmount.x / containerSize.width.toFloat()
                            splitFraction = (splitFraction + dragFraction).coerceIn(0.1f, 0.9f)
                        }
                    }
                }
        )

        Box(
            modifier = Modifier
                .weight(1f - animatedFraction)
                .fillMaxHeight()
        ) {
            rightContent()
        }
    }
}
