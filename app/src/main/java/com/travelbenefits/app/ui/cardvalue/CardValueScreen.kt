package com.travelbenefits.app.ui.cardvalue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.travelbenefits.app.data.repository.OverrideRepository
import com.travelbenefits.app.domain.model.CardValueAnalysis
import com.travelbenefits.app.domain.model.ResolvedWalletCard
import com.travelbenefits.app.ui.common.CaveatCard
import com.travelbenefits.app.ui.common.LabelValue
import com.travelbenefits.app.ui.common.SectionCard
import com.travelbenefits.app.ui.common.formatEpochDay
import com.travelbenefits.app.ui.common.formatUsd

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardValueScreen(onBack: () -> Unit, viewModel: CardValueViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Card value & renewals") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } }) }) { padding ->
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                CaveatCard("Realized value uses only records (mapped transactions, posted credits, used certificates, perks you valued). Forecast uses run-rates and says so. Nothing here makes a cancellation automatically safe - read the warnings.")
            }
            if (state.analyses.isEmpty()) item { Text("Add catalog cards to see their value. Card value needs a date opened (for renewals) and, ideally, a linked Plaid account for spend.") }
            items(state.analyses, key = { it.card.walletCard.id }) { a -> AnalysisCard(a, state.overrides, viewModel) }
            if (state.duplicates.isNotEmpty()) {
                item {
                    SectionCard(title = "Household duplicates") {
                        state.duplicates.forEach { d ->
                            Text("${d.label}: ${d.cards.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text("Same benefit on several cards: one may be enough, but check guest rules and who holds each card before cutting.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalysisCard(a: CardValueAnalysis, overrides: Map<Pair<String, String>, String>, viewModel: CardValueViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val card = a.card
    val scope = "card:${card.walletCard.id}"
    Card(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(card.displayName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text((if (a.realizedNetUsd >= 0) "+" else "-") + formatUsd(kotlin.math.abs(a.realizedNetUsd)), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = if (a.realizedNetUsd >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            }
            Text("Realized last 12 months (fee $${a.annualFeeUsd})" + (a.renewalEpochDay?.let { " • renews ${formatEpochDay(it)}" } ?: " • set date opened for renewal"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(a.dataCoverage, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (expanded) {
                Text("Realized", style = MaterialTheme.typography.labelMedium)
                a.realizedLines.forEach { LabelValue(it.label + (if (it.isEstimate) " (est.)" else "") + (it.note?.let { n -> " - $n" } ?: ""), formatUsd(it.amountUsd)) }
                Text("Forecast next 12 months: ${formatUsd(a.forecastNetUsd)}", style = MaterialTheme.typography.labelMedium)
                a.forecastLines.forEach { LabelValue(it.label + (if (it.isEstimate) " (est.)" else ""), formatUsd(it.amountUsd)) }
                a.firstYearBonusUsd?.let { LabelValue("First-year welcome bonus (separate)", formatUsd(it)) }
                Text("Scenarios", style = MaterialTheme.typography.labelMedium)
                a.scenarios.forEach { sc ->
                    Text("${sc.name}: ${formatUsd(sc.netUsd)}/yr - ${sc.effects.joinToString(" ")}", style = MaterialTheme.typography.bodySmall)
                }
                a.scenarios.flatMap { it.warnings }.distinct().forEach { Text("⚠ $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
                a.assumptions.forEach { Text("Assumption: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text("Your inputs", style = MaterialTheme.typography.labelMedium)
                OverrideField("Authorized users (count)", overrides[scope to OverrideRepository.KEY_AU_COUNT].orEmpty()) { viewModel.setOverride(card, OverrideRepository.KEY_AU_COUNT, it) }
                OverrideField("Welcome bonus points earned", overrides[scope to OverrideRepository.KEY_BONUS_POINTS].orEmpty()) { viewModel.setOverride(card, OverrideRepository.KEY_BONUS_POINTS, it) }
                (card as? ResolvedWalletCard.Catalog)?.entry?.travelPerks?.map { it.kind }?.distinct()?.forEach { kind ->
                    OverrideField("Value of ${kind.label.lowercase()} to you ($/yr)", overrides[scope to "perk:${kind.name}"].orEmpty()) { viewModel.setPerkValue(card, kind, it) }
                }
            }
        }
    }
}

@Composable
private fun OverrideField(label: String, value: String, onChange: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text(label) }, modifier = Modifier.weight(1f))
        TextButton(onClick = { onChange(text) }) { Text("Save") }
    }
}
