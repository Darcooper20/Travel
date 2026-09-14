package com.travelbenefits.app.ui.reconcile

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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.travelbenefits.app.domain.ReconciliationEngine
import com.travelbenefits.app.domain.model.ExpectationStatus
import com.travelbenefits.app.ui.common.CaveatCard
import com.travelbenefits.app.ui.common.formatEpochDay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReconcileScreen(onBack: () -> Unit, viewModel: ReconcileViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val clipboard = LocalClipboardManager.current

    Scaffold(topBar = { TopAppBar(title = { Text("Expected vs received") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } }) }) { padding ->
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize().padding(padding)) {
            item { CaveatCard("Expectations are created when you record a booking or an eligible charge. Nothing is a discrepancy before its due date (posting delays are normal). Matches need evidence; anything unclear waits for you. Claim drafts are text for you to send - the app never submits them.") }
            if (state.rows.isEmpty()) item { Text("No expectations yet. Record a booking on a trip to start tracking what should post.") }
            items(state.rows, key = { it.expectation.id }) { row ->
                val e = row.expectation
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(e.kind.label + (row.tripTitle?.let { " • $it" } ?: row.cardName?.let { " • $it" } ?: ""), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text(e.status.label, style = MaterialTheme.typography.labelMedium, color = when (e.status) { ExpectationStatus.MATCHED -> MaterialTheme.colorScheme.primary; ExpectationStatus.DISCREPANCY -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.onSurfaceVariant })
                        }
                        Text("Expected ${"%,.0f".format(e.expectedAmount)} ${e.unit}" + (e.receivedAmount?.let { " • received ${"%,.0f".format(it)}" } ?: "") + " • due ${formatEpochDay(e.dueByEpochDay)}", style = MaterialTheme.typography.bodySmall)
                        e.note?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        e.evidence?.let { Text("Evidence: $it", style = MaterialTheme.typography.labelSmall) }
                        if (e.status == ExpectationStatus.OPEN) {
                            val suggestion = when (val o = row.outcome) {
                                is ReconciliationEngine.Outcome.Matched -> "Records suggest received (${o.evidence})."
                                is ReconciliationEngine.Outcome.Partial -> "Partially received: ${"%,.0f".format(o.received)} (${o.evidence})."
                                is ReconciliationEngine.Outcome.Discrepancy -> "Past due with no matching record (${o.evidence})."
                                ReconciliationEngine.Outcome.Waiting -> "Waiting - not yet due or no newer records."
                                ReconciliationEngine.Outcome.NoEvidence -> "No authoritative record to check against yet - confirm by hand."
                            }
                            Text(suggestion, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (row.outcome !is ReconciliationEngine.Outcome.Waiting && row.outcome !is ReconciliationEngine.Outcome.NoEvidence) TextButton(onClick = { viewModel.applyOutcome(row) }) { Text("Apply") }
                                TextButton(onClick = { viewModel.resolve(row, ExpectationStatus.MATCHED, e.expectedAmount, "Confirmed by you") }) { Text("Received") }
                                TextButton(onClick = { viewModel.resolve(row, ExpectationStatus.DISCREPANCY, null, "Flagged by you") }) { Text("Missing") }
                                TextButton(onClick = { viewModel.resolve(row, ExpectationStatus.DISMISSED) }) { Text("Dismiss") }
                            }
                        }
                        if (e.status == ExpectationStatus.DISCREPANCY) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { viewModel.openDraft(row) }) { Text("Claim draft") }
                                TextButton(onClick = { viewModel.resolve(row, ExpectationStatus.MATCHED, e.expectedAmount, "Resolved by you") }) { Text("Resolved") }
                            }
                        }
                    }
                }
            }
        }
    }

    state.draft?.let { d ->
        AlertDialog(
            onDismissRequest = viewModel::closeDraft,
            title = { Text(d.subject) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(d.body, style = MaterialTheme.typography.bodySmall)
                    Text("Attach:", style = MaterialTheme.typography.labelMedium)
                    d.checklist.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = { TextButton(onClick = { clipboard.setText(AnnotatedString(d.subject + "\n\n" + d.body)); viewModel.closeDraft() }) { Text("Copy") } },
            dismissButton = { TextButton(onClick = viewModel::closeDraft) { Text("Close") } },
        )
    }
}
