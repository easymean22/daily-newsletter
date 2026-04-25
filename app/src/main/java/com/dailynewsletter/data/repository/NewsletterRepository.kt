package com.dailynewsletter.data.repository

import com.dailynewsletter.data.remote.notion.*
import com.dailynewsletter.service.PrintService
import com.dailynewsletter.ui.newsletter.NewsletterUiItem
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NewsletterRepository @Inject constructor(
    private val notionApi: NotionApi,
    private val settingsRepository: SettingsRepository,
    private val printService: PrintService
) {
    private suspend fun getAuth(): String {
        val key = settingsRepository.getNotionApiKey() ?: throw IllegalStateException("Notion API Key 미설정")
        return "Bearer $key"
    }

    private suspend fun getDbId(): String {
        return settingsRepository.getNewslettersDbId() ?: throw IllegalStateException("Newsletters DB 미초기화")
    }

    suspend fun getNewsletters(): List<NewsletterUiItem> {
        val auth = getAuth()
        val dbId = getDbId()

        val response = notionApi.queryDatabase(
            auth = auth,
            databaseId = dbId,
            request = NotionQueryRequest(
                sorts = listOf(NotionSort(property = "Date", direction = "descending")),
                pageSize = 20
            )
        )

        return response.results.map { page ->
            val title = page.properties["Title"]?.title?.firstOrNull()?.text?.content ?: ""
            val date = page.properties["Date"]?.date?.start ?: ""
            val status = page.properties["Status"]?.select?.name ?: "generated"
            val pageCount = page.properties["Page Count"]?.number?.toInt() ?: 2

            // Fetch page content for HTML
            val contentBlocks = try {
                notionApi.getBlockChildren(auth = auth, blockId = page.id)
            } catch (e: Exception) { null }

            val htmlContent = contentBlocks?.let { blocksToHtml(it) }

            NewsletterUiItem(
                id = page.id,
                title = title,
                date = date,
                status = status,
                pageCount = pageCount,
                htmlContent = htmlContent
            )
        }
    }

    private fun blocksToHtml(response: NotionQueryResponse): String {
        val body = response.results.joinToString("\n") { page ->
            // Simple conversion — page acts as block here
            page.properties.entries.firstOrNull()?.value?.richText?.joinToString("") {
                it.text.content
            } ?: ""
        }
        return """
            <!DOCTYPE html>
            <html><head>
            <meta charset="UTF-8">
            <style>
                body { font-family: 'Noto Sans KR', sans-serif; padding: 20px; line-height: 1.8; font-size: 14px; }
                h2 { color: #1a1a2e; border-bottom: 2px solid #16213e; padding-bottom: 8px; }
                h3 { color: #16213e; }
                .source { color: #666; font-size: 12px; margin-top: 4px; }
            </style>
            </head><body>$body</body></html>
        """.trimIndent()
    }

    suspend fun saveNewsletter(title: String, htmlContent: String, topicIds: List<String>, pageCount: Int) {
        val auth = getAuth()
        val dbId = getDbId()
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

        notionApi.createPage(
            auth = auth,
            request = CreatePageRequest(
                parent = NotionParent(type = "database_id", databaseId = dbId),
                properties = mapOf(
                    "Title" to NotionPropertyValue(
                        type = "title",
                        title = listOf(NotionRichText(text = NotionTextContent(title)))
                    ),
                    "Date" to NotionPropertyValue(
                        type = "date",
                        date = NotionDateValue(start = today)
                    ),
                    "Status" to NotionPropertyValue(
                        type = "select",
                        select = NotionSelectValue("generated")
                    ),
                    "Page Count" to NotionPropertyValue(
                        type = "number",
                        number = pageCount.toDouble()
                    ),
                    "Topics" to NotionPropertyValue(
                        type = "relation",
                        relation = topicIds.map { NotionRelationValue(it) }
                    )
                ),
                children = listOf(
                    NotionBlock(
                        type = "paragraph",
                        paragraph = NotionParagraphBlock(
                            richText = listOf(NotionRichText(text = NotionTextContent(htmlContent)))
                        )
                    )
                )
            )
        )
    }

    suspend fun updateNewsletterStatus(id: String, status: String) {
        val auth = getAuth()
        notionApi.updatePage(
            auth = auth,
            pageId = id,
            request = UpdatePageRequest(
                properties = mapOf(
                    "Status" to NotionPropertyValue(
                        type = "select",
                        select = NotionSelectValue(status)
                    )
                )
            )
        )
    }

    suspend fun printNewsletter(id: String) {
        val newsletters = getNewsletters()
        val newsletter = newsletters.find { it.id == id } ?: throw IllegalStateException("뉴스레터를 찾을 수 없습니다")
        val html = newsletter.htmlContent ?: throw IllegalStateException("뉴스레터 내용이 없습니다")

        printService.printHtml(html, newsletter.title)
        updateNewsletterStatus(id, "printed")
    }
}
