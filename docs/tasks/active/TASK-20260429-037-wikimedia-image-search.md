# Task Brief: Wikimedia Commons 이미지 검색 + 자동 첨부

Task ID: TASK-20260429-037
Status: active

## Goal
뉴스레터 본문에 관련 사진/다이어그램을 자동으로 첨부. Gemini는 마커만 출력 → 우리가 Wikimedia Commons API로 검색 → Notion image block(external URL)으로 저장. 결과 없으면 마커 자체 제거 (대체 X).

## User-visible behavior
- 뉴스레터 생성 시 본문 안 적절한 위치에 관련 이미지(있다면) 자동 삽입.
- Notion에서 보면 실제 그림이 렌더링.
- 검색 결과 없는 주제는 그림 없이 텍스트만 — 자연스러움.

## Scope

### 1. 신규 `app/src/main/java/com/dailynewsletter/data/remote/wikimedia/WikimediaApi.kt`
- Retrofit 인터페이스. Endpoint: `https://commons.wikimedia.org/w/api.php`.
- 함수:
  ```kotlin
  @GET("api.php")
  suspend fun search(
      @Query("action") action: String = "query",
      @Query("format") format: String = "json",
      @Query("generator") generator: String = "search",
      @Query("gsrsearch") query: String,
      @Query("gsrnamespace") namespace: Int = 6,  // File:
      @Query("gsrlimit") limit: Int = 1,
      @Query("prop") prop: String = "imageinfo",
      @Query("iiprop") iiprop: String = "url"
  ): WikimediaResponse
  ```
- DTO `WikimediaResponse(val query: WikimediaQuery?)`, `WikimediaQuery(val pages: Map<String, WikimediaPage>?)`, `WikimediaPage(val title: String, val imageinfo: List<WikimediaImageInfo>?)`, `WikimediaImageInfo(val url: String)`.

### 2. 신규 `app/src/main/java/com/dailynewsletter/service/WikimediaImageSearch.kt`
- `@Singleton` Hilt class. 의존성: `WikimediaApi`.
- 함수:
  ```kotlin
  suspend fun searchFirst(query: String): String? {
      return try {
          val resp = wikimediaApi.search(query = query)
          resp.query?.pages?.values?.firstOrNull()?.imageinfo?.firstOrNull()?.url
      } catch (e: Exception) {
          Log.w(TAG, "search failed for '$query': ${e.message}")
          null
      }
  }
  ```
- 절대 throw하지 않음 — 실패 시 null. 호출자가 마커 제거 결정.

### 3. `app/src/main/java/com/dailynewsletter/di/NetworkModule.kt`
- WikimediaApi Retrofit instance Hilt provides 추가 (별도 base URL):
  ```kotlin
  @Provides @Singleton
  fun provideWikimediaApi(client: OkHttpClient): WikimediaApi =
      Retrofit.Builder()
          .baseUrl("https://commons.wikimedia.org/w/")
          .client(client)
          .addConverterFactory(GsonConverterFactory.create())
          .build()
          .create(WikimediaApi::class.java)
  ```

### 4. `app/src/main/java/com/dailynewsletter/data/remote/notion/NotionModels.kt`
- `NotionBlock`에 `image: NotionImageBlock?` 필드 추가.
- 신규 data class:
  ```kotlin
  data class NotionImageBlock(
      val type: String = "external",
      val external: NotionImageExternal
  )
  data class NotionImageExternal(val url: String)
  ```

### 5. `app/src/main/java/com/dailynewsletter/service/NewsletterGenerationService.kt`
프롬프트 규칙에 추가 (mermaid 규칙 다음):
```text
9. **그림(사진/실제 다이어그램) 첨부 가능**: 개념 이해에 그림이 도움 되면 본문 안에 마커만 삽입:
   `<img-search query="영문 검색어"/>`. URL 직접 X.
   - 검색어는 영문 키워드 (Wikimedia Commons는 영문 인덱스 우세).
   - 1편당 최대 2개. 너무 일반적인 검색어(예: "computer")는 피하고 구체적 명사로.
   - 그림이 도움 안 되는 추상 개념은 마커 생략.
```

