package de.teutonstudio.ccaeroworks.multiblock

import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksTypes
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.LevelAccessor
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
        val ceiling = booleanPropertyValue(state, "ceiling")
        val connectedWest = compatible(
            level.getBlockState(pos.relative(facing.counterClockWise)),
            facing,
            ceiling
        )
        val connectedEast = compatible(
            level.getBlockState(pos.relative(facing.clockWise)),
            facing,
            ceiling
        )

        return setBooleanProperty(
            setBooleanProperty(state, "open_west", connectedWest),
            "open_east",
            connectedEast
        )
    }

    private fun compatible(
        state: BlockState,
        facing: Direction,
        ceiling: Boolean?
    ): Boolean =
        AeroworksTypes.isControlDesk(state.block) &&
            state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) &&
            state.getValue(BlockStateProperties.HORIZONTAL_FACING) == facing &&
            (ceiling == null || booleanPropertyValue(state, "ceiling") == ceiling)

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
