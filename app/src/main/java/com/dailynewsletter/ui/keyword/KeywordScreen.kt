package com.dailynewsletter.ui.keyword

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
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
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeywordScreen(viewModel: KeywordViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddSheet by remember { mutableStateOf(false) }
    var newText by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddTagDialog by remember { mutableStateOf(false) }
    var deleteTagTarget by remember { mutableStateOf<String?>(null) }

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
                title = { Text("키워드") }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddSheet = true }) {
                Icon(Icons.Default.Add, contentDescription = "추가")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Tag filter bar
            TagFilterBar(
                availableTags = state.availableTags,
                selectedTag = state.selectedTagFilter,
                onTagClick = { tag ->
                    viewModel.selectTagFilter(if (tag == state.selectedTagFilter) null else tag)
                },
                onTagLongClick = { tag -> deleteTagTarget = tag },
                onAddTagClick = { showAddTagDialog = true }
            )

            if (state.keywords.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "키워드를 추가해보세요",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
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
    }

    if (showAddSheet) {
        val newTags = remember { mutableStateListOf<String>() }
        var tagInput by remember { mutableStateOf("") }

        ModalBottomSheet(
            onDismissRequest = {
                showAddSheet = false
                newText = ""
                newTags.clear()
                tagInput = ""
            },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("새 키워드", style = MaterialTheme.typography.titleLarge)

                OutlinedTextField(
                    value = newText,
                    onValueChange = { newText = it },
                    label = { Text("내용") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                TagInput(
                    tags = newTags,
                    tagInput = tagInput,
                    availableTags = state.availableTags,
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
                                viewModel.addKeyword(newText.trim(), newTags.toList())
                                showAddSheet = false
                                newText = ""
                                newTags.clear()
                                tagInput = ""
                            }
                        }
                    ) { Text("저장") }
                }
            }
        }
    }

    // Add tag dialog
    if (showAddTagDialog) {
        var newTagName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddTagDialog = false; newTagName = "" },
            title = { Text("새 태그") },
            text = {
                OutlinedTextField(
                    value = newTagName,
                    onValueChange = { newTagName = it },
                    label = { Text("태그 이름") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = newTagName.trim()
                        if (trimmed.isNotEmpty()) {
                            viewModel.addNewTag(trimmed)
                        }
                        showAddTagDialog = false
                        newTagName = ""
                    }
                ) { Text("추가") }
            },
            dismissButton = {
                TextButton(onClick = { showAddTagDialog = false; newTagName = "" }) { Text("취소") }
            }
        )
    }

    // Delete tag confirmation dialog
    deleteTagTarget?.let { tag ->
        AlertDialog(
            onDismissRequest = { deleteTagTarget = null },
            title = { Text("태그 삭제") },
            text = { Text("'$tag' 태그를 삭제하시겠습니까?\n이 태그를 가진 모든 키워드에서 제거됩니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeTag(tag)
                        deleteTagTarget = null
                    }
                ) { Text("삭제") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTagTarget = null }) { Text("취소") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun TagFilterBar(
    availableTags: Set<String>,
    selectedTag: String?,
    onTagClick: (String) -> Unit,
    onTagLongClick: (String) -> Unit,
    onAddTagClick: () -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(availableTags.toList()) { tag ->
            FilterChip(
                selected = tag == selectedTag,
                onClick = { onTagClick(tag) },
                label = { Text(tag) },
                modifier = Modifier.combinedClickable(
                    onClick = { onTagClick(tag) },
                    onLongClick = { onTagLongClick(tag) }
                )
            )
        }
        item {
            IconButton(onClick = onAddTagClick) {
                Icon(Icons.Default.Add, contentDescription = "태그 추가")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun TagInput(
    tags: List<String>,
    tagInput: String,
    availableTags: Set<String>,
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
        // Quick-select from existing tags
        if (availableTags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                availableTags.filter { it !in tags }.forEach { tag ->
                    AssistChip(
                        onClick = {
                            if (tag !in tags) {
                                onTagInputChange(tag)
                                onAddTag()
                            }
                        },
                        label = { Text(tag, style = MaterialTheme.typography.labelSmall) }
                    )
                }
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
                // Time display
                val timeText = formatKeywordTime(keyword.createdTime)
                if (timeText != null) {
                    Text(
                        timeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (keyword.tags.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        keyword.tags.take(3).forEach { tag ->
                            AssistChip(
                                onClick = {},
                                label = { Text(tag, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
                if (keyword.isResolved) {
                    Text(
                        "해결됨",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
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

/** Formats an ISO-8601 datetime string (e.g. Notion created_time) to "yyyy-MM-dd HH:mm". */
private fun formatKeywordTime(iso: String?): String? {
    if (iso.isNullOrBlank()) return null
    return runCatching {
        val odt = OffsetDateTime.parse(iso)
        odt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    }.getOrNull()
}
