---
updated: 2026-04-30
status: accepted
owner: planner
summary: "TASK-027 검증 시도 중 알람 권한 에러 발견 → TASK-040(권한 픽스+에러 라벨 분리) 진행 중. TASK-041(시간 UI 12시간/AM-PM/NumberPicker 다이얼) 대기."
refs:
  - docs/specs/mvp.md
  - docs/context/current-state.md
---

# 진행 중 작업 (Status)

공유 모듈 수정 전 여기에 선언 (글로벌 CLAUDE.md 규칙). 각 Task의 상세는 `docs/tasks/active/TASK-*.md` Brief, 결과는 `docs/task-results/TASK-*-implementer-result.md`.

## 형식

```
- [YYYY-MM-DD | <role> | <상태>] <한 줄 요약> → <Brief 또는 result 경로>
```

상태 값: `진행중`, `검증대기`, `완료`, `블록됨`.

## 검증대기 (사용자 디바이스 빌드·테스트 필요)

- [2026-04-29 | implementer | 검증대기] **TASK-027** — 알람 팝업 + 사운드 + 자동 생성. 8 파일(3 신규 + 5 수정). AlarmService(foreground, MediaPlayer looping, 진동 OFF) + AlarmActivity(잠금화면 위 팝업) + AlarmReceiver 연결 + getLatestUnprintedNewsletter/getLatestPendingTopic/generateLatest helper + Manifest 등록 + CHANNEL_ALARM 채널. → [docs/task-results/TASK-20260429-027-implementer-result.md](task-results/TASK-20260429-027-implementer-result.md)

## 현재 진행 중

- [2026-04-30 | implementer | 진행중] **TASK-040** — Exact alarm permission fix + 알람 에러 라벨 분리. 5 파일(Manifest USE_EXACT_ALARM 추가 + AlarmScheduler RescheduleResult sealed + canScheduleExactAlarms 가드 + AlarmReceiver try/catch + SettingsViewModel AlarmFeedback 채널 분리 + SettingsScreen 권한 안내 스낵바). → [docs/tasks/active/TASK-20260430-040-exact-alarm-permission-fix.md](tasks/active/TASK-20260430-040-exact-alarm-permission-fix.md)

## 다음 작업 후보 (실행 순서)

1. **TASK-041 — 알람 시간 UI 12시간 + AM/PM + NumberPicker 다이얼**. Material3 TimePicker → Android NumberPicker AndroidView 래핑 (휠 다이얼). hour 1~12 + AM/PM 토글. 내부 저장은 24시간 유지. **TASK-040 검증 후 시작** (SettingsScreen.kt 파일 충돌).
2. **TASK-029 — 설정 화면에 기본 프롬프트 입력**. 사용자 정의 프롬프트가 Gemini 호출 시 추가 지시문으로 합쳐짐 (옵션 a 채택).
3. **TASK-031 — 주제 오버홀** (가장 큰 작업). 우선순위 Number 정렬 + AI 자동 우선순위 + 드래그드롭 + 상세 화면 + "특히 다루었으면 하는 부분" 입력 + 태그 시스템 + consumed 최하단 회색 + source keywords 표시 + ready/consumed 라벨 + "추천이유" 한국어 매핑.
4. **TASK-039b — 키워드 길게 눌러 생성 시트**. KeywordScreen long-press → 해당 키워드 focus + 전체 키워드 컨텍스트로 주제 생성.

## Follow-up 후보 (Out-of-scope, 시간 나면)

- KeywordViewModel dead code 청소 (`AutoGenStatus`, 사용 안 되는 의존성 주입). TASK-030 후속.
- `release-hardening`: OkHttp `HttpLoggingInterceptor.Level.BODY`로 인한 Notion 토큰 logcat 노출 → release build에서 NONE/BASIC. 미작성.
- Notion 토큰 회전 (사용자 액션). logcat에 4회+ 노출됨.

## 완료 마일스톤 요약 (TASK 단위 상세는 task-results/)

- **TASK-001~009 (4/26)**: Scope A 1차 — Gemini migration, manual newsletter creation, Notion 3-DB setup, tag system, gallery UI.
- **TASK-010~020 (4/26)**: Scope A 후속 — SuggestedTopic 복원, JSON 구조화 응답, rich_text 분할, HTML→native blocks, 갤러리 UI, 응답 잘림 방지, 자동 로드, NPE/Block→HTML 픽스, 제목/날짜 정리.
- **TASK-021~023 (4/28)**: 인쇄 스택 정상화 — 진단 로깅 → PdfService measure 시도 → PrintManager 시스템 인쇄로 전환 (raw IPP 폐기).
- **TASK-024~025 (4/28)**: 알람 인프라 — Settings 시간/요일 + AlarmManager + Receiver + Boot + Manifest 권한 (실제 사운드/팝업은 TASK-027).
- **TASK-026 (4/28)**: 단일 주제 deep-dive 모드 (다중 주제 묶음 → 1주제 깊게).
- **TASK-028 (4/28)**: 뉴스레터 상세 UI 정리 (인쇄 버튼 1개 + BackHandler).
- **TASK-032 (4/28)**: Mermaid 다이어그램 자동 삽입 (Notion code block, language=mermaid).
- **TASK-033 (4/29)**: 뉴스레터 그리드 lazy load (1+N 호출 → 1 호출, 카드 탭 시 본문 fetch).
- **TASK-034~036 (4/29)**: Gemini 503 재시도 + UI 진행 표시 (배너 + AlertDialog + 워딩 정리, "혼잡"/"실패" 제거).
- **TASK-037 (4/29)**: Wikimedia Commons 이미지 자동 삽입 (사진 1순위, mermaid 보조).
- **TASK-038 (4/29)**: Exponential backoff (4단계 + jitter) + Flash → Flash-Lite 자동 전환.
- **TASK-030 (4/29)**: 키워드 오버홀 — type 제거, 80자 본문 분리, 시간 표시, 태그 chip 바, 필터, + 추가, 길게 눌러 삭제.
- **TASK-039a (4/29)**: 주제 화면 `+` 버튼으로 수동 주제 생성 (전체 키워드 + 이력 기반).
- 직접 수정: 키워드 등록 시 자동 주제 생성 제거 (KeywordViewModel), 주제 목록 날짜 필터 제거 (TopicRepository.getTopics), 그림 우선순위 프롬프트 (사진 1순위/mermaid 보조).

## 선언/해제 규칙

1. 공유 모듈 수정 전 위 "현재 진행 중"에 한 줄 추가.
2. Task Brief는 `docs/tasks/active/TASK-YYYYMMDD-NNN-*.md` 신규 파일.
3. implementer 결과는 `docs/task-results/TASK-YYYYMMDD-NNN-implementer-result.md`.
4. 완료 시 위 "완료 마일스톤" 한 줄 추가, "현재 진행 중"에서 제거.
