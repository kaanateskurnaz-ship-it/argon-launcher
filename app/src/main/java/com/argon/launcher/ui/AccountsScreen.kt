package com.argon.launcher.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.argon.launcher.account.AccountRepository
import com.argon.launcher.account.DeviceCodePrompt
import com.argon.launcher.account.ElyByAuth
import com.argon.launcher.account.MicrosoftAuth
import com.argon.launcher.account.OfflineAuth
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun AccountsScreen(
    repository: AccountRepository,
    microsoftAuth: MicrosoftAuth,
    elyByAuth: ElyByAuth,
    pendingElyByCode: String?,
    onElyByCodeConsumed: () -> Unit,
    onOpenElyByLogin: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var accounts by remember { mutableStateOf(repository.getAccounts()) }
    var selectedId by remember { mutableStateOf(repository.getSelectedAccount()?.id) }

    var offlineUsername by remember { mutableStateOf("") }

    var deviceCodePrompt by remember { mutableStateOf<DeviceCodePrompt?>(null) }
    var microsoftSignInJob by remember { mutableStateOf<Job?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        accounts = repository.getAccounts()
        selectedId = repository.getSelectedAccount()?.id
    }

    // Ely.by finishes in a browser tab, which hands control back to MainActivity via the
    // "argonlauncher://elyby-callback" redirect; that code shows up here as pendingElyByCode.
    LaunchedEffect(pendingElyByCode) {
        val code = pendingElyByCode ?: return@LaunchedEffect
        try {
            val account = elyByAuth.exchangeCodeForAccount(code)
            repository.addOrUpdate(account)
            refresh()
        } catch (e: Exception) {
            statusMessage = "Ely.by sign-in failed: ${e.message}"
        } finally {
            onElyByCodeConsumed()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Accounts", style = MaterialTheme.typography.headlineSmall)

        if (statusMessage != null) {
            Text(statusMessage!!, color = MaterialTheme.colorScheme.error)
        }

        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
            items(accounts, key = { it.id }) { account ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row {
                            RadioButton(
                                selected = account.id == selectedId,
                                onClick = {
                                    repository.select(account.id)
                                    refresh()
                                }
                            )
                            Column {
                                Text(account.username)
                                Text(account.type.name, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        TextButton(onClick = {
                            repository.remove(account.id)
                            refresh()
                        }) { Text("Remove") }
                    }
                }
            }
        }

        Divider()

        Text("Add offline account", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedTextField(
                value = offlineUsername,
                onValueChange = { offlineUsername = it },
                label = { Text("Username") },
                modifier = Modifier.weight(1f)
            )
            Button(onClick = {
                if (offlineUsername.isNotBlank()) {
                    repository.addOrUpdate(OfflineAuth.createAccount(offlineUsername.trim()))
                    offlineUsername = ""
                    refresh()
                }
            }, modifier = Modifier.padding(start = 8.dp)) { Text("Add") }
        }

        Divider()

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                statusMessage = null
                microsoftSignInJob = scope.launch {
                    try {
                        val (prompt, deviceCode) = microsoftAuth.startDeviceCodeFlow()
                        deviceCodePrompt = prompt
                        val account = microsoftAuth.pollForAccount(prompt, deviceCode)
                        repository.addOrUpdate(account)
                        refresh()
                    } catch (e: Exception) {
                        statusMessage = "Microsoft sign-in failed: ${e.message}"
                    } finally {
                        deviceCodePrompt = null
                    }
                }
            }
        ) { Text("Sign in with Microsoft") }

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                statusMessage = null
                onOpenElyByLogin(elyByAuth.buildAuthorizationUrl())
            }
        ) { Text("Sign in with Ely.by") }
    }

    val prompt = deviceCodePrompt
    if (prompt != null) {
        AlertDialog(
            onDismissRequest = { /* Must finish or be cancelled explicitly - this is a device code flow */ },
            title = { Text("Sign in with Microsoft") },
            text = {
                Column {
                    Text("Open the page below and enter this code:")
                    Text(prompt.userCode, style = MaterialTheme.typography.headlineSmall)
                    Text(prompt.verificationUri, style = MaterialTheme.typography.bodySmall)
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(4.dp))
                    CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(prompt.verificationUri)))
                }) { Text("Open browser") }
            },
            dismissButton = {
                TextButton(onClick = {
                    microsoftSignInJob?.cancel()
                    deviceCodePrompt = null
                }) { Text("Cancel") }
            }
        )
    }
}
