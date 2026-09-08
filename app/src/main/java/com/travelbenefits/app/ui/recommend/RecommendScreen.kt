package com.travelbenefits.app.ui.recommend

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import com.travelbenefits.app.domain.model.RecommendationEntry
import com.travelbenefits.app.domain.model.SpendingCategory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecommendScreen(viewModel: RecommendViewModel = hiltViewModel()) {
    val entries by viewModel.uiState.collectAsState()
    val category by viewModel.category.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Best card to use") }) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(SpendingCategory.entries) { c ->
                    FilterChip(
                        selected = c == category,
                        onClick = { viewModel.selectCategory(c) },
                        label = { Text(c.label) },
                    )
                }
            }

            if (entries.isEmpty()) {
                Text(
                    "Add cards to your wallet to get a recommendation.",
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(entries) { index, entry ->
                        RecommendationRow(rank = index + 1, entry = entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecommendationRow(rank: Int, entry: RecommendationEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (rank == 1) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("#$rank", fontWeight = FontWeight.Bold)
                Text(entry.displayName, style = MaterialTheme.typography.titleMedium)
            }
            Text(entry.reason, style = MaterialTheme.typography.bodyMedium)
            if (entry.isEstimate) {
                Text(
                    "Estimate - based on a live lookup, not the built-in catalog.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
