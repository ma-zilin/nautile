package io.github.mazilin.nautile.testing

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * 把 `Dispatchers.Main` 替换为立即执行的调度器，供 `viewModelScope` 在 JVM 单测中运行。
 *
 * 用 `Dispatchers.Unconfined` 而非测试调度器：`viewModelScope` 使用 `Main.immediate`，
 * 只有 Unconfined 才能在测试线程同步执行，避免断言时任务尚未被调度器推进。
 * 不改变生产代码。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
