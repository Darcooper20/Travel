package com.travelbenefits.app.ui.hotels

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
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.travelbenefits.app.domain.model.LoyaltyProgram
import com.travelbenefits.app.domain.model.LoyaltyAccount

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HotelsScreen(onOpenSettings: () -> Unit, viewModel: HotelsViewModel = hiltViewModel()) {
    val accounts by viewModel.accounts.collectAsState()
    val isGmailConnected by viewModel.isGmailConnected.collectAsState()
    val scanState by viewModel.scanState.collectAsState()
    val editState by viewModel.editState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<LoyaltyAccount?>(null) }

    LaunchedEffect(scanState) {
        when (val s = scanState) {
            is ScanState.Done -> {
                snackbarHostState.showSnackbar("Found ${s.count} loyalty account update(s).")
                viewModel.dismissScanState()
            }
            is ScanState.Error -> {
                snackbarHostState.showSnackbar(s.message)
                viewModel.dismissScanState()
            }
            else -> Unit
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Loyalty programs") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.openAdd() }) {
                Icon(Icons.Filled.Add, contentDescription = "Add loyalty account")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Card(modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Scan Gmail for hotel & airline membership numbers and status", style = MaterialTheme.typography.titleMedium)
                    if (!isGmailConnected) {
                        Text("Connect your Gmail account in Settings first.", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = onOpenSettings) { Text("Go to Settings") }
                    } else {
                        when (val s = scanState) {
                            is ScanState.InProgress -> Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(s.message)
                            }
                            else -> Button(onClick = viewModel::startScan) {
                                Icon(Icons.Filled.Sync, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Scan now")
                            }
                        }
                    }
                }
            }

            if (accounts.isEmpty()) {
                Text(
                    "No loyalty accounts yet. Add one manually or scan Gmail above.",
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(accounts, key = { it.id }) { account ->
                        LoyaltyAccountRow(
                            account = account,
                            onClick = { viewModel.openAdd(account) },
                            onDelete = { pendingDelete = account },
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { account ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove ${account.program.displayName}?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(account.id)
                    pendingDelete = null
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }

    if (editState.isOpen) {
        EditAccountDialog(
            state = editState,
            onDismiss = viewModel::closeEdit,
            onChange = viewModel::updateEdit,
            onSave = viewModel::saveEdit,
        )
    }
}

@Composable
private fun LoyaltyAccountRow(account: LoyaltyAccount, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(account.program.displayName, style = MaterialTheme.typography.titleMedium)
                val details = listOfNotNull(
                    account.tier?.let { "Status: $it" },
                    account.membershipNumber?.let { "#$it" },
                    account.pointsBalance?.let { it },
                ).joinToString(" • ")
                Text(
                    details.ifBlank { "No details yet" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Remove") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditAccountDialog(
    state: EditAccountState,
    onDismiss: () -> Unit,
    onChange: ((EditAccountState) -> EditAccountState) -> Unit,
    onSave: () -> Unit,
) {
    var programMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Loyalty account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(expanded = programMenuExpanded, onExpandedChange = { programMenuExpanded = it }) {
                    OutlinedTextField(
                        value = state.program.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Program") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = programMenuExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                    )
                    DropdownMenu(expanded = programMenuExpanded, onDismissRequest = { programMenuExpanded = false }) {
                        LoyaltyProgram.entries.forEach { program ->
                            DropdownMenuItem(
                                text = { Text(program.displayName) },
                                onClick = {
                                    onChange { it.copy(program = program) }
                                    programMenuExpanded = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = state.membershipNumber,
                    onValueChange = { v -> onChange { it.copy(membershipNumber = v) } },
                    label = { Text("Membership number") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.tier,
                    onValueChange = { v -> onChange { it.copy(tier = v) } },
                    label = { Text("Status / tier") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.pointsBalance,
                    onValueChange = { v -> onChange { it.copy(pointsBalance = v) } },
                    label = { Text("Points balance") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = onSave) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
