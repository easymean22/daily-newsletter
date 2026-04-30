package com.dailynewsletter.data.repository

import com.dailynewsletter.data.local.entity.SettingsEntity
import com.dailynewsletter.data.remote.notion.*
import com.dailynewsletter.data.tag.TagNormalizer
import com.dailynewsletter.ui.keyword.KeywordUiItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

private const val LONG_TEXT_THRESHOLD = 80
private const val TITLE_MAX_LENGTH = 50
private const val KEY_ALL_TAGS = "all_tags"

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

    /** Returns title to use for Notion page (truncated if long). */
    private fun buildTitle(text: String): String {
        return if (text.length > LONG_TEXT_THRESHOLD) {
            text.take(TITLE_MAX_LENGTH) + "..."
        } else {
            text
        }
    }

    /** Returns paragraph children blocks for long text, or null for short text. */
    private fun buildBodyBlocks(text: String): List<NotionBlock>? {
        return if (text.length > LONG_TEXT_THRESHOLD) {
            listOf(
                NotionBlock(
                    type = "paragraph",
                    paragraph = NotionParagraphBlock(
                        richText = listOf(NotionRichText(text = NotionTextContent(text)))
                    )
                )
            )
        } else null
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
            val tags = page.properties["Tags"]?.multiSelect?.mapNotNull { it.name } ?: emptyList()

            KeywordUiItem(
                id = page.id,
                title = title,
                type = type,
                isResolved = status == "resolved",
                resolvedDate = resolvedDate,
                tags = tags,
                createdTime = page.createdTime
            )
        }
    }

    suspend fun addKeyword(text: String, tags: List<String>): KeywordUiItem {
        val auth = getAuth()
        val dbId = getDbId()
        val normalizedTags = TagNormalizer.ensureFreeTopicTag(tags)
        val pageTitle = buildTitle(text)
        val bodyBlocks = buildBodyBlocks(text)

        val page = notionApi.createPage(
            auth = auth,
            request = CreatePageRequest(
                parent = NotionParent(type = "database_id", databaseId = dbId),
                properties = mapOf(
                    "Title" to NotionPropertyValue(
                        type = "title",
                        title = listOf(NotionRichText(text = NotionTextContent(pageTitle)))
                    ),
                    "Type" to NotionPropertyValue(
                        type = "select",
                        select = NotionSelectValue("keyword")
                    ),
                    "Status" to NotionPropertyValue(
                        type = "select",
                        select = NotionSelectValue("pending")
                    ),
                    "Tags" to NotionPropertyValue(
                        type = "multi_select",
                        multiSelect = normalizedTags.map { NotionMultiSelectValue(name = it) }
                    )
                ),
                children = bodyBlocks
            )
        )

        val newItem = KeywordUiItem(
            id = page.id,
            title = pageTitle,
            type = "keyword",
            isResolved = false,
            resolvedDate = null,
            tags = normalizedTags,
            createdTime = page.createdTime
        )

        refreshKeywords()
        return newItem
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

    /** Returns all known tags: union of tags from all keywords plus tags stored in SettingsRepository. */
    suspend fun getAllTags(): Set<String> {
        val fromKeywords = _keywords.value.flatMap { it.tags }.toSet()
        val fromSettings = settingsRepository.get(KEY_ALL_TAGS)
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?: emptySet()
        return fromKeywords + fromSettings
    }

    /** Persists a new tag name to SettingsRepository so it appears even before any keyword uses it. */
    suspend fun persistTag(tag: String) {
        val existing = settingsRepository.get(KEY_ALL_TAGS)
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toMutableSet()
            ?: mutableSetOf()
        existing.add(tag.trim())
        settingsRepository.set(KEY_ALL_TAGS, existing.joinToString(","))
    }

    /** Removes a tag from SettingsRepository and patches all keywords that have it. */
    suspend fun removeTagFromAllKeywords(tag: String) {
        val auth = getAuth()

        // Remove from persisted settings
        val existing = settingsRepository.get(KEY_ALL_TAGS)
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() && it != tag }
            ?: emptyList()
        settingsRepository.set(KEY_ALL_TAGS, existing.joinToString(","))

        // Patch keywords in Notion that contain this tag
        val affected = _keywords.value.filter { tag in it.tags }
        affected.forEach { keyword ->
            val updatedTags = keyword.tags.filter { it != tag }
            notionApi.updatePage(
                auth = auth,
                pageId = keyword.id,
                request = UpdatePageRequest(
                    properties = mapOf(
                        "Tags" to NotionPropertyValue(
                            type = "multi_select",
                            multiSelect = updatedTags.map { NotionMultiSelectValue(name = it) }
                        )
                    )
                )
            )
        }

        refreshKeywords()
    }
}
