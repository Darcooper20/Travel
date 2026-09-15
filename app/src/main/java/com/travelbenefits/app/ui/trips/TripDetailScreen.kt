package com.travelbenefits.app.ui.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.travelbenefits.app.domain.model.BenefitItem
import com.travelbenefits.app.domain.model.BookingChannel
import com.travelbenefits.app.domain.model.LoyaltyNumberState
import com.travelbenefits.app.domain.model.LoyaltyProgram
import com.travelbenefits.app.domain.model.ResolvedWalletCard
import com.travelbenefits.app.domain.model.TripStatus
import com.travelbenefits.app.ui.common.CaveatCard
import com.travelbenefits.app.ui.common.DropdownPicker
import com.travelbenefits.app.ui.common.LabelValue
import com.travelbenefits.app.ui.common.SectionCard
import com.travelbenefits.app.ui.common.formatDateRange
import com.travelbenefits.app.ui.common.formatRelative
import com.travelbenefits.app.ui.common.formatUsd

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(onBack: () -> Unit, viewModel: TripDetailViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() } }
    val trip = state.trip
    val f = state.form

    Scaffold(
        topBar = { TopAppBar(title = { Text(trip?.title ?: "Trip") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (trip == null) { Text("Loading…", modifier = Modifier.padding(padding).padding(16.dp)); return@Scaffold }
        val programOptions: List<LoyaltyProgram?> = listOf<LoyaltyProgram?>(null) + LoyaltyProgram.entries
        val cardOptions: List<ResolvedWalletCard?> = listOf<ResolvedWalletCard?>(null) + state.cards
        val certOptions: List<BenefitItem?> = listOf<BenefitItem?>(null) + state.certificates
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                SectionCard(title = null) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(trip.kind.label + " • " + trip.provider, style = MaterialTheme.typography.titleSmall)
                        Text(trip.status.label, style = MaterialTheme.typography.labelMedium, color = if (trip.isCancelled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                    formatDateRange(trip.startEpochDay, trip.endEpochDay)?.let { LabelValue("Dates", it + (trip.nights?.let { n -> " ($n nights)" } ?: "")) }
                    trip.confirmationNumber?.let { LabelValue("Confirmation", it) }
                    trip.destination?.let { LabelValue("Destination", it) }
                    trip.totalCost?.let { LabelValue("Cost (email)", it) }
                    trip.cancellationTerms?.let { LabelValue("Cancellation terms", it) }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (!trip.isCancelled) TextButton(onClick = { viewModel.setStatus(TripStatus.CANCELLED) }) { Text("Mark cancelled") } else TextButton(onClick = { viewModel.setStatus(TripStatus.CONFIRMED) }) { Text("Reinstate") }
                        if (!trip.isCancelled && trip.status != TripStatus.PARTIALLY_CANCELLED) TextButton(onClick = { viewModel.setStatus(TripStatus.PARTIALLY_CANCELLED) }) { Text("Partially cancelled") }
                    }
                }
            }
            if (state.segments.isNotEmpty()) {
                item {
                    SectionCard(title = "Segments") {
                        state.segments.forEach { seg ->
                            Text("${seg.carrier ?: ""} ${seg.flightNumber ?: ""}: ${seg.origin ?: "?"} → ${seg.destination ?: "?"}" + (seg.departLocal?.let { " • dep $it" } ?: "") + (seg.arriveLocal?.let { " • arr $it" } ?: "") + (seg.cabin?.let { " • $it" } ?: "") + (if (seg.status != TripStatus.CONFIRMED) " • ${seg.status.label}" else ""), style = MaterialTheme.typography.bodySmall)
                        }
                        Text("Times are local to each airport as stated in the email; zones: ${state.segments.mapNotNull { it.timeZoneId }.distinct().joinToString().ifBlank { "not stated" }}.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                SectionCard(title = "Loyalty number on the reservation") {
                    DropdownPicker(label = "Program", options = programOptions, selected = trip.loyaltyProgram, optionLabel = { it?.displayName ?: "None" }, onSelected = { p -> viewModel.setLoyaltyState(trip.loyaltyNumberState, p) })
                    DropdownPicker(label = "Number status", options = LoyaltyNumberState.entries, selected = trip.loyaltyNumberState, optionLabel = { it.label }, onSelected = { st -> viewModel.setLoyaltyState(st, trip.loyaltyProgram) })
                    Text("UNKNOWN means the email didn't show it - not that it's missing. Check the reservation and confirm.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                SectionCard(title = "Payment & booking") {
                    OutlinedTextField(value = f.cashPrice, onValueChange = { v -> viewModel.updateForm { it.copy(cashPrice = v) } }, label = { Text("Cash price all-in (USD)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = f.pointsRequired, onValueChange = { v -> viewModel.updateForm { it.copy(pointsRequired = v) } }, label = { Text("Points required (if paying with points)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = f.awardFees, onValueChange = { v -> viewModel.updateForm { it.copy(awardFees = v) } }, label = { Text("Award taxes/fees (USD)") }, modifier = Modifier.fillMaxWidth())
                    DropdownPicker(label = "Booking channel", options = BookingChannel.entries, selected = f.channel, optionLabel = { it.label }, onSelected = { c -> viewModel.updateForm { it.copy(channel = c) } })
                    DropdownPicker(label = "Paid with card", options = cardOptions, selected = state.cards.firstOrNull { it.walletCard.id == f.paymentCardId }, optionLabel = { it?.displayName ?: "Not set" }, onSelected = { c -> viewModel.updateForm { it.copy(paymentCardId = c?.walletCard?.id) } })
                    DropdownPicker(label = "Certificate applied", options = certOptions, selected = state.certificates.firstOrNull { it.id == f.certificateId }, optionLabel = { it?.title ?: "None" }, onSelected = { c -> viewModel.updateForm { it.copy(certificateId = c?.id) } })
                    OutlinedTextField(value = f.travelers, onValueChange = { v -> viewModel.updateForm { it.copy(travelers = v) } }, label = { Text("Travelers") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = f.cancellationTerms, onValueChange = { v -> viewModel.updateForm { it.copy(cancellationTerms = v) } }, label = { Text("Cancellation terms") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = f.departureTime, onValueChange = { v -> viewModel.updateForm { it.copy(departureTime = v) } }, label = { Text("Departure/check-in (HH:mm)") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = f.timeZone, onValueChange = { v -> viewModel.updateForm { it.copy(timeZone = v) } }, label = { Text("Zone (e.g. America/New_York)") }, modifier = Modifier.weight(1f))
                    }
                    Button(onClick = viewModel::recordBooking) { Text("Record booking & expectations") }
                }
            }
            f.comparison?.let { cmp ->
                item {
                    SectionCard(title = "Ways to pay") {
                        cmp.caveats.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        cmp.cashOptions.take(3).forEachIndexed { i, o ->
                            Card(modifier = Modifier.fillMaxWidth(), colors = if (i == 0) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.cardColors()) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Cash on ${o.card.displayName}: ~${formatUsd(o.netValueUsd)} back (${o.effectiveRateLabel}, ${o.confidence.label.lowercase()})", style = MaterialTheme.typography.bodyMedium)
                                    o.conditions.forEach { Text("Condition: $it", style = MaterialTheme.typography.labelSmall) }
                                    o.warnings.forEach { Text("⚠ $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
                                }
                            }
                        }
                        cmp.pointsResult?.let { r ->
                            Text("Points: ${r.recommendation.name.replace('_', ' ').lowercase()} - ${"%.2f".format(r.centsPerPoint)}¢/pt vs ~${r.benchmarkCentsPerPoint}¢; net ${if (r.netAdvantageOfPointsUsd >= 0) "+" else "-"}${formatUsd(kotlin.math.abs(r.netAdvantageOfPointsUsd))} for points after forgone earnings.", style = MaterialTheme.typography.bodyMedium)
                            Text(r.explanation, style = MaterialTheme.typography.bodySmall)
                        } ?: Text("Enter points required to compare a points booking.", style = MaterialTheme.typography.labelSmall)
                        cmp.statusNote?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary) }
                        cmp.certificates.forEach { c -> Text("${if (c.fits) "✓" else "✗"} ${c.item.title}: ${c.note}", style = MaterialTheme.typography.bodySmall) }
                        if (cmp.perksForBestCard.isNotEmpty()) {
                            Text("Perks if paid with ${cmp.cashOptions.first().card.displayName}", style = MaterialTheme.typography.labelMedium)
                            cmp.perksForBestCard.forEach { p -> Text("${p.kind.label}: ${p.description}" + (p.condition?.let { " - applies $it" } ?: " - condition not verified"), style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
                val paidCard = state.cards.firstOrNull { it.walletCard.id == f.paymentCardId } as? ResolvedWalletCard.Catalog
                val guides = paidCard?.entry?.travelPerks?.mapNotNull { com.travelbenefits.app.domain.ProtectionGuide.guide(it.kind)?.let { g -> it to g } }.orEmpty()
                if (guides.isNotEmpty()) item {
                    SectionCard(title = "Protection & claims (paid with ${paidCard!!.displayName})") {
                        Text("Coverage depends on the benefits guide and on the fare being charged to this card; the card name alone guarantees nothing.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        guides.forEach { (perk, g) ->
                            Text(perk.kind.label, style = MaterialTheme.typography.labelMedium)
                            Text(perk.description + (perk.condition?.let { " - $it" } ?: ""), style = MaterialTheme.typography.bodySmall)
                            Text("Deadline: ${g.typicalDeadline}", style = MaterialTheme.typography.labelSmall)
                            Text("Documents: ${g.documents.joinToString("; ")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                SectionCard(title = "History") {
                    if (state.events.isEmpty()) Text("No history yet.", style = MaterialTheme.typography.bodySmall)
                    state.events.forEach { e -> Text("${formatRelative(e.occurredAt)} • ${e.kind.label}: ${e.detail} (${e.source})", style = MaterialTheme.typography.bodySmall) }
                }
            }
            item { CaveatCard("Rewards and perks shown are estimates from catalog rules; the issuer's posting and benefit guide decide. Reconciliation compares what you expected with what actually posts.") }
        }
    }
}
