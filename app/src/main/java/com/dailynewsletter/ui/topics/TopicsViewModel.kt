package com.dailynewsletter.ui.topics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailynewsletter.data.repository.KeywordRepository
import com.dailynewsletter.data.repository.TopicRepository
import com.dailynewsletter.service.GeminiRetry
import com.dailynewsletter.service.GeminiTopicSuggester
import com.dailynewsletter.service.RetryEvent
import com.dailynewsletter.ui.common.exceptionHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TopicUiItem(
    val id: String,
    val title: String,
    val priorityType: String,
    val sourceKeywords: List<String>,
    val status: String,
    val tags: List<String> = emptyList()
)

sealed class TopicGenStatus {
    object Idle : TopicGenStatus()
    object Running : TopicGenStatus()
    data class Retrying(val message: String) : TopicGenStatus()
    data class Success(val count: Int) : TopicGenStatus()
    data class Failed(val message: String) : TopicGenStatus()
}

data class TopicsUiState(
    val topics: List<TopicUiItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val topicGenStatus: TopicGenStatus = TopicGenStatus.Idle
)

@HiltViewModel
class TopicsViewModel @Inject constructor(
    private val topicRepository: TopicRepository,
    private val keywordRepository: KeywordRepository,
    private val geminiTopicSuggester: GeminiTopicSuggester
) : ViewModel() {

    private val _uiState = MutableStateFlow(TopicsUiState())
    val uiState: StateFlow<TopicsUiState> = _uiState.asStateFlow()

    private val exceptionHandler = exceptionHandler { message ->
        _uiState.update { it.copy(error = message, isLoading = false) }
    }

    init {
        loadTodayTopics()
        viewModelScope.launch {
            GeminiRetry.events.collect { event ->
                val current = _uiState.value.topicGenStatus
                if (current is TopicGenStatus.Running || current is TopicGenStatus.Retrying) {
                    val message = when (event) {
                        is RetryEvent.Retrying -> "재시도 중... (${event.attempt}/${event.totalAttempts})"
                        is RetryEvent.Switching -> "${event.toModel}로 전환 중..."
                    }
                    _uiState.update { it.copy(topicGenStatus = TopicGenStatus.Retrying(message)) }
                }
            }
        }
    }

    private fun loadTodayTopics() {
        viewModelScope.launch(exceptionHandler) {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val topics = topicRepository.getTopics()
                _uiState.update { it.copy(topics = topics, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun generateTopicsManually() {
        viewModelScope.launch(exceptionHandler) {
            _uiState.update { it.copy(topicGenStatus = TopicGenStatus.Running) }
            runCatching {
                val pending = keywordRepository.getPendingKeywords()
                val past = topicRepository.getAllPastTopicTitles()
                val suggested = geminiTopicSuggester.suggest(pending, past)
                suggested.forEach { topic ->
                    topicRepository.saveTopic(
                        title = topic.title,
                        priorityType = topic.priorityType,
                        sourceKeywordIds = topic.sourceKeywordIds,
                        tags = emptyList()
                    )
                }
                suggested.size
            }.onSuccess { n ->
                _uiState.update { it.copy(topicGenStatus = TopicGenStatus.Success(n)) }
                loadTopics()
            }.onFailure { e ->
                val msg = e.message ?: "잠시 후 다시 시도해 주세요"
                _uiState.update { it.copy(topicGenStatus = TopicGenStatus.Failed(msg)) }
            }
        }
    }

    fun clearTopicGenStatus() {
        _uiState.update { it.copy(topicGenStatus = TopicGenStatus.Idle) }
    }

    // regenerateTopics() — stub retained for TopicsScreen compile compat.
    fun regenerateTopics() {
        loadTodayTopics()
    }

    private fun loadTopics() {
        viewModelScope.launch(exceptionHandler) {
            try {
                val topics = topicRepository.getTopics()
                _uiState.update { it.copy(topics = topics) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun updateTopicTitle(id: String, newTitle: String) {
        viewModelScope.launch(exceptionHandler) {
            topicRepository.updateTopicTitle(id, newTitle)
            loadTodayTopics()
        }
    }

    fun deleteTopic(id: String) {
        viewModelScope.launch(exceptionHandler) {
            topicRepository.deleteTopic(id)
            _uiState.update { state ->
                state.copy(topics = state.topics.filter { it.id != id })
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
