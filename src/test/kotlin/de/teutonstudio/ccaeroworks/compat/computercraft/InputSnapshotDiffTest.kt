package de.teutonstudio.ccaeroworks.compat.computercraft

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InputSnapshotDiffTest {
    @Test fun `reports only changed and newly visible channel values`() {
        val previous = mapOf(
            0 to mapOf("lever" to 2),
            1 to mapOf("x" to 4, "y" to -1)
        )
        val current = mapOf(
            0 to mapOf("lever" to 2),
            1 to mapOf("x" to 5, "y" to -1),
            2 to mapOf("red" to 3)
        )

        assertEquals(
            listOf(
                DeskInputChange(1, "x", 5),
                DeskInputChange(2, "red", 3)
            ),
            InputSnapshotDiff.changed(previous, current)
        )
    }

    @Test fun `produces deterministic socket and channel order`() {
        val current = linkedMapOf(
            2 to linkedMapOf("z" to 1, "a" to 2),
            0 to linkedMapOf("lever" to 3)
        )

        assertEquals(
            listOf(
                DeskInputChange(0, "lever", 3),
                DeskInputChange(2, "a", 2),
                DeskInputChange(2, "z", 1)
            ),
            InputSnapshotDiff.changed(emptyMap(), current)
        )
    }

    @Test fun `does not emit unchanged snapshots`() {
        val snapshot = mapOf(0 to mapOf("lever" to 7))
        assertEquals(emptyList<DeskInputChange>(), InputSnapshotDiff.changed(snapshot, snapshot))
    }
}
