package com.travelbenefits.app.ui.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.travelbenefits.app.ui.common.DropdownPicker
import com.travelbenefits.app.ui.common.SectionCard
import com.travelbenefits.app.work.SyncScheduler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onLaunchGmailAuth: (Intent) -> Unit,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val sync by viewModel.syncSettings.collectAsState()
    val isGmailConnected by viewModel.isGmailConnected.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var notificationPermission by remember { mutableStateOf(viewModel.hasNotificationPermission()) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationPermission = granted
        viewModel.updateSync { it.copy(notificationsEnabled = granted) }
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Long)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item {
                SectionCard(title = "Email monitor") {
                    Text(
                        "Reads new mail from hotel, airline, shop, card-issuer and booking senders (read-only), extracts balances, status, trips and expiry warnings, and keeps the wallet current.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    ToggleRow(
                        label = "Background sync",
                        checked = sync.autoSyncEnabled,
                        enabled = isGmailConnected,
                        onChange = { on -> viewModel.updateSync { it.copy(autoSyncEnabled = on) } },
                    )
                    if (!isGmailConnected) Text("Connect Gmail below to enable.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    DropdownPicker(
                        label = "Check every",
                        options = SyncScheduler.INTERVAL_CHOICES,
                        selected = if (sync.syncIntervalHours in SyncScheduler.INTERVAL_CHOICES) sync.syncIntervalHours else 12,
                        optionLabel = { "$it hours" },
                        onSelected = { h -> viewModel.updateSync { it.copy(syncIntervalHours = h) } },
                    )
                    ToggleRow(
                        label = "Notify me about changes",
                        checked = sync.notificationsEnabled && notificationPermission,
                        onChange = { on ->
                            if (on && !notificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.updateSync { it.copy(notificationsEnabled = on) }
                            }
                        },
                    )
                    ToggleRow(label = "Scan loyalty program emails", checked = sync.scanLoyaltyEmails, onChange = { on -> viewModel.updateSync { it.copy(scanLoyaltyEmails = on) } })
                    ToggleRow(label = "Scan booking confirmations", checked = sync.scanTripEmails, onChange = { on -> viewModel.updateSync { it.copy(scanTripEmails = on) } })
                    ToggleRow(label = "Scan shop & dining rewards emails", checked = sync.scanShopEmails, onChange = { on -> viewModel.updateSync { it.copy(scanShopEmails = on) } })
                    ToggleRow(label = "Scan credit card statements for rewards balances", checked = sync.scanCardEmails, onChange = { on -> viewModel.updateSync { it.copy(scanCardEmails = on) } })
                    DropdownPicker(
                        label = "First scan looks back",
                        options = listOf(90, 180, 365, 730),
                        selected = if (sync.firstScanLookbackDays in listOf(90, 180, 365, 730)) sync.firstScanLookbackDays else 365,
                        optionLabel = { "$it days" },
                        onSelected = { d -> viewModel.updateSync { it.copy(firstScanLookbackDays = d) } },
                    )
                    DropdownPicker(
                        label = "Max emails per sync (API spend guard)",
                        options = listOf(20, 40, 60, 100, 200),
                        selected = if (sync.maxEmailsPerSync in listOf(20, 40, 60, 100, 200)) sync.maxEmailsPerSync else 60,
                        optionLabel = { "$it emails" },
                        onSelected = { n -> viewModel.updateSync { it.copy(maxEmailsPerSync = n) } },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = viewModel::resetScanHistory) { Text("Re-scan from scratch") }
                        TextButton(onClick = viewModel::clearActivity) { Text("Clear activity feed") }
                    }
                }
            }
            item {
                SectionCard(title = "Anthropic API key") {
                    Text(
                        "Used to read loyalty details, trips and expiry notices out of scanned emails, to look up cards not in the built-in catalog, and for the points advisor. Get one at console.anthropic.com.",
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
                SectionCard(title = "Google OAuth client ID") {
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
                SectionCard(title = "Gmail connection") {
                    if (isGmailConnected) {
                        Text("Connected" + (state.gmailAccountEmail?.let { " as $it" } ?: "") + ".", style = MaterialTheme.typography.bodyMedium)
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
            item {
                SectionCard(title = "About the data") {
                    Text(
                        "Card benefits, tier ladders, expiry rules, transfer ratios and point values are hand-curated snapshots with an \"as of\" date on each entry. " +
                            "They drift; verify anything that matters against the issuer or program before acting. Everything is stored on this device only.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
