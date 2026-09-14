package com.travelbenefits.app.ui.airport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.travelbenefits.app.ui.common.CaveatCard
import com.travelbenefits.app.ui.common.LabelValue
import com.travelbenefits.app.ui.common.formatDateRange
import com.travelbenefits.app.ui.common.formatRelative

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AirportModeScreen(onBack: () -> Unit, viewModel: AirportModeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(topBar = { TopAppBar(title = { Text("Airport mode") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } }) }) { padding ->
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize().padding(padding)) {
            item { CaveatCard("Everything here comes from data already on the phone (works offline; shown as of ${formatRelative(state.cachedAt)}). Live terminal, gate and lounge hours are not available - no provider is integrated - so check the airport or lounge app for those.") }
            if (state.trips.isEmpty()) item { Text("No flights in the next 48 hours.") }
            items(state.trips, key = { it.trip.id }) { at ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(at.trip.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        formatDateRange(at.trip.startEpochDay, at.trip.endEpochDay)?.let { LabelValue("Date", it + (at.trip.departureTimeLocal?.let { t -> " • $t ${at.trip.timeZoneId ?: ""}" } ?: "")) }
                        at.trip.confirmationNumber?.let { LabelValue("Confirmation", it) }
                        at.loyalty?.let { LabelValue("${it.program.displayName} #", it.membershipNumber ?: "not saved") }
                        at.segments.forEach { seg -> Text("${seg.carrier ?: ""} ${seg.flightNumber ?: ""} ${seg.origin ?: "?"} → ${seg.destination ?: "?"}" + (seg.departLocal?.let { " dep $it" } ?: ""), style = MaterialTheme.typography.bodySmall) }
                        if (at.lounges.isNotEmpty()) {
                            Text("Lounge access from your cards", style = MaterialTheme.typography.labelMedium)
                            at.lounges.forEach { l -> Text("${l.cardName}: ${l.perk.description}" + (l.perk.condition?.let { " - $it" } ?: "") + (if (l.isPaymentCard) " (paid with this card)" else ""), style = MaterialTheme.typography.bodySmall) }
                        }
                        if (at.bagPerks.isNotEmpty()) {
                            Text("Bag / boarding perks", style = MaterialTheme.typography.labelMedium)
                            at.bagPerks.forEach { l -> Text("${l.cardName}: ${l.perk.description}" + (l.perk.condition?.let { " - $it" } ?: ""), style = MaterialTheme.typography.bodySmall) }
                        }
                        Text("Lounge guest rules and airline-specific restrictions vary; bring the card itself and the boarding pass.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
