package de.teutonstudio.ccaeroworks.train

import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TrainControlMountsTest {
    @Test
    fun `recognizes only the train control module in captured console data`() {
        assertTrue(TrainControlMounts.hasTrainControls(consoleTag("cc_aeroworks:train_controls")))
        assertFalse(TrainControlMounts.hasTrainControls(consoleTag("aeroworks:throttle_quadrant")))
        assertFalse(TrainControlMounts.hasTrainControls(CompoundTag()))
    }

    @Test
    fun `recognizes a train controller mounted as a module head`() {
        val parent = CompoundTag().apply {
            putString("Type", "aeroworks:yoke")
            put("Heads", CompoundTag().apply {
                put("handle", CompoundTag().apply { putString("Type", "cc_aeroworks:train_controls") })
            })
        }
        val root = CompoundTag().apply { put("Modules", ListTag().apply { add(parent) }) }

        assertTrue(TrainControlMounts.hasTrainControls(root))
    }

    @Test
    fun `classifies console direction using Create train semantics`() {
        assertEquals(
            TrainControlMounts.Alignment.FORWARD,
            TrainControlMounts.alignment(Direction.NORTH, Direction.NORTH)
        )
        assertEquals(
            TrainControlMounts.Alignment.BACKWARD,
            TrainControlMounts.alignment(Direction.NORTH, Direction.SOUTH)
        )
        assertEquals(
            TrainControlMounts.Alignment.SIDEWAYS,
            TrainControlMounts.alignment(Direction.NORTH, Direction.EAST)
        )
    }

    private fun consoleTag(moduleId: String): CompoundTag {
        val module = CompoundTag().apply { putString("Type", moduleId) }
        return CompoundTag().apply { put("Modules", ListTag().apply { add(module) }) }
    }
}
