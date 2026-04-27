package com.dailynewsletter.ui.newsletter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailynewsletter.DailyNewsletterApp
import com.dailynewsletter.data.repository.NewsletterRepository
import com.dailynewsletter.service.GeneratedNewsletter
import com.dailynewsletter.service.NewsletterGenerationService
import com.dailynewsletter.service.NotificationHelper
import com.dailynewsletter.ui.common.exceptionHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NewsletterUiItem(
    val id: String,
    val title: String,
    val date: String,
    val status: String,
    val pageCount: Int,
    val htmlContent: String? = null,
    val tags: List<String> = emptyList()
)

sealed class ManualGenStatus {
    object Idle : ManualGenStatus()
    object Running : ManualGenStatus()
    data class Success(val title: String) : ManualGenStatus()
    data class Failed(val message: String) : ManualGenStatus()
}

data class NewsletterUiState(
    val newsletters: List<NewsletterUiItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val manualGenStatus: ManualGenStatus = ManualGenStatus.Idle
)

@HiltViewModel
class NewsletterViewModel @Inject constructor(
    private val newsletterRepository: NewsletterRepository,
    private val newsletterGenerationService: NewsletterGenerationService,
    private val notificationHelper: NotificationHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewsletterUiState())
    val uiState: StateFlow<NewsletterUiState> = _uiState.asStateFlow()

    private val exceptionHandler = exceptionHandler { message ->
        _uiState.update { it.copy(error = message, isLoading = false) }
    }

    init {
        loadNewsletters()
    }

    fun loadNewsletters() {
        viewModelScope.launch(exceptionHandler) {
            _uiState.update { it.copy(isLoading = true) }
            refreshNewsletters()
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private suspend fun refreshNewsletters() {
        runCatching { newsletterRepository.getNewsletters() }
            .onSuccess { newsletters ->
                _uiState.update { it.copy(newsletters = newsletters) }
            }
            .onFailure { e ->
                _uiState.update { it.copy(error = e.message ?: "뉴스레터 로드에 실패했습니다") }
            }
    }

    fun printNewsletter(id: String) {
        viewModelScope.launch(exceptionHandler) {
            try {
                newsletterRepository.printNewsletter(id)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun generateNewsletterManually(tag: String, pageCount: Int) {
        viewModelScope.launch(exceptionHandler) {
            _uiState.update { it.copy(manualGenStatus = ManualGenStatus.Running) }
            runCatching {
                newsletterGenerationService.generateForSlot(tag, pageCount)
            }.onSuccess { result: GeneratedNewsletter ->
                loadNewsletters()
                _uiState.update { it.copy(manualGenStatus = ManualGenStatus.Success(result.title)) }
                notificationHelper.notify(
                    title = "생성 완료",
                    message = "뉴스레터가 추가되었습니다",
                    channelId = DailyNewsletterApp.CHANNEL_TOPICS
                )
            }.onFailure { e ->
                val msg = e.message ?: "알 수 없는 오류"
                _uiState.update { it.copy(manualGenStatus = ManualGenStatus.Failed(msg)) }
                notificationHelper.notify(
                    title = "생성 실패",
                    message = msg,
                    channelId = DailyNewsletterApp.CHANNEL_TOPICS
                )
            }
        }
    }

    fun clearManualGenStatus() {
        _uiState.update { it.copy(manualGenStatus = ManualGenStatus.Idle) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
