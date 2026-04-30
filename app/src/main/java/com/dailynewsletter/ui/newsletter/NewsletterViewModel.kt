package com.dailynewsletter.ui.newsletter

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailynewsletter.DailyNewsletterApp
import com.dailynewsletter.data.repository.NewsletterRepository
import com.dailynewsletter.service.GeminiRetry
import com.dailynewsletter.service.GeneratedNewsletter
import com.dailynewsletter.service.NewsletterGenerationService
import com.dailynewsletter.service.NotificationHelper
import com.dailynewsletter.service.PrintService
import com.dailynewsletter.service.RetryEvent
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
    data class Retrying(val message: String) : ManualGenStatus()
    data class Success(val title: String) : ManualGenStatus()
    data class Failed(val message: String) : ManualGenStatus()
}

data class NewsletterUiState(
    val newsletters: List<NewsletterUiItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val manualGenStatus: ManualGenStatus = ManualGenStatus.Idle,
    val loadingDetailIds: Set<String> = emptySet()
)

@HiltViewModel
class NewsletterViewModel @Inject constructor(
    private val newsletterRepository: NewsletterRepository,
    private val newsletterGenerationService: NewsletterGenerationService,
    private val notificationHelper: NotificationHelper,
    private val printService: PrintService
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewsletterUiState())
    val uiState: StateFlow<NewsletterUiState> = _uiState.asStateFlow()

    private val exceptionHandler = exceptionHandler { message ->
        _uiState.update { it.copy(error = message, isLoading = false) }
    }

    init {
        loadNewsletters()
        viewModelScope.launch {
            GeminiRetry.events.collect { event ->
                when (event) {
                    is RetryEvent.Retrying -> {
                        val currentStatus = _uiState.value.manualGenStatus
                        if (currentStatus is ManualGenStatus.Running ||
                            currentStatus is ManualGenStatus.Retrying
                        ) {
                            val msg = "잠시 후 다시 시도하고 있어요 (${event.attempt}/${event.totalAttempts})"
                            _uiState.update { it.copy(manualGenStatus = ManualGenStatus.Retrying(msg)) }
                        }
                    }
                    is RetryEvent.Switching -> {
                        val msg = "다른 모델로 시도하고 있어요"
                        _uiState.update { it.copy(manualGenStatus = ManualGenStatus.Retrying(msg)) }
                    }
                }
            }
        }
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

    fun printNewsletter(activity: Activity, item: NewsletterUiItem) {
        val html = item.htmlContent ?: run {
            _uiState.update { it.copy(error = "뉴스레터 내용이 없습니다") }
            return
        }
        printService.startSystemPrint(activity, html, item.title)
        viewModelScope.launch(exceptionHandler) {
            newsletterRepository.markNewsletterPrinted(item.id)
            loadNewsletters()
        }
    }

    fun loadNewsletterContent(id: String) {
        val current = _uiState.value
        if (current.newsletters.find { it.id == id }?.htmlContent != null) return
        if (current.loadingDetailIds.contains(id)) return
        viewModelScope.launch(exceptionHandler) {
            _uiState.update { it.copy(loadingDetailIds = it.loadingDetailIds + id) }
            runCatching { newsletterRepository.getNewsletter(id) }
                .onSuccess { fetched ->
                    _uiState.update { state ->
                        state.copy(
                            newsletters = state.newsletters.map { item ->
                                if (item.id == id) item.copy(htmlContent = fetched.htmlContent) else item
                            },
                            loadingDetailIds = state.loadingDetailIds - id
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { state ->
                        state.copy(
                            error = e.message ?: "본문 로드에 실패했습니다",
                            loadingDetailIds = state.loadingDetailIds - id
                        )
                    }
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
                    title = "생성 미완료",
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
