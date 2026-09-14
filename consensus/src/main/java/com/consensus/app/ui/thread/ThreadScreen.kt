package com.consensus.app.ui.thread

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.consensus.app.data.local.AppSettings
import com.consensus.app.data.local.entity.ExchangeEntity
import com.consensus.app.domain.ProviderAnswer
import com.consensus.app.domain.ProviderId
import com.consensus.app.domain.RunProgress
import com.consensus.app.domain.RunTranscript
import com.consensus.app.domain.Tier
import com.consensus.app.ui.components.MarkdownText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ThreadViewModel = hiltViewModel(),
) {
    val exchanges by viewModel.exchanges.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val title by viewModel.title.collectAsState()
    val options by viewModel.options.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val configured by viewModel.configured.collectAsState()
    val error by viewModel.error.collectAsState()

    var question by rememberSaveable { mutableStateOf("") }
    var showOptions by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val anyRunning = exchanges.any { it.status == ExchangeEntity.RUNNING }

    LaunchedEffect(exchanges.size) {
        if (exchanges.isNotEmpty()) listState.animateScrollToItem(exchanges.lastIndex)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title.ifBlank { "New question" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(modifier = Modifier.navigationBarsPadding().imePadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    options?.let { o -> OptionsSummary(o, settings) { showOptions = true } }
                    Row(verticalAlignment = Alignment.Bottom) {
                        OutlinedTextField(
                            value = question,
                            onValueChange = { question = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(if (exchanges.isEmpty()) "Ask a question" else "Ask a follow-up") },
                            maxLines = 6,
                        )
                        IconButton(onClick = { showOptions = true }) {
                            Icon(Icons.Default.Tune, contentDescription = "Options for this question")
                        }
                        IconButton(
                            onClick = {
                                viewModel.ask(question)
                                question = ""
                            },
                            enabled = question.isNotBlank() && !anyRunning && options != null,
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Ask")
                        }
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (exchanges.isEmpty()) {
                item {
                    Text(
                        "Your question is sent to every selected model at once. Each then reads the others' answers, critiques them and revises its own. Finally the judge model writes one unified answer and lists whatever they still disagree on.\n\nTap the sliders icon to choose models, tiers, judge and rounds for this question.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            items(exchanges, key = { it.id }) { e ->
                ExchangeCard(
                    exchange = e,
                    progress = progress[e.id],
                    transcript = remember(e.transcriptJson) { viewModel.transcript(e) },
                    onCancel = { viewModel.cancel(e.id) },
                    onDelete = { viewModel.deleteExchange(e.id) },
                )
            }
        }
    }

    if (showOptions && options != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showOptions = false }, sheetState = sheetState) {
            OptionsSheet(
                options = options!!,
                settings = settings,
                configured = configured,
                onChange = { viewModel.updateOptions { _ -> it } },
                onDone = { showOptions = false },
            )
        }
    }

    error?.let { msg ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            confirmButton = { TextButton(onClick = { viewModel.clearError() }) { Text("OK") } },
            title = { Text("Can't ask") },
            text = { Text(msg) },
        )
    }
}

@Composable
private fun OptionsSummary(o: QuestionOptions, s: AppSettings, onClick: () -> Unit) {
    val parts = o.included.joinToString(", ") { p -> "${p.displayName} (${(o.tiers[p] ?: Tier.BALANCED).label})" }
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 4.dp)) {
        Text(
            text = (if (parts.isEmpty()) "No providers selected" else parts) +
                " • judge ${o.judge.displayName} • ${o.rounds} round${if (o.rounds == 1) "" else "s"}" +
                (if (o.webSearch) " • search on" else " • search off"),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun OptionsSheet(
    options: QuestionOptions,
    settings: AppSettings,
    configured: Set<ProviderId>,
    onChange: (QuestionOptions) -> Unit,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Options for this question", style = MaterialTheme.typography.titleLarge)

        Text("Models", style = MaterialTheme.typography.titleMedium)
        ProviderId.values().forEach { p ->
            val hasKey = p in configured
            val included = p in options.included
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = included && hasKey,
                        enabled = hasKey,
                        onCheckedChange = { on ->
                            val inc = if (on) options.included + p else options.included - p
                            val judge = if (options.judge in inc) options.judge else inc.firstOrNull() ?: options.judge
                            onChange(options.copy(included = inc, judge = judge))
                        },
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(p.displayName, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (hasKey) settings.modelId(p, options.tiers[p] ?: Tier.BALANCED) else "No API key (add one in Settings)",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                if (hasKey && included) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 8.dp)) {
                        Tier.values().forEach { t ->
                            FilterChip(
                                selected = (options.tiers[p] ?: Tier.BALANCED) == t,
                                onClick = { onChange(options.copy(tiers = options.tiers + (p to t))) },
                                label = { Text(t.label) },
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider()
        Text("Judge (writes the unified answer)", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.included.sortedBy { it.ordinal }.forEach { p ->
                FilterChip(
                    selected = options.judge == p,
                    onClick = { onChange(options.copy(judge = p)) },
                    label = { Text(p.displayName) },
                )
            }
        }

        HorizontalDivider()
        Text("Debate rounds", style = MaterialTheme.typography.titleMedium)
        Text(
            "0 = each model answers once, then the judge summarises. Each extra round has every model read the others' answers, critique them and revise. Cost grows roughly linearly with rounds.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (0..3).forEach { r ->
                FilterChip(selected = options.rounds == r, onClick = { onChange(options.copy(rounds = r)) }, label = { Text("$r") })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = options.stopWhenAgreed, onCheckedChange = { onChange(options.copy(stopWhenAgreed = it)) })
            Spacer(Modifier.width(8.dp))
            Text("Stop early once every model reports agreement")
        }

        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = options.webSearch, onCheckedChange = { onChange(options.copy(webSearch = it)) })
            Spacer(Modifier.width(8.dp))
            Column {
                Text("Web search", fontWeight = FontWeight.SemiBold)
                Text("Lets every model search the web and cite sources. Slower and costs more.", style = MaterialTheme.typography.bodySmall)
            }
        }

        TextButton(onClick = onDone, modifier = Modifier.align(Alignment.End)) { Text("Done") }
    }
}

@Composable
private fun ExchangeCard(
    exchange: ExchangeEntity,
    progress: RunProgress?,
    transcript: RunTranscript?,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by rememberSaveable(exchange.id) { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(exchange.question, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (exchange.status == ExchangeEntity.RUNNING) {
                    IconButton(onClick = onCancel) { Icon(Icons.Default.Stop, contentDescription = "Cancel") }
                } else {
                    IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
                }
            }
            Spacer(Modifier.height(8.dp))
            when (exchange.status) {
                ExchangeEntity.RUNNING -> RunningView(progress)
                ExchangeEntity.ERROR -> Text(
                    exchange.error ?: "Failed",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                else -> {
                    MarkdownText(exchange.finalAnswer ?: "(no answer)")
                    transcript?.let { t ->
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                summaryLine(t),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { expanded = !expanded }) {
                                Text(if (expanded) "Hide details" else "Details")
                                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                            }
                        }
                        AnimatedVisibility(visible = expanded) { TranscriptView(t) }
                    }
                }
            }
        }
    }
}

