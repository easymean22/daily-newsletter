package com.dailynewsletter.ui.newsletter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailynewsletter.data.repository.NewsletterRepository
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
    val htmlContent: String? = null
)

data class NewsletterUiState(
    val newsletters: List<NewsletterUiItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class NewsletterViewModel @Inject constructor(
    private val newsletterRepository: NewsletterRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewsletterUiState())
    val uiState: StateFlow<NewsletterUiState> = _uiState.asStateFlow()

    init {
        loadNewsletters()
    }

    private fun loadNewsletters() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val newsletters = newsletterRepository.getNewsletters()
                _uiState.update { it.copy(newsletters = newsletters, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun printNewsletter(id: String) {
        viewModelScope.launch {
            try {
                newsletterRepository.printNewsletter(id)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
}
