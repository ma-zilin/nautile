package io.github.mazilin.nautile.testing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InMemorySecretStoreTest {

    @Test
    fun put_get_delete_roundtrip() {
        val store = InMemorySecretStore()

        store.put("api", "value")
        assertEquals("value", store.get("api"))

        store.delete("api")
        assertNull(store.get("api"))
    }
}
