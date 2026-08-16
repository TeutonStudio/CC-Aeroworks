package de.teutonstudio.ccaeroworks.display.reactive

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReactiveDisplayFrameTest {
    @Test
    fun storesPixelsAcrossTileBoundariesWithoutDenseRaster() {
        val builder = ReactiveDisplayFrameBuilder(ReactiveDisplaySnapshot.blank(130, 70))
        builder.setPixel(63, 0, true)
        builder.setPixel(64, 0, true)
        builder.setPixel(129, 69, true)

        val (frame, patch) = builder.build(1)
        assertTrue(frame.get(63, 0))
        assertTrue(frame.get(64, 0))
        assertTrue(frame.get(129, 69))
        assertFalse(frame.get(62, 0))
        assertEquals(3, frame.nonEmptyTileCount())
        assertEquals(3, patch?.tiles?.size)
    }

    @Test
    fun noOpWritesDoNotProducePatch() {
        val initialBuilder = ReactiveDisplayFrameBuilder(ReactiveDisplaySnapshot.blank(128, 64))
        initialBuilder.fillRect(1, 1, 10, 4, true)
        val (initial, _) = initialBuilder.build(1)

        val noOp = ReactiveDisplayFrameBuilder(initial)
        noOp.fillRect(1, 1, 10, 4, true)
        val (result, patch) = noOp.build(2)

        assertNull(patch)
        assertEquals(initial.revision, result.revision)
    }

    @Test
    fun clearUsesFullReplacementButDoesNotRemoveReactiveOwnership() {
        val initialBuilder = ReactiveDisplayFrameBuilder(ReactiveDisplaySnapshot.blank(192, 64))
        initialBuilder.fillRect(0, 0, 192, 64, true)
        val (initial, _) = initialBuilder.build(1)

        val nextBuilder = ReactiveDisplayFrameBuilder(initial)
        nextBuilder.clear()
        val (next, patch) = nextBuilder.build(2)

        assertNotNull(patch)
        assertTrue(patch!!.full)
        assertFalse(patch.remove)
        assertEquals(0, patch.tiles.size)
        assertEquals(0, next.nonEmptyTileCount())
    }

    @Test
    fun clearAndRedrawKeepsOnlyNewTiles() {
        val initialBuilder = ReactiveDisplayFrameBuilder(ReactiveDisplaySnapshot.blank(192, 64))
        initialBuilder.fillRect(0, 0, 192, 64, true)
        val (initial, _) = initialBuilder.build(1)

        val nextBuilder = ReactiveDisplayFrameBuilder(initial)
        nextBuilder.clear()
        nextBuilder.fillRect(70, 10, 3, 3, true)
        val (next, patch) = nextBuilder.build(2)

        assertNotNull(patch)
        assertTrue(patch!!.full)
        assertEquals(1, next.nonEmptyTileCount())
        assertTrue(next.get(70, 10))
        assertFalse(next.get(2, 2))
    }

    @Test
    fun snapshotRoundTripsThroughNbt() {
        val builder = ReactiveDisplayFrameBuilder(ReactiveDisplaySnapshot.blank(256, 128))
        builder.setPixel(5, 6, true)
        builder.setPixel(200, 100, true)
        val (frame, _) = builder.build(42)

        val decoded = ReactiveDisplaySnapshot.fromTag(frame.toTag())
        assertNotNull(decoded)
        assertEquals(42, decoded!!.revision)
        assertEquals(256, decoded.width)
        assertEquals(128, decoded.height)
        assertTrue(decoded.get(5, 6))
        assertTrue(decoded.get(200, 100))
    }
}
