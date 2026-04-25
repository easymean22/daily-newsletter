package com.dailynewsletter.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
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
    var showTimePicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        TopAppBar(title = { Text("설정") })

        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

            // Notion 설정
            SectionTitle("Notion 연동")

            SecretTextField(
                label = "Notion API Key",
                value = state.notionApiKey,
                onValueChange = { viewModel.updateSetting(SettingsEntity.KEY_NOTION_API_KEY, it) }
            )

            OutlinedTextField(
                value = state.notionParentPageId,
                onValueChange = { viewModel.updateSetting(SettingsEntity.KEY_NOTION_PARENT_PAGE_ID, it) },
                label = { Text("Notion 상위 페이지 ID") },
                placeholder = { Text("DB가 생성될 페이지 ID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Claude 설정
            SectionTitle("Claude AI 연동")

            SecretTextField(
                label = "Claude API Key",
                value = state.claudeApiKey,
                onValueChange = { viewModel.updateSetting(SettingsEntity.KEY_CLAUDE_API_KEY, it) }
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

            // 프린트 시간
            SectionTitle("프린트 시간")

            Card(
                onClick = { showTimePicker = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("매일", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        " %02d:%02d".format(state.printTimeHour, state.printTimeMinute),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(" 에 프린트", style = MaterialTheme.typography.bodyLarge)
                }
            }

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
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = state.printTimeHour,
            initialMinute = state.printTimeMinute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("프린트 시간 설정") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateSetting(SettingsEntity.KEY_PRINT_TIME_HOUR, timePickerState.hour.toString())
                    viewModel.updateSetting(SettingsEntity.KEY_PRINT_TIME_MINUTE, timePickerState.minute.toString())
                    showTimePicker = false
                }) { Text("확인") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("취소") }
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
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = PasswordVisualTransformation()
    )
}
