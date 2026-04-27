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
    val tags: List<String> = emptyList()
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
    val autoGenStatus: AutoGenStatus = AutoGenStatus.Idle
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
            combine(keywordRepository.observeKeywords(), _filter) { keywords, filter ->
                val filtered = when (filter) {
                    "pending" -> keywords.filter { !it.isResolved }
                    "resolved" -> keywords.filter { it.isResolved }
                    else -> keywords
                }
                Pair(filtered, filter)
            }.collect { (filtered, filter) ->
                // Preserve autoGenStatus so snackbar is not lost when keyword list refreshes.
                _uiState.update { current ->
                    current.copy(keywords = filtered, filter = filter, isLoading = false)
                }
            }
        }
    }

    fun addKeyword(text: String, type: String, tags: List<String> = emptyList()) {
        viewModelScope.launch(exceptionHandler) {
            // Step 1: add keyword — failure aborts auto-gen too
            val newKeyword = try {
                keywordRepository.addKeyword(text, type, tags)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
                return@launch
            }

            // Step 2: auto topic generation (Q1: immediate, no debounce)
            _uiState.update { it.copy(autoGenStatus = AutoGenStatus.Running) }
            runCatching {
                val pending = keywordRepository.getPendingKeywords()
                val past = topicRepository.getAllPastTopicTitles()
                val suggested = geminiTopicSuggester.suggest(
                    pendingKeywords = pending,
                    pastTopicTitles = past
                )
                suggested.forEach { topic ->
                    // Q5: all paths use default tag = 모든주제 alone — pass emptyList(), invariant fills it.
                    topicRepository.saveTopic(
                        title = topic.title,
                        priorityType = topic.priorityType,
                        sourceKeywordIds = topic.sourceKeywordIds,
                        tags = emptyList()
                    )
                }
                suggested.size
            }.onSuccess { n ->
                _uiState.update { it.copy(autoGenStatus = AutoGenStatus.Success(n)) }
            }.onFailure { e ->
                val msg = when {
                    e.message?.contains("API Key") == true -> "Gemini API 키를 먼저 설정해주세요"
                    else -> e.message ?: "자동 주제 생성에 실패했습니다"
                }
                _uiState.update { it.copy(autoGenStatus = AutoGenStatus.Failed(msg)) }
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
