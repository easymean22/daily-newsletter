package com.dailynewsletter.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailynewsletter.data.local.entity.SettingsEntity
import com.dailynewsletter.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val notionApiKey: String = "",
    val notionParentPageId: String = "",
    val claudeApiKey: String = "",
    val printerIp: String = "",
    val printerEmail: String = "",
    val printTimeHour: Int = 7,
    val printTimeMinute: Int = 0,
    val newsletterPages: Int = 2,
    val isSetupComplete: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = settingsRepository.observeAll()
        .map { settings ->
            SettingsUiState(
                notionApiKey = settings[SettingsEntity.KEY_NOTION_API_KEY] ?: "",
                notionParentPageId = settings[SettingsEntity.KEY_NOTION_PARENT_PAGE_ID] ?: "",
                claudeApiKey = settings[SettingsEntity.KEY_CLAUDE_API_KEY] ?: "",
                printerIp = settings[SettingsEntity.KEY_PRINTER_IP] ?: "",
                printerEmail = settings[SettingsEntity.KEY_PRINTER_EMAIL] ?: "",
                printTimeHour = settings[SettingsEntity.KEY_PRINT_TIME_HOUR]?.toIntOrNull() ?: 7,
                printTimeMinute = settings[SettingsEntity.KEY_PRINT_TIME_MINUTE]?.toIntOrNull() ?: 0,
                newsletterPages = settings[SettingsEntity.KEY_NEWSLETTER_PAGES]?.toIntOrNull() ?: 2,
                isSetupComplete = !settings[SettingsEntity.KEY_NOTION_API_KEY].isNullOrBlank()
                        && !settings[SettingsEntity.KEY_CLAUDE_API_KEY].isNullOrBlank()
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun updateSetting(key: String, value: String) {
        viewModelScope.launch {
            settingsRepository.set(key, value)
        }
    }
}
