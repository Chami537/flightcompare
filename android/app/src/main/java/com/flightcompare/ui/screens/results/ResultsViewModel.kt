package com.flightcompare.ui.screens.results

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flightcompare.data.remote.Mapper.toDomain
import com.flightcompare.data.remote.dto.FlightDto
import com.flightcompare.data.remote.dto.SearchResponse
import com.flightcompare.data.repository.FlightRepository
import com.flightcompare.domain.model.Flight
import com.flightcompare.domain.model.Offer
import com.flightcompare.domain.model.SearchState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ResultsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: FlightRepository,
) : ViewModel() {

    private val searchId: String = savedStateHandle.get<String>("searchId") ?: ""

    private val _uiState = MutableStateFlow<SearchState>(SearchState.Loading)
    val uiState: StateFlow<SearchState> = _uiState.asStateFlow()

    init {
        pollResults()
    }

    private fun pollResults() {
        viewModelScope.launch {
            var attempts = 0
            while (attempts < 30) { // max 30 polls (5 min at 10s intervals)
                val result = repository.getSearchStatus(searchId)
                result.fold(
                    onSuccess = { response ->
                        when (response.status) {
                            "complete" -> {
                                val offers = response.offers?.map { it.toDomain() } ?: emptyList()
                                val flights = extractFlights(response)
                                _uiState.value = SearchState.Success(offers, flights)

                                // Cache locally
                                if (response.offers != null && flights.isNotEmpty()) {
                                    repository.cacheResults(
                                        searchId,
                                        response.offers,
                                        flights.map { f ->
                                            FlightDto(f.id, f.origin, f.destination,
                                                f.departureDate, f.returnDate, f.airline,
                                                f.flightNumber, f.departureTime, f.arrivalTime,
                                                f.durationMin, f.stops, f.cabinClass)
                                        }
                                    )
                                }
                                return@launch
                            }
                            "failed" -> {
                                _uiState.value = SearchState.Error(
                                    response.error ?: "Search failed"
                                )
                                return@launch
                            }
                        }
                    },
                    onFailure = { e ->
                        _uiState.value = SearchState.Error(e.message ?: "Connection error")
                        return@launch
                    }
                )
                delay(10_000) // poll every 10s
                attempts++
            }
            _uiState.value = SearchState.Error("Search timed out. Please try again.")
        }
    }

    private fun extractFlights(response: SearchResponse): Map<String, Flight> {
        // The search endpoint returns offers only; we build minimal flight objects
        // from offer data, then enrich on detail view
        // For now, we aggregate by flight_id from offers
        return emptyMap()
    }
}
