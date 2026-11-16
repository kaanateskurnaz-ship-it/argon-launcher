package com.argon.launcher.controls

import android.content.Context
import android.view.KeyEvent
import com.google.gson.Gson
import java.io.File
import java.util.UUID

/**
 * One on-screen button. Position/size are stored as fractions of the play area (0f..1f) so the
 * same layout looks right on any screen size/orientation, rather than in fixed pixels.
 */
data class ControlButtonConfig(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val keyCode: Int,
    val xFraction: Float,
    val yFraction: Float,
    val widthFraction: Float = 0.12f,
    val heightFraction: Float = 0.08f
)

data class ControlLayoutConfig(
    val buttons: List<ControlButtonConfig> = defaultButtons()
) {
    companion object {
        fun defaultButtons(): List<ControlButtonConfig> = listOf(
            ControlButtonConfig(label = "W", keyCode = KeyEvent.KEYCODE_W, xFraction = 0.10f, yFraction = 0.55f),
            ControlButtonConfig(label = "A", keyCode = KeyEvent.KEYCODE_A, xFraction = 0.02f, yFraction = 0.68f),
            ControlButtonConfig(label = "S", keyCode = KeyEvent.KEYCODE_S, xFraction = 0.10f, yFraction = 0.68f),
            ControlButtonConfig(label = "D", keyCode = KeyEvent.KEYCODE_D, xFraction = 0.18f, yFraction = 0.68f),
            ControlButtonConfig(label = "Jump", keyCode = KeyEvent.KEYCODE_SPACE, xFraction = 0.80f, yFraction = 0.68f, widthFraction = 0.16f),
            ControlButtonConfig(label = "Sneak", keyCode = KeyEvent.KEYCODE_SHIFT_LEFT, xFraction = 0.80f, yFraction = 0.80f, widthFraction = 0.16f),
            ControlButtonConfig(label = "Inv", keyCode = KeyEvent.KEYCODE_E, xFraction = 0.90f, yFraction = 0.05f)
        )
    }
}

/** Persists the player's customized control layout as JSON in app-private storage. */
class ControlLayoutRepository(context: Context) {
    private val file = File(context.filesDir, "touch_controls.json")
    private val gson = Gson()

    fun load(): ControlLayoutConfig {
        if (!file.exists()) return ControlLayoutConfig()
        return try {
            gson.fromJson(file.readText(), ControlLayoutConfig::class.java) ?: ControlLayoutConfig()
        } catch (e: Exception) {
            ControlLayoutConfig()
        }
    }

    fun save(config: ControlLayoutConfig) {
        file.writeText(gson.toJson(config))
    }
}
