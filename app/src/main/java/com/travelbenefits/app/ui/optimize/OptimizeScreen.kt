package com.travelbenefits.app.ui.optimize

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.travelbenefits.app.domain.model.CashVsPointsResult
import com.travelbenefits.app.domain.model.LoyaltyProgram
import com.travelbenefits.app.domain.model.LoyaltyProgramKind
import com.travelbenefits.app.domain.model.ResolvedWalletCard
import com.travelbenefits.app.domain.model.SpendingCategory
import com.travelbenefits.app.ui.common.CaveatCard
import com.travelbenefits.app.ui.common.DropdownPicker
import com.travelbenefits.app.ui.common.LabelValue
import com.travelbenefits.app.ui.common.SectionCard
import com.travelbenefits.app.ui.common.formatMultiplier
import com.travelbenefits.app.ui.common.formatPoints
import com.travelbenefits.app.ui.common.formatUsd

private val tabs = listOf("Earn", "Redeem", "Transfer", "Ask")

/** Programs that card points can actually be earned into or transferred to - shops/dining never qualify. */
private val travelPrograms: List<LoyaltyProgram> = LoyaltyProgram.entries.filter { it.kind != LoyaltyProgramKind.SHOP }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptimizeScreen(viewModel: OptimizeViewModel = hiltViewModel()) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(topBar = { TopAppBar(title = { Text("Maximize points") }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { index, title ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) })
                }
            }
            when (tab) {
                0 -> EarnTab(viewModel)
                1 -> RedeemTab(viewModel)
                2 -> TransferTab(viewModel)
                else -> AskTab(viewModel)
            }
        }
    }
}

// ---------------------------------------------------------------- Earn

