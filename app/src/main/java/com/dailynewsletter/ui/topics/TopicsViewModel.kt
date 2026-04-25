package com.dailynewsletter.ui.topics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailynewsletter.data.repository.TopicRepository
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
    val status: String
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

    init {
        loadTodayTopics()
    }

    private fun loadTodayTopics() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val topics = topicRepository.getTodayTopics()
                _uiState.update { it.copy(topics = topics, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun regenerateTopics() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                topicRepository.regenerateTopics()
                loadTodayTopics()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun updateTopicTitle(id: String, newTitle: String) {
        viewModelScope.launch {
            topicRepository.updateTopicTitle(id, newTitle)
            loadTodayTopics()
        }
    }

    fun deleteTopic(id: String) {
        viewModelScope.launch {
            topicRepository.deleteTopic(id)
            _uiState.update { state ->
                state.copy(topics = state.topics.filter { it.id != id })
            }
        }
    }
}
