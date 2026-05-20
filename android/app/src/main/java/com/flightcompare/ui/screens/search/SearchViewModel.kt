package com.flightcompare.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flightcompare.data.repository.FlightRepository
import com.flightcompare.domain.model.AirportSuggestion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class SearchEvent {
    data class NavigateToResults(val searchId: String) : SearchEvent()
}

data class SearchUiState(
    val origin: String = "",
    val destination: String = "",
    val departureDate: String = "",
    val returnDate: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val originSuggestions: List<AirportSuggestion> = emptyList(),
    val destinationSuggestions: List<AirportSuggestion> = emptyList(),
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: FlightRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _events = Channel<SearchEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var originDebounceJob: Job? = null
    private var destinationDebounceJob: Job? = null

    fun updateOrigin(value: String) {
        _uiState.value = _uiState.value.copy(origin = value, originSuggestions = emptyList())
        originDebounceJob?.cancel()
        originDebounceJob = viewModelScope.launch {
            delay(300)
            if (value.length >= 2) {
                repository.searchAirports(value)
                    .onSuccess { suggestions ->
                        _uiState.value = _uiState.value.copy(originSuggestions = suggestions)
                    }
            }
        }
    }

    fun updateDestination(value: String) {
        _uiState.value = _uiState.value.copy(destination = value, destinationSuggestions = emptyList())
        destinationDebounceJob?.cancel()
        destinationDebounceJob = viewModelScope.launch {
            delay(300)
            if (value.length >= 2) {
                repository.searchAirports(value)
                    .onSuccess { suggestions ->
                        _uiState.value = _uiState.value.copy(destinationSuggestions = suggestions)
                    }
            }
        }
    }

    fun selectOrigin(airport: AirportSuggestion) {
        _uiState.value = _uiState.value.copy(
            origin = airport.code,
            originSuggestions = emptyList()
        )
    }

    fun selectDestination(airport: AirportSuggestion) {
        _uiState.value = _uiState.value.copy(
            destination = airport.code,
            destinationSuggestions = emptyList()
        )
    }

    fun swapOriginDestination() {
        val current = _uiState.value
        _uiState.value = current.copy(
            origin = current.destination,
            destination = current.origin,
            originSuggestions = emptyList(),
            destinationSuggestions = emptyList(),
        )
    }

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
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    _events.send(SearchEvent.NavigateToResults(response.searchId))
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
