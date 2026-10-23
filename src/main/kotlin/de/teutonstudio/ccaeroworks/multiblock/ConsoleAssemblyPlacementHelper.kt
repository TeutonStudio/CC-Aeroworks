package de.teutonstudio.ccaeroworks.multiblock

import de.teutonstudio.ccaeroworks.train.TrainControlConsoleIntegration
import net.createmod.catnip.placement.IPlacementHelper
import net.createmod.catnip.placement.PlacementHelpers
import net.createmod.catnip.placement.PlacementOffset
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.ItemInteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.BlockHitResult
import java.util.function.Predicate

/** Create-style end arrows for extending exactly the clicked UUID row. */
object ConsoleAssemblyPlacementHelper : IPlacementHelper {
    private var helperId = -1

    @JvmStatic
    fun register() {
        if (helperId < 0) helperId = PlacementHelpers.register(this)
    }

    @JvmStatic
    fun tryPlace(
        stack: ItemStack,
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hit: BlockHitResult
    ): ItemInteractionResult? {
        if (player.isShiftKeyDown || !player.mayBuild() || !matchesItem(stack) || !matchesState(state)) return null
        val item = stack.item as? BlockItem ?: return null
        val offset = getOffset(player, level, state, pos, hit, stack)
        if (!offset.isSuccessful) return null
        return offset.placeInWorld(level, item, player, hand, hit)
    }

    override fun getItemPredicate(): Predicate<ItemStack> = Predicate { stack ->
        val block = (stack.item as? BlockItem)?.block ?: return@Predicate false
        ConsoleAssemblyAdapters.profile(block) != null ||
            TrainControlConsoleIntegration.isConsoleControl(block.defaultBlockState())
    }

    override fun getStatePredicate(): Predicate<BlockState> = Predicate { state ->
        ConsoleAssemblyAdapters.profile(state) != null || TrainControlConsoleIntegration.isIntegrated(state)
    }

    override fun getOffset(
        player: Player,
        level: Level,
        state: BlockState,
        pos: BlockPos,
        hit: BlockHitResult
    ): PlacementOffset = getOffset(player, level, state, pos, hit, player.getItemInHand(InteractionHand.MAIN_HAND))

    override fun getOffset(
        player: Player,
        level: Level,
        state: BlockState,
        pos: BlockPos,
        hit: BlockHitResult,
        stack: ItemStack
    ): PlacementOffset {
        if (player.isShiftKeyDown || ConsoleAssemblyRuntime.id(level, pos) == null) return PlacementOffset.fail()
        val row = ConsoleAssemblyRuntime.members(level, pos)
        if (!row.complete || row.tooLong || row.positions.size >= ConsoleAssemblyRuntime.MAX_SEGMENTS) {
            return PlacementOffset.fail()
        }
        val facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING)
        val itemBlock = (stack.item as? BlockItem)?.block ?: return PlacementOffset.fail()
        val itemProfile = ConsoleAssemblyAdapters.profile(itemBlock)
        val rowGeometry = ConsoleAssemblyRuntime.geometry(state) ?: return PlacementOffset.fail()
        if (itemProfile != null && itemProfile.geometry != rowGeometry) return PlacementOffset.fail()
        if (TrainControlConsoleIntegration.isConsoleControl(itemBlock.defaultBlockState()) &&
            !ConsoleAssemblyRuntime.acceptsCreateControl(state)
        ) return PlacementOffset.fail()

        val direction = lateralDirection(facing, hit.direction) ?: return PlacementOffset.fail()
        var end = pos.immutable()
        while (true) {
            val next = end.relative(direction)
            if (!level.isLoaded(next) || !ConsoleAssemblyRuntime.connected(level, end, next)) break
            end = next
        }
        val target = end.relative(direction)
        if (!level.isLoaded(target) || !level.getBlockState(target).canBeReplaced()) return PlacementOffset.fail()

        val transformed: (BlockState) -> BlockState = { initial ->
            var result = initial
            if (result.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                result = result.setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
            }
            if (TrainControlConsoleIntegration.isConsoleControl(result)) {
                result = result
                    .setValue(TrainControlConsoleIntegration.GEOMETRY, rowGeometry)
                    .setValue(
                        TrainControlConsoleIntegration.COPYCAT,
                        TrainControlConsoleIntegration.copycat(state)
                    )
            }
            result
        }
        if (!level.isClientSide) ConsoleAssemblyPlacement.capture(level, target, end, false)
        return PlacementOffset.success(target.subtract(pos), transformed)
            .withGhostState(transformed(itemBlock.defaultBlockState()))
    }

    /** Only the two row-axis faces may extend a console assembly. */
    @JvmStatic
    fun lateralDirection(facing: Direction, hitFace: Direction): Direction? = when (hitFace) {
        facing.counterClockWise -> facing.counterClockWise
        facing.clockWise -> facing.clockWise
        else -> null
    }
}
