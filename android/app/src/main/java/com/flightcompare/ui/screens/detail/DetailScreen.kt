package com.flightcompare.ui.screens.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.flightcompare.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    flightId: String,
    onViewHistory: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.actionMessage) {
        uiState.actionMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.data?.flight?.let { "${it.origin} → ${it.destination}" } ?: "Flight") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = viewModel::toggleBookmark,
                        enabled = !uiState.isActionLoading,
                    ) {
                        if (uiState.isActionLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                if (uiState.isBookmarked) Icons.Default.Bookmark
                                else Icons.Default.BookmarkBorder,
                                contentDescription = "Bookmark",
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            uiState.isLoading -> LoadingOverlay(message = "Loading flight details...", modifier = Modifier.padding(padding))
            uiState.error != null -> ErrorBanner(
                message = uiState.error!!,
                onRetry = viewModel::loadDetail,
            )
            uiState.data != null -> {
                val data = uiState.data!!
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Flight Summary
                    item {
                        FlightCard(
                            flight = data.flight,
                            lowestPriceCents = data.lowestPriceCents,
                            offers = data.offers.take(4),
                        )
                    }

                    // Price comparison table
                    if (data.offers.isNotEmpty()) {
                        item {
                            Text(
                                "Compare Prices",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }

                        items(data.offers) { offer ->
                            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column {
                                        Text(offer.source, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            "Updated: ${offer.scrapedAt ?: "N/A"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        PriceTag(priceCents = offer.priceCents)
                                        val bookingUrl = offer.bookingLink
                                        if (!bookingUrl.isNullOrBlank()) {
                                            TextButton(
                                                onClick = {
                                                    try {
                                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(bookingUrl))
                                                        context.startActivity(intent)
                                                    } catch (_: Exception) {
                                                        // No browser available — ignore
                                                    }
                                                },
                                                contentPadding = PaddingValues(0.dp),
                                            ) {
                                                Text("Book")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Actions
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedButton(
                                onClick = { onViewHistory(flightId) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.TrendingDown, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Price History")
                            }

                            Button(
                                onClick = viewModel::showAlertDialog,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Default.NotificationsActive, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Set Alert")
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(32.dp)) }
                }
            }
        }

        // Alert Dialog
        if (uiState.showAlertDialog) {
            AlertDialog(
                onDismissRequest = viewModel::hideAlertDialog,
                title = { Text("Set Price Alert") },
                text = {
                    Column {
                        Text("Get notified when price drops below:")
                        Spacer(modifier = Modifier.height(8.dp))
                        val isValid = uiState.alertPrice.toDoubleOrNull()?.let { it > 0 } ?: false
                        val showError = uiState.alertPrice.isNotEmpty() && !isValid
                        OutlinedTextField(
                            value = uiState.alertPrice,
                            onValueChange = viewModel::updateAlertPrice,
                            label = { Text("Target price ($)") },
                            placeholder = { Text("e.g. 250") },
                            singleLine = true,
                            isError = showError,
                            supportingText = if (showError) {
                                { Text("Enter a valid price") }
                            } else null,
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val cents = (uiState.alertPrice.toDoubleOrNull()?.times(100)?.toInt()) ?: 0
                            if (cents > 0) viewModel.setAlert(cents)
                        },
                        enabled = (uiState.alertPrice.toDoubleOrNull()?.let { it > 0 } ?: false)
                            && !uiState.isActionLoading,
                    ) {
                        Text("Set Alert")
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::hideAlertDialog) {
                        Text("Cancel")
                    }
                },
            )
        }
    }
}
