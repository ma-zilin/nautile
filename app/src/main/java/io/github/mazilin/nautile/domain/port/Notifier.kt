package io.github.mazilin.nautile.domain.port

/** 通知端口；实现放 platform/notification。 */
interface Notifier {
    fun notify(id: String, title: String, body: String)
}
