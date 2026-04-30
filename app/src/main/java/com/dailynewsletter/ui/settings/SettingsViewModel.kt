package com.dailynewsletter.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailynewsletter.alarm.AlarmScheduler
import com.dailynewsletter.data.local.entity.SettingsEntity
import com.dailynewsletter.data.repository.SettingsRepository
import com.dailynewsletter.service.NotionSetupService
import com.dailynewsletter.ui.common.exceptionHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import javax.inject.Inject

sealed class SetupResult {
    object Idle : SetupResult()
    object Running : SetupResult()
    object Success : SetupResult()
    data class Failed(val message: String) : SetupResult()
}

data class SettingsUiState(
    val notionApiKey: String = "",
    val notionParentPageId: String = "",
    val geminiApiKey: String = "",
    val printerIp: String = "",
    val printerEmail: String = "",
    val printTimeHour: Int = 7,
    val printTimeMinute: Int = 0,
    val newsletterPages: Int = 2,
    val isSetupComplete: Boolean = false,
    val keywordsDbId: String? = null,
    val isSetupRunning: Boolean = false,
    val setupResult: SetupResult = SetupResult.Idle,
    val error: String? = null,
    val alarmHour: Int = 7,
    val alarmMinute: Int = 0,
    val alarmDays: Set<DayOfWeek> = emptySet()
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val notionSetupService: NotionSetupService,
    private val alarmScheduler: AlarmScheduler
) : ViewModel() {

    private val _setupState = MutableStateFlow<SetupStateHolder>(SetupStateHolder())

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.observeAll(),
        _setupState
    ) { settings, setupHolder ->
        val alarmDaysRaw = settings[SettingsEntity.KEY_ALARM_DAYS] ?: ""
        val parsedAlarmDays: Set<DayOfWeek> = if (alarmDaysRaw.isBlank()) emptySet() else {
            alarmDaysRaw.split(",")
                .mapNotNull { token -> runCatching { DayOfWeek.valueOf(token.trim()) }.getOrNull() }
                .toSet()
        }
        SettingsUiState(
            notionApiKey = settings[SettingsEntity.KEY_NOTION_API_KEY] ?: "",
            notionParentPageId = settings[SettingsEntity.KEY_NOTION_PARENT_PAGE_ID] ?: "",
            geminiApiKey = settings[SettingsEntity.KEY_GEMINI_API_KEY] ?: "",
            printerIp = settings[SettingsEntity.KEY_PRINTER_IP] ?: "",
            printerEmail = settings[SettingsEntity.KEY_PRINTER_EMAIL] ?: "",
            printTimeHour = settings[SettingsEntity.KEY_PRINT_TIME_HOUR]?.toIntOrNull() ?: 7,
            printTimeMinute = settings[SettingsEntity.KEY_PRINT_TIME_MINUTE]?.toIntOrNull() ?: 0,
            newsletterPages = settings[SettingsEntity.KEY_NEWSLETTER_PAGES]?.toIntOrNull() ?: 2,
            isSetupComplete = !settings[SettingsEntity.KEY_NOTION_API_KEY].isNullOrBlank()
                    && !settings[SettingsEntity.KEY_GEMINI_API_KEY].isNullOrBlank(),
            keywordsDbId = settings[SettingsEntity.KEY_KEYWORDS_DB_ID],
            isSetupRunning = setupHolder.isRunning,
            setupResult = setupHolder.result,
            error = setupHolder.error,
            alarmHour = settings[SettingsEntity.KEY_PRINT_TIME_HOUR]?.toIntOrNull() ?: 7,
            alarmMinute = settings[SettingsEntity.KEY_PRINT_TIME_MINUTE]?.toIntOrNull() ?: 0,
            alarmDays = parsedAlarmDays
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    private val exceptionHandler = exceptionHandler { message ->
        _setupState.update { it.copy(isRunning = false, result = SetupResult.Failed(message), error = message) }
    }

    fun updateSetting(key: String, value: String) {
        viewModelScope.launch(exceptionHandler) {
            settingsRepository.set(key, value)
        }
    }

    fun runSetup() {
        viewModelScope.launch(exceptionHandler) {
            _setupState.update { it.copy(isRunning = true, result = SetupResult.Running, error = null) }
            try {
                notionSetupService.setupDatabases()
                _setupState.update { it.copy(isRunning = false, result = SetupResult.Success) }
            } catch (e: Exception) {
                val msg = e.message ?: "DB 생성 실패"
                _setupState.update { it.copy(isRunning = false, result = SetupResult.Failed(msg), error = msg) }
            }
        }
    }

    fun setAlarmHour(h: Int) {
        viewModelScope.launch(exceptionHandler) {
            settingsRepository.setPrintTimeHour(h)
            alarmScheduler.reschedule()
        }
    }

    fun setAlarmMinute(m: Int) {
        viewModelScope.launch(exceptionHandler) {
            settingsRepository.setPrintTimeMinute(m)
            alarmScheduler.reschedule()
        }
    }

    fun toggleAlarmDay(day: DayOfWeek) {
        viewModelScope.launch(exceptionHandler) {
            val current = uiState.value.alarmDays
            val updated = if (day in current) current - day else current + day
            settingsRepository.setAlarmDays(updated)
            alarmScheduler.reschedule()
        }
    }

    fun clearSetupResult() {
        _setupState.update { it.copy(result = SetupResult.Idle) }
    }

    fun clearError() {
        _setupState.update { it.copy(error = null) }
    }
}

/** Internal holder for mutable setup progress — kept separate from the settings DB flow. */
private data class SetupStateHolder(
    val isRunning: Boolean = false,
    val result: SetupResult = SetupResult.Idle,
    val error: String? = null
)
