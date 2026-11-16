package com.argon.launcher.controls

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Tracks whether the on-screen touch buttons should currently be shown.
 *
 * The rule: the moment a real (non-virtual) physical keyboard, mouse or game controller sends
 * an event, the buttons get out of the way, since the player is clearly not using touch right
 * now. The moment the player taps the touchscreen again, they come back. This is driven from
 * the host Activity's dispatch* overrides (see MainActivity), which see every input event
 * before the UI does.
 */
class InputModeState {
    var autoHideEnabled by mutableStateOf(true)
    var controlsVisible by mutableStateOf(true)
        private set

    private var hiddenByExternalInput = false

    fun onKeyEvent(event: KeyEvent) {
        val device = event.device
        if (device == null || device.isVirtual) return
        val source = event.source
        val isPhysicalInput = (source and InputDevice.SOURCE_KEYBOARD) != 0 ||
                (source and InputDevice.SOURCE_GAMEPAD) != 0 ||
                (source and InputDevice.SOURCE_DPAD) != 0
        if (isPhysicalInput) hideForExternalInput()
    }

    fun onGenericMotionEvent(event: MotionEvent) {
        val source = event.source
        val isPhysicalInput = (source and InputDevice.SOURCE_MOUSE) != 0 ||
                (source and InputDevice.SOURCE_MOUSE_RELATIVE) != 0 ||
                (source and InputDevice.SOURCE_JOYSTICK) != 0 ||
                (source and InputDevice.SOURCE_GAMEPAD) != 0
        if (isPhysicalInput) hideForExternalInput()
    }

    fun onTouchEvent(event: MotionEvent) {
        if (event.actionMasked != MotionEvent.ACTION_DOWN) return
        if ((event.source and InputDevice.SOURCE_TOUCHSCREEN) == 0) return
        if (hiddenByExternalInput) {
            hiddenByExternalInput = false
            controlsVisible = true
        }
    }

    private fun hideForExternalInput() {
        if (!autoHideEnabled || hiddenByExternalInput) return
        hiddenByExternalInput = true
        controlsVisible = false
    }
}
