package de.teutonstudio.ccaeroworks.network

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

class PlayerSessionStateTest {
    @Test
    fun `logout cleanup removes every state owned by the player`() {
        val loggedOut = UUID.randomUUID()
        val otherPlayer = UUID.randomUUID()
        val state = PlayerSessionState<Key, String>(Key::player, ttlTicks = 40)
        val pointer = Key(loggedOut, "pointer")
        val draw = Key(loggedOut, "draw")
        val other = Key(otherPlayer, "draw")
        state.put(pointer, "pointer-state", tick = 10)
        state.put(draw, "draw-state", tick = 10)
        state.put(other, "other-state", tick = 10)

        state.removePlayer(loggedOut)

        assertNull(state.get(pointer))
        assertNull(state.get(draw))
        assertEquals("other-state", state.get(other))
    }

    @Test
    fun `dimension cleanup uses the same player removal path`() {
        val player = UUID.randomUUID()
        val state = PlayerSessionState<Key, String>(Key::player, ttlTicks = 40)
        val session = Key(player, "ui-session")
        state.put(session, "overworld desk", tick = 10)

        state.removePlayer(player)

        assertNull(state.get(session))
    }

    @Test
    fun `aborted state expires while refreshed state remains`() {
        val player = UUID.randomUUID()
        val state = PlayerSessionState<Key, String>(Key::player, ttlTicks = 40)
        val abandoned = Key(player, "abandoned-draw")
        val active = Key(player, "active-draw")
        state.put(abandoned, "stale", tick = 10)
        state.put(active, "fresh", tick = 10)
        state.touch(active, tick = 50)

        state.expire(tick = 51)

        assertNull(state.get(abandoned))
        assertEquals("fresh", state.get(active))
    }

    @Test
    fun `terminal draw cleanup removes only the completed gesture`() {
        val player = UUID.randomUUID()
        val state = PlayerSessionState<Key, String>(Key::player, ttlTicks = 40)
        val completed = Key(player, "gesture-1")
        val continuing = Key(player, "gesture-2")
        state.put(completed, "end", tick = 10)
        state.put(continuing, "sample", tick = 10)

        state.remove(completed)

        assertNull(state.get(completed))
        assertEquals("sample", state.get(continuing))
    }

    private data class Key(val player: UUID, val id: String)
}
