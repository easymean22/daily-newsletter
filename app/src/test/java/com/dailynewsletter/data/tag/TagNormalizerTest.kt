package com.dailynewsletter.data.tag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [TagNormalizer] 단위 테스트.
 *
 * 근거:
 * - ADR-0003 §4 정규화 정책 — trim + lowercase + 내부 공백 단일화
 * - ADR-0003 §3 invariant — Topics는 항상 `자유주제` 포함
 * - docs/plans/tag-system.md §"자유주제 자동 보충 로직"
 */
class TagNormalizerTest {

    // ----- normalize() -----

    @Test
    fun `normalize trims leading and trailing whitespace`() {
        assertEquals("it", TagNormalizer.normalize("  IT  "))
    }

    @Test
    fun `normalize lowercases ASCII letters`() {
        assertEquals("it", TagNormalizer.normalize("IT"))
        assertEquals("it", TagNormalizer.normalize("It"))
        assertEquals("it", TagNormalizer.normalize("iT"))
    }

    @Test
    fun `normalize collapses internal whitespace to single space`() {
        assertEquals("hello world", TagNormalizer.normalize("hello    world"))
        assertEquals("i t", TagNormalizer.normalize("i  t"))
        // tabs and mixed whitespace
        assertEquals("hello world", TagNormalizer.normalize("hello\t \nworld"))
    }

    @Test
    fun `normalize leaves korean characters unchanged`() {
        // 한글은 lowercase 영향 없음
        assertEquals("자유주제", TagNormalizer.normalize("자유주제"))
        assertEquals("자유주제", TagNormalizer.normalize("  자유주제  "))
        assertEquals("자유 주제", TagNormalizer.normalize("자유  주제"))
    }

    @Test
    fun `normalize treats variations of free topic tag as equivalent keys`() {
        val canonical = TagNormalizer.normalize(TagNormalizer.FREE_TOPIC_TAG)
        assertEquals(canonical, TagNormalizer.normalize("자유주제 "))
        assertEquals(canonical, TagNormalizer.normalize(" 자유주제"))
        // 다중 공백 정규화 — "자유 주제"는 다른 태그로 간주(내부에 공백이 있으므로)
        // 다만 "자유  주제" (공백 2개)와 "자유 주제" (공백 1개)는 동일 키여야 함
        assertEquals(
            TagNormalizer.normalize("자유 주제"),
            TagNormalizer.normalize("자유   주제")
        )
    }

    // ----- ensureFreeTopicTag() -----

    @Test
    fun `ensureFreeTopicTag appends free topic tag when list is empty`() {
        val result = TagNormalizer.ensureFreeTopicTag(emptyList())
        assertEquals(listOf(TagNormalizer.FREE_TOPIC_TAG), result)
    }

    @Test
    fun `ensureFreeTopicTag appends free topic when list contains only blanks`() {
        val result = TagNormalizer.ensureFreeTopicTag(listOf("", "   ", "\t"))
        // 공백만 있는 원소는 제거되고, 결과적으로 빈 리스트 → 자유주제 append
        assertEquals(listOf(TagNormalizer.FREE_TOPIC_TAG), result)
    }

    @Test
    fun `ensureFreeTopicTag preserves existing free topic tag without duplication`() {
        val result = TagNormalizer.ensureFreeTopicTag(listOf("자유주제"))
        assertEquals(listOf("자유주제"), result)
    }

    @Test
    fun `ensureFreeTopicTag recognizes free topic variants via normalized key`() {
        // 사용자가 공백 포함해서 입력해도 자유주제 포함으로 간주 (중복 append 금지)
        val result = TagNormalizer.ensureFreeTopicTag(listOf(" 자유주제 ", "IT"))
        // 결과 리스트 길이는 2 (자유주제 추가 안 됨). trim()은 적용됨.
        assertEquals(2, result.size)
        assertEquals("자유주제", result[0])
        assertEquals("IT", result[1])
    }

    @Test
    fun `ensureFreeTopicTag appends free topic when other tags exist but no free topic`() {
        val result = TagNormalizer.ensureFreeTopicTag(listOf("IT", "경제"))
        assertEquals(listOf("IT", "경제", "자유주제"), result)
    }

    @Test
    fun `ensureFreeTopicTag trims each element preserving case and order`() {
        // 저장 표시 형태는 원형(trim만). lowercase 적용 안 됨.
        val result = TagNormalizer.ensureFreeTopicTag(listOf("  IT  ", "경제 "))
        assertEquals(listOf("IT", "경제", "자유주제"), result)
    }

    @Test
    fun `ensureFreeTopicTag does not dedupe non-free-topic tags`() {
        // 정규화 기반 dedupe는 ensureFreeTopicTag의 책임이 아님 (자유주제만 관장).
        // 다른 태그 중복은 상위 레이어 책임이므로 그대로 통과하는지 확인.
        val result = TagNormalizer.ensureFreeTopicTag(listOf("IT", "it", "IT"))
        assertTrue(result.containsAll(listOf("IT", "it", "자유주제")))
        assertEquals(4, result.size) // 3개 원형 + 자유주제
    }
}
