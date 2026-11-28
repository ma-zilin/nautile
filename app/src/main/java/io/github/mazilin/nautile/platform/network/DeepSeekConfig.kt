package io.github.mazilin.nautile.platform.network

/** DeepSeek 端点、模型与超时常量；模型名不在界面展示。 */
object DeepSeekConfig {

    const val ENDPOINT = "https://api.deepseek.com/chat/completions"
    const val MODEL = "deepseek-chat"

    /** 连接超时（架构 §5.4 建议默认值，弱网手感需真机核对）。 */
    const val CONNECT_TIMEOUT_MS = 15_000

    /** 单次读取超时。 */
    const val READ_TIMEOUT_MS = 60_000

    /** 整体流超时。 */
    const val OVERALL_TIMEOUT_MS = 120_000
}
