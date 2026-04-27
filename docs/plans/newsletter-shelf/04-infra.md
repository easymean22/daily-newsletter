---
updated: 2026-04-19
summary: "크로스커팅/인프라 — Worker 처분, WorkManager 정리, 배포 전 체크, Notion/DI 배선"
parent: ./README.md
---

# 크로스커팅 / 인프라

## Worker / 스케줄 처분 (요약)

| 대상 | 처분 | 사유 |
| --- | --- | --- |
| `DailyTopicWorker` | **제거** | topic-generation-paths가 이미 결정 (spec §3 재정의). |
| `NewsletterWorker` | **제거** | 본 플랜 결정 — T−30m 사전 생성은 lazy 보충 모델과 충돌. |
| `PrintWorker` | **유지, 입력 방식 변경** | 슬롯 루프를 내부에 품는 Orchestrator 호출로 대체. |
| `CleanupWorker` | **유지 (MVP 판정 밖)** | spec §7 "CleanupWorker는 MVP 판정 대상 아님". |

## WorkManager 정리 (앱 업데이트 시)

`WorkScheduler.scheduleAll()` 진입부:

```
workManager.cancelUniqueWork("newsletter_generation")
workManager.cancelUniqueWork("daily_topic_selection")   // topic-generation-paths 몫
```

중복 cancel은 no-op. 기존 사용자 단말에 enqueue되어 있던 periodic work를 정리.

## 배포 전 체크리스트 반영

- **Claude API 호출 횟수 상한**: lazy 2개 동기 호출 + 후보 복수 시 추천 호출 1개 = 슬롯당 최대 3회. 요일별 슬롯 수가 N개면 하루 최대 3N회. `release-hardening` 또는 사용자 안내 문서에 기재 필요 (사용자 확인 필요 #3).
- **Notion rate limit**: 슬롯당 쿼리 1 + 생성 시 saveTopic markConsumed N개 ≤ 10. 요일 전체 합쳐도 수십 회 → 3 req/s Notion 공식 한계 대비 여유.
- **Worker 실행 시 Claude/Notion 토큰 로깅**: `HttpLoggingInterceptor.Level.BODY`가 켜져 있어 API 키가 로그에 찍힐 위험. `release-hardening`이 다룰 이슈이나, Worker가 실행되면서 `adb logcat`에 민감 정보 노출 → 배포 전 필수 점검 항목으로 메모.

## Notion 신규 엔드포인트

- `NotionApi`에 `@GET("v1/pages/{id}") suspend fun getPage(...)` 추가 — `NewsletterRepository.getNewsletter(id)` 단건 조회용. 응답은 기존 `NotionPage` 재사용.

## Hilt / DI 배선

- `PrintOrchestrator`는 `@Singleton` + `@Inject constructor` → 자동 발견.
- `ClaudeNewsletterRecommender`는 Stub 수준으로 `@Singleton` + 내부 fallback 메서드만. Claude 호출 추가 시 `ClaudeApi` 주입.
- `NotificationHelper`는 `@Singleton` + `@ApplicationContext` 주입 — 기존 `PrintWorker`의 `showNotification` 코드 이동.
- `PrintWorker`는 `@HiltWorker` + `@AssistedInject` 유지. 의존성: `PrintOrchestrator` 1개로 축소.
