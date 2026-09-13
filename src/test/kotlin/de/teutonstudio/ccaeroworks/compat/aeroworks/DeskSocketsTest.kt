package de.teutonstudio.ccaeroworks.compat.aeroworks

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DeskSocketsTest {
    @Test
    fun `additional Aeroworks sockets use stable generated names`() {
        assertEquals("socket_3", DeskSockets.name(3))
        assertEquals(3, DeskSockets.index("socket_3"))
        assertEquals(12, DeskSockets.index("SOCKET_12"))
        assertNull(DeskSockets.index("socket_2"))
        assertNull(DeskSockets.index("socket_-1"))
    }
    @Test fun `maps named Aeroworks sockets in stable order`() {
        assertEquals("left", DeskSockets.name(0))
        assertEquals("right", DeskSockets.name(1))
        assertEquals("big", DeskSockets.name(2))
        assertEquals(0, DeskSockets.index("LEFT"))
        assertEquals(2, DeskSockets.index("big"))
    }

    @Test fun `keeps additional sockets observable and addressable`() {
        assertEquals("socket_5", DeskSockets.name(5))
        assertEquals(5, DeskSockets.index("socket_5"))
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
