package com.dailynewsletter.ui.keyword

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailynewsletter.data.repository.KeywordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class KeywordUiItem(
    val id: String,
    val title: String,
    val type: String,
    val isResolved: Boolean,
    val resolvedDate: String? = null
)

data class KeywordUiState(
    val keywords: List<KeywordUiItem> = emptyList(),
    val filter: String = "all",
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class KeywordViewModel @Inject constructor(
    private val keywordRepository: KeywordRepository
) : ViewModel() {

    private val _filter = MutableStateFlow("all")
    private val _uiState = MutableStateFlow(KeywordUiState())
    val uiState: StateFlow<KeywordUiState> = _uiState.asStateFlow()

    init {
        loadKeywords()
    }

    private fun loadKeywords() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            combine(keywordRepository.observeKeywords(), _filter) { keywords, filter ->
                val filtered = when (filter) {
                    "pending" -> keywords.filter { !it.isResolved }
                    "resolved" -> keywords.filter { it.isResolved }
                    else -> keywords
                }
                KeywordUiState(keywords = filtered, filter = filter, isLoading = false)
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun addKeyword(text: String, type: String) {
        viewModelScope.launch {
            keywordRepository.addKeyword(text, type)
        }
    }

    fun deleteKeyword(id: String) {
        viewModelScope.launch {
            keywordRepository.deleteKeyword(id)
        }
    }

    fun toggleResolved(id: String) {
        viewModelScope.launch {
            keywordRepository.toggleResolved(id)
        }
    }

    fun setFilter(filter: String) {
        _filter.value = filter
    }
}
