package com.flightcompare.ui.screens.bookmarks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.flightcompare.domain.model.Bookmark
import com.flightcompare.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    onFlightClick: (String) -> Unit,
    viewModel: BookmarksViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Bookmarks") })
        }
    ) { padding ->
        when {
            uiState.isLoading -> LoadingOverlay(modifier = Modifier.padding(padding))
            uiState.error != null -> ErrorBanner(
                message = uiState.error!!,
                onRetry = viewModel::load,
            )
            uiState.bookmarks.isEmpty() -> EmptyState(
                icon = { Icon(Icons.Default.BookmarkBorder, contentDescription = null) },
                title = "No bookmarks yet",
                subtitle = "Bookmark flights from search results to track prices",
                modifier = Modifier.padding(padding),
            )
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(uiState.bookmarks, key = { it.id }) { bookmark ->
                        BookmarkCard(
                            bookmark = bookmark,
                            onClick = { onFlightClick(bookmark.flightId) },
                            onDelete = { viewModel.delete(bookmark.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BookmarkCard(
    bookmark: Bookmark,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            bookmark.flight?.let { flight ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${flight.origin} → ${flight.destination}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = flight.airline,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                        bookmark.currentPriceCents?.let { price ->
                            PriceTag(priceCents = price)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(
                            onClick = onDelete,
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Text("Remove", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}
