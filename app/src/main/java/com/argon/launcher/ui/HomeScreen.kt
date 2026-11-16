package com.argon.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(onNavigate: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
    ) {
        Text(
            "Argon Launcher",
            style = androidx.compose.material3.MaterialTheme.typography.headlineMedium
        )
        Text(
            "A from-scratch prototype covering accounts, mod browsing, and customizable touch " +
                "controls. It does not include a Minecraft-launching engine yet, so \"Controls\" " +
                "below is a stand-in play surface for trying the overlay, not a running game.",
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Start
        )

        Button(onClick = { onNavigate("accounts") }, modifier = Modifier.fillMaxWidth()) {
            Text("Accounts")
        }
        Button(onClick = { onNavigate("mods") }, modifier = Modifier.fillMaxWidth()) {
            Text("Mod Browser")
        }
        Button(onClick = { onNavigate("controls") }, modifier = Modifier.fillMaxWidth()) {
            Text("Touch Controls")
        }
    }
}
