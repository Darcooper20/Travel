package com.travelbenefits.app.ui.loyalty

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.travelbenefits.app.domain.model.LoyaltyAccount
import com.travelbenefits.app.domain.model.LoyaltyProgram
import com.travelbenefits.app.domain.model.LoyaltyProgramKind
import com.travelbenefits.app.domain.model.ProgramRegion
import com.travelbenefits.app.ui.common.CaveatCard
import com.travelbenefits.app.ui.common.DropdownPicker
import com.travelbenefits.app.ui.common.LabelValue
import com.travelbenefits.app.ui.common.formatEpochMillisDate
import com.travelbenefits.app.ui.common.formatPoints
import com.travelbenefits.app.ui.common.formatRelative
import com.travelbenefits.app.ui.common.formatUsd
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoyaltyScreen(onOpenSettings: () -> Unit, viewModel: LoyaltyViewModel = hiltViewModel()) {
    val accounts by viewModel.accounts.collectAsState()
    val isGmailConnected by viewModel.isGmailConnected.collectAsState()
    val editState by viewModel.editState.collectAsState()
    var pendingDelete by remember { mutableStateOf<LoyaltyAccount?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Loyalty programs") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.openAdd() }) {
                Icon(Icons.Filled.Add, contentDescription = "Add loyalty account")
            }
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (!isGmailConnected) {
                item {
                    // The card told people to go to Settings without giving them a way
                    // there; onOpenSettings was already wired in the nav host but unused.
                    Column {
                        CaveatCard("Connect Gmail in Settings and the email monitor will fill this in from your loyalty statements and booking confirmations automatically.")
                        TextButton(onClick = onOpenSettings) { Text("Open Settings") }
                    }
                }
            }
            if (accounts.isEmpty()) {
                item {
                    Text(
                        "No loyalty accounts yet. Tap + to add one, or run Sync on the Home screen.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            val members = accounts.map { it.account.memberName }.distinct().sortedBy { it ?: "" }
            members.forEach { member ->
                if (members.size > 1 || member != null) {
                    item { Text(member ?: "Me", style = MaterialTheme.typography.titleLarge) }
                }
                LoyaltyProgramKind.entries.forEach { kind ->
                    val group = accounts.filter { it.account.program.kind == kind && it.account.memberName == member }
                    if (group.isNotEmpty()) {
                        item { Text(kind.label, style = MaterialTheme.typography.titleMedium) }
                        items(group, key = { it.account.id }) { AccountCard(it, onEdit = { viewModel.openAdd(it.account) }, onDelete = { pendingDelete = it.account }, onHypothetical = { v -> viewModel.setHypothetical(it.account.id, v) }) }
                    }
                }
            }
            item {
                var cost by remember { mutableStateOf("") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = cost, onValueChange = { cost = it }, label = { Text("Your typical paid night/segment cost (USD), for forecasts") }, modifier = Modifier.weight(1f))
                    TextButton(onClick = { viewModel.setTypicalNightCost(cost) }) { Text("Save") }
                }
            }
            item {
                Text(
                    "Tier thresholds, expiry rules and point values are a curated snapshot (see each program's notes) - programs change them often, so verify before planning around them.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    pendingDelete?.let { account ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove ${account.programDisplayName}?") },
            text = { Text("The next email sync may add it back if a statement shows up; delete it from Gmail scanning by turning off loyalty scanning in Settings.") },
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
private fun AccountCard(state: AccountCardState, onEdit: () -> Unit, onDelete: () -> Unit, onHypothetical: (Int) -> Unit = {}) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val account = state.account
    val profile = state.profile

    Card(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(account.programDisplayName, style = MaterialTheme.typography.titleMedium)
                    if (account.isUncatalogued) {
                        Text(
                            "Found in your email. Not in the built-in catalog, so there is no tier ladder, expiry rule or point value for it - the balance is shown as-is.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        listOfNotNull(account.tier ?: "No status", account.membershipNumber?.let { "#$it" }).joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(state.balanceLabel ?: "—", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    state.estimatedValueUsd?.let {
                        Text("~${formatUsd(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Tier progress
            val progress = state.progress
            val next = progress.nextTier
            if (next != null) {
                val fraction = progress.fraction
                if (fraction != null) {
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                    Text(
                        "${progress.currentValue ?: 0} / ${next.threshold} ${progress.metric.unit} → ${next.name}" +
                            (progress.remaining?.let { " (${it} to go)" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Text(
                        if (next.threshold != null) {
                            "Next: ${next.name} at ${formatPoints(next.threshold.toLong())} ${progress.metric.unit}" + (next.altQualification?.let { " ($it)" } ?: "") + " - tap to enter your year-to-date ${progress.metric.label.lowercase()}."
                        } else {
                            "Next: ${next.name} (threshold not encoded - see ${profile.accountUrl.substringAfter("://").substringBefore("/")})"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (progress.currentTier != null) {
                Text("Top tier reached.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }

            // Expiry
            state.expiryEstimateAt?.let { expiry ->
                val days = TimeUnit.MILLISECONDS.toDays(expiry - System.currentTimeMillis())
                val color = if (days <= 30) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                Text(
                    if (days < 0) "Points may have expired (${formatEpochMillisDate(expiry)})" else "Points expire ~${formatEpochMillisDate(expiry)} ($days days) unless there's activity",
                    style = MaterialTheme.typography.bodySmall,
                    color = color,
                )
            } ?: run {
                if (profile.expiration.inactivityMonths == null) {
                    Text(profile.expiration.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Collapse details" else "Expand details",
                    )
                    Text(if (expanded) "Less" else "Details & history")
                }
                Row {
                    IconButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(profile.accountUrl))) }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open program site")
                    }
                    IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Remove") }
                }
            }

            if (expanded) {
                state.awardNightsEstimate?.let { nights ->
                    profile.awardBand?.let { band ->
                        LabelValue("Typical award nights", "~$nights (at ${formatPoints(band.typicalNightPoints.toLong())}/night)")
                        Text(band.note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                // No valuation is very different from a valuation of zero, so a
                // programme the app has no estimate for says nothing here.
                if (profile.balanceUnit == com.travelbenefits.app.domain.model.BalanceUnit.POINTS) {
                    val cents = profile.estValueCentsPerPointOrNull
                    if (cents != null) LabelValue("Est. value per point", "$cents¢")
                    else LabelValue("Est. value per point", "Not in the app for this programme")
                }
                profile.basePointsPerDollar?.let { LabelValue("Base earn", "${it}x per $ with ${account.programDisplayName}") }
                LabelValue("Expiry rule", profile.expiration.summary)
                if (profile.tiers.isNotEmpty()) {
                    Text("Elite ladder (${profile.qualifyingMetric.label.lowercase()})", style = MaterialTheme.typography.labelMedium)
                    profile.tiers.forEach { tier ->
                        val marker = if (tier == progress.currentTier) "● " else "○ "
                        Text(
                            marker + tier.name + (tier.threshold?.let { " - ${formatPoints(it.toLong())}" } ?: "") + (tier.altQualification?.let { " ($it)" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        tier.perks?.takeIf { tier == next }?.let { Text("   $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                profile.notes?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                state.forecast?.let { fc ->
                    Spacer(Modifier.height(4.dp))
                    Text("Status forecast", style = MaterialTheme.typography.labelMedium)
                    LabelValue("Posted this year", fc.posted?.toString() ?: "unknown")
                    LabelValue("Booked (number confirmed)", "+${fc.booked} ${fc.metric.unit}")
                    LabelValue("Hypothetical", "+${fc.hypothetical}")
                    fc.nextTier?.let { nt -> LabelValue("Projected vs ${nt.name}", (fc.projected?.toString() ?: "?") + " / " + (nt.threshold?.toString() ?: "?") + (fc.remainingAfterBooked?.let { r -> " (${r} to go)" } ?: "")) }
                    fc.estimatedCostToTierUsd?.let { LabelValue("Rough cost to close the gap", formatUsd(it) + " (information only)") }
                    Text(fc.awardStaysRule + (fc.qualificationYear?.let { " Qualification year: $it." } ?: ""), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    fc.notes.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    var hypo by remember { mutableStateOf("") }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = hypo, onValueChange = { hypo = it }, label = { Text("What if I add… (${fc.metric.unit})") }, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onHypothetical(hypo.toIntOrNull() ?: 0) }) { Text("Apply") }
                    }
                }
                if (state.history.size > 1) {
                    Spacer(Modifier.height(4.dp))
                    Text("Balance history", style = MaterialTheme.typography.labelMedium)
                    state.history.forEach { snap ->
                        LabelValue(formatRelative(snap.recordedAt), formatPoints(snap.points) + (snap.tier?.let { " • $it" } ?: ""))
                    }
                }
                Text(
                    "Source: ${if (account.source.name == "GMAIL_SCAN") "Gmail" else "manual"}" +
                        (account.sourceEmailSubject?.let { " - \"$it\"" } ?: "") +
                        " • updated ${formatRelative(account.lastUpdated)} • data as of ${profile.dataAsOf}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EditAccountDialog(
    state: EditAccountState,
    onDismiss: () -> Unit,
    onChange: ((EditAccountState) -> EditAccountState) -> Unit,
    onSave: () -> Unit,
) {
    val metric = com.travelbenefits.app.data.catalog.LoyaltyProgramCatalog.profileFor(state.program).qualifyingMetric
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Loyalty account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                // The catch-all for email-discovered programmes is not something to pick by hand.
                val pickable = remember { LoyaltyProgram.entries.filter { it != LoyaltyProgram.OTHER_REWARDS } }
                // With programmes from five markets in one list, an unfiltered
                // dropdown is a long scroll; the region filter starts on the
                // selected programme's own market so reopening an account lands
                // where you left it.
                var regionFilter by remember(state.program) { mutableStateOf<ProgramRegion?>(state.program.region) }
                DropdownPicker(
                    label = "Market",
                    options = listOf<ProgramRegion?>(null) + ProgramRegion.entries,
                    selected = regionFilter,
                    optionLabel = { it?.label ?: "All markets" },
                    onSelected = { regionFilter = it },
                )
                val options = pickable.filter { regionFilter == null || it.region == regionFilter }
                    .ifEmpty { pickable }
                DropdownPicker(
                    label = "Program",
                    // The selected programme always stays in the list, even when a
                    // filter would exclude it, so the field can never show a value
                    // the menu cannot offer back.
                    options = if (state.program in options) options else listOf(state.program) + options,
                    selected = state.program,
                    optionLabel = { "${it.displayName} (${it.kind.label})" },
                    onSelected = { program -> onChange { it.copy(program = program) } },
                )
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
                    label = { Text(if (com.travelbenefits.app.data.catalog.LoyaltyProgramCatalog.profileFor(state.program).balanceUnit == com.travelbenefits.app.domain.model.BalanceUnit.DOLLARS) "Balance (USD)" else "Points balance") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.qualifyingProgress,
                    onValueChange = { v -> onChange { it.copy(qualifyingProgress = v) } },
                    label = { Text("${metric.label} this year (${metric.unit})") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.pointsExpireOn,
                    onValueChange = { v -> onChange { it.copy(pointsExpireOn = v) } },
                    label = { Text("Points expire on (YYYY-MM-DD, optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.memberName,
                    onValueChange = { v -> onChange { it.copy(memberName = v) } },
                    label = { Text("Household member (blank = me)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = onSave) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
