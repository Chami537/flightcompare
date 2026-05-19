package com.flightcompare.ui.screens.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flightcompare.data.repository.FlightRepository
import com.flightcompare.domain.model.PricePoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val points: List<PricePoint> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val minPrice: Int? = null,
    val maxPrice: Int? = null,
    val currentPrice: Int? = null,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: FlightRepository,
) : ViewModel() {

    private val flightId: String = savedStateHandle.get<String>("flightId") ?: ""

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        loadHistory()
    }

    fun loadHistory() {
        viewModelScope.launch {
            val result = repository.getPriceHistory(flightId)
            result.fold(
                onSuccess = { history ->
                    val prices = history.points.map { it.priceCents }
                    _uiState.value = HistoryUiState(
                        points = history.points,
                        isLoading = false,
                        minPrice = prices.minOrNull(),
                        maxPrice = prices.maxOrNull(),
                        currentPrice = prices.lastOrNull(),
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message,
                    )
                }
            )
        }
    }
}
