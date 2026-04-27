package com.dailynewsletter.data.repository

import com.dailynewsletter.data.remote.notion.*
import com.dailynewsletter.service.PrintService
import com.dailynewsletter.ui.newsletter.NewsletterUiItem
import java.time.OffsetDateTime
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

            val htmlContent = contentBlocks?.let {
                try { blocksToHtml(it) } catch (e: Exception) { null }
            }

            val tags = page.properties["Tags"]?.multiSelect?.mapNotNull { it.name } ?: emptyList()

            NewsletterUiItem(
                id = page.id,
                title = title,
                date = date,
                status = status,
                pageCount = pageCount,
                htmlContent = htmlContent,
                tags = tags
            )
        }
    }

    private fun blocksToHtml(response: NotionBlocksResponse): String {
        fun plainText(richText: List<NotionRichText>): String =
            richText.joinToString("") { it.text.content }

        val htmlParts = mutableListOf<String>()
        var inList = false

        for (block in response.results) {
            when (block.type) {
                "heading_1" -> {
                    if (inList) { htmlParts.add("</ul>"); inList = false }
                    val text = plainText(block.heading_1?.richText ?: emptyList())
                    if (text.isNotEmpty()) htmlParts.add("<h1>$text</h1>")
                }
                "heading_2" -> {
                    if (inList) { htmlParts.add("</ul>"); inList = false }
                    val text = plainText(block.heading_2?.richText ?: emptyList())
                    if (text.isNotEmpty()) htmlParts.add("<h2>$text</h2>")
                }
                "heading_3" -> {
                    if (inList) { htmlParts.add("</ul>"); inList = false }
                    val text = plainText(block.heading_3?.richText ?: emptyList())
                    if (text.isNotEmpty()) htmlParts.add("<h3>$text</h3>")
                }
                "paragraph" -> {
                    if (inList) { htmlParts.add("</ul>"); inList = false }
                    val text = plainText(block.paragraph?.richText ?: emptyList())
                    if (text.isNotEmpty()) htmlParts.add("<p>$text</p>")
                }
                "bulleted_list_item" -> {
                    if (!inList) { htmlParts.add("<ul>"); inList = true }
                    val text = plainText(block.bulleted_list_item?.richText ?: emptyList())
                    if (text.isNotEmpty()) htmlParts.add("<li>$text</li>")
                }
                else -> {
                    // Unsupported block type — skip
                }
            }
        }

        if (inList) htmlParts.add("</ul>")

        val body = htmlParts.joinToString("\n")
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

    suspend fun saveNewsletter(title: String, htmlContent: String, topicIds: List<String>, pageCount: Int, tags: List<String>): String {
        val auth = getAuth()
        val dbId = getDbId()
        val today = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

        val page = notionApi.createPage(
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
                    ),
                    "Tags" to NotionPropertyValue(
                        type = "multi_select",
                        multiSelect = tags.map { NotionMultiSelectValue(name = it) }
                    )
                ),
                children = htmlToBlocks(htmlContent)
            )
        )
        return page.id
    }

    suspend fun getNewsletter(id: String): NewsletterUiItem {
        val auth = getAuth()
        val page = notionApi.getPage(auth = auth, pageId = id)
        val title = page.properties["Title"]?.title?.firstOrNull()?.text?.content ?: ""
        val date = page.properties["Date"]?.date?.start ?: ""
        val status = page.properties["Status"]?.select?.name ?: "generated"
        val pageCount = page.properties["Page Count"]?.number?.toInt() ?: 2
        val tags = page.properties["Tags"]?.multiSelect?.mapNotNull { it.name } ?: emptyList()

        val contentBlocks = try {
            notionApi.getBlockChildren(auth = auth, blockId = page.id)
        } catch (e: Exception) { null }

        val htmlContent = contentBlocks?.let { blocksToHtml(it) }

        return NewsletterUiItem(
            id = page.id,
            title = title,
            date = date,
            status = status,
            pageCount = pageCount,
            htmlContent = htmlContent,
            tags = tags
        )
    }

    suspend fun findUnprintedByTagAndPages(tag: String, pageCount: Int): List<NewsletterUiItem> {
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
                            select = NotionSelectFilter(equals = "generated")
                        ),
                        NotionFilter(
                            property = "Page Count",
                            number = NotionNumberFilter(equals = pageCount.toDouble())
                        )
                    )
                )
            )
        )

        return response.results.map { page ->
            val title = page.properties["Title"]?.title?.firstOrNull()?.text?.content ?: ""
            val date = page.properties["Date"]?.date?.start ?: ""
            val status = page.properties["Status"]?.select?.name ?: "generated"
            val pc = page.properties["Page Count"]?.number?.toInt() ?: 2
            val tags = page.properties["Tags"]?.multiSelect?.mapNotNull { it.name } ?: emptyList()

            val contentBlocks = try {
                notionApi.getBlockChildren(auth = auth, blockId = page.id)
            } catch (e: Exception) { null }

            val htmlContent = contentBlocks?.let { blocksToHtml(it) }

            NewsletterUiItem(
                id = page.id,
                title = title,
                date = date,
                status = status,
                pageCount = pc,
                htmlContent = htmlContent,
                tags = tags
            )
        }
    }

    suspend fun findUnprintedNewsletterByTag(tagName: String): NewsletterUiItem? {
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
                            multiSelect = NotionMultiSelectFilter(contains = tagName)
                        ),
                        NotionFilter(
                            property = "Status",
                            select = NotionSelectFilter(equals = "generated")
                        )
                    )
                ),
                pageSize = 1
            )
        )

        val page = response.results.firstOrNull() ?: return null
        val title = page.properties["Title"]?.title?.firstOrNull()?.text?.content ?: ""
        val date = page.properties["Date"]?.date?.start ?: ""
        val status = page.properties["Status"]?.select?.name ?: "generated"
        val pageCount = page.properties["Page Count"]?.number?.toInt() ?: 2
        val tags = page.properties["Tags"]?.multiSelect?.mapNotNull { it.name } ?: emptyList()

        val contentBlocks = try {
            notionApi.getBlockChildren(auth = auth, blockId = page.id)
        } catch (e: Exception) { null }

        val htmlContent = contentBlocks?.let { blocksToHtml(it) }

        return NewsletterUiItem(
            id = page.id,
            title = title,
            date = date,
            status = status,
            pageCount = pageCount,
            htmlContent = htmlContent,
            tags = tags
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
        val newsletter = getNewsletter(id)
        val html = newsletter.htmlContent ?: throw IllegalStateException("뉴스레터 내용이 없습니다")

        printService.printHtml(html, newsletter.title)
        updateNewsletterStatus(id, "printed")
    }

    /**
     * Converts an HTML string produced by Gemini into a flat list of Notion native blocks.
     *
     * Supported tags: h1, h2, h3, p, ul > li.
     * Meta/structure tags (html, head, body, style, meta, DOCTYPE) are ignored.
     * Inline tags (strong, code, em, etc.) are stripped to plain text.
     * HTML entities (&lt; &gt; &amp; &nbsp;) are decoded.
     * Defensive: never throws; returns partial/empty list on malformed input.
     */
    private fun htmlToBlocks(html: String): List<NotionBlock> {
        val blocks = mutableListOf<NotionBlock>()

        // Decode common HTML entities
        fun decodeEntities(s: String): String = s
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&nbsp;", " ")
            .replace("&#39;", "'")
            .replace("&quot;", "\"")

        // Strip all remaining inline HTML tags, leaving their text content
        fun stripTags(s: String): String = s.replace(Regex("<[^>]*>"), "").trim()

        fun plainText(raw: String): String = decodeEntities(stripTags(raw)).trim()

        fun richTextFrom(text: String): List<NotionRichText> =
            chunkText(text).map { NotionRichText(text = NotionTextContent(it)) }

        // Process block-level tags: h1, h2, h3, p
        val blockTagPattern = Regex(
            "<(h1|h2|h3|p)(\\s[^>]*)?>([\\s\\S]*?)<\\/\\1>",
            RegexOption.IGNORE_CASE
        )
        // ul blocks
        val ulPattern = Regex("<ul(\\s[^>]*)?>[\\s\\S]*?<\\/ul>", RegexOption.IGNORE_CASE)
        val liPattern = Regex("<li(\\s[^>]*)?>([ \\s\\S]*?)<\\/li>", RegexOption.IGNORE_CASE)

        // Build a list of (startIndex, block) by scanning for block-level elements in order
        data class IndexedBlock(val start: Int, val block: NotionBlock)
        val indexed = mutableListOf<IndexedBlock>()

        // Collect h1/h2/h3/p matches
        for (match in blockTagPattern.findAll(html)) {
            val tag = match.groupValues[1].lowercase()
            val text = plainText(match.groupValues[3])
            if (text.isEmpty()) continue
            val rt = richTextFrom(text)
            val block = when (tag) {
                "h1" -> NotionBlock(type = "heading_1", heading_1 = NotionHeadingBlock(rt))
                "h2" -> NotionBlock(type = "heading_2", heading_2 = NotionHeadingBlock(rt))
                "h3" -> NotionBlock(type = "heading_3", heading_3 = NotionHeadingBlock(rt))
                else -> NotionBlock(type = "paragraph", paragraph = NotionParagraphBlock(rt))
            }
            indexed.add(IndexedBlock(match.range.first, block))
        }

        // Collect ul blocks (expand each li into its own bulleted_list_item block)
        for (ulMatch in ulPattern.findAll(html)) {
            val ulStart = ulMatch.range.first
            for (liMatch in liPattern.findAll(ulMatch.value)) {
                val text = plainText(liMatch.groupValues[2])
                if (text.isEmpty()) continue
                val rt = listOf(NotionRichText(text = NotionTextContent(text.take(1900))))
                indexed.add(IndexedBlock(
                    ulStart + liMatch.range.first,
                    NotionBlock(type = "bulleted_list_item", bulleted_list_item = NotionListItemBlock(rt))
                ))
            }
        }

        // Sort by position in the original HTML to preserve document order
        indexed.sortBy { it.start }
        indexed.forEach { blocks.add(it.block) }

        // Fallback: if nothing matched, return the entire stripped content as one paragraph
        if (blocks.isEmpty()) {
            val fallback = plainText(html)
            if (fallback.isNotEmpty()) {
                blocks.add(NotionBlock(
                    type = "paragraph",
                    paragraph = NotionParagraphBlock(richTextFrom(fallback))
                ))
            }
        }

        return blocks
    }

    /** Splits [text] into chunks of at most [max] characters each.
     *  Notion rich_text.text.content must be ≤ 2000 chars; using 1900 as safe upper bound.
     *  Returns listOf("") for empty input so callers always get at least one entry.
     */
    private fun chunkText(text: String, max: Int = 1900): List<String> {
        if (text.isEmpty()) return listOf("")
        val chunks = mutableListOf<String>()
        var offset = 0
        while (offset < text.length) {
            chunks.add(text.substring(offset, minOf(offset + max, text.length)))
            offset += max
        }
        return chunks
    }
}
