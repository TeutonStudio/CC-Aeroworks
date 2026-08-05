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
        assertEquals(DeskDisplayType.THREE_DIGIT.pixelWidth, state.width)
        assertEquals(DeskDisplayType.THREE_DIGIT.pixelHeight, state.height)
        assertFalse(state.pixels.get(0, 0))
        state.setPixel(state.width - 1, state.height - 1, true)

        state.selectDisplayType(DeskDisplayType.TWO_DIGIT)
        assertEquals(DeskDisplayType.TWO_DIGIT.pixelWidth, state.width)
        assertEquals(DeskDisplayType.TWO_DIGIT.pixelHeight, state.height)
        assertTrue(state.pixels.get(0, 0))

        state.selectDisplayType(DeskDisplayType.THREE_DIGIT)
        assertTrue(state.pixels.get(state.width - 1, state.height - 1))
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

        state.setPixel(state.width / 2, state.height / 2, true)
        state.clear()
        assertTrue(state.pixels.rows().all { row -> row.all { it == '0' } })
        assertEquals(null, state.lastEditedPixel)
    }
}
