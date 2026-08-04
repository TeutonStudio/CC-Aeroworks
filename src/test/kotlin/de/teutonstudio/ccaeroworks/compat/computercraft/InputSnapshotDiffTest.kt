package de.teutonstudio.ccaeroworks.compat.computercraft

import de.teutonstudio.ccaeroworks.compat.aeroworks.DeskInputSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InputSnapshotDiffTest {
    @Test
    fun `reports changed new and removed channel values`() {
        val previous = mapOf(
            0 to DeskInputSnapshot("aeroworks:lever", mapOf("lever" to 2)),
            1 to DeskInputSnapshot("aeroworks:joystick", mapOf("x" to 4, "y" to -1))
        )
        val current = mapOf(
            0 to DeskInputSnapshot("aeroworks:lever", mapOf("lever" to 2)),
            1 to DeskInputSnapshot("aeroworks:joystick", mapOf("x" to 5)),
            2 to DeskInputSnapshot("aeroworks:button", mapOf("red" to 3))
        )

        assertEquals(
            listOf(
                DeskInputChange(1, "x", "aeroworks:joystick", 5),
                DeskInputChange(1, "y", "aeroworks:joystick", null),
                DeskInputChange(2, "red", "aeroworks:button", 3)
            ),
            InputSnapshotDiff.changed(previous, current)
        )
    }

    @Test
    fun `reports module replacement even when a channel value is unchanged`() {
        val previous = mapOf(
            0 to DeskInputSnapshot("aeroworks:first", mapOf("value" to 7))
        )
        val current = mapOf(
            0 to DeskInputSnapshot("aeroworks:second", mapOf("value" to 7))
        )

        assertEquals(
            listOf(DeskInputChange(0, "value", "aeroworks:second", 7)),
            InputSnapshotDiff.changed(previous, current)
        )
    }

    @Test
    fun `produces deterministic socket and channel order`() {
        val current = linkedMapOf(
            2 to DeskInputSnapshot("aeroworks:multi", linkedMapOf("z" to 1, "a" to 2)),
            0 to DeskInputSnapshot("aeroworks:lever", mapOf("lever" to 3))
        )

        assertEquals(
            listOf(
                DeskInputChange(0, "lever", "aeroworks:lever", 3),
                DeskInputChange(2, "a", "aeroworks:multi", 2),
                DeskInputChange(2, "z", "aeroworks:multi", 1)
            ),
            InputSnapshotDiff.changed(emptyMap(), current)
        )
    }

    @Test
    fun `does not emit unchanged snapshots`() {
        val snapshot = mapOf(
            0 to DeskInputSnapshot("aeroworks:lever", mapOf("lever" to 7))
        )
        assertEquals(emptyList<DeskInputChange>(), InputSnapshotDiff.changed(snapshot, snapshot))
    }
}
