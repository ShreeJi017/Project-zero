package com.zerogravity.assistant.ui.navigation

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

fun Modifier.horizontalPageSwipe(
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
): Modifier = pointerInput(onSwipeLeft, onSwipeRight) {
    var horizontalDistance = 0f
    detectHorizontalDragGestures(
        onHorizontalDrag = { change, dragAmount ->
            change.consume()
            horizontalDistance += dragAmount
        },
        onDragEnd = {
            if (abs(horizontalDistance) >= SWIPE_DISTANCE_PX) {
                if (horizontalDistance < 0f) onSwipeLeft() else onSwipeRight()
            }
            horizontalDistance = 0f
        },
        onDragCancel = { horizontalDistance = 0f },
    )
}

private const val SWIPE_DISTANCE_PX = 72f
