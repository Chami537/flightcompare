package com.flightcompare.ui.screens.alerts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.flightcompare.domain.model.Alert
import com.flightcompare.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(
    onFlightClick: (String) -> Unit,
    viewModel: AlertsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Price Alerts") })
        }
    ) { padding ->
        when {
            uiState.isLoading -> LoadingOverlay(modifier = Modifier.padding(padding))
            uiState.error != null -> ErrorBanner(
                message = uiState.error!!,
                onRetry = viewModel::load,
            )
            uiState.alerts.isEmpty() -> EmptyState(
                icon = { Icon(Icons.Default.NotificationsOff, contentDescription = null) },
                title = "No price alerts",
                subtitle = "Set alerts on flight details to get notified of price drops",
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
                    items(uiState.alerts, key = { it.id }) { alert ->
                        AlertCard(
                            alert = alert,
                            onToggle = { viewModel.toggle(alert.id, it) },
                            onDelete = { viewModel.delete(alert.id) },
                            onClick = { onFlightClick(alert.flightId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AlertCard(
    alert: Alert,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            alert.flight?.let { flight ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${flight.origin} → ${flight.destination}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            flight.airline,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    Switch(
                        checked = alert.isActive,
                        onCheckedChange = onToggle,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Price comparison
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Target", style = MaterialTheme.typography.labelSmall)
                        PriceTag(priceCents = alert.targetPriceCents)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val progress = alert.currentPriceCents?.let { current ->
                            alert.targetPriceCents.toFloat() / current.coerceAtLeast(1)
                        } ?: 0f
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .width(100.dp)
                                .height(8.dp),
                        )
                        Text(
                            "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Current", style = MaterialTheme.typography.labelSmall)
                        alert.currentPriceCents?.let {
                            PriceTag(priceCents = it)
                        } ?: Text("N/A")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
