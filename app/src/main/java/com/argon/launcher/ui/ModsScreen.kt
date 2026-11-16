package com.argon.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.argon.launcher.mods.CurseForgeClient
import com.argon.launcher.mods.InstallEvent
import com.argon.launcher.mods.ModClient
import com.argon.launcher.mods.ModInstaller
import com.argon.launcher.mods.ModSource
import com.argon.launcher.mods.ModSummary
import com.argon.launcher.mods.ModrinthClient
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ModsScreen() {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val installer = remember { ModInstaller() }

    var query by remember { mutableStateOf("") }
    var isModpack by remember { mutableStateOf(false) }
    var source by remember { mutableStateOf(ModSource.MODRINTH) }
    var curseForgeApiKey by remember { mutableStateOf("") }
    var mcVersionFilter by remember { mutableStateOf("") }

    var results by remember { mutableStateOf<List<ModSummary>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }

    // modId -> last status line shown while it (and its dependencies) install
    val installStatus = remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    val client: ModClient = remember(source, curseForgeApiKey) {
        if (source == ModSource.MODRINTH) ModrinthClient() else CurseForgeClient(curseForgeApiKey)
    }

    fun runSearch() {
        searchError = null
        isSearching = true
        scope.launch {
            try {
                results = client.search(query, isModpack, mcVersionFilter.ifBlank { null })
            } catch (e: Exception) {
                searchError = "Search failed: ${e.message}"
            } finally {
                isSearching = false
            }
        }
    }

    fun install(mod: ModSummary) {
        scope.launch {
            try {
                val version = client.findBestVersion(mod.id, mcVersionFilter.ifBlank { null })
                if (version == null) {
                    installStatus.value = installStatus.value + (mod.id to "No compatible version found")
                    return@launch
                }
                val modsDir = File(context.filesDir, "mods")
                installer.installModWithDependencies(client, mod.id, version, modsDir) { event ->
                    val line = when (event) {
                        is InstallEvent.Downloading -> "Downloading ${event.fileName}"
                        is InstallEvent.DependencyMissing -> "Dependency ${event.modId} not found, skipped"
                        is InstallEvent.DependencyFailed -> "Dependency ${event.modId} failed: ${event.reason}"
                        InstallEvent.Done -> "Installed"
                    }
                    installStatus.value = installStatus.value + (mod.id to line)
                }
            } catch (e: Exception) {
                installStatus.value = installStatus.value + (mod.id to "Install failed: ${e.message}")
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Mod Browser", style = MaterialTheme.typography.headlineSmall)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = source == ModSource.MODRINTH, onClick = { source = ModSource.MODRINTH }, label = { Text("Modrinth") })
            FilterChip(selected = source == ModSource.CURSEFORGE, onClick = { source = ModSource.CURSEFORGE }, label = { Text("CurseForge") })
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !isModpack, onClick = { isModpack = false }, label = { Text("Mods") })
            FilterChip(selected = isModpack, onClick = { isModpack = true }, label = { Text("Modpacks") })
        }

        if (source == ModSource.CURSEFORGE) {
            OutlinedTextField(
                value = curseForgeApiKey,
                onValueChange = { curseForgeApiKey = it },
                label = { Text("CurseForge API key (console.curseforge.com)") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search") },
                modifier = Modifier.weight(1f)
            )
            Button(onClick = { runSearch() }, modifier = Modifier.padding(start = 8.dp)) { Text("Go") }
        }

        OutlinedTextField(
            value = mcVersionFilter,
            onValueChange = { mcVersionFilter = it },
            label = { Text("Minecraft version (optional, e.g. 1.21)") },
            modifier = Modifier.fillMaxWidth()
        )

        if (isSearching) CircularProgressIndicator()
        if (searchError != null) Text(searchError!!, color = MaterialTheme.colorScheme.error)

        Divider()

        LazyColumn {
            items(results, key = { it.id }) { mod ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(mod.title, style = MaterialTheme.typography.titleMedium)
                        if (mod.description.isNotBlank()) Text(mod.description, style = MaterialTheme.typography.bodySmall)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val status = installStatus.value[mod.id]
                            Text(status ?: "", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Button(onClick = { install(mod) }) { Text("Install") }
                        }
                    }
                }
            }
        }
    }
}
