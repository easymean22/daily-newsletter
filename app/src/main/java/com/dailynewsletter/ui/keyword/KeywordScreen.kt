package com.dailynewsletter.ui.keyword

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeywordScreen(viewModel: KeywordViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddSheet by remember { mutableStateOf(false) }
    var newText by remember { mutableStateOf("") }
    var newType by remember { mutableStateOf("keyword") }
    val snackbarHostState = remember { SnackbarHostState() }

    // Observe error state and show snackbar
    LaunchedEffect(state.error) {
        val errorMsg = state.error
        if (!errorMsg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(errorMsg)
            viewModel.clearError()
        }
    }

    // Observe autoGenStatus and show snackbar
    LaunchedEffect(state.autoGenStatus) {
        when (val status = state.autoGenStatus) {
            is AutoGenStatus.Running -> snackbarHostState.showSnackbar("주제 생성 중...")
            is AutoGenStatus.Success -> {
                snackbarHostState.showSnackbar("주제 ${status.count}개가 생성되었습니다")
                viewModel.clearAutoGenStatus()
            }
            is AutoGenStatus.Failed -> {
                snackbarHostState.showSnackbar(status.message)
                viewModel.clearAutoGenStatus()
            }
            is AutoGenStatus.Idle -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("키워드 / 메모") },
                actions = {
                    FilterChips(
                        selectedFilter = state.filter,
                        onFilterChange = viewModel::setFilter
                    )
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddSheet = true }) {
                Icon(Icons.Default.Add, contentDescription = "추가")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (state.keywords.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "키워드나 메모를 추가해보세요",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
            ) {
                items(state.keywords, key = { it.id }) { keyword ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart) {
                                viewModel.deleteKeyword(keyword.id)
                                true
                            } else false
                        }
                    )

                    SwipeToDismissBox(
                        state = dismissState,
                        backgroundContent = {
                            val color by animateColorAsState(
                                if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart)
                                    MaterialTheme.colorScheme.errorContainer
                                else Color.Transparent,
                                label = "bg"
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(color)
                                    .padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Icon(Icons.Default.Delete, "삭제", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    ) {
                        KeywordCard(
                            keyword = keyword,
                            onToggleResolved = { viewModel.toggleResolved(keyword.id) }
                        )
                    }
                }
            }
        }
    }

    if (showAddSheet) {
        val newTags = remember { mutableStateListOf<String>() }
        var tagInput by remember { mutableStateOf("") }

        ModalBottomSheet(
            onDismissRequest = {
                showAddSheet = false
                newText = ""
                newType = "keyword"
                newTags.clear()
                tagInput = ""
            },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("새 키워드 / 메모", style = MaterialTheme.typography.titleLarge)

                OutlinedTextField(
                    value = newText,
                    onValueChange = { newText = it },
                    label = { Text("내용") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                TypeSelector(selected = newType, onSelect = { newType = it })

                TagInput(
                    tags = newTags,
                    tagInput = tagInput,
                    onTagInputChange = { tagInput = it },
                    onAddTag = {
                        val trimmed = tagInput.trim()
                        if (trimmed.isNotEmpty() && trimmed !in newTags) {
                            newTags.add(trimmed)
                        }
                        tagInput = ""
                    },
                    onRemoveTag = { tag -> newTags.remove(tag) }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        showAddSheet = false
                        newText = ""
                        newTags.clear()
                        tagInput = ""
                    }) { Text("취소") }
                    TextButton(
                        onClick = {
                            if (newText.isNotBlank()) {
                                viewModel.addKeyword(newText.trim(), newType, newTags.toList())
                                showAddSheet = false
                                newText = ""
                                newType = "keyword"
                                newTags.clear()
                                tagInput = ""
                            }
                        }
                    ) { Text("저장") }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun TagInput(
    tags: List<String>,
    tagInput: String,
    onTagInputChange: (String) -> Unit,
    onAddTag: () -> Unit,
    onRemoveTag: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = tagInput,
                onValueChange = onTagInputChange,
                label = { Text("태그") },
                placeholder = { Text("태그 입력 후 추가") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            IconButton(onClick = onAddTag, enabled = tagInput.isNotBlank()) {
                Icon(Icons.Default.Add, contentDescription = "태그 추가")
            }
        }
        if (tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                tags.forEach { tag ->
                    InputChip(
                        selected = false,
                        onClick = {},
                        label = { Text(tag) },
                        trailingIcon = {
                            IconButton(onClick = { onRemoveTag(tag) }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "태그 삭제",
                                    modifier = Modifier.padding(2.dp)
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun KeywordCard(keyword: KeywordUiItem, onToggleResolved: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (keyword.isResolved) CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) else CardDefaults.cardColors()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    keyword.title,
                    style = MaterialTheme.typography.bodyLarge
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                if (keyword.type == "keyword") "키워드" else "메모",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                    if (keyword.isResolved) {
                        Text(
                            "해결됨",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            IconButton(onClick = onToggleResolved) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "해결 토글",
                    tint = if (keyword.isResolved) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeSelector(selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = if (selected == "keyword") "키워드" else "메모",
            onValueChange = {},
            readOnly = true,
            label = { Text("타입") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("키워드") }, onClick = { onSelect("keyword"); expanded = false })
            DropdownMenuItem(text = { Text("메모") }, onClick = { onSelect("memo"); expanded = false })
        }
    }
}

@Composable
private fun FilterChips(selectedFilter: String, onFilterChange: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf("all" to "전체", "pending" to "대기", "resolved" to "해결").forEach { (key, label) ->
            TextButton(onClick = { onFilterChange(key) }) {
                Text(
                    label,
                    color = if (selectedFilter == key) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
