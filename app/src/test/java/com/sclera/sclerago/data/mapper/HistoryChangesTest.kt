package com.sclera.sclerago.data.mapper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryChangesTest {
    @Test
    fun explicitTypeTakesPrecedenceAndOlderEntriesUseFieldId() {
        val changes = fieldChanges(
            """[{"field":"Installed","fieldId":"a","type":"date","from":"1795746600","to":"1795833000000"},{"field":"Expiry","fieldId":"b","from":null,"to":"2026-11-27"},{"field":"Name","to":"123"}]""",
            mapOf("a" to "text", "b" to "date"),
        )
        assertEquals("date", changes[0].type)
        assertEquals("date", changes[1].type)
        assertNull(changes[2].type)
        assertNull(changes[1].from)
    }
}
