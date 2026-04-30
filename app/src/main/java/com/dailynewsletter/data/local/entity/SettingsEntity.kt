package com.dailynewsletter.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey
    val key: String,
    val value: String
) {
    companion object {
        const val KEY_NOTION_API_KEY = "notion_api_key"
        const val KEY_NOTION_PARENT_PAGE_ID = "notion_parent_page_id"
        const val KEY_GEMINI_API_KEY = "gemini_api_key"
        const val KEY_PRINTER_IP = "printer_ip"
        const val KEY_PRINTER_EMAIL = "printer_email"
        const val KEY_PRINT_TIME_HOUR = "print_time_hour"
        const val KEY_PRINT_TIME_MINUTE = "print_time_minute"
        const val KEY_NEWSLETTER_PAGES = "newsletter_pages"
        const val KEY_KEYWORDS_DB_ID = "keywords_db_id"
        const val KEY_TOPICS_DB_ID = "topics_db_id"
        const val KEY_NEWSLETTERS_DB_ID = "newsletters_db_id"
        const val KEY_ALARM_DAYS = "alarm_days"
    }
}
