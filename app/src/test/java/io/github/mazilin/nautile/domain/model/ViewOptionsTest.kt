package io.github.mazilin.nautile.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * G9 视图选项编解码（CMP-41、FR-V-03、AC-T-08）。
 *
 * 预期依据 `docs/需求基线.md` v2.2 与用户 2026-11-25 的 G9 口径：
 * - 每模块独立保存“分类 + 排序”，编码稳定、与语言/枚举声明顺序无关；
 * - 空值、缺项、未知值、整体损坏都回落默认（分类=项目、排序=手动），不崩溃、不串模块；
 * - 任意组合往返一致。
 */
class ViewOptionsTest {

    @Test
    fun default_is_project_classification_and_manual_sort() {
        assertEquals(ViewClassification.PROJECT, ViewOptions.Default.classification)
        assertEquals(ViewSort.MANUAL, ViewOptions.Default.sort)
    }

    @Test
    fun encode_default_is_stable_text() {
        assertEquals("classification=project;sort=manual", ViewOptions.Default.encode())
    }

    @Test
    fun encode_non_default_uses_fixed_tokens() {
        assertEquals(
            "classification=priority;sort=due",
            ViewOptions(ViewClassification.PRIORITY, ViewSort.DUE).encode(),
        )
        assertEquals(
            "classification=due;sort=created",
            ViewOptions(ViewClassification.DUE, ViewSort.CREATED).encode(),
        )
    }

    @Test
    fun decode_null_blank_and_garbage_falls_back_to_default() {
        assertEquals(ViewOptions.Default, ViewOptions.decode(null))
        assertEquals(ViewOptions.Default, ViewOptions.decode(""))
        assertEquals(ViewOptions.Default, ViewOptions.decode("   "))
        assertEquals(ViewOptions.Default, ViewOptions.decode("not-a-valid-option"))
        assertEquals(ViewOptions.Default, ViewOptions.decode("=;=;"))
    }

    @Test
    fun decode_missing_keys_keeps_default_per_key() {
        // 只出现分类：排序保持默认（手动）。
        assertEquals(
            ViewOptions(ViewClassification.DUE, ViewSort.MANUAL),
            ViewOptions.decode("classification=due"),
        )
        // 只出现排序：分类保持默认（项目）。
        assertEquals(
            ViewOptions(ViewClassification.PROJECT, ViewSort.CREATED),
            ViewOptions.decode("sort=created"),
        )
    }

    @Test
    fun decode_unknown_values_falls_back_per_key_and_ignores_other_keys() {
        assertEquals(
            ViewOptions(ViewClassification.PROJECT, ViewSort.MANUAL),
            ViewOptions.decode("classification=tags;sort=random"),
        )
        // 未知排序不影响已知分类。
        assertEquals(
            ViewOptions(ViewClassification.PRIORITY, ViewSort.MANUAL),
            ViewOptions.decode("classification=priority;sort=random;extra=1"),
        )
    }

    @Test
    fun decode_is_case_and_whitespace_tolerant_for_known_tokens() {
        assertEquals(
            ViewOptions(ViewClassification.PRIORITY, ViewSort.DUE),
            ViewOptions.decode(" Classification = Priority ; Sort = DUE "),
        )
    }

    @Test
    fun every_combination_round_trips() {
        ViewClassification.entries.forEach { classification ->
            ViewSort.entries.forEach { sort ->
                val original = ViewOptions(classification, sort)
                assertEquals(
                    "往返应保持一致：$classification/$sort",
                    original,
                    ViewOptions.decode(original.encode()),
                )
            }
        }
    }

    @Test
    fun different_modules_are_read_independently_and_missing_module_uses_default() {
        val stored = mapOf(
            GtdPosition.NEXT_ACTION to ViewOptions(ViewClassification.PRIORITY, ViewSort.CREATED).encode(),
            GtdPosition.WAITING to ViewOptions(ViewClassification.DUE, ViewSort.DUE).encode(),
        )

        assertEquals(
            ViewOptions(ViewClassification.PRIORITY, ViewSort.CREATED),
            ViewOptions.fromSettings(stored, GtdPosition.NEXT_ACTION),
        )
        assertEquals(
            ViewOptions(ViewClassification.DUE, ViewSort.DUE),
            ViewOptions.fromSettings(stored, GtdPosition.WAITING),
        )
        // 未保存过的模块回落默认，不受其他模块影响。
        assertEquals(ViewOptions.Default, ViewOptions.fromSettings(stored, GtdPosition.INBOX))
        assertNotEquals(
            ViewOptions.fromSettings(stored, GtdPosition.NEXT_ACTION),
            ViewOptions.fromSettings(stored, GtdPosition.WAITING),
        )
    }
}
