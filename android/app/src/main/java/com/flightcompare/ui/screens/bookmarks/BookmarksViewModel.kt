package com.flightcompare.ui.screens.bookmarks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flightcompare.data.repository.FlightRepository
import com.flightcompare.domain.model.Bookmark
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BookmarksUiState(
    val bookmarks: List<Bookmark> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class BookmarksViewModel @Inject constructor(
    private val repository: FlightRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookmarksUiState())
    val uiState: StateFlow<BookmarksUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = repository.getBookmarks()
            result.fold(
                onSuccess = { bookmarks ->
                    _uiState.value = BookmarksUiState(
                        bookmarks = bookmarks,
                        isLoading = false,
                    )
                },
                onFailure = { e ->
                    _uiState.value = BookmarksUiState(
                        isLoading = false,
                        error = e.message,
                    )
                }
            )
        }
    }

    fun delete(id: Int) {
        viewModelScope.launch {
            repository.deleteBookmark(id)
            load()
        }
    }
}
