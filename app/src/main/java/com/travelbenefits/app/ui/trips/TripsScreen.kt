package com.travelbenefits.app.ui.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.travelbenefits.app.domain.model.LoyaltyProgram
import com.travelbenefits.app.domain.model.Trip
import com.travelbenefits.app.domain.model.TripKind
import com.travelbenefits.app.ui.common.DropdownPicker
import com.travelbenefits.app.ui.common.formatDateRange
import com.travelbenefits.app.ui.common.formatPoints

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripsScreen(viewModel: TripsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val addState by viewModel.addState.collectAsState()
    var pendingDelete by remember { mutableStateOf<Trip?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Trips") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::openAdd) { Icon(Icons.Filled.Add, contentDescription = "Add trip") }
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.upcoming.isEmpty() && state.past.isEmpty()) {
                item {
                    Text(
                        "No trips yet. The email monitor adds bookings it finds in confirmation emails (flights, hotels, cars, rail); you can also add one by hand.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (state.upcoming.isNotEmpty()) {
                item { Text("Upcoming", style = MaterialTheme.typography.titleMedium) }
                items(state.upcoming, key = { it.trip.id }) { row ->
                    TripCard(row, onMarkAttached = { viewModel.markLoyaltyAttached(row.trip) }, onDelete = { pendingDelete = row.trip })
                }
            }
            if (state.past.isNotEmpty()) {
                item { Text("Past", style = MaterialTheme.typography.titleMedium) }
                items(state.past, key = { it.trip.id }) { row ->
                    TripCard(row, onMarkAttached = { viewModel.markLoyaltyAttached(row.trip) }, onDelete = { pendingDelete = row.trip })
                }
            }
        }
    }

    pendingDelete?.let { trip ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove trip?") },
            text = { Text(trip.title) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(trip.id)
                    pendingDelete = null
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }

    if (addState.isOpen) {
        AddTripDialog(addState, onDismiss = viewModel::closeAdd, onChange = viewModel::updateAdd, onSave = viewModel::saveAdd)
    }
}

@Composable
private fun TripCard(row: TripRowState, onMarkAttached: () -> Unit, onDelete: () -> Unit) {
    val trip = row.trip
    val icon = when (trip.kind) {
        TripKind.FLIGHT -> Icons.Filled.Flight
        TripKind.HOTEL -> Icons.Filled.Hotel
        TripKind.CAR -> Icons.Filled.DirectionsCar
        TripKind.RAIL -> Icons.Filled.Train
        TripKind.OTHER -> Icons.Filled.Luggage
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = trip.kind.label, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(trip.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        listOfNotNull(formatDateRange(trip.startEpochDay, trip.endEpochDay), trip.provider.takeIf { !trip.title.contains(it, ignoreCase = true) }).joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Remove") }
            }
            val facts = listOfNotNull(
                trip.confirmationNumber?.let { "Confirmation $it" },
                trip.destination?.takeIf { trip.kind != TripKind.HOTEL || !trip.title.contains(it, ignoreCase = true) },
                trip.totalCost,
                trip.pointsUsed?.let { "${formatPoints(it)} points redeemed" },
                trip.pointsEarnedEstimate?.let { "~${formatPoints(it)} points to earn" },
            )
            if (facts.isNotEmpty()) Text(facts.joinToString(" • "), style = MaterialTheme.typography.bodySmall)
            row.earningNote?.let { note ->
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (row.earningIsProblem) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (row.earningIsProblem && trip.loyaltyProgram != null) {
                TextButton(onClick = onMarkAttached) { Text("I've added my number to this booking") }
            }
            trip.sourceEmailSubject?.let {
                Text("From email: \"$it\"", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            trip.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun AddTripDialog(
    state: AddTripState,
    onDismiss: () -> Unit,
    onChange: ((AddTripState) -> AddTripState) -> Unit,
    onSave: () -> Unit,
) {
    val programOptions: List<LoyaltyProgram?> = listOf<LoyaltyProgram?>(null) + LoyaltyProgram.entries
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a trip") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                DropdownPicker(label = "Type", options = TripKind.entries, selected = state.kind, optionLabel = { it.label }, onSelected = { k -> onChange { it.copy(kind = k) } })
                OutlinedTextField(value = state.provider, onValueChange = { v -> onChange { it.copy(provider = v) } }, label = { Text("Airline / hotel / company") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = state.title, onValueChange = { v -> onChange { it.copy(title = v) } }, label = { Text("Title (optional)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = state.destination, onValueChange = { v -> onChange { it.copy(destination = v) } }, label = { Text("Destination / property") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = state.confirmationNumber, onValueChange = { v -> onChange { it.copy(confirmationNumber = v) } }, label = { Text("Confirmation number") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = state.startDate, onValueChange = { v -> onChange { it.copy(startDate = v) } }, label = { Text("Start (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = state.endDate, onValueChange = { v -> onChange { it.copy(endDate = v) } }, label = { Text("End (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
                DropdownPicker(
                    label = "Loyalty program",
                    options = programOptions,
                    selected = state.program,
                    optionLabel = { it?.displayName ?: "None / unknown" },
                    onSelected = { p -> onChange { it.copy(program = p) } },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = state.loyaltyNumberOnBooking, onCheckedChange = { c -> onChange { it.copy(loyaltyNumberOnBooking = c) } })
                    Text("My member number is on the booking", style = MaterialTheme.typography.bodyMedium)
                }
                OutlinedTextField(value = state.notes, onValueChange = { v -> onChange { it.copy(notes = v) } }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = onSave, enabled = state.provider.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
