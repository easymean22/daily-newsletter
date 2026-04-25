package com.dailynewsletter.data.tag

/**
 * 태그 정규화 및 "자유주제" invariant 강제용 유틸.
 *
 * 관련:
 * - ADR-0003 §4 정규화 정책 (trim + lowercase + 내부 공백 단일화 → 비교 키)
 * - ADR-0003 §3 invariant ("모든 Topics 레코드는 항상 자유주제 태그를 포함")
 * - docs/plans/tag-system.md §"자유주제 자동 보충 로직"
 *
 * 주의: 저장 표시 형태는 사용자 입력의 trim() 결과(원형 보존). 비교는 항상 [normalize]로.
 */
object TagNormalizer {

    /**
     * 시스템 시드 태그 — invariant의 안전망.
     */
    const val FREE_TOPIC_TAG: String = "자유주제"

    private val WHITESPACE_REGEX = Regex("\\s+")

    /**
     * 비교 키 생성: trim + lowercase + 내부 공백 단일화.
     *
     * 한글은 lowercase의 영향이 없다.
     * Notion multi_select의 옵션 이름 중복 판정/매칭 비교는 모두 이 함수를 경유.
     */
    fun normalize(input: String): String =
        input.trim()
            .lowercase()
            .replace(WHITESPACE_REGEX, " ")

    /**
     * Topics 전용 invariant 강제.
     *
     * 동작:
     * 1. 각 원소에 trim()만 적용 (저장 표시 형태는 원형 보존).
     * 2. trim 후 빈 문자열은 제거.
     * 3. 정규화 키 기준으로 [FREE_TOPIC_TAG]가 이미 포함돼 있으면 그대로 반환.
     * 4. 그렇지 않으면 목록 끝에 [FREE_TOPIC_TAG]를 append.
     *
     * 적용 지점: `TopicRepository.saveTopic(...)` 단일 게이트웨이.
     * Keywords/Newsletters에는 적용하지 않는다.
     */
    fun ensureFreeTopicTag(input: List<String>): List<String> {
        val trimmed = input.map { it.trim() }.filter { it.isNotEmpty() }
        val freeKey = normalize(FREE_TOPIC_TAG)
        val hasFree = trimmed.any { normalize(it) == freeKey }
        return if (hasFree) trimmed else trimmed + FREE_TOPIC_TAG
    }
}
