package com.consensus.app.ui.threads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.consensus.app.domain.ProviderId
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadListScreen(
    onOpenThread: (Long) -> Unit,
    onNewThread: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ThreadListViewModel = hiltViewModel(),
) {
    val threads by viewModel.threads.collectAsState()
    val configured by viewModel.configuredProviders.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Consensus") },
                actions = {
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewThread,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New question") },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (configured.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth().padding(16.dp).clickable(onClick = onOpenSettings)) {
                    Text(
                        "No API keys yet. Tap here to open Settings and add keys for " +
                            ProviderId.values().joinToString(", ") { it.displayName } + ".",
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else if (configured.size < ProviderId.values().size) {
                Text(
                    "Keys configured: " + configured.joinToString(", ") { it.displayName } +
                        ". Missing: " + ProviderId.values().filter { it !in configured }.joinToString(", ") { it.displayName } + ".",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (threads.isEmpty()) {
                Text(
                    "Ask a question and it goes to every model you selected. They answer, critique each other, and one of them writes a unified answer.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                items(threads, key = { it.id }) { t ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenThread(t.id) }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(t.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(t.updatedAt)),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        IconButton(onClick = { viewModel.delete(t.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete thread")
                        }
                    }
                }
            }
        }
    }
}