### 6. `app/src/main/java/com/dailynewsletter/data/repository/NewsletterRepository.kt`
- `htmlToBlocks(html: String): List<NotionBlock>` 시그니처 → `suspend fun htmlToBlocks(html: String): List<NotionBlock>`.
- 새 의존성 주입: `private val wikimediaImageSearch: WikimediaImageSearch` (생성자에 추가).
- 함수 변경 사항:
  1. mermaid 추출 패스 그대로 보존.
  2. 새 패스: `<img-search query="..."/>` 마커 정규식으로 추출 → 각 query에 대해 `wikimediaImageSearch.searchFirst(query)` 병렬 호출 (`coroutineScope { queries.map { async { ... } }.awaitAll() }`).
  3. 결과 URL 있으면 `NotionImageBlock`으로 NotionBlock 생성, 없으면 마커 영역만 제거 (대체 없음).
  4. 위치 보존을 위해 indexed-block 정렬 (mermaid 처리 패턴과 동일).
- 호출자 (saveNewsletter 등 4곳)는 이미 suspend 컨텍스트 안에서 호출 중이라 시그니처 변경만으로 자동 호환.

## Out of Scope
- Bing/Google Custom Search — 후속.
- 이미지 라이센스 검증 — Wikimedia는 대부분 CC, 우리는 그대로 임베드.
- 이미지 URL HEAD 검증 — Wikimedia 응답에 있는 URL 그대로 신뢰.
- 캐싱 — 매번 새 검색.

## Files Owned By This Task
- `app/src/main/java/com/dailynewsletter/data/remote/wikimedia/WikimediaApi.kt` (신규)
- `app/src/main/java/com/dailynewsletter/service/WikimediaImageSearch.kt` (신규)
- `app/src/main/java/com/dailynewsletter/di/NetworkModule.kt`
- `app/src/main/java/com/dailynewsletter/data/remote/notion/NotionModels.kt`
- `app/src/main/java/com/dailynewsletter/service/NewsletterGenerationService.kt`
- `app/src/main/java/com/dailynewsletter/data/repository/NewsletterRepository.kt`

## Files Explicitly Not Owned
- `app/src/main/java/com/dailynewsletter/service/GeminiRetry.kt` (병렬 TASK-036 소유)
- `app/src/main/java/com/dailynewsletter/ui/newsletter/*` (병렬 TASK-036 소유)
- 그 외 모든 파일.

## Forbidden Changes
- No new dependency (Retrofit/Gson/OkHttp 이미 있음).
- No new permission (인터넷은 이미).
- No DB 스키마 변경.

## Acceptance Criteria
- [ ] `WikimediaApi.kt`, `WikimediaImageSearch.kt` 신규 파일.
- [ ] `NetworkModule.kt`에 `provideWikimediaApi` 추가.
- [ ] `NotionImageBlock` data class 정의 + `NotionBlock.image` 필드.
- [ ] NewsletterGenerationService 프롬프트에 `img-search` 마커 지시 등장.
- [ ] `NewsletterRepository.htmlToBlocks`가 `suspend` + WikimediaImageSearch 호출.
- [ ] 빌드 성공 또는 SKIPPED_ENVIRONMENT_NOT_AVAILABLE.

## Verification Command Candidates
- `grep -rn "WikimediaApi\\|WikimediaImageSearch\\|NotionImageBlock\\|img-search" app/src/main`
- `./gradlew :app:assembleDebug`

## Expected Implementer Output
- 변경 파일 6개 (2신규 + 4수정). 핵심 코드 인용. grep 결과. 빌드 결과. 사용자 다음 동작 1줄.

## STOP_AND_ESCALATE
- htmlToBlocks를 suspend로 바꿨을 때 다른 caller가 non-suspend 컨텍스트(예: 일반 fun)에서 호출 중이면 escalate.
- Wikimedia API가 OkHttp 인터셉터(Authorization 헤더 자동 추가)와 충돌하면 — Wikimedia는 인증 불필요, Retrofit instance를 별도로 만들거나 인터셉터 분리. escalate 불필요 (빌더 수준에서 처리).
