package io.github.mazilin.nautile.ui.screen.todo

import io.github.mazilin.nautile.domain.model.GtdPosition

/** GTD 清单显示名（基线术语：收件箱、下一步行动、等待中、将来／也许、已结束）。 */
val GtdPosition.title: String
    get() = when (this) {
        GtdPosition.INBOX -> "收件箱"
        GtdPosition.NEXT_ACTION -> "下一步行动"
        GtdPosition.WAITING -> "等待中"
        GtdPosition.SOMEDAY -> "将来／也许"
        GtdPosition.ENDED -> "已结束"
    }
