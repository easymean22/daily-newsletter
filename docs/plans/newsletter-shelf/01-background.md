---
updated: 2026-04-19
summary: "lazy 보충 모델 재정의 배경 + 본 플랜이 답해야 할 7개 결정 + 기존 코드 이슈 4건"
parent: ./README.md
---

# 배경 / 관련 요구사항

spec §3은 뉴스레터 파이프라인을 다음처럼 재정의했다.

> 뉴스레터는 Notion Newsletters DB에 미프린트 상태로 쌓여 있는 풀 (= 미프린트 Newsletter 집합)로 관리한다.
> 생성 경로: (1) 수동 생성 버튼 — 사용자가 주제를 골라 즉시 뉴스레터 1건을 만들고 미프린트 Newsletter 집합에 추가. (2) 프린트 시점 lazy 보충 — 슬롯에 매칭되는 풀이 비어 있으면 그 시점에 **2개 생성** (1 사용 + 1 비축).
> 1개 뉴스레터 = 주제 N개를 엮어 만든다. N은 Claude가 자동으로 결정. 사용자는 슬롯 단위의 "장수"만 지정.
> 사용된 주제는 consumed 처리.

본 플랜은 핸드오프 **#4 (lazy 보충 모델)** 와 **#5 (다중 주제 엮기 N=auto)** 를 **한 플랜에 통합**한다 — 두 핸드오프가 같은 생성 함수 `generateForSlot(tag, pageCount)`의 설계 결정을 공유하므로 분리하면 겹치는 trade-off를 두 번 정리해야 하고, 일관성도 깨지기 쉽다.

## 핵심 결정 포인트 (본 플랜이 답해야 할 질문)

1. **미프린트 Newsletter 집합의 데이터 소스**: Newsletters DB를 그대로 쓸 것인가, 별도 엔티티를 둘 것인가?
2. **lazy 2개 생성의 트리거 시점**: 앱 진입 / 주제 추가 후 / 프린트 시점 중 어디에서?
3. **lazy 2개 생성의 동기성**: 2개 동기 vs 1개 동기 + 1개 BG?
4. **생성 단위**: 슬롯별(태그·장수 스코프) vs 전역(오늘의 주제 전체)?
5. **consumed 상태 전이 시점**: 뉴스레터 저장 직후 vs 프린트 완료 시점?
6. **"다중 태그 슬롯"(핸드오프 #6에서 확정 예정) 대비**: 단일 태그 가정으로 짤 것인가, 리스트 가정으로 짤 것인가?
7. **기존 `NewsletterWorker` / T−30m 스케줄**: 제거 / 재배치 / 유지?

§1·§2·§3은 되돌리기 어려워 ADR-0005로 박고, 나머지는 본 플랜에서 결정한다.

## 발견된 기존 코드 이슈 (본 플랜이 함께 수리)

1. **`NewsletterWorker`가 T−30m에 "오늘의 주제 전체로 1개 HTML 생성"을 한다** — spec §3의 슬롯·태그·장수 모델과 무관. 본 플랜이 제거 결정.
2. **`PrintWorker`가 `inputData.newsletter_id`를 기대** — 호출자가 없음. 어느 코드도 `PrintWorker` enqueue에 `newsletter_id`를 넣지 않음 → 현재 코드가 실행되면 `Result.failure()`로 끝난다. 본 플랜이 PrintWorker 입력 구조 교체.
3. **`NewsletterGenerationService.generateAndSaveNewsletter()`가 `topics.isEmpty()`면 조용히 return** — 실패 알림 없음. spec §6 엣지 정책과 불일치 (알림 필요).
4. **`NewsletterRepository.printNewsletter(id)`가 `getNewsletters()` 호출 후 `find { it.id == id }`** — N+1 라운드트립, id가 이미 있는데 DB 전체 쿼리. 효율 문제 + 신뢰성 문제 (20개 페이지 기본 pageSize를 넘는 경우 못 찾음). 본 플랜에서 단건 조회 API로 교체.
