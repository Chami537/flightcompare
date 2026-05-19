package com.flightcompare.ui.screens.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.flightcompare.domain.model.PricePoint
import com.flightcompare.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    flightId: String,
    onBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Price History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> LoadingOverlay(modifier = Modifier.padding(padding))
            uiState.error != null -> ErrorBanner(
                message = uiState.error!!,
                onRetry = viewModel::loadHistory,
            )
            uiState.points.isNotEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                ) {
                    // Stats row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Lowest", style = MaterialTheme.typography.labelMedium)
                            uiState.minPrice?.let {
                                PriceTag(priceCents = it)
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Current", style = MaterialTheme.typography.labelMedium)
                            uiState.currentPrice?.let {
                                PriceTag(priceCents = it)
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Highest", style = MaterialTheme.typography.labelMedium)
                            uiState.maxPrice?.let {
                                PriceTag(priceCents = it)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Chart
                    PriceChart(
                        points = uiState.points,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp),
                    )
                }
            }
            else -> EmptyState(
                icon = { Icon(Icons.Default.TrendingDown, contentDescription = null) },
                title = "No price history yet",
            )
        }
    }
}

@Composable
fun PriceChart(
    points: List<PricePoint>,
    modifier: Modifier = Modifier,
) {
    if (points.size < 2) return

    val prices = points.map { it.priceCents }
    val minPrice = prices.min()
    val maxPrice = prices.max()
    val priceRange = (maxPrice - minPrice).coerceAtLeast(1)
    val lineColor = MaterialTheme.colorScheme.primary
    val fillColor = lineColor.copy(alpha = 0.1f)

    Canvas(modifier = modifier) {
        val stepX = size.width / (points.size - 1)
        val path = Path()
        val fillPath = Path()

        points.forEachIndexed { index, point ->
            val x = index * stepX
            val y = size.height - ((point.priceCents - minPrice).toFloat() / priceRange * size.height * 0.8f) - size.height * 0.1f

            if (index == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, size.height)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }

        fillPath.lineTo((points.size - 1) * stepX, size.height)
        fillPath.close()

        drawPath(fillPath, color = fillColor)
        drawPath(path, color = lineColor, style = Stroke(width = 3.dp.toPx()))

        // Draw dots
        points.forEachIndexed { index, point ->
            val x = index * stepX
            val y = size.height - ((point.priceCents - minPrice).toFloat() / priceRange * size.height * 0.8f) - size.height * 0.1f
            drawCircle(color = lineColor, radius = 4.dp.toPx(), center = Offset(x, y))
        }
    }
}
