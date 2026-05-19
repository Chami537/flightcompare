package com.flightcompare.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flightcompare.data.repository.FlightRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val origin: String = "",
    val destination: String = "",
    val departureDate: String = "",
    val returnDate: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchId: String? = null,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: FlightRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    fun updateOrigin(value: String) { _uiState.value = _uiState.value.copy(origin = value) }
    fun updateDestination(value: String) { _uiState.value = _uiState.value.copy(destination = value) }
    fun updateDepartureDate(value: String) { _uiState.value = _uiState.value.copy(departureDate = value) }
    fun updateReturnDate(value: String) { _uiState.value = _uiState.value.copy(returnDate = value) }
    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }

    fun search() {
        val state = _uiState.value
        if (state.origin.isBlank() || state.destination.isBlank() || state.departureDate.isBlank()) {
            _uiState.value = state.copy(error = "Please fill in all required fields")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = repository.searchFlights(
                origin = state.origin.uppercase().trim(),
                destination = state.destination.uppercase().trim(),
                departureDate = state.departureDate.trim(),
                returnDate = state.returnDate.takeIf { it.isNotBlank() }?.trim(),
            )
            result.fold(
                onSuccess = { response ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        searchId = response.searchId,
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Search failed",
                    )
                }
            )
        }
    }
}
