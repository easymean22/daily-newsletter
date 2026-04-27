package com.dailynewsletter.ui.topics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailynewsletter.data.repository.TopicRepository
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

data class TopicsUiState(
    val topics: List<TopicUiItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class TopicsViewModel @Inject constructor(
    private val topicRepository: TopicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TopicsUiState())
    val uiState: StateFlow<TopicsUiState> = _uiState.asStateFlow()

    private val exceptionHandler = exceptionHandler { message ->
        _uiState.update { it.copy(error = message, isLoading = false) }
    }

    init {
        loadTodayTopics()
    }

    private fun loadTodayTopics() {
        viewModelScope.launch(exceptionHandler) {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val topics = topicRepository.getTodayTopics()
                _uiState.update { it.copy(topics = topics, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    // regenerateTopics() — stub retained for TopicsScreen compile compat.
    // Full orchestration (step 7, manual button) is out of scope for this task.
    fun regenerateTopics() {
        // TODO(step-7): implement manual regeneration via GeminiTopicSuggester orchestration.
        loadTodayTopics()
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
