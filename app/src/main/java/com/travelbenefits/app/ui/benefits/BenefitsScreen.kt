package com.travelbenefits.app.ui.benefits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.travelbenefits.app.domain.model.BenefitItem
import com.travelbenefits.app.domain.model.BenefitKind
import com.travelbenefits.app.domain.model.CreditStatus
import com.travelbenefits.app.domain.model.CreditState
import com.travelbenefits.app.domain.model.LedgerEntryKind
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import com.travelbenefits.app.domain.model.LoyaltyProgram
import com.travelbenefits.app.domain.model.ResolvedWalletCard
import com.travelbenefits.app.ui.common.DropdownPicker
import com.travelbenefits.app.ui.common.SectionCard
import com.travelbenefits.app.ui.common.formatEpochDay
import com.travelbenefits.app.ui.common.formatUsd
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BenefitsScreen(onBack: () -> Unit, viewModel: BenefitsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val edit by viewModel.editState.collectAsState()
    val ledgerForm by viewModel.ledgerForm.collectAsState()
    val today = LocalDate.now().toEpochDay()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Credits & certificates") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
        floatingActionButton = { FloatingActionButton(onClick = { viewModel.openAdd() }) { Icon(Icons.Filled.Add, contentDescription = "Add certificate") } },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            item {
                SectionCard(title = "Unused this period") {
                    Text(formatUsd(state.unusedCreditValueUsd), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "in card credits still available" + if (state.expiringSoonCount > 0) " • ${state.expiringSoonCount} item(s) end within 30 days" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Amount-based ledger: record eligible charges, pending and posted credits, and reversals. Pending is never shown as received. Reset periods follow each credit's verified basis; assumed periods are marked.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item { Text("Certificates & vouchers", style = MaterialTheme.typography.titleMedium) }
            if (state.items.isEmpty()) {
                item { Text("None yet. Free-night awards, companion passes and upgrade certificates found in email land here; tap + to add one by hand.", style = MaterialTheme.typography.bodySmall) }
            }
            items(state.items, key = { "b-${it.id}" }) { item ->
                BenefitRow(item, today, onToggle = { viewModel.toggleItemUsed(item) }, onEdit = { viewModel.openAdd(item) }, onDelete = { viewModel.deleteItem(item.id) })
            }

            item { Text("Card credits", style = MaterialTheme.typography.titleMedium) }
            if (state.credits.isEmpty()) {
                item { Text("Add catalog cards on the Cards tab to see their credits here.", style = MaterialTheme.typography.bodySmall) }
            }
            items(state.credits, key = { it.key }) { credit ->
                CreditRow(
                    credit, today,
                    onRecord = { kind -> viewModel.openLedgerEntry(credit, kind) },
                    onResolve = { id, ok -> viewModel.resolveProposal(id, ok) },
                    onDeleteEntry = { id -> viewModel.deleteLedgerEntry(id) },
                )
            }
        }
    }

    if (ledgerForm.isOpen) {
        val f = ledgerForm
        AlertDialog(
            onDismissRequest = viewModel::closeLedgerEntry,
            title = { Text(f.credit?.credit?.label ?: "Record") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DropdownPicker(label = "Type", options = listOf(LedgerEntryKind.POSTED, LedgerEntryKind.PENDING, LedgerEntryKind.EXPECTED, LedgerEntryKind.REVERSED, LedgerEntryKind.ADJUSTMENT), selected = f.kind, optionLabel = { it.label }, onSelected = { k -> viewModel.updateLedgerEntry { it.copy(kind = k) } })
                    OutlinedTextField(value = f.amount, onValueChange = { v -> viewModel.updateLedgerEntry { it.copy(amount = v) } }, label = { Text("Amount (USD)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = f.date, onValueChange = { v -> viewModel.updateLedgerEntry { it.copy(date = v) } }, label = { Text("Date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = f.note, onValueChange = { v -> viewModel.updateLedgerEntry { it.copy(note = v) } }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(onClick = viewModel::saveLedgerEntry, enabled = f.amount.isNotBlank()) { Text("Save") } },
            dismissButton = { TextButton(onClick = viewModel::closeLedgerEntry) { Text("Cancel") } },
        )
    }

    if (edit.isOpen) {
        EditBenefitDialog(edit, state.cards, onDismiss = viewModel::closeEdit, onChange = viewModel::updateEdit, onSave = viewModel::saveEdit)
    }
}

@Composable
private fun CreditRow(credit: CreditStatus, today: Long, onRecord: (LedgerEntryKind) -> Unit, onResolve: (Long, Boolean) -> Unit, onDeleteEntry: (Long) -> Unit) {
    val daysLeft = credit.periodEndsEpochDay - today
    val done = credit.state == CreditState.RECEIVED || credit.state == CreditState.USED_AMOUNT_UNKNOWN
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (done) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant) else CardDefaults.cardColors(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(credit.credit.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(credit.state.label, style = MaterialTheme.typography.labelMedium, color = when (credit.state) {
                    CreditState.RECEIVED -> MaterialTheme.colorScheme.primary
                    CreditState.NEEDS_CONFIRMATION, CreditState.USED_AMOUNT_UNKNOWN, CreditState.EXPIRED, CreditState.REVERSED -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                })
            }
            Text(credit.walletCard.displayName + " • " + credit.period.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(credit.credit.description, style = MaterialTheme.typography.bodySmall)
            Text(
                "Allowance ${cents(credit.allowanceCents)} • received ${cents(credit.receivedCents)} • pending ${cents(credit.pendingCents)} • uncommitted ${if (credit.hasUnknownAmountUsage && credit.receivedCents == 0L) "unknown" else cents(credit.uncommittedCents)}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Period ${formatEpochDay(credit.window.startEpochDay)} – ${formatEpochDay(credit.window.endEpochDay)} (${credit.window.basis.label}${if (credit.window.isAssumed) ", assumed" else ""}) • ${if (daysLeft >= 0) "$daysLeft days left" else "ended"}",
                style = MaterialTheme.typography.labelSmall,
                color = if (credit.isAvailable && daysLeft in 0..7) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (credit.credit.requiresEnrollment) Text("Enrollment required before the credit applies.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
            credit.entries.forEach { e ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${e.kind.label}: ${if (e.kind == LedgerEntryKind.USED_UNKNOWN_AMOUNT) "amount unknown" else cents(e.amountCents)} • ${formatEpochDay(e.epochDay)}" + (if (e.needsConfirmation) " • proposed" else ""), style = MaterialTheme.typography.labelSmall)
                        e.note?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    if (e.needsConfirmation) {
                        TextButton(onClick = { onResolve(e.id, true) }) { Text("Confirm") }
                        TextButton(onClick = { onResolve(e.id, false) }) { Text("Not this") }
                    } else {
                        IconButton(onClick = { onDeleteEntry(e.id) }) { Icon(Icons.Filled.Delete, contentDescription = "Remove entry") }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onRecord(LedgerEntryKind.POSTED) }) { Text("Credit received") }
                TextButton(onClick = { onRecord(LedgerEntryKind.PENDING) }) { Text("Pending") }
                TextButton(onClick = { onRecord(LedgerEntryKind.EXPECTED) }) { Text("Charge made") }
            }
        }
    }
}

private fun cents(c: Long): String = String.format("$%,.2f", c / 100.0)

@Composable
private fun BenefitRow(item: BenefitItem, today: Long, onToggle: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val daysLeft = item.expiresEpochDay?.let { it - today }
    Card(onClick = onEdit, modifier = Modifier.fillMaxWidth(), colors = if (item.isUsed) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant) else CardDefaults.cardColors()) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = item.isUsed, onCheckedChange = { onToggle() })
            Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    listOfNotNull(item.kind.label, item.program?.displayName, item.valueUsd?.let { "~" + formatUsd(it) }).joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (daysLeft != null) {
                    Text(
                        if (daysLeft < 0) "Expired ${formatEpochDay(item.expiresEpochDay)}" else "Expires ${formatEpochDay(item.expiresEpochDay)} ($daysLeft days)",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (!item.isUsed && daysLeft <= 30) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                item.sourceEmailSubject?.let { Text("From email: \"$it\"", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Remove") }
        }
    }
}

@Composable
private fun EditBenefitDialog(
    state: EditBenefitState,
    cards: List<ResolvedWalletCard>,
    onDismiss: () -> Unit,
    onChange: ((EditBenefitState) -> EditBenefitState) -> Unit,
    onSave: () -> Unit,
) {
    val programOptions: List<LoyaltyProgram?> = listOf<LoyaltyProgram?>(null) + LoyaltyProgram.entries
    val cardOptions: List<ResolvedWalletCard?> = listOf<ResolvedWalletCard?>(null) + cards
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (state.id == 0L) "Add certificate" else "Edit certificate") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                DropdownPicker(label = "Type", options = BenefitKind.entries, selected = state.kind, optionLabel = { it.label }, onSelected = { k -> onChange { it.copy(kind = k) } })
                OutlinedTextField(value = state.title, onValueChange = { v -> onChange { it.copy(title = v) } }, label = { Text("Name (e.g. Free Night Award up to 35k)") }, modifier = Modifier.fillMaxWidth())
                DropdownPicker(label = "Program", options = programOptions, selected = state.program, optionLabel = { it?.displayName ?: "None" }, onSelected = { p -> onChange { it.copy(program = p) } })
                DropdownPicker(label = "From card", options = cardOptions, selected = cards.firstOrNull { it.walletCard.id == state.walletCardId }, optionLabel = { it?.displayName ?: "None" }, onSelected = { c -> onChange { it.copy(walletCardId = c?.walletCard?.id) } })
                OutlinedTextField(value = state.valueUsd, onValueChange = { v -> onChange { it.copy(valueUsd = v) } }, label = { Text("Approximate value (USD)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = state.expiresOn, onValueChange = { v -> onChange { it.copy(expiresOn = v) } }, label = { Text("Expires (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = state.notes, onValueChange = { v -> onChange { it.copy(notes = v) } }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = onSave, enabled = state.title.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
