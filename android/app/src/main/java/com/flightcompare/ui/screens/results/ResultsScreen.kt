package com.flightcompare.ui.screens.results

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.flightcompare.domain.model.SearchState
import com.flightcompare.ui.components.EmptyState
import com.flightcompare.ui.components.ErrorBanner
import com.flightcompare.ui.components.LoadingOverlay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    searchId: String,
    onFlightClick: (String) -> Unit,
    viewModel: ResultsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Results") },
                navigationIcon = {
                    IconButton(onClick = { /* back */ }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (val state = uiState) {
                is SearchState.Loading -> LoadingOverlay()
                is SearchState.Error -> ErrorBanner(
                    message = state.message,
                    onRetry = { /* viewModel.retry() */ }
                )
                is SearchState.Success -> {
                    val groupedOffers = state.offers.groupBy { o ->
                        // group by source for now; ideally by flight
                        o.bookingLink ?: o.source
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.offers) { offer ->
                            // Simplified result card - shows comparison across platforms
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
                                        Text(
                                            text = "$${offer.priceCents / 100}.${(offer.priceCents % 100).toString().padStart(2, '0')}",
                                            style = MaterialTheme.typography.headlineSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    offer.bookingLink?.let { url ->
                                        TextButton(onClick = { onFlightClick(url) }) {
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
