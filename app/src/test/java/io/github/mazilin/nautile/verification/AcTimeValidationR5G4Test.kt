package io.github.mazilin.nautile.verification

import io.github.mazilin.nautile.domain.rule.StrictTimeParseResult
import io.github.mazilin.nautile.domain.rule.TaskTimeRules
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R5-G4 独立验收（JVM 单测）：严格 24 小时制 `HH:mm` 时间校验（00–23 / 00–59）。
 *
 * 验收依据：
 * - `docs/需求基线.md` v2.16（FR-D-02、FR-M-01、AC-D-07、AC-M-01、BR-70）
 * - `.plans/设计/03_交互与动效规格.md`（§2.5.3、§4 G4）
 *
 * 覆盖范围：
 * - 合法边界值（`00:00`、`09:05`、`12:00`、`23:59`、含首尾空白）；
 * - 空输入与纯空白返回 `Empty`；
 * - 全部非法/越界/非 24h 格式返回 `Invalid` 且携带明确中文报错，绝不静默修正。
 */
class AcTimeValidationR5G4Test {

    @Test
    fun AC_D_07_FR_D_02_parseStrict24hTime_accepts_valid_boundaries_and_trims_whitespace() {
        val validCases = listOf(
            "00:00" to LocalTime.of(0, 0),
            "09:05" to LocalTime.of(9, 5),
            "12:00" to LocalTime.of(12, 0),
            "18:45" to LocalTime.of(18, 45),
            "23:59" to LocalTime.of(23, 59),
            " 00:00 " to LocalTime.of(0, 0),
            "\t09:05\n" to LocalTime.of(9, 5),
            "  23:59  " to LocalTime.of(23, 59),
        )

        validCases.forEach { (raw, expected) ->
            val result = TaskTimeRules.parseStrict24hTime(raw)
            assertEquals("合法 24h 时间 \"$raw\" 应解析为 $expected", StrictTimeParseResult.Valid(expected), result)
            val parsed = (result as StrictTimeParseResult.Valid).time
            assertEquals("格式化回 24h 文本应一致", raw.trim(), TaskTimeRules.format24hTime(parsed))
        }
    }

    @Test
    fun AC_D_07_AC_M_01_parseStrict24hTime_returns_empty_for_blank_inputs() {
        val blankCases = listOf("", " ", "   ", "\t\n")
        blankCases.forEach { raw ->
            assertEquals(
                "空白输入 \"$raw\" 应返回 Empty",
                StrictTimeParseResult.Empty,
                TaskTimeRules.parseStrict24hTime(raw),
            )
        }
    }

    @Test
    fun AC_D_07_AC_M_01_parseStrict24hTime_rejects_out_of_range_and_malformed_without_silent_fix() {
        val invalidCases = listOf(
            "24:00",
            "25:10",
            "12:60",
            "9:30",
            "09:5",
            "09:30 AM",
            "12:30 PM",
            "ab:cd",
            "1230",
            "-01:00",
            "12:30:00",
            "09：30", // 全角冒号
            "00:99",
            "99:00",
            " 24:00 ",
            "23:59:59",
            "+09:30",
        )

        invalidCases.forEach { raw ->
            val result = TaskTimeRules.parseStrict24hTime(raw)
            assertTrue(
                "非法时间 \"$raw\" 必须返回 Invalid，绝不得静默修正，实际：$result",
                result is StrictTimeParseResult.Invalid,
            )
            val invalid = result as StrictTimeParseResult.Invalid
            assertEquals(
                "非法时间 \"$raw\" 应返回统一中文错误文案",
                TaskTimeRules.INVALID_TIME_MESSAGE,
                invalid.message,
            )
            assertTrue("错误文案不得为空", invalid.message.isNotBlank())
        }
    }

    @Test
    fun AC_M_01_FR_M_01_parseStrict24hTime_exhaustively_verifies_all_24x60_valid_pairs_and_neighbors() {
        for (hour in 0..23) {
            for (minute in 0..59) {
                val formatted = "%02d:%02d".format(hour, minute)
                assertEquals(
                    StrictTimeParseResult.Valid(LocalTime.of(hour, minute)),
                    TaskTimeRules.parseStrict24hTime(formatted),
                )
            }
        }
        // 小时与分钟紧邻上界均必须拒绝。
        for (minute in 0..59) {
            val raw = "24:%02d".format(minute)
            assertTrue(
                "小时 24 越界 ($raw) 必须被拒绝",
                TaskTimeRules.parseStrict24hTime(raw) is StrictTimeParseResult.Invalid,
            )
        }
        for (hour in 0..23) {
            val raw = "%02d:60".format(hour)
            assertTrue(
                "分钟 60 越界 ($raw) 必须被拒绝",
                TaskTimeRules.parseStrict24hTime(raw) is StrictTimeParseResult.Invalid,
            )
        }
    }
}
