package com.dailynewsletter.data.repository

import com.dailynewsletter.data.local.entity.SettingsEntity
import com.dailynewsletter.data.remote.notion.*
import com.dailynewsletter.ui.keyword.KeywordUiItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeywordRepository @Inject constructor(
    private val notionApi: NotionApi,
    private val settingsRepository: SettingsRepository
) {
    private val _keywords = MutableStateFlow<List<KeywordUiItem>>(emptyList())

    fun observeKeywords(): Flow<List<KeywordUiItem>> = _keywords.asStateFlow()

    private suspend fun getAuth(): String {
        val key = settingsRepository.getNotionApiKey() ?: throw IllegalStateException("Notion API Key가 설정되지 않았습니다")
        return "Bearer $key"
    }

    private suspend fun getDbId(): String {
        return settingsRepository.getKeywordsDbId() ?: throw IllegalStateException("Keywords DB가 초기화되지 않았습니다")
    }

    suspend fun refreshKeywords() {
        val auth = getAuth()
        val dbId = getDbId()

        val response = notionApi.queryDatabase(
            auth = auth,
            databaseId = dbId,
            request = NotionQueryRequest(
                sorts = listOf(NotionSort(timestamp = "created_time", direction = "descending"))
            )
        )

        _keywords.value = response.results.map { page ->
            val title = page.properties["Title"]?.title?.firstOrNull()?.text?.content ?: ""
            val type = page.properties["Type"]?.select?.name ?: "keyword"
            val status = page.properties["Status"]?.select?.name ?: "pending"
            val resolvedDate = page.properties["Resolved Date"]?.date?.start

            KeywordUiItem(
                id = page.id,
                title = title,
                type = type,
                isResolved = status == "resolved",
                resolvedDate = resolvedDate
            )
        }
    }

    suspend fun addKeyword(text: String, type: String) {
        val auth = getAuth()
        val dbId = getDbId()

        notionApi.createPage(
            auth = auth,
            request = CreatePageRequest(
                parent = NotionParent(type = "database_id", databaseId = dbId),
                properties = mapOf(
                    "Title" to NotionPropertyValue(
                        type = "title",
                        title = listOf(NotionRichText(text = NotionTextContent(text)))
                    ),
                    "Type" to NotionPropertyValue(
                        type = "select",
                        select = NotionSelectValue(type)
                    ),
                    "Status" to NotionPropertyValue(
                        type = "select",
                        select = NotionSelectValue("pending")
                    )
                )
            )
        )

        refreshKeywords()
    }

    suspend fun deleteKeyword(id: String) {
        val auth = getAuth()
        // Archive the page (Notion doesn't truly delete via API)
        notionApi.updatePage(
            auth = auth,
            pageId = id,
            request = UpdatePageRequest(
                properties = mapOf(
                    "Status" to NotionPropertyValue(
                        type = "select",
                        select = NotionSelectValue("deleted")
                    )
                )
            )
        )
        _keywords.value = _keywords.value.filter { it.id != id }
    }

    suspend fun toggleResolved(id: String) {
        val auth = getAuth()
        val keyword = _keywords.value.find { it.id == id } ?: return
        val newStatus = if (keyword.isResolved) "pending" else "resolved"
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

        val properties = mutableMapOf<String, NotionPropertyValue>(
            "Status" to NotionPropertyValue(
                type = "select",
                select = NotionSelectValue(newStatus)
            )
        )

        if (newStatus == "resolved") {
            properties["Resolved Date"] = NotionPropertyValue(
                type = "date",
                date = NotionDateValue(start = today)
            )
        }

        notionApi.updatePage(
            auth = auth,
            pageId = id,
            request = UpdatePageRequest(properties = properties)
        )

        refreshKeywords()
    }

    suspend fun getPendingKeywords(): List<KeywordUiItem> {
        refreshKeywords()
        return _keywords.value.filter { !it.isResolved }
    }

    suspend fun cleanupResolvedKeywords() {
        val auth = getAuth()
        val dbId = getDbId()
        val twoWeeksAgo = LocalDate.now().minusWeeks(2).format(DateTimeFormatter.ISO_LOCAL_DATE)

        val response = notionApi.queryDatabase(
            auth = auth,
            databaseId = dbId,
            request = NotionQueryRequest(
                filter = NotionFilter(
                    and = listOf(
                        NotionFilter(
                            property = "Status",
                            select = NotionSelectFilter(equals = "resolved")
                        ),
                        NotionFilter(
                            property = "Resolved Date",
                            date = NotionDateFilter(onOrBefore = twoWeeksAgo)
                        )
                    )
                )
            )
        )

        response.results.forEach { page ->
            notionApi.deleteBlock(auth = auth, blockId = page.id)
        }

        refreshKeywords()
    }
}
