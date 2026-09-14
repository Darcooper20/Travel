package com.travelbenefits.app.ui.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.travelbenefits.app.domain.model.CardCatalogEntry
import com.travelbenefits.app.domain.model.ResolvedWalletCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(viewModel: WalletViewModel = hiltViewModel()) {
    val cards by viewModel.resolvedCards.collectAsState()
    val addCardState by viewModel.addCardState.collectAsState()
    val editCardState by viewModel.editCardState.collectAsState()
    var pendingDelete by remember { mutableStateOf<ResolvedWalletCard?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Your wallet") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::openAddCard) {
                Icon(Icons.Filled.Add, contentDescription = "Add card")
            }
        },
    ) { padding ->
        if (cards.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
            ) {
                Text("No cards yet. Tap + to add the ones you carry.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                item {
                    Text(
                        "Tap a card to set its rewards balance and last four digits; the email monitor updates balances from issuer statement emails it can match.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(cards, key = { it.walletCard.id }) { card ->
                    WalletCardRow(card = card, onClick = { viewModel.openEditCard(card) }, onDelete = { pendingDelete = card })
                }
            }
        }
    }

    pendingDelete?.let { card ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove ${card.displayName}?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeCard(card.walletCard.id)
                    pendingDelete = null
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }

    if (editCardState.isOpen) {
        EditCardDialog(
            state = editCardState,
            onDismiss = viewModel::closeEditCard,
            onChange = viewModel::updateEditCard,
            onSave = viewModel::saveEditCard,
        )
    }

    if (addCardState.isOpen) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(onDismissRequest = viewModel::closeAddCard, sheetState = sheetState) {
            AddCardSheetContent(
                state = addCardState,
                onQueryChange = viewModel::onQueryChange,
                onPickCatalogCard = viewModel::addCatalogCard,
                onLookupCustom = viewModel::lookupCustomCard,
                onConfirmCustom = viewModel::confirmAddCustomCard,
            )
        }
    }
}

@Composable
private fun WalletCardRow(card: ResolvedWalletCard, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(card.displayName, style = MaterialTheme.typography.titleMedium)
                val subtitle = when (card) {
                    is ResolvedWalletCard.Catalog -> "${card.entry.issuer} • \$${card.entry.annualFeeUsd}/yr"
                    is ResolvedWalletCard.Custom -> card.lookup?.issuer ?: "Looked up card - tap to refresh in Settings"
                }
                Text(
                    subtitle + (card.walletCard.last4?.let { " • ••••$it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val balance = card.walletCard.rewardsBalance
                if (balance != null) {
                    Text(
                        "${String.format("%,d", balance)} ${card.rewardCurrency?.displayName ?: "rewards"}" +
                            (card.rewardsValueUsd?.let { " (~${String.format("$%,.0f", it)})" } ?: "") +
                            (card.walletCard.rewardsBalanceAsOf?.let { " • ${com.travelbenefits.app.ui.common.formatRelative(it)}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Text("Balance unknown - tap to enter", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove")
            }
        }
    }
}

@Composable
private fun EditCardDialog(
    state: EditCardUiState,
    onDismiss: () -> Unit,
    onChange: ((EditCardUiState) -> EditCardUiState) -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(state.displayName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.rewardsBalance,
                    onValueChange = { v -> onChange { it.copy(rewardsBalance = v) } },
                    label = { Text("Rewards balance" + (state.currencyName?.let { " ($it)" } ?: "")) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.last4,
                    onValueChange = { v -> onChange { it.copy(last4 = v) } },
                    label = { Text("Last 4 digits (to match statement emails)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.nickname,
                    onValueChange = { v -> onChange { it.copy(nickname = v) } },
                    label = { Text("Nickname") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.notes,
                    onValueChange = { v -> onChange { it.copy(notes = v) } },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = onSave) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AddCardSheetContent(
    state: AddCardUiState,
    onQueryChange: (String) -> Unit,
    onPickCatalogCard: (CardCatalogEntry) -> Unit,
    onLookupCustom: () -> Unit,
    onConfirmCustom: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Add a card", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            label = { Text("Card name") },
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.catalogResults.isNotEmpty()) {
            Text("From the built-in catalog:", style = MaterialTheme.typography.labelLarge)
            state.catalogResults.take(6).forEach { entry ->
                Card(onClick = { onPickCatalogCard(entry) }, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(entry.displayName, style = MaterialTheme.typography.bodyLarge)
                        Text("${entry.issuer} • \$${entry.annualFeeUsd}/yr", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        } else if (state.query.isNotBlank()) {
            Text("Not in the built-in catalog.", style = MaterialTheme.typography.bodyMedium)

            when {
                state.isLookingUp -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Searching the web for current benefits…")
                }
                state.lookupResult != null -> {
                    val result = state.lookupResult
                    Card {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(result.displayName, style = MaterialTheme.typography.titleMedium)
                            Text(result.summary, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "⚠ ${result.sourcesNote}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    Button(onClick = onConfirmCustom, modifier = Modifier.fillMaxWidth()) {
                        Text("Add this card")
                    }
                }
                state.lookupError != null -> {
                    Text(state.lookupError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = onLookupCustom, modifier = Modifier.fillMaxWidth()) { Text("Try again") }
                }
                else -> {
                    OutlinedButton(onClick = onLookupCustom, modifier = Modifier.fillMaxWidth()) {
                        Text("Look up \"${state.query}\" online")
                    }
                    TextButton(onClick = onConfirmCustom, modifier = Modifier.fillMaxWidth()) {
                        Text("Skip lookup, just add the name")
                    }
                }
            }
        }
    }
}
