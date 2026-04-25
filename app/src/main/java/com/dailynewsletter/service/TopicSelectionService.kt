package com.dailynewsletter.service

import com.dailynewsletter.data.remote.claude.ClaudeApi
import com.dailynewsletter.data.remote.claude.ClaudeMessage
import com.dailynewsletter.data.remote.claude.ClaudeRequest
import com.dailynewsletter.data.repository.KeywordRepository
import com.dailynewsletter.data.repository.SettingsRepository
import com.dailynewsletter.data.repository.TopicRepository
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import javax.inject.Inject
import javax.inject.Singleton

data class SelectedTopic(
    val title: String,
    val priorityType: String,
    val sourceKeywordIds: List<String>,
    val reason: String
)

@Singleton
class TopicSelectionService @Inject constructor(
    private val claudeApi: ClaudeApi,
    private val keywordRepository: KeywordRepository,
    private val settingsRepository: SettingsRepository
) {
    // TopicRepository is injected at call time to avoid circular dependency
    private var _topicRepository: TopicRepository? = null

    fun setTopicRepository(repo: TopicRepository) {
        _topicRepository = repo
    }

    suspend fun selectAndSaveTopics() {
        val topicRepository = _topicRepository ?: throw IllegalStateException("TopicRepository not set")
        val apiKey = settingsRepository.getClaudeApiKey() ?: throw IllegalStateException("Claude API Key 미설정")
        val pages = settingsRepository.getNewsletterPages()

        val pendingKeywords = keywordRepository.getPendingKeywords()
        if (pendingKeywords.isEmpty()) return

        val pastTopics = topicRepository.getAllPastTopicTitles()

        val keywordList = pendingKeywords.joinToString("\n") { "- [${it.id}] ${it.title} (${it.type})" }
        val pastTopicList = if (pastTopics.isNotEmpty()) {
            pastTopics.joinToString("\n") { "- $it" }
        } else "없음"

        val prompt = """
당신은 학습 주제 선정 AI입니다. 사용자가 저장한 키워드/메모를 기반으로 오늘 읽을 주제를 선정해주세요.

## 저장된 키워드/메모
$keywordList

## 이미 다룬 주제 (선정하지 마세요)
$pastTopicList

## 선정 우선순위
1. 키워드로 직접 적어 놓은 내용 (priorityType: "direct")
2. 키워드를 종합했을 때 알면 좋을 선행 지식 (priorityType: "prerequisite")
3. 키워드와 기존 읽은 내용을 종합했을 때 알면 좋을 주변 지식 (priorityType: "peripheral")

## 규칙
- 이미 다룬 주제는 선정하지 않습니다
- A4 ${pages}페이지 분량에 적합한 수의 주제를 선정하세요 (보통 2-3개)
- 각 주제는 구체적이고 학습 가능한 단위여야 합니다

## 응답 형식 (JSON 배열만 출력)
```json
[
  {
    "title": "주제 제목",
    "priorityType": "direct|prerequisite|peripheral",
    "sourceKeywordIds": ["키워드id1"],
    "reason": "선정 이유"
  }
]
```
""".trimIndent()

        val response = claudeApi.createMessage(
            apiKey = apiKey,
            request = ClaudeRequest(
                messages = listOf(ClaudeMessage(role = "user", content = prompt)),
                maxTokens = 2048
            )
        )

        val text = response.content.firstOrNull()?.text ?: return
        val jsonStr = text.substringAfter("```json").substringBefore("```").trim()
            .ifEmpty { text.trim() }

        val topics: List<SelectedTopic> = try {
            Gson().fromJson(jsonStr, object : TypeToken<List<SelectedTopic>>() {}.type)
        } catch (e: Exception) {
            return
        }

        topics.forEach { topic ->
            topicRepository.saveTopic(
                title = topic.title,
                priorityType = topic.priorityType,
                sourceKeywordIds = topic.sourceKeywordIds
            )
        }
    }
}
