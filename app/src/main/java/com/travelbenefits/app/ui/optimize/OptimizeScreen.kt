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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
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
import com.travelbenefits.app.ui.common.formatEpochDay
import com.travelbenefits.app.ui.common.formatRelative
import com.travelbenefits.app.domain.model.AwardWatch
import com.travelbenefits.app.domain.model.SpendPeriod
import com.travelbenefits.app.domain.model.BookingChannel
import com.travelbenefits.app.domain.model.Confidence
import com.travelbenefits.app.ui.navigation.NavigationRequests

private val tabs = listOf("Spend", "Earn", "Redeem", "Transfer", "Watch", "Ask")

/** Programs that card points can actually be earned into or transferred to - shops/dining never qualify. */
private val travelPrograms: List<LoyaltyProgram> = LoyaltyProgram.entries.filter { it.kind != LoyaltyProgramKind.SHOP }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptimizeScreen(viewModel: OptimizeViewModel = hiltViewModel()) {
    var tab by rememberSaveable { mutableIntStateOf(1) }

    Scaffold(topBar = { TopAppBar(title = { Text("Maximize points") }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
                tabs.forEachIndexed { index, title ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) })
                }
            }
            when (tab) {
                0 -> SpendTab(viewModel)
                1 -> EarnTab(viewModel)
                2 -> RedeemTab(viewModel)
                3 -> TransferTab(viewModel)
                4 -> WatchTab(viewModel)
                else -> AskTab(viewModel)
            }
        }
    }
}

// --------------------------------------------------------------- Spend

