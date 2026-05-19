package com.flightcompare.ui.screens.results

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flightcompare.data.remote.toDomain
import com.flightcompare.data.repository.FlightRepository
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
                                _uiState.value = SearchState.Success(
                                    offers = offers,
                                    flights = emptyMap()
                                )

                                // Cache offers locally
                                if (response.offers != null) {
                                    repository.cacheOffers(searchId, response.offers)
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
}
