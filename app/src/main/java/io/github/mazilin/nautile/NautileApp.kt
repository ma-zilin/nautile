package io.github.mazilin.nautile

import android.app.Application
import io.github.mazilin.nautile.di.AppContainer

/**
 * Application 只负责初始化手工装配容器；不承载业务逻辑。
 */
class NautileApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
