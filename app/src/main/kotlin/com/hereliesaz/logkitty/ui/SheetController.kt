package com.hereliesaz.logkitty.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The four detents supported by the LogKitty overlay.
 *
 * - [HIDDEN]: an invisible swipe-up zone at the bottom of the screen; the window shrinks so the
 *   underlying app receives every touch outside that strip. A swipe up advances to [PEEK].
 * - [PEEK]: a one-line ticker showing the most recent log entry.
 * - [HALF]: roughly half-height of the screen, the comfortable "browse" position.
 * - [FULL]: extended down so the log occupies everything but the bottom 10% of the screen.
 */
enum class SheetDetent { HIDDEN, PEEK, HALF, FULL }

/**
 * Holds the current sheet detent in a way that both the Compose UI and the hosting
 * Service can read and mutate. The Service uses [detentFlow] to size the WindowManager
 * window; the UI uses [detent] for animations.
 *
 * [isEnabled] is set to false while the user is on the launcher: the overlay shrinks
 * its window so it no longer intercepts the system swipe-up-for-app-drawer gesture.
 *
 * The controller is created once per overlay session and shared across both worlds.
 */
@Stable
class SheetController {
    private val _detent = mutableStateOf(SheetDetent.HIDDEN)
    /** Compose-friendly mutable state for animations. */
    var detent: SheetDetent
        get() = _detent.value
        set(value) {
            _detent.value = value
            _detentFlow.value = value
        }

    private val _detentFlow = MutableStateFlow(SheetDetent.HIDDEN)
    val detentFlow: StateFlow<SheetDetent> = _detentFlow.asStateFlow()

    private val _isEnabled = mutableStateOf(true)
    private val _isEnabledFlow = MutableStateFlow(true)
    var isEnabled: Boolean
        get() = _isEnabled.value
        set(value) {
            if (_isEnabled.value == value) return
            _isEnabled.value = value
            _isEnabledFlow.value = value
            if (!value) {
                detent = SheetDetent.HIDDEN
            }
        }
    val isEnabledFlow: StateFlow<Boolean> = _isEnabledFlow.asStateFlow()

    fun hide() { detent = SheetDetent.HIDDEN }
    fun peek() { detent = SheetDetent.PEEK }
    fun half() { detent = SheetDetent.HALF }
    fun full() { detent = SheetDetent.FULL }

    /** Cycle one detent toward HIDDEN. Stops at HIDDEN. */
    fun stepDown() {
        detent = when (detent) {
            SheetDetent.FULL -> SheetDetent.HALF
            SheetDetent.HALF -> SheetDetent.PEEK
            SheetDetent.PEEK -> SheetDetent.HIDDEN
            SheetDetent.HIDDEN -> SheetDetent.HIDDEN
        }
    }

    /** Cycle one detent toward FULL. Stops at FULL. */
    fun stepUp() {
        detent = when (detent) {
            SheetDetent.HIDDEN -> SheetDetent.PEEK
            SheetDetent.PEEK -> SheetDetent.HALF
            SheetDetent.HALF -> SheetDetent.FULL
            SheetDetent.FULL -> SheetDetent.FULL
        }
    }
}
