package de.teutonstudio.ccaeroworks.train

import com.mred231.aeroworks.content.controls.console.ConsoleBlock
import com.mred231.aeroworks.content.controls.console.ConsoleDeskBlock
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksTypes
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockSkin
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockSkinState
import de.teutonstudio.ccaeroworks.multiblock.ConsoleAssemblyAdapters
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.StringRepresentable
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

enum class TrainControlConsoleGeometry(
    private val serializedName: String
) : StringRepresentable {
    NONE("none"),
    DESK("desk"),
    STAND("stand");

    override fun getSerializedName(): String = serializedName
}

/** The two native Create controls are deliberately classified in this order.
 * ContraptionControlsBlock extends ControlsBlock, but has a different interaction and renderer. */
enum class ConsoleControlKind {
    TRAIN,
    CONTRAPTION
}

/** Shared state and geometry rules for Create's native controls inside a console row. */
object TrainControlConsoleIntegration {
    @JvmField
    val GEOMETRY: EnumProperty<TrainControlConsoleGeometry> = EnumProperty.create(
        "cc_aeroworks_geometry",
        TrainControlConsoleGeometry::class.java
    )

    /** Selects the exact Aeroworks chassis family used by an integrated Create control. */
    @JvmField
    val COPYCAT: BooleanProperty = BooleanProperty.create("cc_aeroworks_copycat")

    @JvmStatic
    fun kind(state: BlockState): ConsoleControlKind? = when (BuiltInRegistries.BLOCK.getKey(state.block)) {
        TRAIN_CONTROLS_ID -> ConsoleControlKind.TRAIN
        CONTRAPTION_CONTROLS_ID -> ConsoleControlKind.CONTRAPTION
        else -> null
    }

    @JvmStatic
    fun isConsoleControl(state: BlockState): Boolean = kind(state) != null

    @JvmStatic
    fun isTrainControls(state: BlockState): Boolean = kind(state) == ConsoleControlKind.TRAIN

    @JvmStatic
    fun isContraptionControls(state: BlockState): Boolean =
        kind(state) == ConsoleControlKind.CONTRAPTION

    @JvmStatic
    fun isIntegrated(state: BlockState): Boolean =
        isConsoleControl(state) &&
            state.hasProperty(GEOMETRY) &&
            state.getValue(GEOMETRY) != TrainControlConsoleGeometry.NONE

    @JvmStatic
    fun geometry(state: BlockState): TrainControlConsoleGeometry? = when {
        ConsoleAssemblyAdapters.profile(state) != null -> {
            if (state.hasProperty(ConsoleBlock.CEILING) && state.getValue(ConsoleBlock.CEILING)) null
            else ConsoleAssemblyAdapters.profile(state)!!.geometry
        }

        isIntegrated(state) -> state.getValue(GEOMETRY)
        else -> null
    }

    @JvmStatic
    fun integratedGeometry(state: BlockState): TrainControlConsoleGeometry? =
        if (isIntegrated(state)) state.getValue(GEOMETRY) else null

    @JvmStatic
    fun copycat(state: BlockState): Boolean =
        ConsoleAssemblyAdapters.profile(state)?.copycat
            ?: (isIntegrated(state) && state.hasProperty(COPYCAT) && state.getValue(COPYCAT))

