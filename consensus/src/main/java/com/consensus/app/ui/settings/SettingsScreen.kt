package com.consensus.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.consensus.app.domain.ProviderId
import com.consensus.app.domain.Tier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(state.savedMessage) {
        state.savedMessage?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    TextButton(onClick = { viewModel.save() }, enabled = state.loaded && state.dirty) { Text("Save") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Keys are stored encrypted on this phone and only ever sent to the provider they belong to. " +
                    "Model IDs are editable because providers rename models often; check each provider's model list if a call fails with an unknown-model error.",
                style = MaterialTheme.typography.bodySmall,
            )

            ProviderId.values().forEach { p ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p.displayName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text("Include by default", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.width(6.dp))
                            Switch(checked = state.settings.enabled[p] ?: true, onCheckedChange = { viewModel.setEnabled(p, it) })
                        }
                        KeyField(
                            value = state.keys[p].orEmpty(),
                            onChange = { viewModel.setKey(p, it) },
                        )
                        TextButton(onClick = { runCatching { uriHandler.openUri(p.keyUrl) } }) {
                            Text("Get a ${p.displayName} API key")
                        }
                        Text("Model IDs", fontWeight = FontWeight.SemiBold)
                        Tier.values().forEach { t ->
                            OutlinedTextField(
                                value = state.settings.modelIds[p]?.get(t).orEmpty(),
                                onValueChange = { viewModel.setModelId(p, t, it) },
                                label = { Text(t.label) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Text("Default tier", fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Tier.values().forEach { t ->
                                FilterChip(
                                    selected = (state.settings.defaultTier[p] ?: Tier.BALANCED) == t,
                                    onClick = { viewModel.setDefaultTier(p, t) },
                                    label = { Text(t.label) },
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider()
            Text("Defaults for new questions", style = MaterialTheme.typography.titleMedium)
            Text("Judge", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProviderId.values().forEach { p ->
                    FilterChip(
                        selected = state.settings.defaultJudge == p,
                        onClick = { viewModel.setDefaultJudge(p) },
                        label = { Text(p.displayName) },
                    )
                }
            }
            Text("Debate rounds", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (0..3).forEach { r ->
                    FilterChip(selected = state.settings.defaultRounds == r, onClick = { viewModel.setDefaultRounds(r) }, label = { Text("$r") })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = state.settings.stopWhenAgreed, onCheckedChange = { viewModel.setStopWhenAgreed(it) })
                Spacer(Modifier.width(8.dp))
                Text("Stop early once every model reports agreement")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = state.settings.webSearch, onCheckedChange = { viewModel.setWebSearch(it) })
                Spacer(Modifier.width(8.dp))
                Text("Web search on by default")
            }

            Button(onClick = { viewModel.save() }, enabled = state.loaded && state.dirty, modifier = Modifier.fillMaxWidth()) {
                Text("Save")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun KeyField(value: String, onChange: (String) -> Unit) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text("API key") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = if (visible) "Hide" else "Show")
            }
        },
    )
}
