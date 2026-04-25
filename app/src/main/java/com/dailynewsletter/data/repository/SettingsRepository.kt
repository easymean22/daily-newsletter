package com.dailynewsletter.data.repository

import com.dailynewsletter.data.local.dao.SettingsDao
import com.dailynewsletter.data.local.entity.SettingsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val settingsDao: SettingsDao
) {
    suspend fun get(key: String): String? = settingsDao.get(key)

    fun observe(key: String): Flow<String?> = settingsDao.observe(key)

    suspend fun set(key: String, value: String) {
        settingsDao.set(SettingsEntity(key, value))
    }

    suspend fun getNotionApiKey(): String? = get(SettingsEntity.KEY_NOTION_API_KEY)
    suspend fun getClaudeApiKey(): String? = get(SettingsEntity.KEY_CLAUDE_API_KEY)
    suspend fun getPrinterIp(): String? = get(SettingsEntity.KEY_PRINTER_IP)
    suspend fun getPrinterEmail(): String? = get(SettingsEntity.KEY_PRINTER_EMAIL)
    suspend fun getNewsletterPages(): Int = get(SettingsEntity.KEY_NEWSLETTER_PAGES)?.toIntOrNull() ?: 2

    suspend fun getPrintTimeHour(): Int = get(SettingsEntity.KEY_PRINT_TIME_HOUR)?.toIntOrNull() ?: 7
    suspend fun getPrintTimeMinute(): Int = get(SettingsEntity.KEY_PRINT_TIME_MINUTE)?.toIntOrNull() ?: 0

    suspend fun getKeywordsDbId(): String? = get(SettingsEntity.KEY_KEYWORDS_DB_ID)
    suspend fun getTopicsDbId(): String? = get(SettingsEntity.KEY_TOPICS_DB_ID)
    suspend fun getNewslettersDbId(): String? = get(SettingsEntity.KEY_NEWSLETTERS_DB_ID)

    fun observeAll(): Flow<Map<String, String>> = settingsDao.observeAll().map { list ->
        list.associate { it.key to it.value }
    }
}
