package com.dailynewsletter.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import java.time.DayOfWeek
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailynewsletter.data.local.entity.SettingsEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAlarmTimePicker by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Show setup result snackbar
    LaunchedEffect(state.setupResult) {
        when (val result = state.setupResult) {
            is SetupResult.Success -> {
                snackbarHostState.showSnackbar("Notion DB 3개가 생성되었습니다")
                viewModel.clearSetupResult()
            }
            is SetupResult.Failed -> {
                snackbarHostState.showSnackbar("DB 생성 실패: ${result.message}")
                viewModel.clearSetupResult()
            }
            else -> Unit
        }
    }

    // Show generic error snackbar
    LaunchedEffect(state.error) {
        val errorMsg = state.error
        if (!errorMsg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(errorMsg)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .verticalScroll(rememberScrollState())
        ) {
            TopAppBar(title = { Text("설정") })

            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

                // Notion 설정
                SectionTitle("Notion 연동")

                SecretTextField(
                    label = "Notion API Key",
                    value = state.notionApiKey,
                    onValueChange = { viewModel.updateSetting(SettingsEntity.KEY_NOTION_API_KEY, it) },
                    enabled = !state.isSetupRunning
                )

                OutlinedTextField(
                    value = state.notionParentPageId,
                    onValueChange = { viewModel.updateSetting(SettingsEntity.KEY_NOTION_PARENT_PAGE_ID, it) },
                    label = { Text("Notion 상위 페이지 ID") },
                    placeholder = { Text("DB가 생성될 페이지 ID") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !state.isSetupRunning
                )

                // Notion DB 자동 생성 버튼
                val alreadySetup = !state.keywordsDbId.isNullOrBlank()
                val canSetup = state.notionApiKey.isNotBlank() &&
                        state.notionParentPageId.isNotBlank() &&
                        !state.isSetupRunning &&
                        !alreadySetup

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(
                        onClick = { viewModel.runSetup() },
                        enabled = canSetup,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (state.isSetupRunning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(
                                if (alreadySetup) "이미 생성됨 (DB 존재)" else "Notion DB 자동 생성"
                            )
                        }
                    }
                    when {
                        alreadySetup -> Text(
                            "Notion DB가 이미 생성되어 있습니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        state.notionApiKey.isBlank() || state.notionParentPageId.isBlank() -> Text(
                            "Notion 토큰과 상위 페이지 ID를 입력해주세요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Gemini 설정
                SectionTitle("Gemini AI 연동")

                SecretTextField(
                    label = "Gemini API Key",
                    value = state.geminiApiKey,
                    onValueChange = { viewModel.updateSetting(SettingsEntity.KEY_GEMINI_API_KEY, it) }
                )

                // 프린터 설정
                SectionTitle("프린터")

                OutlinedTextField(
                    value = state.printerIp,
                    onValueChange = { viewModel.updateSetting(SettingsEntity.KEY_PRINTER_IP, it) },
                    label = { Text("프린터 IP (Wi-Fi)") },
                    placeholder = { Text("192.168.0.100") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )

                OutlinedTextField(
                    value = state.printerEmail,
                    onValueChange = { viewModel.updateSetting(SettingsEntity.KEY_PRINTER_EMAIL, it) },
                    label = { Text("프린터 ePrint 이메일 (선택)") },
                    placeholder = { Text("printer@hpeprint.com") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )

                // 뉴스레터 분량
                SectionTitle("뉴스레터 분량")

                Column {
                    Text(
                        "A4 ${state.newsletterPages}페이지",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = state.newsletterPages.toFloat(),
                        onValueChange = {
                            viewModel.updateSetting(
                                SettingsEntity.KEY_NEWSLETTER_PAGES,
                                it.toInt().toString()
                            )
                        },
                        valueRange = 1f..5f,
                        steps = 3
                    )
                }

                // 인쇄 알람
                SectionTitle("인쇄 알람")

                Card(
                    onClick = { showAlarmTimePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("알람 시간: ", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "%02d:%02d".format(state.alarmHour, state.alarmMinute),
                            style = MaterialTheme.typography.headlineMedium,
                            color = if (state.alarmDays.isEmpty())
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else
                                MaterialTheme.colorScheme.primary
                        )
                    }
                }

                val dayLabels = listOf(
                    DayOfWeek.MONDAY to "월",
                    DayOfWeek.TUESDAY to "화",
                    DayOfWeek.WEDNESDAY to "수",
                    DayOfWeek.THURSDAY to "목",
                    DayOfWeek.FRIDAY to "금",
                    DayOfWeek.SATURDAY to "토",
                    DayOfWeek.SUNDAY to "일"
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    dayLabels.forEach { (day, label) ->
                        FilterChip(
                            selected = day in state.alarmDays,
                            onClick = { viewModel.toggleAlarmDay(day) },
                            label = { Text(label) }
                        )
                    }
                }

                if (state.alarmDays.isEmpty()) {
                    Text(
                        "요일을 선택하면 알람이 활성화됩니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showAlarmTimePicker) {
        val alarmPickerState = rememberTimePickerState(
            initialHour = state.alarmHour,
            initialMinute = state.alarmMinute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showAlarmTimePicker = false },
            title = { Text("알람 시간 설정") },
            text = { TimePicker(state = alarmPickerState) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setAlarmHour(alarmPickerState.hour)
                    viewModel.setAlarmMinute(alarmPickerState.minute)
                    showAlarmTimePicker = false
                }) { Text("확인") }
            },
            dismissButton = {
                TextButton(onClick = { showAlarmTimePicker = false }) { Text("취소") }
            }
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun SecretTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        enabled = enabled
    )
}