@Composable
private fun EarnTab(viewModel: OptimizeViewModel) {
    val state by viewModel.earnState.collectAsState()
    val programOptions: List<LoyaltyProgram?> = listOf<LoyaltyProgram?>(null) + travelPrograms

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
        item {
            DropdownPicker(label = "Spending category", options = SpendingCategory.entries, selected = state.category, optionLabel = { it.label }, onSelected = viewModel::selectEarnCategory)
        }
        item {
            DropdownPicker(
                label = "Goal",
                options = programOptions,
                selected = state.targetProgram,
                optionLabel = { it?.let { p -> "Most ${p.displayName} points" } ?: "Most overall value" },
                onSelected = viewModel::selectEarnTarget,
            )
        }
        val target = state.targetProgram
        if (target == null) {
            if (state.byValue.isEmpty()) item { Text("Add cards on the Cards tab to get a recommendation.") }
            items(state.byValue, key = { it.walletCard.id }) { entry ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(entry.displayName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text(
                                if (entry.estimatedValueCentsPerDollar.isFinite()) String.format("%.1f¢/$", entry.estimatedValueCentsPerDollar) else "n/a",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Text(
                            formatMultiplier(entry.multiplier, entry.rewardCurrency.displayAsPercent) + " " + entry.rewardCurrency.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(entry.reason, style = MaterialTheme.typography.bodySmall)
                        if (entry.isEstimate) Text("Estimate - benefit data came from a live lookup.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
        } else {
            val profile = viewModel.profileFor(target)
            item {
                CaveatCard(
                    "Ranked by ${target.displayName} points per dollar, counting co-brand cards directly and bank points at their transfer ratio. " +
                        "At the app's ~${profile.estValueCentsPerPoint}¢/pt estimate; transfer ratios are a snapshot - confirm before moving points.",
                )
            }
            if (state.byProgram.isEmpty()) item { Text("Add cards on the Cards tab first.") }
            items(state.byProgram, key = { it.walletCard.walletCard.id }) { entry ->
                val ppd = entry.programPointsPerDollar
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = if (ppd == null) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant) else CardDefaults.cardColors(),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(entry.walletCard.displayName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text(
                                ppd?.let { String.format("%.1f pts/$", it) } ?: "—",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Text(
                            "Card earns ${formatMultiplier(entry.cardMultiplier, entry.cardCurrency.displayAsPercent)} ${entry.cardCurrency.displayName} on ${entry.category.label.lowercase()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(entry.via, style = MaterialTheme.typography.bodySmall)
                        ppd?.let { Text("≈ ${String.format("%.1f", it * profile.estValueCentsPerPoint)}¢ of value per $", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
            item {
                val eliteCards = viewModel.cards.collectAsState().value.filterIsInstance<ResolvedWalletCard.Catalog>()
                    .flatMap { c -> c.entry.loyaltyBenefits.filter { it.program == target }.map { c.displayName to it } }
                if (eliteCards.isNotEmpty()) {
                    SectionCard(title = "Status from your cards") {
                        eliteCards.forEach { (card, benefit) ->
                            Text("$card: ${benefit.benefit}", style = MaterialTheme.typography.bodyMedium)
                            Text(benefit.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------- Redeem

@Composable
private fun RedeemTab(viewModel: OptimizeViewModel) {
    val state by viewModel.redeemState.collectAsState()
    val cards by viewModel.cards.collectAsState()
    val cardOptions: List<ResolvedWalletCard?> = listOf<ResolvedWalletCard?>(null) + cards

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                "Found a booking you could pay with points? Enter both prices and the app works out the cents-per-point, what a paid booking would have earned, and which is the better deal.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            DropdownPicker(label = "Program", options = LoyaltyProgram.entries, selected = state.program, optionLabel = { "${it.displayName} (${it.kind.label})" }, onSelected = { p -> viewModel.updateRedeem { it.copy(program = p) } })
        }
        item {
            OutlinedTextField(
                value = state.cashPrice,
                onValueChange = { v -> viewModel.updateRedeem { it.copy(cashPrice = v) } },
                label = { Text("Cash price, all-in (USD)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = state.pointsRequired,
                onValueChange = { v -> viewModel.updateRedeem { it.copy(pointsRequired = v) } },
                label = { Text("Points / miles required") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = state.awardFees,
                onValueChange = { v -> viewModel.updateRedeem { it.copy(awardFees = v) } },
                label = { Text("Taxes/fees on the award (USD)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            DropdownPicker(
                label = "Card you'd pay cash with",
                options = cardOptions,
                selected = cards.firstOrNull { it.walletCard.id == state.payWithCardId },
                optionLabel = { it?.displayName ?: "Not sure / none" },
                onSelected = { c -> viewModel.updateRedeem { it.copy(payWithCardId = c?.walletCard?.id) } },
            )
        }
        item {
            Button(onClick = viewModel::evaluateRedeem, enabled = state.cashPrice.isNotBlank() && state.pointsRequired.isNotBlank()) { Text("Compare") }
        }
        state.result?.let { result ->
            item { ResultCard(result) }
            item {
                val context = LocalContext.current
                val profile = viewModel.profileFor(result.input.program)
                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(profile.awardSearchUrl))) }) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Open ${result.input.program.displayName} award search")
                }
            }
        }
    }
}

@Composable
private fun ResultCard(result: CashVsPointsResult) {
    val (headline, color) = when (result.recommendation) {
        CashVsPointsResult.Recommendation.USE_POINTS -> "Use points" to MaterialTheme.colorScheme.primary
        CashVsPointsResult.Recommendation.PAY_CASH -> "Pay cash, keep the points" to MaterialTheme.colorScheme.tertiary
        CashVsPointsResult.Recommendation.TOSS_UP -> "Toss-up" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(headline, style = MaterialTheme.typography.headlineSmall, color = color, fontWeight = FontWeight.SemiBold)
            LabelValue("Value per point", String.format("%.2f¢ (benchmark ~%.1f¢)", result.centsPerPoint, result.benchmarkCentsPerPoint))
            LabelValue("Points you'd redeem", formatPoints(result.input.pointsRequired))
            if (result.programPointsForgone > 0) LabelValue("Program points a paid booking earns", formatPoints(result.programPointsForgone))
            if (result.cardRewardsForgoneUsd > 0) LabelValue("Card rewards a paid booking earns", "~" + formatUsd(result.cardRewardsForgoneUsd))
            LabelValue("Net edge for points", (if (result.netAdvantageOfPointsUsd >= 0) "+" else "-") + formatUsd(kotlin.math.abs(result.netAdvantageOfPointsUsd)))
            Text(result.explanation, style = MaterialTheme.typography.bodySmall)
            Text(
                "Uses the app's point-value estimates and base earning rates; award stays usually don't earn points or count toward status.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ------------------------------------------------------------ Transfer

@Composable
private fun TransferTab(viewModel: OptimizeViewModel) {
    val state by viewModel.transferState.collectAsState()
    val profile = viewModel.profileFor(state.program)
    val context = LocalContext.current

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
        item {
            DropdownPicker(label = "Top up which program?", options = travelPrograms, selected = state.program, optionLabel = { it.displayName }, onSelected = viewModel::selectTransferProgram)
        }
        item {
            SectionCard(title = state.program.displayName) {
                LabelValue("Current balance", state.currentBalance?.let { formatPoints(it) } ?: "unknown")
                LabelValue("App's value estimate", "~${profile.estValueCentsPerPoint}¢ per point")
                profile.awardBand?.let { LabelValue("Typical award night", "${formatPoints(it.typicalNightPoints.toLong())} pts (${formatPoints(it.lowNightPoints.toLong())}–${formatPoints(it.highNightPoints.toLong())})") }
                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(profile.awardSearchUrl))) }) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Search awards")
                }
            }
        }
        item { Text("From cards you hold", style = MaterialTheme.typography.titleMedium) }
        if (state.fromWallet.isEmpty()) {
            item {
                Text(
                    "None of your catalog cards earn a currency that transfers into ${state.program.displayName}. See the full partner list below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(state.fromWallet, key = { it.walletCard.walletCard.id }) { option ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(option.walletCard.displayName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(option.partner.ratioLabel(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Text("1,000 ${option.partner.from.displayName} → ${formatPoints(option.programPointsPer1000.toLong())} ${state.program.displayName} points", style = MaterialTheme.typography.bodySmall)
                    option.walletCard.walletCard.rewardsBalance?.let { held ->
                        Text(
                            "You hold ${formatPoints(held)} ${option.partner.from.displayName} → up to ${formatPoints((held * option.partner.ratio).toLong())} ${state.program.displayName} points",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        if (option.valueUpliftCents >= 0) {
                            String.format("Worth ~%.2f¢ per card point here vs ~%.2f¢ as %s - a good use.", option.partner.ratio * profile.estValueCentsPerPoint, option.partner.from.estValueCentsPerPoint, option.partner.from.displayName)
                        } else {
                            String.format("Worth ~%.2f¢ per card point here vs ~%.2f¢ as %s - only transfer for a specific redemption that pencils out.", option.partner.ratio * profile.estValueCentsPerPoint, option.partner.from.estValueCentsPerPoint, option.partner.from.displayName)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    option.partner.note?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
        item { Text("All known partners", style = MaterialTheme.typography.titleMedium) }
        if (state.allPartners.isEmpty()) {
            item { Text("No bank currency transfers into ${state.program.displayName} in the app's table - its points come from stays/flights and the co-brand card.", style = MaterialTheme.typography.bodySmall) }
        }
        items(state.allPartners, key = { it.from.name }) { partner ->
            LabelValue(partner.from.displayName, partner.ratioLabel() + (partner.note?.let { " • $it" } ?: ""))
        }
        item {
            Text(
                "Transfers are one-way and usually irreversible; ratios and partners change and banks run periodic bonuses. Snapshot: ${state.allPartners.firstOrNull()?.dataAsOf ?: "n/a"}.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ----------------------------------------------------------------- Ask

@Composable
private fun AskTab(viewModel: OptimizeViewModel) {
    val state by viewModel.advisorState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            item {
                CaveatCard(
                    "Describe a trip (\"3 nights in Lisbon in March\", \"SFO–Tokyo business class in October\") and Claude researches, with live web search, the best use of the points and cards you hold. " +
                        "It can't see live award inventory - it tells you where to confirm. Only balances, tiers and card names are shared, never member numbers or emails.",
                )
            }
            items(state.messages) { message ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = if (message.isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Text(message.text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (state.isThinking) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Researching…", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) } }
            if (state.messages.isNotEmpty()) {
                item { TextButton(onClick = viewModel::clearAdvisor) { Text("Clear conversation") } }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.question,
                onValueChange = viewModel::updateQuestion,
                label = { Text("Where do you want to go?") },
                modifier = Modifier.weight(1f),
                maxLines = 4,
            )
            IconButton(onClick = viewModel::ask, enabled = state.question.isNotBlank() && !state.isThinking) {
                Icon(Icons.Filled.Send, contentDescription = "Ask")
            }
        }
    }
}
