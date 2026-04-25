package com.dailynewsletter.service

import com.dailynewsletter.data.remote.claude.ClaudeApi
import com.dailynewsletter.data.remote.claude.ClaudeMessage
import com.dailynewsletter.data.remote.claude.ClaudeRequest
import com.dailynewsletter.data.repository.NewsletterRepository
import com.dailynewsletter.data.repository.SettingsRepository
import com.dailynewsletter.data.repository.TopicRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NewsletterGenerationService @Inject constructor(
    private val claudeApi: ClaudeApi,
    private val topicRepository: TopicRepository,
    private val newsletterRepository: NewsletterRepository,
    private val settingsRepository: SettingsRepository
) {
    suspend fun generateAndSaveNewsletter() {
        val apiKey = settingsRepository.getClaudeApiKey() ?: throw IllegalStateException("Claude API Key 미설정")
        val pages = settingsRepository.getNewsletterPages()

        val topics = topicRepository.getTodayTopics()
        if (topics.isEmpty()) return

        val topicList = topics.joinToString("\n") { "- ${it.title} (${it.priorityType})" }
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy년 MM월 dd일"))
        val charCount = pages * 1800 // A4 한 페이지 약 1800자

        val prompt = """
당신은 기술 뉴스레터 작성 AI입니다. 아래 주제들에 대해 학습용 뉴스레터를 작성해주세요.

## 오늘의 주제
$topicList

## 작성 규칙
1. **언어**: 기본적으로 한국어로 작성하되, 영어로 표현하는 것이 정확한 기술 용어는 영어로 작성
2. **톤**: 학술적이지만 지나치게 어려운 표현은 사용하지 않음. 정의된 정확한 표현 사용
3. **분량**: 약 ${charCount}자 (A4 ${pages}페이지 분량)
4. **구조**: 각 주제별로 섹션을 나누고, 핵심 개념 → 상세 설명 → 실용적 예시 순서로 작성
5. **출처**: 논문, 공식 문서, 신뢰할 수 있는 블로그 등 공신력 있는 자료를 우선 인용. 각 섹션 끝에 참고 자료 명시

## 출력 형식
HTML로 출력해주세요. 다음 구조를 따르세요:

<h1>Daily Newsletter - $today</h1>

각 주제별:
<h2>주제 제목</h2>
<h3>핵심 개념</h3>
<p>...</p>
<h3>상세 설명</h3>
<p>...</p>
<h3>실용적 예시</h3>
<p>...</p>
<p class="source">참고: [출처 목록]</p>

HTML만 출력하세요. 코드 블록으로 감싸지 마세요.
""".trimIndent()

        val response = claudeApi.createMessage(
            apiKey = apiKey,
            request = ClaudeRequest(
                messages = listOf(ClaudeMessage(role = "user", content = prompt)),
                maxTokens = 8192,
                system = "당신은 기술 학습 뉴스레터를 작성하는 전문가입니다. HTML 형식으로만 응답하세요."
            )
        )

        val htmlContent = response.content.firstOrNull()?.text ?: return
        val cleanHtml = htmlContent
            .removePrefix("```html")
            .removeSuffix("```")
            .trim()

        val fullHtml = """
<!DOCTYPE html>
<html><head>
<meta charset="UTF-8">
<style>
    @page { size: A4; margin: 20mm; }
    body {
        font-family: 'Noto Sans KR', 'Malgun Gothic', sans-serif;
        line-height: 1.8;
        font-size: 11pt;
        color: #1a1a1a;
    }
    h1 {
        font-size: 18pt;
        color: #0d1b2a;
        border-bottom: 3px solid #1b263b;
        padding-bottom: 8px;
        margin-bottom: 20px;
    }
    h2 {
        font-size: 14pt;
        color: #1b263b;
        border-left: 4px solid #415a77;
        padding-left: 12px;
        margin-top: 24px;
    }
    h3 {
        font-size: 12pt;
        color: #415a77;
        margin-top: 16px;
    }
    p { margin: 8px 0; text-align: justify; }
    .source {
        font-size: 9pt;
        color: #778da9;
        border-top: 1px solid #e0e1dd;
        padding-top: 8px;
        margin-top: 16px;
    }
    code {
        background: #f0f0f0;
        padding: 2px 6px;
        border-radius: 3px;
        font-size: 10pt;
    }
</style>
</head><body>
$cleanHtml
</body></html>
""".trimIndent()

        val title = "Daily Newsletter - $today"
        val topicIds = topics.map { it.id }

        newsletterRepository.saveNewsletter(title, fullHtml, topicIds, pages)
    }
}
