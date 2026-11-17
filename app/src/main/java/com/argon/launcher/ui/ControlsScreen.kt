package com.argon.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.argon.launcher.controls.ControlLayoutConfig
import com.argon.launcher.controls.ControlLayoutRepository
import com.argon.launcher.controls.InputModeState
import com.argon.launcher.controls.TouchControlOverlay

/**
 * Stand-in "play surface" for trying out the touch control overlay, since this project doesn't
 * include a real Minecraft-launching engine (see the project README). [inputModeState] is the
 * single instance MainActivity feeds from its dispatch*Event overrides, so plugging in a
 * keyboard/mouse/controller here behaves exactly like it would during real gameplay later.
 */
@Composable
fun ControlsScreen(inputModeState: InputModeState, repository: ControlLayoutRepository) {
    var config by remember { mutableStateOf(repository.load()) }
    var editing by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF2E7D32)))

        TouchControlOverlay(
            config = config,
            visible = inputModeState.controlsVisible,
            editing = editing,
            onConfigChange = { config = it }
        )

        Row(
            modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = { editing = !editing }) { Text(if (editing) "Bitti" else "Özelleştir") }
            if (editing) {
                Button(onClick = { repository.save(config) }) { Text("Kaydet") }
                Button(onClick = { config = ControlLayoutConfig() }) { Text("Sıfırla") }
            }
        }

        if (!editing) {
            Text(
                text = if (inputModeState.controlsVisible)
                    "Dokunmatik kontroller görünür - klavye/fare/kontrolcü bağlayınca gizlenirler"
                else
                    "Kontroller gizli (fiziksel giriş algılandı) - geri getirmek için ekrana dokun",
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                color = Color.White
            )
        }
    }
}
