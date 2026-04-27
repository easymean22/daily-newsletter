package com.dailynewsletter.data.remote.notion

import retrofit2.http.*

interface NotionApi {

    @POST("v1/databases")
    suspend fun createDatabase(
        @Header("Authorization") auth: String,
        @Header("Notion-Version") version: String = "2022-06-28",
        @Header("Content-Type") contentType: String = "application/json",
        @Body request: CreateDatabaseRequest
    ): NotionDatabaseResponse

    @POST("v1/databases/{databaseId}/query")
    suspend fun queryDatabase(
        @Header("Authorization") auth: String,
        @Header("Notion-Version") version: String = "2022-06-28",
        @Header("Content-Type") contentType: String = "application/json",
        @Path("databaseId") databaseId: String,
        @Body request: NotionQueryRequest
    ): NotionQueryResponse

    @POST("v1/pages")
    suspend fun createPage(
        @Header("Authorization") auth: String,
        @Header("Notion-Version") version: String = "2022-06-28",
        @Header("Content-Type") contentType: String = "application/json",
        @Body request: CreatePageRequest
    ): NotionPage

    @PATCH("v1/pages/{pageId}")
    suspend fun updatePage(
        @Header("Authorization") auth: String,
        @Header("Notion-Version") version: String = "2022-06-28",
        @Header("Content-Type") contentType: String = "application/json",
        @Path("pageId") pageId: String,
        @Body request: UpdatePageRequest
    ): NotionPage

    @DELETE("v1/blocks/{blockId}")
    suspend fun deleteBlock(
        @Header("Authorization") auth: String,
        @Header("Notion-Version") version: String = "2022-06-28",
        @Path("blockId") blockId: String
    )

    @GET("v1/pages/{pageId}")
    suspend fun getPage(
        @Header("Authorization") auth: String,
        @Header("Notion-Version") version: String = "2022-06-28",
        @Path("pageId") pageId: String
    ): NotionPage

    @GET("v1/blocks/{blockId}/children")
    suspend fun getBlockChildren(
        @Header("Authorization") auth: String,
        @Header("Notion-Version") version: String = "2022-06-28",
        @Path("blockId") blockId: String,
        @Query("start_cursor") startCursor: String? = null,
        @Query("page_size") pageSize: Int = 100
    ): NotionBlocksResponse
}
