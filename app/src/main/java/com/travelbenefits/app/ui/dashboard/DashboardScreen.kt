package com.travelbenefits.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.travelbenefits.app.domain.model.ActivityEvent
import com.travelbenefits.app.domain.model.ActivityKind
import com.travelbenefits.app.domain.model.Alert
import com.travelbenefits.app.domain.model.ActionItem
import com.travelbenefits.app.domain.model.ActionState
import androidx.compose.material3.SnackbarResult
import com.travelbenefits.app.domain.model.LoyaltyProgramKind
import com.travelbenefits.app.domain.model.Trip
import com.travelbenefits.app.ui.common.AlertRow
import com.travelbenefits.app.ui.common.SectionCard
import com.travelbenefits.app.ui.common.formatDateRange
import com.travelbenefits.app.ui.common.formatPoints
import com.travelbenefits.app.ui.common.formatRelative
import com.travelbenefits.app.ui.common.formatUsd

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onOpenLoyalty: () -> Unit,
    onOpenTrips: () -> Unit,
    onOpenOptimize: () -> Unit,
    onOpenWallet: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBenefits: () -> Unit,
    onOpenCardValue: () -> Unit = {},
    onOpenReconcile: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val lastActionChange by viewModel.lastActionChange.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDone by remember { androidx.compose.runtime.mutableStateOf(false) }

    LaunchedEffect(lastActionChange) {
        val key = lastActionChange ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar("Action updated", actionLabel = "Undo", duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) viewModel.undoActionState(key) else viewModel.clearActionChange()
    }

    LaunchedEffect(syncState) {
        when (val s = syncState) {
            is SyncUiState.Done -> {
                snackbarHostState.showSnackbar(s.summary, duration = SnackbarDuration.Long)
                viewModel.dismissSyncState()
            }
            is SyncUiState.Error -> {
                snackbarHostState.showSnackbar(s.message, duration = SnackbarDuration.Long)
                viewModel.dismissSyncState()
            }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Loyalty wallet") },
                actions = {
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item { PortfolioCard(state, onOpenLoyalty) }
            item { SyncCard(state, syncState, onSync = viewModel::syncNow, onOpenSettings = onOpenSettings) }

            val open = state.actions.filter { it.state == ActionState.OPEN }
            val hidden = state.actions.size - open.size
            if (state.actions.isNotEmpty()) {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Actions (${open.size})", style = MaterialTheme.typography.titleMedium)
                        if (hidden > 0) TextButton(onClick = { showDone = !showDone }) { Text(if (showDone) "Hide $hidden done/snoozed" else "Show $hidden done/snoozed") }
                    }
                }
                items(if (showDone) state.actions else open.take(10), key = { "act-${it.key}" }) { action ->
                    ActionRow(
                        action,
                        onOpen = when (action.destination) {
                            Alert.Destination.TRIPS -> onOpenTrips
                            Alert.Destination.CARDS -> if (action.kind == com.travelbenefits.app.domain.model.ActionKind.RENEWAL) onOpenCardValue else onOpenWallet
                            Alert.Destination.BENEFITS -> onOpenBenefits
                            Alert.Destination.OPTIMIZE -> onOpenOptimize
                            Alert.Destination.LOYALTY -> if (action.tripId != null) onOpenTrips else onOpenLoyalty
                        },
                        onState = { st -> viewModel.setActionState(action, st) },
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    QuickTile(Modifier.weight(1f), Icons.Filled.Luggage, "${state.upcomingTrips.size} upcoming", "Trips", onOpenTrips)
                    QuickTile(Modifier.weight(1f), Icons.Filled.CreditCard, "${state.cardCount} card(s)", "Cards", onOpenWallet)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    QuickTile(Modifier.weight(1f), Icons.Filled.CardGiftcard, "~${formatUsd(state.unusedCreditsUsd)} unused", "Credits & certificates (${state.unusedCreditCount})", onOpenBenefits)
                    QuickTile(Modifier.weight(1f), Icons.Filled.CreditCard, "Expected vs received", "Reconciliation & card value", onOpenReconcile)
                }
            }
            item {
                Card(onClick = onOpenOptimize, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Maximize points", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Best card to earn toward a program, cash-vs-points on a booking, transfer partners, and an AI redemption advisor.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            if (state.upcomingTrips.isNotEmpty()) {
                item { Text("Coming up", style = MaterialTheme.typography.titleMedium) }
                items(state.upcomingTrips, key = { "trip-${it.id}" }) { trip -> UpcomingTripRow(trip, onOpenTrips) }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Activity" + if (state.unreadActivity > 0) " (${state.unreadActivity} new)" else "", style = MaterialTheme.typography.titleMedium)
                    if (state.unreadActivity > 0) TextButton(onClick = viewModel::markActivityRead) { Text("Mark read") }
                }
            }
            if (state.activity.isEmpty()) {
                item {
                    Text(
                        "Nothing yet. Connect Gmail and run a sync to pull memberships, balances and trips out of your inbox.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(state.activity, key = { "act-${it.id}" }) { event -> ActivityRow(event) }
            }
        }
    }
}

@Composable
private fun ActionRow(action: ActionItem, onOpen: () -> Unit, onState: (ActionState) -> Unit) {
    val dim = action.state != ActionState.OPEN
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth(), colors = if (dim) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant) else CardDefaults.cardColors()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(action.kind.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(
                    listOfNotNull(action.amountUsd?.let { "~" + formatUsd(it) }, action.deadlineEpochDay?.let { formatDateRange(it, it) }, "${action.effort.label} • ${action.confidence.label.lowercase()} confidence").joinToString(" • "),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(action.title, style = MaterialTheme.typography.titleSmall)
            Text(action.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Next: ${action.nextStep} (source: ${action.source})", style = MaterialTheme.typography.labelSmall)
            if (action.state == ActionState.OPEN) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { onState(ActionState.COMPLETED) }) { Text("Done") }
                    TextButton(onClick = { onState(ActionState.SNOOZED) }) { Text("Snooze 3d") }
                    TextButton(onClick = { onState(ActionState.DISMISSED) }) { Text("Dismiss") }
                }
            } else {
                Text(action.state.name.lowercase().replaceFirstChar { it.uppercase() } + (action.snoozedUntilEpochDay?.let { " until ${formatDateRange(it, it)}" } ?: ""), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun PortfolioCard(state: DashboardUiState, onOpenLoyalty: () -> Unit) {
    Card(onClick = onOpenLoyalty, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Estimated points value", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatUsd(state.portfolioValueUsd), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                TotalChip(Modifier.weight(1f), Icons.Filled.Hotel, "${formatPoints(state.hotelPointsTotal)} pts", "Hotels")
                TotalChip(Modifier.weight(1f), Icons.Filled.Flight, "${formatPoints(state.airlineMilesTotal)} mi", "Airlines")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                TotalChip(Modifier.weight(1f), Icons.Filled.ShoppingBag, "~${formatUsd(state.shopValueUsd)}", "Shops & dining")
                TotalChip(Modifier.weight(1f), Icons.Filled.CreditCard, "~${formatUsd(state.cardRewardsValueUsd)}", "Card points (${state.cardsWithBalance}/${state.cardCount})")
            }
            if (state.accounts.isEmpty()) {
                Text("No loyalty accounts yet - sync Gmail or add them by hand.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Spacer(Modifier.height(4.dp))
                state.accounts.take(6).forEach { summary ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(summary.account.program.displayName, style = MaterialTheme.typography.bodyMedium)
                            summary.account.tier?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(summary.balanceLabel ?: "—", style = MaterialTheme.typography.bodyMedium)
                            summary.estimatedValueUsd?.let { Text("~${formatUsd(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
                if (state.accounts.size > 6) Text("+${state.accounts.size - 6} more", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "Values use the app's per-point estimates, not a guaranteed redemption.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TotalChip(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(6.dp))
        Column {
            Text(value, style = MaterialTheme.typography.bodyMedium)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SyncCard(state: DashboardUiState, syncState: SyncUiState, onSync: () -> Unit, onOpenSettings: () -> Unit) {
    SectionCard(title = "Email monitor") {
        when {
            !state.isGmailConnected || !state.hasAnthropicKey -> {
                Text(
                    listOfNotNull(
                        if (!state.isGmailConnected) "Gmail isn't connected" else null,
                        if (!state.hasAnthropicKey) "no Anthropic API key" else null,
                    ).joinToString(" and ").replaceFirstChar { it.uppercase() } + ". Both are needed to read loyalty statements and booking confirmations.",
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = onOpenSettings) { Text("Set up in Settings") }
            }
            syncState is SyncUiState.Running -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(syncState.message, style = MaterialTheme.typography.bodyMedium)
            }
            else -> {
                val last = if (state.lastSyncAt > 0) "Last sync ${formatRelative(state.lastSyncAt)}" else "Never synced"
                Text(last + if (state.autoSyncEnabled) " • background sync on" else " • background sync off", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                state.lastSyncSummary?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Button(onClick = onSync) {
                    Icon(Icons.Filled.Sync, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Sync now")
                }
            }
        }
    }
}

@Composable
private fun QuickTile(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun UpcomingTripRow(trip: Trip, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (trip.loyaltyProgram?.kind == LoyaltyProgramKind.AIRLINE) Icons.Filled.Flight else Icons.Filled.Hotel,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(trip.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    listOfNotNull(formatDateRange(trip.startEpochDay, trip.endEpochDay), trip.confirmationNumber?.let { "Conf. $it" }).joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ActivityRow(event: ActivityEvent) {
    val color = when (event.kind) {
        ActivityKind.SYNC_FAILED, ActivityKind.POINTS_EXPIRING -> MaterialTheme.colorScheme.error
        ActivityKind.SYNC_COMPLETED, ActivityKind.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.primary
    }
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(event.kind.label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = if (event.isRead) FontWeight.Normal else FontWeight.Bold)
            Text(formatRelative(event.occurredAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(event.title, style = MaterialTheme.typography.bodyMedium)
        event.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
