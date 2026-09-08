package com.travelbenefits.app.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VisualTransformation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun SettingsScreen(
    onLaunchGmailAuth: (Intent) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val isGmailConnected by viewModel.isGmailConnected.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item {
                SettingsSection(title = "Anthropic API key") {
                    Text(
                        "Used for looking up cards not in the built-in catalog and for reading loyalty details out of scanned emails. Get one at console.anthropic.com.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = state.anthropicApiKey,
                        onValueChange = viewModel::onAnthropicApiKeyChange,
                        label = { Text("API key") },
                        visualTransformation = VisualTransformation.None,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                SettingsSection(title = "Google OAuth client ID") {
                    Text(
                        "Required to connect Gmail. Create an \"Android\" OAuth client in Google Cloud Console for this app - see README.md for the exact steps (package name + signing certificate SHA-1).",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = state.googleClientId,
                        onValueChange = viewModel::onGoogleClientIdChange,
                        label = { Text("Client ID (ends in .apps.googleusercontent.com)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                SettingsSection(title = "Gmail connection") {
                    if (isGmailConnected) {
                        Text("Connected.", style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = viewModel::disconnectGmail) { Text("Disconnect") }
                    } else {
                        Text(
                            "Not connected. This only ever reads mail (read-only scope) - it never sends, deletes, or modifies anything in your inbox.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(
                            onClick = { viewModel.buildGmailAuthIntent()?.let(onLaunchGmailAuth) },
                            enabled = state.googleClientId.isNotBlank(),
                        ) {
                            Text("Connect Gmail")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
