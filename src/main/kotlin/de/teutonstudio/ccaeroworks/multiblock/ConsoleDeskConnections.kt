package de.teutonstudio.ccaeroworks.multiblock

import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksTypes
import de.teutonstudio.ccaeroworks.train.TrainControlConsoleGeometry
import de.teutonstudio.ccaeroworks.train.TrainControlConsoleIntegration
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.BooleanProperty

object ConsoleDeskConnections {
    @JvmStatic
    fun apply(level: LevelAccessor, pos: BlockPos, state: BlockState): BlockState {
        if (!AeroworksTypes.isControlDesk(state.block) ||
            !state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
        ) {
            return state
        }

        val facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING)
        val world = level as? Level
        val connectedWest = world?.let {
            ConsoleAssemblyRuntime.connected(it, pos, pos.relative(facing.counterClockWise))
        } ?: false
        val connectedEast = world?.let {
            ConsoleAssemblyRuntime.connected(it, pos, pos.relative(facing.clockWise))
        } ?: false

        return setBooleanProperty(
            setBooleanProperty(state, "open_west", connectedWest),
            "open_east",
            connectedEast
        )
    }

    private fun booleanPropertyValue(state: BlockState, name: String): Boolean? {
        val property = state.properties
            .filterIsInstance<BooleanProperty>()
            .firstOrNull { it.name == name }
            ?: return null
        return state.getValue(property)
    }

    private fun setBooleanProperty(
        state: BlockState,
        name: String,
        value: Boolean
    ): BlockState {
        val property = state.properties
            .filterIsInstance<BooleanProperty>()
            .firstOrNull { it.name == name }
            ?: return state
        return if (state.getValue(property) == value) state else state.setValue(property, value)
    }
}
