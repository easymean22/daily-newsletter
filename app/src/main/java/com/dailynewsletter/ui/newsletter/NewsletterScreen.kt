package com.dailynewsletter.ui.newsletter

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
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
                }
            )
        }
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
                    Text("생성된 뉴스레터가 없습니다", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.padding(padding),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    items(state.newsletters, key = { it.id }) { newsletter ->
                        Card(
                            onClick = { selectedNewsletter = newsletter },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(newsletter.title, style = MaterialTheme.typography.titleMedium)
                                Text(newsletter.date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                                    val statusText = when(newsletter.status) {
                                        "generated" -> "생성됨"
                                        "printed" -> "프린트 완료"
                                        else -> "실패"
                                    }
                                    val statusColor = when(newsletter.status) {
                                        "printed" -> MaterialTheme.colorScheme.primary
                                        "failed" -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                    AssistChip(onClick = {}, label = { Text(statusText) })
                                    Text("${newsletter.pageCount}페이지", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