    @JvmStatic
    fun stateForPlacement(state: BlockState?, context: BlockPlaceContext): BlockState? {
        if (state == null || !state.hasProperty(GEOMETRY)) return state
        var result = reset(state)
        if (context.replacingClickedOnBlock()) return result

        val placedPos = context.clickedPos
        val clickedFace = context.clickedFace
        val targetPos = placedPos.relative(clickedFace.opposite)
        val targetState = context.level.getBlockState(targetPos)
        if (!de.teutonstudio.ccaeroworks.multiblock.ConsoleAssemblyRuntime.acceptsCreateControl(targetState)) {
            return result
        }
        val targetGeometry = geometry(targetState) ?: return result
        if (!targetState.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return result
        val targetFacing = targetState.getValue(BlockStateProperties.HORIZONTAL_FACING)
        if (clickedFace != targetFacing.clockWise && clickedFace != targetFacing.counterClockWise) return result

        result = result
            .setValue(BlockStateProperties.HORIZONTAL_FACING, targetFacing)
            .setValue(GEOMETRY, targetGeometry)
            .setValue(COPYCAT, copycat(targetState))
        if (targetState.hasProperty(ConsoleMultiblockSkinState.SKIN)) {
            result = result.setValue(
                ConsoleMultiblockSkinState.SKIN,
                targetState.getValue(ConsoleMultiblockSkinState.SKIN)
            )
        }
        return applyConnections(context.level, placedPos, result)
    }

    @JvmStatic
    fun applyConnections(level: LevelAccessor, pos: BlockPos, state: BlockState): BlockState {
        if (!isConsoleControl(state) || !state.hasProperty(GEOMETRY)) return state
        val geometry = state.getValue(GEOMETRY)
        if (geometry == TrainControlConsoleGeometry.NONE) return reset(state)
        val facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING)
        val world = level as? Level
        val west = world?.let {
            de.teutonstudio.ccaeroworks.multiblock.ConsoleAssemblyRuntime.connected(
                it, pos, pos.relative(facing.counterClockWise)
            )
        } ?: false
        val east = world?.let {
            de.teutonstudio.ccaeroworks.multiblock.ConsoleAssemblyRuntime.connected(
                it, pos, pos.relative(facing.clockWise)
            )
        } ?: false
        return state
            .setValue(ConsoleDeskBlock.OPEN_WEST, west)
            .setValue(ConsoleDeskBlock.OPEN_EAST, east)
    }

    @JvmStatic
    fun compatible(
        state: BlockState,
        facing: Direction,
        geometry: TrainControlConsoleGeometry
    ): Boolean {
        if (geometry == TrainControlConsoleGeometry.NONE ||
            !state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ||
            state.getValue(BlockStateProperties.HORIZONTAL_FACING) != facing
        ) {
            return false
        }
        return geometry(state) == geometry
    }

    @JvmStatic
    fun integratedShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext,
        collision: Boolean
    ): VoxelShape? {
        val geometry = geometry(state) ?: return null
        val path = if (geometry == TrainControlConsoleGeometry.STAND) "control_stand" else "control_desk"
        val block = AeroworksTypes.controlDeskBlock(path)
        var projected = block.defaultBlockState()
            .setValue(BlockStateProperties.HORIZONTAL_FACING, state.getValue(BlockStateProperties.HORIZONTAL_FACING))
        if (projected.hasProperty(ConsoleBlock.CEILING)) {
            projected = projected.setValue(ConsoleBlock.CEILING, false)
        }
        projected = projected
            .setValue(ConsoleDeskBlock.OPEN_WEST, state.getValue(ConsoleDeskBlock.OPEN_WEST))
            .setValue(ConsoleDeskBlock.OPEN_EAST, state.getValue(ConsoleDeskBlock.OPEN_EAST))
        return if (collision) {
            projected.getCollisionShape(level, pos, context)
        } else {
            projected.getShape(level, pos, context)
        }
    }

    fun reset(state: BlockState): BlockState {
        var result = state
            .setValue(GEOMETRY, TrainControlConsoleGeometry.NONE)
            .setValue(COPYCAT, false)
            .setValue(ConsoleDeskBlock.OPEN_WEST, false)
            .setValue(ConsoleDeskBlock.OPEN_EAST, false)
        if (result.hasProperty(ConsoleMultiblockSkinState.SKIN)) {
            result = result.setValue(ConsoleMultiblockSkinState.SKIN, ConsoleMultiblockSkin.DEFAULT)
        }
        return result
    }

    private val TRAIN_CONTROLS_ID = ResourceLocation.fromNamespaceAndPath("create", "controls")
    private val CONTRAPTION_CONTROLS_ID = ResourceLocation.fromNamespaceAndPath("create", "contraption_controls")
}
