package com.dailynewsletter.data.remote.notion

import com.google.gson.annotations.SerializedName

// Database creation
data class CreateDatabaseRequest(
    val parent: NotionParent,
    val title: List<NotionRichText>,
    val properties: Map<String, NotionPropertySchema>
)

data class NotionParent(
    @SerializedName("type") val type: String = "page_id",
    @SerializedName("page_id") val pageId: String? = null,
    @SerializedName("database_id") val databaseId: String? = null
)

data class NotionRichText(
    val type: String = "text",
    val text: NotionTextContent
)

data class NotionTextContent(
    val content: String
)

data class NotionPropertySchema(
    val type: String,
    val title: Map<String, Any>? = null,
    val select: NotionSelectOptions? = null,
    @SerializedName("multi_select") val multiSelect: NotionMultiSelectSchema? = null,
    val relation: NotionRelationConfig? = null,
    val date: Map<String, Any>? = null,
    val number: Map<String, Any>? = null,
    @SerializedName("created_time") val createdTime: Map<String, Any>? = null,
    @SerializedName("rich_text") val richText: Map<String, Any>? = null
)

data class NotionSelectOptions(
    val options: List<NotionSelectOption>
)

data class NotionSelectOption(
    val name: String,
    val color: String? = null
)

data class NotionMultiSelectSchema(
    val options: List<NotionSelectOption>
)

data class NotionRelationConfig(
    @SerializedName("database_id") val databaseId: String,
    @SerializedName("single_property") val singleProperty: Map<String, Any> = emptyMap()
)

// Database query
data class NotionQueryRequest(
    val filter: NotionFilter? = null,
    val sorts: List<NotionSort>? = null,
    @SerializedName("start_cursor") val startCursor: String? = null,
    @SerializedName("page_size") val pageSize: Int = 100
)

data class NotionFilter(
    val property: String? = null,
    val select: NotionSelectFilter? = null,
    @SerializedName("multi_select") val multiSelect: NotionMultiSelectFilter? = null,
    val date: NotionDateFilter? = null,
    val number: NotionNumberFilter? = null,
    val and: List<NotionFilter>? = null,
    val or: List<NotionFilter>? = null
)

data class NotionNumberFilter(
    val equals: Double? = null,
    @SerializedName("does_not_equal") val doesNotEqual: Double? = null,
    @SerializedName("greater_than") val greaterThan: Double? = null,
    @SerializedName("less_than") val lessThan: Double? = null
)

data class NotionSelectFilter(
    val equals: String? = null,
    @SerializedName("does_not_equal") val doesNotEqual: String? = null
)

data class NotionMultiSelectFilter(
    val contains: String? = null,
    @SerializedName("does_not_contain") val doesNotContain: String? = null
)

data class NotionDateFilter(
    val equals: String? = null,
    val before: String? = null,
    val after: String? = null,
    @SerializedName("on_or_before") val onOrBefore: String? = null
)

data class NotionSort(
    val property: String? = null,
    val timestamp: String? = null,
    val direction: String = "descending"
)

// Query response
data class NotionQueryResponse(
    val results: List<NotionPage>,
    @SerializedName("has_more") val hasMore: Boolean,
    @SerializedName("next_cursor") val nextCursor: String?
)

data class NotionPage(
    val id: String,
    val properties: Map<String, NotionPropertyValue>,
    @SerializedName("created_time") val createdTime: String? = null
)

data class NotionPropertyValue(
    val type: String,
    val title: List<NotionRichText>? = null,
    val select: NotionSelectValue? = null,
    @SerializedName("multi_select") val multiSelect: List<NotionMultiSelectValue>? = null,
    val date: NotionDateValue? = null,
    val relation: List<NotionRelationValue>? = null,
    val number: Double? = null,
    @SerializedName("rich_text") val richText: List<NotionRichText>? = null
)

data class NotionSelectValue(
    val name: String?
)

data class NotionMultiSelectValue(
    val name: String? = null,
    val id: String? = null
)

data class NotionDateValue(
    val start: String?,
    val end: String? = null
)

data class NotionRelationValue(
    val id: String
)

// Page creation
data class CreatePageRequest(
    val parent: NotionParent,
    val properties: Map<String, NotionPropertyValue>,
    val children: List<NotionBlock>? = null
)

data class UpdatePageRequest(
    val properties: Map<String, NotionPropertyValue>
)

data class NotionBlock(
    val type: String,
    val paragraph: NotionParagraphBlock? = null,
    val heading_1: NotionHeadingBlock? = null,
    val heading_2: NotionHeadingBlock? = null,
    val heading_3: NotionHeadingBlock? = null,
    val bulleted_list_item: NotionListItemBlock? = null,
    val code: NotionCodeBlock? = null,
    val image: NotionImageBlock? = null
)

data class NotionImageBlock(
    val type: String = "external",
    val external: NotionImageExternal
)

data class NotionImageExternal(val url: String)

data class NotionCodeBlock(
    @SerializedName("rich_text") val richText: List<NotionRichText>,
    val language: String = "plain text"
)

data class NotionParagraphBlock(
    @SerializedName("rich_text") val richText: List<NotionRichText>
)

data class NotionHeadingBlock(
    @SerializedName("rich_text") val richText: List<NotionRichText>
)

data class NotionListItemBlock(
    @SerializedName("rich_text") val richText: List<NotionRichText>
)

// Block children response
data class NotionBlocksResponse(
    val results: List<NotionBlock>,
    @SerializedName("has_more") val hasMore: Boolean = false,
    @SerializedName("next_cursor") val nextCursor: String? = null
)

// Database response
data class NotionDatabaseResponse(
    val id: String,
    val title: List<NotionRichText>
)
