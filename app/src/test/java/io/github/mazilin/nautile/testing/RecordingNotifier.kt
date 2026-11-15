package io.github.mazilin.nautile.testing

import io.github.mazilin.nautile.domain.port.Notifier

/** 记录通知调用的测试替身。 */
class RecordingNotifier : Notifier {

    data class Recorded(val id: String, val title: String, val body: String)

    val records = mutableListOf<Recorded>()

    override fun notify(id: String, title: String, body: String) {
        records += Recorded(id, title, body)
    }
}
