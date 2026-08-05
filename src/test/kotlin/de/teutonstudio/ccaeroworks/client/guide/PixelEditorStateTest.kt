package de.teutonstudio.ccaeroworks.client.guide

import de.teutonstudio.ccaeroworks.display.DeskDisplayType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PixelEditorStateTest {
    @Test
    fun `keeps separate drafts for both display sizes`() {
        val state = PixelEditorState()
        state.setPixel(0, 0, true)

        state.selectDisplayType(DeskDisplayType.THREE_DIGIT)
        assertEquals(11, state.width)
        assertFalse(state.pixels.get(0, 0))
        state.setPixel(10, 4, true)

        state.selectDisplayType(DeskDisplayType.TWO_DIGIT)
        assertEquals(7, state.width)
        assertTrue(state.pixels.get(0, 0))

        state.selectDisplayType(DeskDisplayType.THREE_DIGIT)
        assertTrue(state.pixels.get(10, 4))
    }

    @Test
    fun `large display only accepts the big socket`() {
        val state = PixelEditorState()
        state.selectSocket("right")
        state.selectDisplayType(DeskDisplayType.THREE_DIGIT)

        assertEquals("big", state.socketName)
        assertEquals(listOf("big"), state.availableSockets())
        assertThrows(IllegalArgumentException::class.java) { state.selectSocket("left") }
    }

    @Test
    fun `clear fill and invert update the complete grid`() {
        val state = PixelEditorState()

        state.fill()
        assertTrue(state.pixels.rows().all { row -> row.all { it == '1' } })

        state.invert()
        assertTrue(state.pixels.rows().all { row -> row.all { it == '0' } })

        state.setPixel(3, 2, true)
        state.clear()
        assertTrue(state.pixels.rows().all { row -> row.all { it == '0' } })
        assertEquals(null, state.lastEditedPixel)
    }
}