@Composable
private fun SpendTab(viewModel: OptimizeViewModel) {
    val state by viewModel.spendState.collectAsState()
    var expandedCategory by rememberSaveable { androidx.compose.runtime.mutableStateOf<String?>(null) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
        if (!state.isConfigured || !state.hasAccounts) {
            item {
                CaveatCard(
                    if (!state.isConfigured) {
                        "Link your card accounts through Plaid (Settings → Bank & card transactions) and this tab shows real spend per category, which card it went on, and what the best card in your wallet would have earned instead."
                    } else {
                        "Backend configured. Link an account in Settings, map it to a wallet card, then sync."
                    },
                )
            }
            return@LazyColumn
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                DropdownPicker(label = "Period", options = SpendPeriod.entries, selected = state.period, optionLabel = { it.label }, onSelected = viewModel::selectSpendPeriod, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                if (state.isSyncing) CircularProgressIndicator(modifier = Modifier.size(20.dp)) else TextButton(onClick = viewModel::syncSpendNow) { Text("Sync") }
            }
            state.message?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        val report = state.report ?: return@LazyColumn
        item {
            val headline = report.headline
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (report.missedTotalUsd > 5) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (headline != null && headline.misroutedUsd > 0) {
                            "You put ${formatUsd(headline.misroutedUsd)} of ${headline.category.label.lowercase()} on the wrong card"
                        } else {
                            "Spend is on the right cards"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "${report.period.label}: ${formatUsd(report.totalSpendUsd)} across ${report.transactionCount} purchases earned ~${formatUsd(report.earnedTotalUsd)}; " +
                            "the best card per category would have earned ~${formatUsd(report.earnedTotalUsd + report.missedTotalUsd)} (${formatUsd(report.missedTotalUsd)} left on the table).",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (report.unmappedSpendUsd > 0) {
                        Text("${formatUsd(report.unmappedSpendUsd)} came from accounts not mapped to a wallet card - map them in Settings to judge that spend.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("Estimates from catalog earn rates and the app's point valuations, not the issuer's actual posting.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        items(report.categories, key = { it.category.name }) { cat ->
            val expanded = expandedCategory == cat.category.name
            Card(onClick = { expandedCategory = if (expanded) null else cat.category.name }, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(cat.category.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(formatUsd(cat.spendUsd), style = MaterialTheme.typography.titleSmall)
                    }
                    Text(
                        if (cat.missedUsd > 0.5) "~${formatUsd(cat.missedUsd)} missed • best: ${cat.bestCard?.displayName ?: "-"} (${cat.bestMultiplierLabel})" else "On the best card" + (cat.bestCard?.let { " (${it.displayName}, ${cat.bestMultiplierLabel})" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (cat.missedUsd > 0.5) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                    if (expanded) {
                        cat.byCard.forEach { cs ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${cs.accountLabel} (${cs.multiplierLabel})", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                Text("${formatUsd(cs.spendUsd)} → ~${formatUsd(cs.valueEarnedUsd)}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Earn

@Composable
private fun EarnTab(viewModel: OptimizeViewModel) {
    val state by viewModel.earnState.collectAsState()
    val purchase by viewModel.purchaseState.collectAsState()
    val programOptions: List<LoyaltyProgram?> = listOf<LoyaltyProgram?>(null) + travelPrograms
    val categoryOptions: List<SpendingCategory?> = listOf<SpendingCategory?>(null) + SpendingCategory.entries
    androidx.compose.runtime.LaunchedEffect(Unit) {
        NavigationRequests.consumePurchaseMerchant()?.let { viewModel.prefillPurchase(it) }
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
        item { Text("Which card for this purchase?", style = MaterialTheme.typography.titleMedium) }
        item {
            OutlinedTextField(value = purchase.merchant, onValueChange = { v -> viewModel.updatePurchase { it.copy(merchant = v) } }, label = { Text("Merchant (e.g. Whole Foods, Delta, Shell)") }, modifier = Modifier.fillMaxWidth())
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = purchase.amount, onValueChange = { v -> viewModel.updatePurchase { it.copy(amount = v) } }, label = { Text("Amount (USD)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Switch(checked = purchase.isForeign, onCheckedChange = { c -> viewModel.updatePurchase { it.copy(isForeign = c) } })
                    Text("Abroad / foreign", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        item {
            DropdownPicker(
                label = "Category" + (if (purchase.categoryOverride == null && purchase.guessedCategory != null) " (guessed: ${purchase.guessedCategory!!.label}, ${purchase.guessConfidence.label.lowercase()} confidence)" else ""),
                options = categoryOptions, selected = purchase.categoryOverride,
                optionLabel = { it?.label ?: (purchase.guessedCategory?.let { g -> "Auto: ${g.label}" } ?: "Auto (unknown - pick one)") },
                onSelected = { c -> viewModel.updatePurchase { it.copy(categoryOverride = c) } },
            )
        }
        item {
            DropdownPicker(label = "How it's booked", options = BookingChannel.entries, selected = purchase.channel, optionLabel = { it.label }, onSelected = { c -> viewModel.updatePurchase { it.copy(channel = c) } })
        }
        purchase.recommendation?.let { rec ->
            rec.categoryWarning?.let { item { CaveatCard(it) } }
            items(rec.options, key = { "p-${it.card.walletCard.id}" }) { opt ->
                Card(modifier = Modifier.fillMaxWidth(), colors = if (opt == rec.options.firstOrNull()) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.cardColors()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(opt.card.displayName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text("~${formatUsd(opt.netValueUsd)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            "${opt.effectiveRateLabel} ${opt.rewardCurrency.displayName} → ${if (opt.rewardCurrency.displayAsPercent) formatUsd(opt.rewardsEarned) else String.format("%,.0f pts", opt.rewardsEarned)}" +
                                (opt.foreignFeeUsd?.takeIf { it > 0 }?.let { " − ${formatUsd(it)} foreign fee" } ?: "") + " • ${opt.confidence.label} confidence",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        opt.reasons.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                        opt.conditions.forEach { Text("Condition: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        opt.warnings.forEach { Text("⚠ $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
                        opt.thresholdNotes.forEach { Text("Note: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary) }
                    }
                }
            }
            item { Text("Values are estimates from catalog rules and point valuations; issuers decide the merchant's category. Set your own valuations in Settings.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        item { Text("Category ranking", style = MaterialTheme.typography.titleMedium) }
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
        item {
            val buys by viewModel.balanceBuys.collectAsState()
            if (buys.isNotEmpty()) {
                SectionCard(title = "What your balances buy") {
                    Text("Rough nights (hotels) or one-way flights (airlines) at each program's low / typical / high award levels - estimates from the catalog, not live pricing.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    buys.forEach { b ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(b.account.program.displayName, style = MaterialTheme.typography.bodyMedium)
                                Text(b.balanceLabel ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                if (b.typicalNights != null) "${b.lowNights} / ${b.typicalNights} / ${b.highNights}" else "no chart",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
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
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Transfer bonuses", style = MaterialTheme.typography.titleMedium)
                    Text(
                        state.bonusesCheckedAt?.let { "Researched ${formatRelative(it)} via web search - confirm on the bank's transfer page." } ?: "Not checked yet. Turn on research in the Watch tab for weekly checks, or check now.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.isRefreshingBonuses) CircularProgressIndicator(modifier = Modifier.size(18.dp)) else TextButton(onClick = viewModel::refreshBonuses) { Text("Check now") }
            }
            if (state.allBonuses.isNotEmpty()) {
                state.allBonuses.forEach { b ->
                    Text(
                        "${b.from.displayName} → ${b.to.displayName}: +${b.bonusPercent}%" + (b.endsEpochDay?.let { " until ${formatEpochDay(it)}" } ?: "") + (b.note?.let { " • $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (b.to == state.program) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            } else if (state.bonusesCheckedAt != null) {
                Text("No bonuses running into the app's tracked programs at last check.", style = MaterialTheme.typography.bodySmall)
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
                    val bonus = state.bonuses[option.partner.from]
                    val effectiveRatio = bonus?.effectiveRatio(option.partner.ratio) ?: option.partner.ratio
                    if (bonus != null) {
                        Text(
                            "+${bonus.bonusPercent}% bonus running: 1,000 → ${formatPoints((1000 * effectiveRatio).toLong())}" + (bonus.endsEpochDay?.let { " until ${formatEpochDay(it)}" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    option.walletCard.walletCard.rewardsBalance?.let { held ->
                        Text(
                            "You hold ${formatPoints(held)} ${option.partner.from.displayName} → up to ${formatPoints((held * effectiveRatio).toLong())} ${state.program.displayName} points",
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
                    var amount by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("I transferred (card points)") }, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            val pts = amount.replace(",", "").toLongOrNull() ?: return@TextButton
                            val bonus = state.bonuses[option.partner.from]
                            val ratio = bonus?.effectiveRatio(option.partner.ratio) ?: option.partner.ratio
                            viewModel.recordTransfer(option.walletCard, state.program, pts, (pts * ratio).toLong())
                            amount = ""
                        }) { Text("Record") }
                    }
                }
            }
        }
        item { Text("Transfers are recorded here after you make them on the bank's site - the app never moves points itself.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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

// --------------------------------------------------------------- Watch

@Composable
private fun WatchTab(viewModel: OptimizeViewModel) {
    val state by viewModel.watchState.collectAsState()
    val add by viewModel.addWatchState.collectAsState()

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
        item {
            CaveatCard(
                "Award watches are researched, not live: once a day (if enabled) Claude searches the web for evidence that your route or stay is bookable with points and tells you where to confirm. " +
                    "No program exposes real inventory to personal apps. Each check is an API call.",
            )
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Daily research", style = MaterialTheme.typography.titleSmall)
                    Text("Checks active watches daily and transfer bonuses weekly.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = state.researchEnabled, onCheckedChange = viewModel::setResearchEnabled)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = viewModel::openAddWatch) { Text("Add watch") }
                if (state.isChecking) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    Text("Researching…", style = MaterialTheme.typography.bodySmall)
                } else if (state.watches.any { it.active }) {
                    TextButton(onClick = { viewModel.checkWatchNow() }) { Text("Check all now") }
                }
            }
            state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        }
        if (state.watches.isEmpty()) {
            item { Text("No watches yet. Example: \"Hyatt Regency Kyoto, 3 nights, 2027-03-10 to 03-13, standard award\" or \"SFO → NRT business, October, United or ANA\".", style = MaterialTheme.typography.bodySmall) }
        }
        items(state.watches, key = { "w-${it.id}" }) { watch -> WatchRow(watch, viewModel) }
    }

    if (add.isOpen) {
        val programOptions: List<LoyaltyProgram?> = listOf<LoyaltyProgram?>(null) + travelPrograms
        androidx.compose.material3.AlertDialog(
            onDismissRequest = viewModel::closeAddWatch,
            title = { Text("Add award watch") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(value = add.title, onValueChange = { v -> viewModel.updateAddWatch { it.copy(title = v) } }, label = { Text("Title (optional)") }, modifier = Modifier.fillMaxWidth())
                    DropdownPicker(label = "Program", options = programOptions, selected = add.program, optionLabel = { it?.displayName ?: "Any / not sure" }, onSelected = { p -> viewModel.updateAddWatch { it.copy(program = p) } })
                    OutlinedTextField(value = add.origin, onValueChange = { v -> viewModel.updateAddWatch { it.copy(origin = v) } }, label = { Text("Origin (flights)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = add.destination, onValueChange = { v -> viewModel.updateAddWatch { it.copy(destination = v) } }, label = { Text("Destination / hotel") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = add.dateFrom, onValueChange = { v -> viewModel.updateAddWatch { it.copy(dateFrom = v) } }, label = { Text("From date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = add.dateTo, onValueChange = { v -> viewModel.updateAddWatch { it.copy(dateTo = v) } }, label = { Text("To date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = add.notes, onValueChange = { v -> viewModel.updateAddWatch { it.copy(notes = v) } }, label = { Text("Cabin / room / flexibility notes") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(onClick = viewModel::saveWatch, enabled = add.title.isNotBlank() || add.destination.isNotBlank()) { Text("Save") } },
            dismissButton = { TextButton(onClick = viewModel::closeAddWatch) { Text("Cancel") } },
        )
    }
}

@Composable
private fun WatchRow(watch: AwardWatch, viewModel: OptimizeViewModel) {
    Card(modifier = Modifier.fillMaxWidth(), colors = if (watch.lastFound) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.cardColors()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(watch.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(checked = watch.active, onCheckedChange = { viewModel.setWatchActive(watch.id, it) })
            }
            Text(
                listOfNotNull(watch.program?.displayName, listOfNotNull(watch.dateFrom, watch.dateTo).joinToString(" → ").ifBlank { null }, watch.notes).joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (watch.lastCheckedAt == null) "Not checked yet" else (if (watch.lastFound) "Looks available" else "Nothing found") + " • checked ${formatRelative(watch.lastCheckedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = if (watch.lastFound) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            watch.lastResult?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { viewModel.checkWatchNow(watch.id) }) { Text("Check now") }
                watch.program?.let { p ->
                    val context = LocalContext.current
                    TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(viewModel.profileFor(p).awardSearchUrl))) }) {
                        Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Search")
                    }
                }
                TextButton(onClick = { viewModel.deleteWatch(watch.id) }) { Text("Remove") }
            }
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
