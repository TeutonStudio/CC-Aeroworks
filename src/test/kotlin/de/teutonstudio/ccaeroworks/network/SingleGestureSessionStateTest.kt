package de.teutonstudio.ccaeroworks.network

import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SingleGestureSessionStateTest {
    @Test
    fun `display invalidation rejects old continuation without losing other displays`() {
        val player = UUID.randomUUID()
        val sessions = sessions()
        val changed = Key(player, "display-a")
        val unchanged = Key(player, "display-b")
        sessions.start(changed, 10, "a", 1)
        sessions.start(unchanged, 11, "b", 1)
        sessions.removeIf { it == changed }
        assertEquals(SingleGestureSessionState.AdvanceStatus.MISSING_START,
            sessions.advance(changed, 10, 1, 2) { it }.status)
        assertEquals(SingleGestureSessionState.AdvanceStatus.ACCEPTED,
            sessions.advance(unchanged, 11, 1, 2) { it }.status)
    }

    @Test
    fun `duplicate start for the same gesture is rejected`() {
        val player = UUID.randomUUID()
        val sessions = sessions()
        val key = Key(player, "display-a")

        assertTrue(sessions.start(key, gestureId = 10, value = "start", tick = 1))
        assertFalse(sessions.start(key, gestureId = 10, value = "duplicate", tick = 1))
        assertEquals(1, sessions.size())
    }

    @Test
    fun `new gesture replaces an abandoned gesture in the same slot`() {
        val player = UUID.randomUUID()
        val sessions = sessions()
        val key = Key(player, "display-a")
        sessions.start(key, gestureId = 10, value = "old", tick = 1)

        assertTrue(sessions.start(key, gestureId = 11, value = "new", tick = 2))
        assertEquals(1, sessions.size())

        val stale = sessions.advance(key, gestureId = 10, sequence = 1, tick = 3) { "wrong" }
        assertEquals(SingleGestureSessionState.AdvanceStatus.STALE_GESTURE, stale.status)

        val accepted = sessions.advance(key, gestureId = 11, sequence = 1, tick = 3) { "$it-next" }
        assertEquals(SingleGestureSessionState.AdvanceStatus.ACCEPTED, accepted.status)
        assertEquals("new", accepted.previous)
    }

    @Test
    fun `missing and skipped sequences are rejected without advancing state`() {
        val player = UUID.randomUUID()
        val sessions = sessions()
        val key = Key(player, "display-a")

        val missing = sessions.advance(key, gestureId = 20, sequence = 1, tick = 1) { it }
        assertEquals(SingleGestureSessionState.AdvanceStatus.MISSING_START, missing.status)

        sessions.start(key, gestureId = 20, value = "start", tick = 2)
        val skipped = sessions.advance(key, gestureId = 20, sequence = 2, tick = 3) { "$it-skipped" }
        assertEquals(SingleGestureSessionState.AdvanceStatus.OUT_OF_SEQUENCE, skipped.status)
        assertEquals(1, skipped.expectedSequence)

        val accepted = sessions.advance(key, gestureId = 20, sequence = 1, tick = 4) { "$it-one" }
        assertEquals(SingleGestureSessionState.AdvanceStatus.ACCEPTED, accepted.status)
        assertEquals("start", accepted.previous)
    }

    @Test
    fun `slots and players remain independent and lifecycle cleanup works`() {
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        val sessions = sessions()
        val firstA = Key(first, "display-a")
        val firstB = Key(first, "display-b")
        val secondA = Key(second, "display-a")
        sessions.start(firstA, 1, "a", 1)
        sessions.start(firstB, 2, "b", 1)
        sessions.start(secondA, 3, "c", 1)

        sessions.removePlayer(first)

        assertEquals(1, sessions.size())
        assertNull(sessions.remove(firstA))
        assertEquals("c", sessions.remove(secondA))
    }

    private fun sessions() = SingleGestureSessionState<Key, String>(Key::player, ttlTicks = 40)

    private data class Key(val player: UUID, val slot: String)
}
