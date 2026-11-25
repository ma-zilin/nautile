package io.github.mazilin.nautile.domain.model

/**
 * 待办列表分类方式（G9、CMP-39、FR-V-03）。
 *
 * 本轮只提供项目、优先级、截止日期三种；标签分类后置，当前 `Task` 也没有标签字段。
 */
enum class ViewClassification {
    PROJECT,
    PRIORITY,
    DUE,
}

/**
 * 待办列表组内排序方式（G9、CMP-40）。
 *
 * [MANUAL] 依赖 `Task.manualOrder`，是唯一会写库的排序。
 */
enum class ViewSort {
    DUE,
    PRIORITY,
    CREATED,
    MANUAL,
}

/**
 * 单个 GTD 模块的视图选项（CMP-41）。
 *
 * 编码为稳定文本 `classification=<项目|优先级|截止>;sort=<截止|优先级|创建|手动>`，
 * 与 `UserSettings.perModuleViewOptions` 的“按模块记忆”一一对应；解析失败、缺项或未知值
 * 一律回落到默认（分类=项目、排序=手动），不崩溃、不影响其他模块。
 */
data class ViewOptions(
    val classification: ViewClassification = ViewClassification.PROJECT,
    val sort: ViewSort = ViewSort.MANUAL,
) {

    /** 稳定文本编码：键顺序固定，值用小写记号（不随枚举名或语言变化）。 */
    fun encode(): String =
        "classification=${classification.token()};sort=${sort.token()}"

    companion object {

        /** 未保存过选项时的默认值：分类=项目、排序=手动（与 FR-V-01 默认按项目分组一致）。 */
        val Default = ViewOptions()

        /** 读取某模块的已保存选项；缺失或无法解析时返回 [Default]。 */
        fun fromSettings(
            perModuleViewOptions: Map<GtdPosition, String>,
            position: GtdPosition,
        ): ViewOptions = decode(perModuleViewOptions[position])

        /**
         * 解析稳定文本；逐项容错：缺项与未知值各自回落默认，其他键忽略。
         * 空串、null 与整体格式损坏都返回 [Default]。
         */
        fun decode(text: String?): ViewOptions {
            if (text.isNullOrBlank()) return Default
            var classification = Default.classification
            var sort = Default.sort
            text.split(';').forEach { part ->
                val separator = part.indexOf('=')
                if (separator <= 0) return@forEach
                val key = part.substring(0, separator).trim().lowercase()
                val value = part.substring(separator + 1).trim().lowercase()
                when (key) {
                    "classification" -> classification = classificationFrom(value)
                    "sort" -> sort = sortFrom(value)
                }
            }
            return ViewOptions(classification = classification, sort = sort)
        }

        private fun classificationFrom(token: String): ViewClassification = when (token) {
            "priority" -> ViewClassification.PRIORITY
            "due" -> ViewClassification.DUE
            else -> ViewClassification.PROJECT
        }

        private fun sortFrom(token: String): ViewSort = when (token) {
            "due" -> ViewSort.DUE
            "priority" -> ViewSort.PRIORITY
            "created" -> ViewSort.CREATED
            else -> ViewSort.MANUAL
        }
    }
}

private fun ViewClassification.token(): String = when (this) {
    ViewClassification.PROJECT -> "project"
    ViewClassification.PRIORITY -> "priority"
    ViewClassification.DUE -> "due"
}

private fun ViewSort.token(): String = when (this) {
    ViewSort.DUE -> "due"
    ViewSort.PRIORITY -> "priority"
    ViewSort.CREATED -> "created"
    ViewSort.MANUAL -> "manual"
}
