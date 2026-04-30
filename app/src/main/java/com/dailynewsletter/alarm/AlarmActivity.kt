package com.dailynewsletter.alarm

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.dailynewsletter.data.repository.NewsletterRepository
import com.dailynewsletter.service.NewsletterGenerationService
import com.dailynewsletter.service.PrintService
import com.dailynewsletter.ui.newsletter.NewsletterUiItem
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AlarmUiState {
    object Loading : AlarmUiState()
    data class Ready(val newsletter: NewsletterUiItem) : AlarmUiState()
    data class Generating(val message: String) : AlarmUiState()
    data class GenerationFailed(val message: String) : AlarmUiState()
}

@AndroidEntryPoint
class AlarmActivity : ComponentActivity() {

    @Inject
    lateinit var newsletterRepository: NewsletterRepository

    @Inject
    lateinit var newsletterGenerationService: NewsletterGenerationService

    @Inject
    lateinit var printService: PrintService

    private var uiState: AlarmUiState by mutableStateOf(AlarmUiState.Loading)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show on lock screen and turn screen on
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AlarmScreen(
                        state = uiState,
                        onPrintClick = { onPrintClick() }
                    )
                }
            }
        }

        lifecycleScope.launch {
            loadOrGenerateNewsletter()
        }
    }

    private suspend fun loadOrGenerateNewsletter() {
        try {
            val unprinted = newsletterRepository.getLatestUnprintedNewsletter()
            if (unprinted != null) {
                uiState = AlarmUiState.Ready(unprinted)
            } else {
                uiState = AlarmUiState.Generating("뉴스레터 준비 중...")
                try {
                    // Default page count 2; no specific tag — pick latest pending topic
                    val generated = newsletterGenerationService.generateLatest(pageCount = 2)
                    val newsletter = newsletterRepository.getNewsletter(generated.id)
                    uiState = AlarmUiState.Ready(newsletter)
                } catch (e: Exception) {
                    uiState = AlarmUiState.GenerationFailed("생성 실패: ${e.message ?: "알 수 없는 오류"}")
                }
            }
        } catch (e: Exception) {
            uiState = AlarmUiState.GenerationFailed("오류: ${e.message ?: "알 수 없는 오류"}")
        }
    }

    private fun onPrintClick() {
        AlarmService.stop(this)
        val item = (uiState as? AlarmUiState.Ready)?.newsletter ?: return
        val html = item.htmlContent
        if (html != null) {
            printService.startSystemPrint(this, html, item.title)
        }
        lifecycleScope.launch {
            try {
                newsletterRepository.markNewsletterPrinted(item.id)
            } catch (_: Exception) {
            }
            finish()
        }
    }
}

@Composable
private fun AlarmScreen(
    state: AlarmUiState,
    onPrintClick: () -> Unit
) {
    // Disable back press — user must tap print button
    BackHandler(enabled = true) { /* intentionally empty */ }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "프린트 하세요",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                when (state) {
                    is AlarmUiState.Loading -> {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "로딩 중...")
                    }
                    is AlarmUiState.Ready -> {
                        Text(
                            text = state.newsletter.title,
                            fontSize = 16.sp
                        )
                    }
                    is AlarmUiState.Generating -> {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = state.message)
                    }
                    is AlarmUiState.GenerationFailed -> {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onPrintClick,
                    enabled = state is AlarmUiState.Ready,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "인쇄", fontSize = 18.sp)
                }
            }
        }
    }
}
