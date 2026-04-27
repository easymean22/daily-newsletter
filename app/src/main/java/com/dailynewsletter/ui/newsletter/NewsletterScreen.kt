package com.dailynewsletter.ui.newsletter

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsletterScreen(viewModel: NewsletterViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedNewsletter by remember { mutableStateOf<NewsletterUiItem?>(null) }
    var showGenerateSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

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
                snackbarHostState.showSnackbar("생성 완료: ${status.title}")
                viewModel.clearManualGenStatus()
            }
            is ManualGenStatus.Failed -> {
                snackbarHostState.showSnackbar("생성 실패: ${status.message}")
                viewModel.clearManualGenStatus()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(selectedNewsletter?.title ?: "뉴스레터") },
                actions = {
                    selectedNewsletter?.let {
                        IconButton(onClick = { viewModel.printNewsletter(it.id) }) {
                            Icon(Icons.Default.Print, "프린트")
                        }
                    }
                    if (selectedNewsletter == null) {
                        IconButton(onClick = { showGenerateSheet = true }) {
                            Icon(Icons.Default.Add, "뉴스레터 생성")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (selectedNewsletter != null) {
            // Newsletter detail view with WebView
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            webViewClient = WebViewClient()
                            settings.javaScriptEnabled = false
                        }
                    },
                    update = { webView ->
                        selectedNewsletter?.htmlContent?.let { html ->
                            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { selectedNewsletter = null }) {
                        Text("목록으로")
                    }
                    Button(onClick = {
                        selectedNewsletter?.let { viewModel.printNewsletter(it.id) }
                    }) {
                        Icon(Icons.Default.Print, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("프린트")
                    }
                }
            }
        } else when {
            state.isLoading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.newsletters.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
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
                    modifier = Modifier.padding(padding),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    items(state.newsletters, key = { it.id }) { newsletter ->
                        NewsletterCard(
                            newsletter = newsletter,
                            onClick = { selectedNewsletter = newsletter }
                        )
                    }
                }
            }
        }

        // Manual generation bottom sheet
        if (showGenerateSheet) {
            GenerateNewsletterSheet(
                isGenerating = state.manualGenStatus is ManualGenStatus.Running,
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
                        Text("생성 중...")
                    } else {
                        Text("생성")
                    }
                }
            }
        }
    }
}
