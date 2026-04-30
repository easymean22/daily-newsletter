package com.dailynewsletter.data.repository

import com.dailynewsletter.data.remote.notion.*
import com.dailynewsletter.data.tag.TagNormalizer
import com.dailynewsletter.ui.topics.TopicUiItem
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TopicRepository @Inject constructor(
    private val notionApi: NotionApi,
    private val settingsRepository: SettingsRepository
) {
    private suspend fun getAuth(): String {
        val key = settingsRepository.getNotionApiKey() ?: throw IllegalStateException("Notion API Key 미설정")
        return "Bearer $key"
    }

    private suspend fun getDbId(): String {
        return settingsRepository.getTopicsDbId() ?: throw IllegalStateException("Topics DB 미초기화")
    }

    suspend fun getTopics(): List<TopicUiItem> {
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

        return response.results.map { page ->
            TopicUiItem(
                id = page.id,
                title = page.properties["Title"]?.title?.firstOrNull()?.text?.content ?: "",
                priorityType = page.properties["Priority Type"]?.select?.name ?: "direct",
                sourceKeywords = page.properties["Source Keywords"]?.relation?.map { it.id } ?: emptyList(),
                status = page.properties["Status"]?.select?.name ?: "selected",
                tags = page.properties["Tags"]?.multiSelect?.mapNotNull { it.name } ?: emptyList()
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

    suspend fun findPendingTopicsByTag(tag: String): List<TopicUiItem> {
        val auth = getAuth()
        val dbId = getDbId()

        val response = notionApi.queryDatabase(
            auth = auth,
            databaseId = dbId,
            request = NotionQueryRequest(
                filter = NotionFilter(
                    and = listOf(
                        NotionFilter(
                            property = "Tags",
                            multiSelect = NotionMultiSelectFilter(contains = tag)
                        ),
                        NotionFilter(
                            property = "Status",
                            select = NotionSelectFilter(doesNotEqual = "consumed")
                        )
                    )
                ),
                sorts = listOf(NotionSort(property = "Date", direction = "descending")),
                pageSize = 100
            )
        )

        return response.results.map { page ->
            TopicUiItem(
                id = page.id,
                title = page.properties["Title"]?.title?.firstOrNull()?.text?.content ?: "",
                priorityType = page.properties["Priority Type"]?.select?.name ?: "direct",
                sourceKeywords = page.properties["Source Keywords"]?.relation?.map { it.id } ?: emptyList(),
                status = page.properties["Status"]?.select?.name ?: "selected",
                tags = page.properties["Tags"]?.multiSelect?.mapNotNull { it.name } ?: emptyList()
            )
        }
    }

    /**
     * Returns the single most recent topic that has not been consumed yet.
     * Returns null if no pending topic exists.
     */
    suspend fun getLatestPendingTopic(): TopicUiItem? {
        val auth = getAuth()
        val dbId = getDbId()

        val response = notionApi.queryDatabase(
            auth = auth,
            databaseId = dbId,
            request = NotionQueryRequest(
                filter = NotionFilter(
                    property = "Status",
                    select = NotionSelectFilter(doesNotEqual = "consumed")
                ),
                sorts = listOf(NotionSort(property = "Date", direction = "descending")),
                pageSize = 1
            )
        )

        val page = response.results.firstOrNull() ?: return null
        return TopicUiItem(
            id = page.id,
            title = page.properties["Title"]?.title?.firstOrNull()?.text?.content ?: "",
            priorityType = page.properties["Priority Type"]?.select?.name ?: "direct",
            sourceKeywords = page.properties["Source Keywords"]?.relation?.map { it.id } ?: emptyList(),
            status = page.properties["Status"]?.select?.name ?: "selected",
            tags = page.properties["Tags"]?.multiSelect?.mapNotNull { it.name } ?: emptyList()
        )
    }

    suspend fun markTopicsConsumed(ids: List<String>) {
        val auth = getAuth()
        for (id in ids) {
            try {
                notionApi.updatePage(
                    auth = auth,
                    pageId = id,
                    request = UpdatePageRequest(
                        properties = mapOf(
                            "Status" to NotionPropertyValue(
                                type = "select",
                                select = NotionSelectValue("consumed")
                            )
                        )
                    )
                )
            } catch (e: Exception) {
                android.util.Log.w("TopicRepository", "markTopicsConsumed failed for id=$id: ${e.message}")
            }
        }
    }

    suspend fun saveTopic(title: String, priorityType: String, sourceKeywordIds: List<String>, tags: List<String>) {
        val auth = getAuth()
        val dbId = getDbId()
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val normalizedTags = TagNormalizer.ensureFreeTopicTag(tags)

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
                    ),
                    "Tags" to NotionPropertyValue(
                        type = "multi_select",
                        multiSelect = normalizedTags.map { NotionMultiSelectValue(name = it) }
                    )
                )
            )
        )
    }
}