private fun summaryLine(t: RunTranscript): String {
    val judge = t.judge?.let { "${it.provider.displayName} judged" } ?: ""
    val n = t.rounds.firstOrNull()?.answers?.count { it.ok } ?: 0
    val rounds = t.rounds.size - 1
    val consensus = when (t.consensusReached) {
        true -> "all agreed"
        false -> "disagreement remained"
        null -> if (rounds == 0) "no debate round" else "agreement unknown"
    }
    val tokens = t.totalUsage.input + t.totalUsage.output
    return "$judge • $n models • $rounds round${if (rounds == 1) "" else "s"} • $consensus • ${"%,d".format(tokens)} tokens"
}

@Composable
private fun RunningView(progress: RunProgress?) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text(progress?.stage ?: "Starting", style = MaterialTheme.typography.bodyMedium)
        }
        progress?.providerStates?.takeIf { it.isNotEmpty() }?.let { states ->
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                states.entries.sortedBy { it.key.ordinal }.forEach { (p, s) ->
                    val label = when (s) {
                        RunProgress.ProviderState.RUNNING -> "${p.displayName}…"
                        RunProgress.ProviderState.DONE -> "${p.displayName} ✓"
                        RunProgress.ProviderState.FAILED -> "${p.displayName} ✗"
                        RunProgress.ProviderState.WAITING -> p.displayName
                        RunProgress.ProviderState.SKIPPED -> "${p.displayName} (skipped)"
                    }
                    Text(label, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun TranscriptView(t: RunTranscript) {
    Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Text(
            "Options: judge ${t.options.judge.displayName}, up to ${t.options.maxRounds} round(s), web search ${if (t.options.webSearch) "on" else "off"}. " +
                "Tokens in/out: ${"%,d".format(t.totalUsage.input)} / ${"%,d".format(t.totalUsage.output)}.",
            style = MaterialTheme.typography.bodySmall,
        )
        t.rounds.forEach { round ->
            Text(
                if (round.index == 0) "Initial answers" else "Debate round ${round.index}",
                style = MaterialTheme.typography.titleSmall,
            )
            round.answers.sortedBy { it.provider.ordinal }.forEach { a -> AnswerBlock(a) }
        }
        t.judge?.let { j ->
            Text("Judge: ${j.provider.displayName} (${j.model})", style = MaterialTheme.typography.titleSmall)
            Text(
                "${"%,d".format(j.usage.input)} in / ${"%,d".format(j.usage.output)} out, ${j.durationMs / 1000}s",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun AnswerBlock(a: ProviderAnswer) {
    var open by rememberSaveable(a.provider.name, a.model, a.durationMs) { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("${a.provider.displayName} • ${a.model}", fontWeight = FontWeight.SemiBold)
                    Text(
                        buildString {
                            if (a.error != null) append("Failed") else append("${"%,d".format(a.usage.input)} in / ${"%,d".format(a.usage.output)} out")
                            append(", ${a.durationMs / 1000}s")
                            when (a.agrees) {
                                true -> append(", AGREE")
                                false -> append(", DISAGREE")
                                null -> {}
                            }
                            if (a.citations.isNotEmpty()) append(", ${a.citations.size} source${if (a.citations.size == 1) "" else "s"}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (a.error == null) {
                    IconButton(onClick = { open = !open }) {
                        Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                    }
                }
            }
            a.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            AnimatedVisibility(visible = open) {
                Column(modifier = Modifier.padding(top = 6.dp)) {
                    MarkdownText(a.text.orEmpty())
                    if (a.citations.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text("Sources", style = MaterialTheme.typography.labelMedium)
                        a.citations.forEach { c ->
                            TextButton(onClick = { runCatching { uriHandler.openUri(c.url) } }, contentPadding = PaddingValues(0.dp)) {
                                Text(c.title?.takeIf { it.isNotBlank() } ?: c.url, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}
