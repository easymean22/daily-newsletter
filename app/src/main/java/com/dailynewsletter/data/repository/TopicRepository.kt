package com.dailynewsletter.data.repository

import com.dailynewsletter.data.remote.notion.*
import com.dailynewsletter.service.TopicSelectionService
import com.dailynewsletter.ui.topics.TopicUiItem
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TopicRepository @Inject constructor(
    private val notionApi: NotionApi,
    private val settingsRepository: SettingsRepository,
    private val topicSelectionService: TopicSelectionService
) {
    private suspend fun getAuth(): String {
        val key = settingsRepository.getNotionApiKey() ?: throw IllegalStateException("Notion API Key 미설정")
        return "Bearer $key"
    }

    private suspend fun getDbId(): String {
        return settingsRepository.getTopicsDbId() ?: throw IllegalStateException("Topics DB 미초기화")
    }

    suspend fun getTodayTopics(): List<TopicUiItem> {
        val auth = getAuth()
        val dbId = getDbId()
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

        val response = notionApi.queryDatabase(
            auth = auth,
            databaseId = dbId,
            request = NotionQueryRequest(
                filter = NotionFilter(
                    property = "Date",
                    date = NotionDateFilter(equals = today)
                )
            )
        )

        return response.results.map { page ->
            TopicUiItem(
                id = page.id,
                title = page.properties["Title"]?.title?.firstOrNull()?.text?.content ?: "",
                priorityType = page.properties["Priority Type"]?.select?.name ?: "direct",
                sourceKeywords = page.properties["Source Keywords"]?.relation?.map { it.id } ?: emptyList(),
                status = page.properties["Status"]?.select?.name ?: "selected"
            )
        }
    }

    suspend fun getAllPastTopicTitles(): List<String> {
        val auth = getAuth()
        val dbId = getDbId()

        val response = notionApi.queryDatabase(
            auth = auth,
            databaseId = dbId,
            request = NotionQueryRequest(
                sorts = listOf(NotionSort(property = "Date", direction = "descending")),
                pageSize = 100
            )
        )

        return response.results.mapNotNull { page ->
            page.properties["Title"]?.title?.firstOrNull()?.text?.content
        }
    }

    suspend fun regenerateTopics() {
        // Delete today's existing topics
        val todayTopics = getTodayTopics()
        val auth = getAuth()
        todayTopics.forEach { topic ->
            notionApi.deleteBlock(auth = auth, blockId = topic.id)
        }

        // Generate new topics
        topicSelectionService.selectAndSaveTopics()
    }

    suspend fun updateTopicTitle(id: String, newTitle: String) {
        val auth = getAuth()
        notionApi.updatePage(
            auth = auth,
            pageId = id,
            request = UpdatePageRequest(
                properties = mapOf(
                    "Title" to NotionPropertyValue(
                        type = "title",
                        title = listOf(NotionRichText(text = NotionTextContent(newTitle)))
                    ),
                    "Status" to NotionPropertyValue(
                        type = "select",
                        select = NotionSelectValue("modified")
                    )
                )
            )
        )
    }

    suspend fun deleteTopic(id: String) {
        val auth = getAuth()
        notionApi.deleteBlock(auth = auth, blockId = id)
    }

    suspend fun saveTopic(title: String, priorityType: String, sourceKeywordIds: List<String>) {
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
                    "Priority Type" to NotionPropertyValue(
                        type = "select",
                        select = NotionSelectValue(priorityType)
                    ),
                    "Status" to NotionPropertyValue(
                        type = "select",
                        select = NotionSelectValue("selected")
                    ),
                    "Date" to NotionPropertyValue(
                        type = "date",
                        date = NotionDateValue(start = today)
                    ),
                    "Source Keywords" to NotionPropertyValue(
                        type = "relation",
                        relation = sourceKeywordIds.map { NotionRelationValue(it) }
                    )
                )
            )
        )
    }
}
