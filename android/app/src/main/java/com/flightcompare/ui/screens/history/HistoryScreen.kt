package com.flightcompare.ui.screens.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.flightcompare.domain.model.PricePoint
import com.flightcompare.ui.components.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

private val axisLabelStyle = TextStyle(fontSize = 10.sp, color = Color.Gray)
private val dateFormatter = DateTimeFormatter.ofPattern("MM/dd")

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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> LoadingOverlay(message = "Loading price history...", modifier = Modifier.padding(padding))
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
                icon = { Icon(Icons.AutoMirrored.Filled.TrendingDown, contentDescription = null) },
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

    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val prices = points.map { it.priceCents }
    val minPrice = prices.min()
    val maxPrice = prices.max()
    val priceRange = (maxPrice - minPrice).coerceAtLeast(1)
    val lineColor = MaterialTheme.colorScheme.primary
    val fillColor = lineColor.copy(alpha = 0.1f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    // Selected point index for tooltip
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    // Compute nice Y-axis labels
    val yTickCount = 4
    val yStep = priceRange.toFloat() / (yTickCount + 1)
    val yLabels = (0..yTickCount + 1).map { i -> maxPrice - (yStep * i).toInt() }
    val yLabelTexts = yLabels.map { "$${it / 100}" }

    // Measure Y label width for margin
    val maxYLabelWidth = textMeasurer.measure(
        yLabelTexts.maxByOrNull { it.length } ?: "$1000",
        axisLabelStyle,
    ).size.width.toFloat()

    // Pre-compute chart layout values (values in px, computed in dp via density)
    val leftMarginDp = with(density) { (maxYLabelWidth + 12.dp.toPx()).toDp() }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(points) {
                    detectTapGestures { offset ->
                        val leftMarginPx = leftMarginDp.toPx()
                        val chartWidth = size.width - leftMarginPx
                        val stepX = chartWidth / (points.size - 1)
                        val index = ((offset.x - leftMarginPx) / stepX).roundToInt()

                        selectedIndex = if (index in points.indices) {
                            // If tapping the same point, deselect
                            if (index == selectedIndex) null else index
                        } else {
                            null
                        }
                    }
                },
        ) {
            val leftMarginPx = leftMarginDp.toPx()
            val chartWidth = size.width - leftMarginPx
            val chartHeight = size.height * 0.8f
            val topPadding = size.height * 0.1f

            if (points.size < 2) return@Canvas

            val stepX = chartWidth / (points.size - 1)

            // Draw Y-axis gridlines and labels
            yLabels.forEachIndexed { index, price ->
                val y = topPadding + chartHeight * (maxPrice - price).toFloat() / priceRange
                drawLine(
                    color = gridColor,
                    start = Offset(leftMarginPx, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                )
                val label = yLabelTexts[index]
                val labelLayout = textMeasurer.measure(label, axisLabelStyle)
                drawText(
                    textLayoutResult = labelLayout,
                    topLeft = Offset(
                        leftMarginPx - labelLayout.size.width - 8.dp.toPx(),
                        y - labelLayout.size.height / 2,
                    ),
                )
            }

            // Draw X-axis date labels
            val xLabelHeight = 16.dp.toPx()
            points.forEachIndexed { index, point ->
                if (index % maxOf(1, points.size / 5) == 0 || index == points.size - 1) {
                    val dateStr = point.scrapedAt?.let { iso ->
                        try {
                            val instant = Instant.parse(iso)
                            val localDate = instant.atZone(ZoneId.systemDefault()).toLocalDate()
                            localDate.format(dateFormatter)
                        } catch (_: Exception) { null }
                    }
                    dateStr?.let {
                        val x = leftMarginPx + index * stepX
                        val labelLayout = textMeasurer.measure(it, axisLabelStyle)
                        drawText(
                            textLayoutResult = labelLayout,
                            topLeft = Offset(
                                x - labelLayout.size.width / 2,
                                size.height - xLabelHeight,
                            ),
                        )
                    }
                }
            }

            // Draw the chart line and fill area
            val path = Path()
            val fillPath = Path()

            points.forEachIndexed { index, point ->
                val x = leftMarginPx + index * stepX
                val y = topPadding + chartHeight * (maxPrice - point.priceCents).toFloat() / priceRange

                if (index == 0) {
                    path.moveTo(x, y)
                    fillPath.moveTo(x, topPadding + chartHeight)
                    fillPath.lineTo(x, y)
                } else {
                    path.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }

            fillPath.lineTo(leftMarginPx + (points.size - 1) * stepX, topPadding + chartHeight)
            fillPath.close()

            drawPath(fillPath, color = fillColor)
            drawPath(path, color = lineColor, style = Stroke(width = 3.dp.toPx()))

            // Draw data point dots (highlight selected)
            points.forEachIndexed { index, point ->
                val x = leftMarginPx + index * stepX
                val y = topPadding + chartHeight * (maxPrice - point.priceCents).toFloat() / priceRange
                val radius = if (index == selectedIndex) 7.dp.toPx() else 4.dp.toPx()
                val dotColor = if (index == selectedIndex) lineColor else lineColor
                drawCircle(color = dotColor, radius = radius, center = Offset(x, y))
            }
        }

        // Tooltip overlay
        selectedIndex?.let { idx ->
            val point = points[idx]
            val tooltipX = with(density) {
                val leftMarginPx = leftMarginDp.toPx()
                leftMarginDp + (leftMarginPx / density.density).dp // approximate position
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                shadowElevation = 4.dp,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 8.dp, start = 8.dp),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    val dollars = point.priceCents / 100
                    val cents = point.priceCents % 100
                    Text(
                        text = "$${dollars}.${cents.toString().padStart(2, '0')}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = lineColor,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Source: ${point.sourceWebsite}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    val dateText = point.scrapedAt?.let { iso ->
                        try {
                            val instant = Instant.parse(iso)
                            val localDate = instant.atZone(ZoneId.systemDefault()).toLocalDate()
                            localDate.toString()
                        } catch (_: Exception) { iso.take(10) }
                    } ?: "N/A"
                    Text(
                        text = "Date: $dateText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
