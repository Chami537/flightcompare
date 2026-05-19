package com.flightcompare.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flightcompare.data.repository.FlightRepository
import com.flightcompare.domain.model.FlightWithOffers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailUiState(
    val data: FlightWithOffers? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val isBookmarked: Boolean = false,
    val alertPrice: String = "",
    val showAlertDialog: Boolean = false,
    val actionMessage: String? = null,
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: FlightRepository,
) : ViewModel() {

    private val flightId: String = savedStateHandle.get<String>("flightId") ?: ""

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    init {
        loadDetail()
        checkBookmarkStatus()
    }

    fun loadDetail() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = repository.getFlightDetail(flightId)
            result.fold(
                onSuccess = { data ->
                    _uiState.value = _uiState.value.copy(
                        data = data,
                        isLoading = false,
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load",
                    )
                }
            )
        }
    }

    fun toggleBookmark() {
        viewModelScope.launch {
            if (_uiState.value.isBookmarked) {
                // Find bookmark id and delete - simplified for now
                _uiState.value = _uiState.value.copy(
                    isBookmarked = false,
                    actionMessage = "Bookmark removed",
                )
            } else {
                val result = repository.createBookmark(flightId)
                result.fold(
                    onSuccess = {
                        _uiState.value = _uiState.value.copy(
                            isBookmarked = true,
                            actionMessage = "Bookmarked!",
                        )
                    },
                    onFailure = { e ->
                        _uiState.value = _uiState.value.copy(
                            actionMessage = "Failed: ${e.message}",
                        )
                    }
                )
            }
        }
    }

    fun setAlert(targetPriceCents: Int) {
        viewModelScope.launch {
            val result = repository.createAlert(flightId, targetPriceCents)
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        showAlertDialog = false,
                        alertPrice = "",
                        actionMessage = "Alert set! We'll notify you when price drops below $${targetPriceCents / 100}.",
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        actionMessage = "Failed: ${e.message}",
                    )
                }
            )
        }
    }

    fun showAlertDialog() {
        _uiState.value = _uiState.value.copy(showAlertDialog = true)
    }

    fun hideAlertDialog() {
        _uiState.value = _uiState.value.copy(showAlertDialog = false)
    }

    fun updateAlertPrice(value: String) {
        _uiState.value = _uiState.value.copy(alertPrice = value)
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(actionMessage = null)
    }

    private fun checkBookmarkStatus() {
        viewModelScope.launch {
            val result = repository.getBookmarks()
            result.fold(
                onSuccess = { bookmarks ->
                    _uiState.value = _uiState.value.copy(
                        isBookmarked = bookmarks.any { it.flightId == flightId }
                    )
                },
                onFailure = { }
            )
        }
    }
}
