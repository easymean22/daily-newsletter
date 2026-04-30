package com.dailynewsletter.ui.newsletter

import android.app.Activity
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsletterScreen(viewModel: NewsletterViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? Activity
    var selectedNewsletter by remember { mutableStateOf<NewsletterUiItem?>(null) }
    var showGenerateSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Return to grid when back is pressed from detail view
    BackHandler(enabled = selectedNewsletter != null) {
        selectedNewsletter = null
    }

    // Observe error state and show snackbar
    LaunchedEffect(state.error) {
        val errorMsg = state.error
        if (!errorMsg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(errorMsg)
            viewModel.clearError()
        }
    }

    // Handle manual generation status changes
    LaunchedEffect(state.manualGenStatus) {
        when (val status = state.manualGenStatus) {
            is ManualGenStatus.Success -> {
                snackbarHostState.showSnackbar("뉴스레터가 추가되었습니다")
                viewModel.clearManualGenStatus()
            }
            else -> {}
        }
    }

    // AlertDialog for final transient failure
    if (state.manualGenStatus is ManualGenStatus.Failed) {
        AlertDialog(
            onDismissRequest = { viewModel.clearManualGenStatus() },
            title = { Text("생성을 마치지 못했어요") },
            text = { Text("잠시 후 다시 시도해 주세요") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearManualGenStatus() }) {
                    Text("확인")
                }
            }
        )
    }

    // Always use the fresh item from state to avoid stale htmlContent
    val currentItem = selectedNewsletter?.let { sel ->
        state.newsletters.find { it.id == sel.id } ?: sel
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentItem?.title ?: "뉴스레터") },
                navigationIcon = {
                    if (currentItem != null) {
                        IconButton(onClick = { selectedNewsletter = null }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "뒤로가기")
                        }
                    }
                },
                actions = {
                    currentItem?.let { item ->
                        IconButton(
                            onClick = {
                                activity?.let { viewModel.printNewsletter(it, item) }
                            },
                            enabled = item.htmlContent != null
                        ) {
                            Icon(Icons.Default.Print, "프린트")
                        }
                    }
                    if (currentItem == null) {
                        IconButton(onClick = { showGenerateSheet = true }) {
                            Icon(Icons.Default.Add, "뉴스레터 생성")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        // Persistent generation status banner (visible even when BottomSheet is closed)
        val genStatus = state.manualGenStatus
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (genStatus is ManualGenStatus.Running || genStatus is ManualGenStatus.Retrying) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    tonalElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = if (genStatus is ManualGenStatus.Retrying) genStatus.message
                                   else "뉴스레터 생성 중...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // Main content below banner
            if (currentItem != null) {
                // Newsletter detail view
                if (currentItem.htmlContent == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("본문 로드 중...")
                        }
                    }
                } else {
                    AndroidView(
                        factory = { context ->
                            WebView(context).apply {
                                webViewClient = WebViewClient()
                                settings.javaScriptEnabled = false
                            }
                        },
                        update = { webView ->
                            webView.loadDataWithBaseURL(null, currentItem.htmlContent, "text/html", "UTF-8", null)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            } else when {
                state.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.newsletters.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "아직 뉴스레터가 없습니다. 상단 +로 직접 생성할 수 있어요.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(12.dp)
                    ) {
                        items(state.newsletters, key = { it.id }) { newsletter ->
                            NewsletterCard(
                                newsletter = newsletter,
                                onClick = {
                                    viewModel.loadNewsletterContent(newsletter.id)
                                    selectedNewsletter = newsletter
                                }
                            )
                        }
                    }
                }
            }
        }

        // Manual generation bottom sheet
        if (showGenerateSheet) {
            val retryMessage = (state.manualGenStatus as? ManualGenStatus.Retrying)?.message
            GenerateNewsletterSheet(
                isGenerating = state.manualGenStatus is ManualGenStatus.Running ||
                    state.manualGenStatus is ManualGenStatus.Retrying,
                retryMessage = retryMessage,
                onDismiss = { showGenerateSheet = false },
                onGenerate = { tag, pageCount ->
                    showGenerateSheet = false
                    viewModel.generateNewsletterManually(tag, pageCount)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewsletterCard(
    newsletter: NewsletterUiItem,
    onClick: () -> Unit
) {
    val (statusLabel, statusColor) = when (newsletter.status) {
        "printed" -> "🖨 인쇄됨" to MaterialTheme.colorScheme.primary
        "generated" -> "✓ 생성됨" to MaterialTheme.colorScheme.tertiary
        else -> newsletter.status to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelSmall,
                color = statusColor,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
            Text(
                text = newsletter.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenerateNewsletterSheet(
    isGenerating: Boolean,
    retryMessage: String? = null,
    onDismiss: () -> Unit,
    onGenerate: (tag: String, pageCount: Int) -> Unit
) {
    var tag by remember { mutableStateOf("모든주제") }
    var pageCount by remember { mutableStateOf(2f) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("뉴스레터 생성", style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = tag,
                onValueChange = { tag = it },
                label = { Text("태그") },
                placeholder = { Text("예: 모든주제") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Column {
                Text(
                    "장수: ${pageCount.toInt()}페이지",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = pageCount,
                    onValueChange = { pageCount = it },
                    valueRange = 1f..5f,
                    steps = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (retryMessage != null) {
                Text(
                    text = retryMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                TextButton(onClick = onDismiss, enabled = !isGenerating) {
                    Text("취소")
                }
                Button(
                    onClick = { onGenerate(tag.trim().ifBlank { "모든주제" }, pageCount.toInt()) },
                    enabled = !isGenerating
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (retryMessage != null) "잠시만요..." else "생성 중...")
                    } else {
                        Text("생성")
                    }
                }
            }
        }
    }
}
