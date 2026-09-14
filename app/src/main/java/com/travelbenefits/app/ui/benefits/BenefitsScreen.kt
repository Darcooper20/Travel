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
                        "Credit lists come from the card catalog and reset on calendar periods (monthly/annual). Cards with anniversary-based credits may differ - adjust to your card's terms.",
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
            items(state.credits, key = { it.key }) { credit -> CreditRow(credit, today, onToggle = { viewModel.toggleCredit(credit) }) }
        }
    }

    if (edit.isOpen) {
        EditBenefitDialog(edit, state.cards, onDismiss = viewModel::closeEdit, onChange = viewModel::updateEdit, onSave = viewModel::saveEdit)
    }
}

@Composable
private fun CreditRow(credit: CreditStatus, today: Long, onToggle: () -> Unit) {
    val daysLeft = credit.periodEndsEpochDay - today
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (credit.isAvailable) CardDefaults.cardColors() else CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = !credit.isAvailable, onCheckedChange = { onToggle() })
            Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(credit.credit.label, style = MaterialTheme.typography.titleSmall)
                Text(credit.walletCard.displayName + " • " + credit.period.label + " • ~" + formatUsd(credit.periodValueUsd), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(credit.credit.description, style = MaterialTheme.typography.bodySmall)
                Text(
                    if (credit.isAvailable) "Unused - period ends ${formatEpochDay(credit.periodEndsEpochDay)} ($daysLeft days)" else "Used this period",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (credit.isAvailable && daysLeft <= 7) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

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
