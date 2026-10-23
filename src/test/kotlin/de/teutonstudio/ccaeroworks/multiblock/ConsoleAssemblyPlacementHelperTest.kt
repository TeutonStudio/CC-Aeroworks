package de.teutonstudio.ccaeroworks.multiblock

import net.minecraft.core.Direction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ConsoleAssemblyPlacementHelperTest {
    @Test
    fun `only left and right faces extend a row`() {
        Direction.Plane.HORIZONTAL.forEach { facing ->
            val left = facing.counterClockWise
            val right = facing.clockWise

            assertEquals(left, ConsoleAssemblyPlacementHelper.lateralDirection(facing, left))
            assertEquals(right, ConsoleAssemblyPlacementHelper.lateralDirection(facing, right))
            assertNull(ConsoleAssemblyPlacementHelper.lateralDirection(facing, facing))
            assertNull(ConsoleAssemblyPlacementHelper.lateralDirection(facing, facing.opposite))
            assertNull(ConsoleAssemblyPlacementHelper.lateralDirection(facing, Direction.UP))
            assertNull(ConsoleAssemblyPlacementHelper.lateralDirection(facing, Direction.DOWN))
        }
    }
}
