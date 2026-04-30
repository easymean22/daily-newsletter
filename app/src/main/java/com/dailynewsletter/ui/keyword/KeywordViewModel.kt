package com.dailynewsletter.ui.keyword

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailynewsletter.data.repository.KeywordRepository
import com.dailynewsletter.data.repository.TopicRepository
import com.dailynewsletter.service.GeminiTopicSuggester
import com.dailynewsletter.ui.common.exceptionHandler
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
    val resolvedDate: String? = null,
    val tags: List<String> = emptyList(),
    val createdTime: String? = null
)

sealed class AutoGenStatus {
    object Idle : AutoGenStatus()
    object Running : AutoGenStatus()
    data class Success(val count: Int) : AutoGenStatus()
    data class Failed(val message: String) : AutoGenStatus()
}

data class KeywordUiState(
    val keywords: List<KeywordUiItem> = emptyList(),
    val filter: String = "all",
    val isLoading: Boolean = false,
    val error: String? = null,
    val autoGenStatus: AutoGenStatus = AutoGenStatus.Idle,
    val availableTags: Set<String> = emptySet(),
    val selectedTagFilter: String? = null
)

@HiltViewModel
class KeywordViewModel @Inject constructor(
    private val keywordRepository: KeywordRepository,
    private val topicRepository: TopicRepository,
    private val geminiTopicSuggester: GeminiTopicSuggester
) : ViewModel() {

    private val _filter = MutableStateFlow("all")
    private val _uiState = MutableStateFlow(KeywordUiState())
    val uiState: StateFlow<KeywordUiState> = _uiState.asStateFlow()

    private val exceptionHandler = exceptionHandler { message ->
        _uiState.update { it.copy(error = message) }
    }

    init {
        loadKeywords()
    }

    private fun loadKeywords() {
        viewModelScope.launch(exceptionHandler) {
            _uiState.update { it.copy(isLoading = true) }
            runCatching { keywordRepository.refreshKeywords() }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message ?: "키워드 로드에 실패했습니다") }
                }
            loadTags()
            combine(keywordRepository.observeKeywords(), _filter) { keywords, filter ->
                val filtered = when (filter) {
                    "pending" -> keywords.filter { !it.isResolved }
                    "resolved" -> keywords.filter { it.isResolved }
                    else -> keywords
                }
                Pair(filtered, filter)
            }.collect { (filtered, filter) ->
                _uiState.update { current ->
                    val tagFilter = current.selectedTagFilter
                    val tagFiltered = if (tagFilter != null) {
                        filtered.filter { tagFilter in it.tags }
                    } else filtered
                    current.copy(keywords = tagFiltered, filter = filter, isLoading = false)
                }
            }
        }
    }

    fun loadTags() {
        viewModelScope.launch(exceptionHandler) {
            val tags = keywordRepository.getAllTags()
            _uiState.update { it.copy(availableTags = tags) }
        }
    }

    fun addKeyword(text: String, tags: List<String> = emptyList()) {
        viewModelScope.launch(exceptionHandler) {
            try {
                keywordRepository.addKeyword(text, tags)
                loadTags()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun selectTagFilter(tag: String?) {
        _uiState.update { it.copy(selectedTagFilter = tag) }
        // Re-apply filtering by reloading from cached flow
        viewModelScope.launch(exceptionHandler) {
            keywordRepository.refreshKeywords()
        }
    }

    fun addNewTag(tag: String) {
        val trimmed = tag.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch(exceptionHandler) {
            keywordRepository.persistTag(trimmed)
            _uiState.update { it.copy(availableTags = it.availableTags + trimmed) }
        }
    }

    fun removeTag(tag: String) {
        viewModelScope.launch(exceptionHandler) {
            keywordRepository.removeTagFromAllKeywords(tag)
            _uiState.update { current ->
                current.copy(
                    availableTags = current.availableTags - tag,
                    selectedTagFilter = if (current.selectedTagFilter == tag) null else current.selectedTagFilter
                )
            }
        }
    }

    fun clearAutoGenStatus() {
        _uiState.update { it.copy(autoGenStatus = AutoGenStatus.Idle) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun deleteKeyword(id: String) {
        viewModelScope.launch(exceptionHandler) {
            keywordRepository.deleteKeyword(id)
        }
    }

    fun toggleResolved(id: String) {
        viewModelScope.launch(exceptionHandler) {
            keywordRepository.toggleResolved(id)
        }
    }

    fun setFilter(filter: String) {
        _filter.value = filter
    }
}
