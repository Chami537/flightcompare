package com.flightcompare.ui.screens.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flightcompare.data.repository.FlightRepository
import com.flightcompare.domain.model.Alert
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlertsUiState(
    val alerts: List<Alert> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class AlertsViewModel @Inject constructor(
    private val repository: FlightRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlertsUiState())
    val uiState: StateFlow<AlertsUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = repository.getAlerts()
            result.fold(
                onSuccess = { alerts ->
                    _uiState.value = AlertsUiState(alerts = alerts, isLoading = false)
                },
                onFailure = { e ->
                    _uiState.value = AlertsUiState(isLoading = false, error = e.message)
                }
            )
        }
    }

    fun toggle(id: Int, isActive: Boolean) {
        viewModelScope.launch {
            repository.toggleAlert(id, isActive)
            load()
        }
    }

    fun delete(id: Int) {
        viewModelScope.launch {
            repository.deleteAlert(id)
            load()
        }
    }
}
