package com.dailynewsletter.service

import com.dailynewsletter.data.local.entity.SettingsEntity
import com.dailynewsletter.data.remote.notion.*
import com.dailynewsletter.data.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotionSetupService @Inject constructor(
    private val notionApi: NotionApi,
    private val settingsRepository: SettingsRepository
) {
    suspend fun setupDatabases() {
        val apiKey = settingsRepository.getNotionApiKey() ?: throw IllegalStateException("Notion API Key 미설정")
        val parentPageId = settingsRepository.get(SettingsEntity.KEY_NOTION_PARENT_PAGE_ID)
            ?: throw IllegalStateException("Notion 상위 페이지 ID 미설정")
        val auth = "Bearer $apiKey"

        // Skip if already set up
        if (settingsRepository.getKeywordsDbId() != null) return

        // 1. Create Keywords DB
        val keywordsDb = notionApi.createDatabase(
            auth = auth,
            request = CreateDatabaseRequest(
                parent = NotionParent(type = "page_id", pageId = parentPageId),
                title = listOf(NotionRichText(text = NotionTextContent("Keywords"))),
                properties = mapOf(
                    "Title" to NotionPropertySchema(type = "title"),
                    "Type" to NotionPropertySchema(
                        type = "select",
                        select = NotionSelectOptions(listOf(
                            NotionSelectOption("keyword", "blue"),
                            NotionSelectOption("memo", "green")
                        ))
                    ),
                    "Status" to NotionPropertySchema(
                        type = "select",
                        select = NotionSelectOptions(listOf(
                            NotionSelectOption("pending", "yellow"),
                            NotionSelectOption("resolved", "green"),
                            NotionSelectOption("deleted", "red")
                        ))
                    ),
                    "Resolved Date" to NotionPropertySchema(type = "date", date = emptyMap()),
                    "Created At" to NotionPropertySchema(type = "created_time", createdTime = emptyMap())
                )
            )
        )
        settingsRepository.set(SettingsEntity.KEY_KEYWORDS_DB_ID, keywordsDb.id)

        // 2. Create Topics DB
        val topicsDb = notionApi.createDatabase(
            auth = auth,
            request = CreateDatabaseRequest(
                parent = NotionParent(type = "page_id", pageId = parentPageId),
                title = listOf(NotionRichText(text = NotionTextContent("Topics"))),
                properties = mapOf(
                    "Title" to NotionPropertySchema(type = "title"),
                    "Source Keywords" to NotionPropertySchema(
                        type = "relation",
                        relation = NotionRelationConfig(databaseId = keywordsDb.id)
                    ),
                    "Priority Type" to NotionPropertySchema(
                        type = "select",
                        select = NotionSelectOptions(listOf(
                            NotionSelectOption("direct", "blue"),
                            NotionSelectOption("prerequisite", "orange"),
                            NotionSelectOption("peripheral", "purple")
                        ))
                    ),
                    "Status" to NotionPropertySchema(
                        type = "select",
                        select = NotionSelectOptions(listOf(
                            NotionSelectOption("selected", "yellow"),
                            NotionSelectOption("read", "green"),
                            NotionSelectOption("modified", "blue")
                        ))
                    ),
                    "Date" to NotionPropertySchema(type = "date", date = emptyMap())
                )
            )
        )
        settingsRepository.set(SettingsEntity.KEY_TOPICS_DB_ID, topicsDb.id)

        // 3. Create Newsletters DB
        val newslettersDb = notionApi.createDatabase(
            auth = auth,
            request = CreateDatabaseRequest(
                parent = NotionParent(type = "page_id", pageId = parentPageId),
                title = listOf(NotionRichText(text = NotionTextContent("Newsletters"))),
                properties = mapOf(
                    "Title" to NotionPropertySchema(type = "title"),
                    "Date" to NotionPropertySchema(type = "date", date = emptyMap()),
                    "Topics" to NotionPropertySchema(
                        type = "relation",
                        relation = NotionRelationConfig(databaseId = topicsDb.id)
                    ),
                    "Status" to NotionPropertySchema(
                        type = "select",
                        select = NotionSelectOptions(listOf(
                            NotionSelectOption("generated", "yellow"),
                            NotionSelectOption("printed", "green"),
                            NotionSelectOption("failed", "red")
                        ))
                    ),
                    "Page Count" to NotionPropertySchema(type = "number", number = emptyMap())
                )
            )
        )
        settingsRepository.set(SettingsEntity.KEY_NEWSLETTERS_DB_ID, newslettersDb.id)
    }
}
