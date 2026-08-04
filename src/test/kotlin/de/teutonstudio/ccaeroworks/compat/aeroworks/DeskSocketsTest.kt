package de.teutonstudio.ccaeroworks.compat.aeroworks

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DeskSocketsTest {
    @Test fun `maps named Aeroworks sockets in stable order`() {
        assertEquals("left", DeskSockets.name(0))
        assertEquals("right", DeskSockets.name(1))
        assertEquals("big", DeskSockets.name(2))
        assertEquals(0, DeskSockets.index("LEFT"))
        assertEquals(2, DeskSockets.index("big"))
    }

    @Test fun `keeps unknown sockets observable without accepting invented names`() {
        assertEquals("socket_5", DeskSockets.name(5))
        assertNull(DeskSockets.index("socket_5"))
        assertNull(DeskSockets.index("center"))
    }

    @Test fun `describes only sockets exposed by the desk`() {
        assertEquals(
            listOf(
                mapOf("name" to "left", "index" to 0),
                mapOf("name" to "right", "index" to 1)
            ),
            DeskSockets.entries(2)
        )
        assertEquals(emptyList<Map<String, Any>>(), DeskSockets.entries(0))
    }
}
