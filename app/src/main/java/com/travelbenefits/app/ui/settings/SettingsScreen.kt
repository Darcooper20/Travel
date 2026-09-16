package com.travelbenefits.app.ui.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.travelbenefits.app.data.repository.BackupRepository
import com.travelbenefits.app.domain.model.RewardCurrency
import com.travelbenefits.app.auth.PlaidLinkCoordinator
import com.travelbenefits.app.domain.model.ResolvedWalletCard
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
    onLaunchPlaidLink: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val plaid by viewModel.plaidState.collectAsState()
    val valuations by viewModel.valuations.collectAsState()
    val prefs by viewModel.preferences.collectAsState()
    val members by viewModel.members.collectAsState()
    val sync by viewModel.syncSettings.collectAsState()
    val lastCrash by viewModel.lastCrash.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val isGmailConnected by viewModel.isGmailConnected.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var notificationPermission by remember { mutableStateOf(viewModel.hasNotificationPermission()) }
    var advancedOpen by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationPermission = granted
        viewModel.updateSync { it.copy(notificationsEnabled = granted) }
    }
    val exportJsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let { viewModel.exportTo(it, csv = false) } }
    val exportCsvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> uri?.let { viewModel.exportTo(it, csv = true) } }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { viewModel.importFrom(it) } }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Long)
            viewModel.dismissMessage()
        }
    }
    LaunchedEffect(plaid.linkState) {
        when (val l = plaid.linkState) {
            is PlaidLinkCoordinator.LinkState.Linked -> {
                snackbarHostState.showSnackbar("Linked ${l.item.institutionName ?: "account"} with ${l.item.accounts.size} account(s). Map each card account below, then Sync now.", duration = SnackbarDuration.Long)
                viewModel.acknowledgePlaidLink()
            }
            is PlaidLinkCoordinator.LinkState.Failed -> {
                snackbarHostState.showSnackbar(l.message, duration = SnackbarDuration.Long)
                viewModel.acknowledgePlaidLink()
            }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
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
            if (lastCrash != null) {
                item {
                    SectionCard(title = "The app stopped unexpectedly") {
                        Text(
                            "A report was saved the last time this happened. Nothing was sent anywhere. Share it with whoever maintains " +
                                "this build so the cause can be found; it contains a stack trace and your device model, and no keys or account data.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        SelectionContainer {
                            Text(
                                lastCrash.orEmpty().lineSequence().take(14).joinToString("\n"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                val share = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "Travel Benefits crash report")
                                    putExtra(Intent.EXTRA_TEXT, lastCrash.orEmpty())
                                }
                                runCatching { context.startActivity(Intent.createChooser(share, "Share crash report")) }
                            }) { Text("Share report") }
                            TextButton(onClick = viewModel::dismissCrashReport) { Text("Dismiss") }
                        }
                    }
                }
            }
            item {
                SectionCard(title = "Connections") {
                    Text(
                        "Everything works without any connection: add cards, programs and trips by hand or import a backup. Each connection below is optional and read-only, and can be removed here at any time.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    ConnectionRow("Gmail (read-only)", if (isGmailConnected) "Connected" + (state.gmailAccountEmail?.let { " as $it" } ?: "") else if (state.googleClientId.isBlank()) "Needs a Google OAuth client ID (Advanced configuration)" else "Not connected", isGmailConnected)
                    ConnectionRow("Anthropic API key", if (state.anthropicApiKey.isNotBlank()) "Saved on this device (encrypted)" else "Not set - email reading, card lookup and the advisor are off", state.anthropicApiKey.isNotBlank())
                    ConnectionRow("Plaid backend", if (state.plaidBackendUrl.isNotBlank() && state.plaidAppToken.isNotBlank()) "Configured; ${plaid.items.size} institution(s) linked" else "Not configured - spend analysis uses manual entries", state.plaidBackendUrl.isNotBlank() && state.plaidAppToken.isNotBlank())
                    ConnectionRow("seats.aero award inventory", if (state.seatsAeroKey.isNotBlank()) "Key saved" else "No key - award search uses labelled research leads and imports", state.seatsAeroKey.isNotBlank())
                    if (isGmailConnected) {
                        OutlinedButton(onClick = viewModel::disconnectGmail) { Text("Disconnect Gmail") }
                    } else {
                        Button(
                            onClick = { viewModel.buildGmailAuthIntent()?.let(onLaunchGmailAuth) },
                            enabled = state.googleClientId.isNotBlank(),
                        ) { Text("Connect Gmail") }
                    }
                    TextButton(onClick = { advancedOpen = !advancedOpen }) { Text(if (advancedOpen) "Hide advanced configuration" else "Advanced configuration (keys, OAuth client, Plaid backend)") }
                }
            }
            if (advancedOpen) {
                item {
                    SectionCard(title = "Advanced configuration") {
                        Text(
                            "Secrets entered here are stored only in Android's encrypted preferences on this device. They are never exported, logged or sent anywhere except the service they belong to.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text("Anthropic API key", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Used to read loyalty details, trips and expiry notices out of scanned emails, to look up cards not in the built-in catalog, and for the points advisor. Get one at console.anthropic.com (pay-as-you-go).",
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
                        Text("Google OAuth client ID", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Required to connect Gmail. Create an \"Android\" OAuth client in Google Cloud Console and register exactly these two values, copied from this build:",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        val setup = viewModel.oauthSetupInfo
                        CopyableValue("Package name", setup.packageName)
                        CopyableValue("SHA-1 certificate fingerprint", setup.signingSha1 ?: "unavailable on this device")
                        OutlinedTextField(
                            value = state.googleClientId,
                            onValueChange = viewModel::onGoogleClientIdChange,
                            label = { Text("Client ID (ends in .apps.googleusercontent.com)") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        // The scheme is compiled into the manifest, so a generic build can
                        // never receive a response meant for someone else's client ID. Say
                        // so here rather than letting Google's page fail with invalid_client.
                        val mismatch = viewModel.redirectSchemeMismatch()
                        CopyableValue("This build receives sign-in on", setup.redirectScheme)
                        if (mismatch != null) {
                            Text(
                                "That client ID needs a build that receives sign-in on $mismatch. This one cannot, so sign-in will fail. " +
                                    "Build one with -PappAuthRedirectScheme=$mismatch, or run the \"Build Android APK\" action with that value and install the APK it publishes.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        } else if (state.googleClientId.isNotBlank()) {
                            Text(
                                "This build matches that client ID.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(
                            "If sign-in fails with \"Custom URI scheme is not enabled for your Android client\", open that client in " +
                                "Google Cloud Console and enable the custom URI scheme option under Advanced Settings. Google turns it off " +
                                "by default for new Android clients.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text("Plaid backend", style = MaterialTheme.typography.titleSmall)
                        Text("The tiny worker you host (plaid-backend/README.md). The Plaid client secret stays on the worker, never in this app.", style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(
                            value = state.plaidBackendUrl,
                            onValueChange = viewModel::onPlaidBackendUrlChange,
                            label = { Text("Backend URL (https://….workers.dev)") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = state.plaidAppToken,
                            onValueChange = viewModel::onPlaidAppTokenChange,
                            label = { Text("APP_TOKEN") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("seats.aero Partner API", style = MaterialTheme.typography.titleSmall)
                        Text("Requires their Pro subscription. Results are cached inventory with an 'as of' time, labelled as such.", style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(value = state.seatsAeroKey, onValueChange = viewModel::onSeatsAeroKeyChange, label = { Text("Partner-Authorization key") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                    }
                }
            }
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
                SectionCard(title = "Bank & card transactions (Plaid)") {
                    Text(
                        "Links your card accounts through a tiny backend you host (see plaid-backend/README.md) so the app can see real spend per card and category, judge it against your wallet, and track welcome-bonus progress. Plaid returns transactions, not points balances.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (state.plaidBackendUrl.isBlank() || state.plaidAppToken.isBlank()) {
                        Text("Backend not configured - set the URL and APP_TOKEN under Connections › Advanced configuration.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { viewModel.startPlaidLink(onLaunchPlaidLink) },
                            enabled = state.plaidBackendUrl.isNotBlank() && state.plaidAppToken.isNotBlank() && !plaid.isBusy && plaid.linkState !is PlaidLinkCoordinator.LinkState.Exchanging,
                        ) { Text("Link an account") }
                        if (plaid.items.isNotEmpty()) OutlinedButton(onClick = viewModel::syncPlaidNow, enabled = !plaid.isBusy) { Text("Sync now") }
                        if (plaid.isBusy || plaid.linkState is PlaidLinkCoordinator.LinkState.Exchanging) androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.padding(start = 4.dp).height(20.dp))
                    }
                    if (plaid.items.isNotEmpty()) Text("${plaid.transactionCount} transactions stored (last ~400 days).", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    plaid.items.forEach { item ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(item.institutionName ?: item.itemId.take(12), style = MaterialTheme.typography.titleSmall)
                                TextButton(onClick = { viewModel.removePlaidItem(item.itemId) }) { Text("Unlink") }
                            }
                            item.lastError?.let { Text("Last sync error: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
                            item.accounts.forEach { account ->
                                val cardOptions: List<ResolvedWalletCard?> = listOf<ResolvedWalletCard?>(null) + plaid.cards
                                DropdownPicker(
                                    label = account.label,
                                    options = cardOptions,
                                    selected = plaid.cards.firstOrNull { it.walletCard.id == account.walletCardId },
                                    optionLabel = { it?.displayName ?: "Not a wallet card / ignore" },
                                    onSelected = { c -> viewModel.mapPlaidAccount(account.accountId, c?.walletCard?.id) },
                                )
                            }
                        }
                    }
                }
            }
            item {
                SectionCard(title = "Reminders") {
                    Text(
                        "A daily local check (no network) for points and certificates about to expire (90/60/30/7 days), unused card credits near period end, welcome-bonus deadlines, quarterly category activation, and tomorrow's trips/check-in.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    ToggleRow(label = "Daily reminders", checked = sync.dailyRemindersEnabled, onChange = { on -> viewModel.updateSync { it.copy(dailyRemindersEnabled = on) } })
                    ToggleRow(label = "Award watches & transfer-bonus research (uses API)", checked = sync.researchEnabled, onChange = { on -> viewModel.updateSync { it.copy(researchEnabled = on) } })
                    ToggleRow(label = "Quiet hours (${sync.quietStartHour}:00–${sync.quietEndHour}:00)", checked = sync.quietHoursEnabled, onChange = { on -> viewModel.updateSync { it.copy(quietHoursEnabled = on) } })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DropdownPicker(label = "Quiet from", options = (0..23).toList(), selected = sync.quietStartHour, optionLabel = { "$it:00" }, onSelected = { h -> viewModel.updateSync { it.copy(quietStartHour = h) } }, modifier = Modifier.weight(1f))
                        DropdownPicker(label = "until", options = (0..23).toList(), selected = sync.quietEndHour, optionLabel = { "$it:00" }, onSelected = { h -> viewModel.updateSync { it.copy(quietEndHour = h) } }, modifier = Modifier.weight(1f))
                    }
                    TextButton(onClick = viewModel::runRemindersNow) { Text("Run reminder check now") }
                }
            }
            item {
                SectionCard(title = "Travel preferences") {
                    Text("Used by award search defaults, the advisor and the cash-vs-points band. Nothing is learned silently: these are the only inputs, and Reset clears them.", style = MaterialTheme.typography.bodySmall)
                    PrefField("Home airports (IATA, comma-separated)", prefs.homeAirports.joinToString(",")) { viewModel.setPreference(com.travelbenefits.app.domain.model.UserPreferences.KEY_HOME_AIRPORTS, it) }
                    PrefField("Preferred airlines", prefs.preferredAirlines.joinToString(",")) { viewModel.setPreference(com.travelbenefits.app.domain.model.UserPreferences.KEY_AIRLINES, it) }
                    PrefField("Preferred hotel brands", prefs.preferredHotels.joinToString(",")) { viewModel.setPreference(com.travelbenefits.app.domain.model.UserPreferences.KEY_HOTELS, it) }
                    DropdownPicker(label = "Usual cabin", options = com.travelbenefits.app.domain.model.Cabin.entries, selected = prefs.cabin, optionLabel = { it.label }, onSelected = { viewModel.setPreference(com.travelbenefits.app.domain.model.UserPreferences.KEY_CABIN, it.name) })
                    PrefField("Budget per trip (USD)", prefs.budgetUsdPerTrip?.toString().orEmpty()) { viewModel.setPreference(com.travelbenefits.app.domain.model.UserPreferences.KEY_BUDGET, it) }
                    PrefField("Date flexibility (± days)", prefs.flexibilityDays.toString()) { viewModel.setPreference(com.travelbenefits.app.domain.model.UserPreferences.KEY_FLEX, it) }
                    PrefField("Max connections", prefs.maxConnections.toString()) { viewModel.setPreference(com.travelbenefits.app.domain.model.UserPreferences.KEY_CONNECTIONS, it) }
                    PrefField("Prefer points over cash (0-100)", prefs.pointsPreference.toString()) { viewModel.setPreference(com.travelbenefits.app.domain.model.UserPreferences.KEY_POINTS_PREF, it) }
                    ToggleRow(label = "Convenience over value", checked = prefs.convenienceOverValue, onChange = { on -> viewModel.setPreference(com.travelbenefits.app.domain.model.UserPreferences.KEY_CONVENIENCE, on.toString()) })
                    TextButton(onClick = viewModel::resetPreferences) { Text("Reset preferences") }
                }
            }
            item {
                SectionCard(title = "Household") {
                    Text("Members are labels for who holds a card or account. Nothing assumes balances can be pooled - each program's pooling rule is shown on the Loyalty screen.", style = MaterialTheme.typography.bodySmall)
                    members.forEach { m ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(m.name + if (m.isOwner) " (owner)" else "", style = MaterialTheme.typography.bodyMedium)
                            if (!m.isOwner) TextButton(onClick = { viewModel.deleteMember(m.id) }) { Text("Remove") }
                        }
                    }
                    var newMember by remember { mutableStateOf("") }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = newMember, onValueChange = { newMember = it }, label = { Text("Add member") }, modifier = Modifier.weight(1f))
                        TextButton(onClick = { viewModel.addMember(newMember); newMember = "" }) { Text("Add") }
                    }
                }
            }
            item {
                SectionCard(title = "Award data provider (optional)") {
                    Text("seats.aero Partner API key (requires their Pro subscription). Results are cached inventory, labelled as such. Without a key, award search falls back to AI research leads and manual imports.", style = MaterialTheme.typography.bodySmall)
                    Text(if (state.seatsAeroKey.isNotBlank()) "Key saved. Change it under Connections › Advanced configuration." else "No key. Add one under Connections › Advanced configuration.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                SectionCard(title = "Your point valuations (¢ per point)") {
                    Text("Used by the best-card picker, cash-vs-points and card value instead of the app's estimates. Leave blank to use the estimate.", style = MaterialTheme.typography.bodySmall)
                    RewardCurrency.entries.filter { !it.displayAsPercent && it != RewardCurrency.GENERIC_POINTS }.forEach { c ->
                        ValuationField(c, valuations[c], onSave = { v -> viewModel.setValuation(c, v) })
                    }
                }
            }
            item {
                SectionCard(title = "Backup & export") {
                    Text("Nothing is stored in the cloud. Export a JSON backup now and then; the CSV is a flat balance sheet for spreadsheets. Secrets are never exported.", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { exportJsonLauncher.launch(BackupRepository.suggestedFileName("json")) }) { Text("Export JSON") }
                        OutlinedButton(onClick = { exportCsvLauncher.launch(BackupRepository.suggestedFileName("csv")) }) { Text("Export CSV") }
                    }
                    TextButton(onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }) { Text("Import JSON backup (merges into current data)") }
                }
            }
            item {
                SectionCard(title = "About the data") {
                    Text(
                        "Card benefits, tier ladders, expiry rules, transfer ratios and point values are hand-curated snapshots with an \"as of\" date on each entry. " +
                            "They drift; verify anything that matters against the issuer or program before acting. Everything is stored on this device only.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = { viewModel.showSetupGuideAgain(); onBack() }) { Text("Show the setup guide again") }
                }
            }
        }
    }
}

/** A label with a selectable value, so setup values can be copied off the phone. */
@Composable
private fun CopyableValue(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer { Text(value, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun ConnectionRow(name: String, detail: String, ok: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        // Shape plus a spoken label, so the state does not depend on seeing a coloured dot.
        Icon(
            if (ok) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = if (ok) "Configured" else "Not configured",
            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(18.dp),
        )
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(name, style = MaterialTheme.typography.bodyMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PrefField(label: String, value: String, onSave: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text(label) }, modifier = Modifier.weight(1f))
        TextButton(onClick = { onSave(text) }) { Text("Save") }
    }
}

@Composable
private fun ValuationField(currency: RewardCurrency, value: Double?, onSave: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value?.toString().orEmpty()) }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("${currency.displayName} (est. ${currency.estValueCentsPerPoint}¢)") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        TextButton(onClick = { onSave(text) }) { Text("Save") }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
