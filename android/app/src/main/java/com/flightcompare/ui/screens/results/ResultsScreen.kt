package com.flightcompare.ui.screens.results

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.flightcompare.domain.model.SearchState
import com.flightcompare.ui.components.EmptyState
import com.flightcompare.ui.components.ErrorBanner
import com.flightcompare.ui.components.LoadingOverlay
import com.flightcompare.ui.components.PriceTag

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    searchId: String,
    onFlightClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: ResultsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Results") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (val state = uiState) {
                is SearchState.Loading -> LoadingOverlay(message = "Searching across platforms...")
                is SearchState.Error -> ErrorBanner(
                    message = state.message,
                    onRetry = viewModel::retry,
                )
                is SearchState.Success -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.offers) { offer ->
                            ElevatedCard(
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column {
                                        Text(
                                            text = offer.source,
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        PriceTag(priceCents = offer.priceCents)
                                    }
                                    val fid = offer.flightId
                                    if (!fid.isNullOrBlank()) {
                                        TextButton(onClick = { onFlightClick(fid) }) {
                                            Text("View")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                is SearchState.Idle -> EmptyState(
                    icon = { Icon(Icons.Default.Search, contentDescription = null) },
                    title = "No results yet",
                )
            }
        }
    }
}
