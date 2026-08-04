package de.teutonstudio.ccaeroworks.computer

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import com.mred231.aeroworks.content.controls.ConsoleDeskBlock
import com.mred231.aeroworks.content.controls.ConsoleType
import dan200.computercraft.shared.common.IBundledRedstoneBlock
import dan200.computercraft.shared.computer.core.ComputerFamily
import de.teutonstudio.ccaeroworks.registry.CCBlockEntities
import de.teutonstudio.ccaeroworks.registry.CCItems
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.loot.LootParams
import net.minecraft.world.level.storage.loot.parameters.LootContextParams

class ComputerControlDeskBlock(
    properties: Properties,
    consoleType: ConsoleType,
    val family: ComputerFamily
) : ConsoleDeskBlock(properties, consoleType), IBundledRedstoneBlock {
    override fun getBlockEntityClass(): Class<ConsoleBlockEntity> = ConsoleBlockEntity::class.java

    override fun getBlockEntityType(): BlockEntityType<out ConsoleBlockEntity> =
        CCBlockEntities.COMPUTER_CONTROL_DESK.get()

    override fun isSignalSource(state: BlockState): Boolean = true

    override fun getDirectSignal(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        direction: Direction
    ): Int = (level.getBlockEntity(pos) as? ComputerControlDeskBlockEntity)
        ?.redstoneOutput(direction) ?: 0

    override fun getSignal(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        direction: Direction
    ): Int = getDirectSignal(state, level, pos, direction)

    override fun getBundledRedstoneOutput(
        world: Level,
        pos: BlockPos,
        side: Direction
    ): Int = (world.getBlockEntity(pos) as? ComputerControlDeskBlockEntity)
        ?.bundledRedstoneOutput(side) ?: 0

    override fun getCloneItemStack(level: LevelReader, pos: BlockPos, state: BlockState): ItemStack {
        val stack = ItemStack(
            if (family == ComputerFamily.ADVANCED) CCItems.ADVANCED_COMPUTER_CONTROL_DESK.get()
            else CCItems.COMPUTER_CONTROL_DESK.get()
        )
        (level.getBlockEntity(pos) as? ComputerControlDeskBlockEntity)?.writeToItem(stack)
        return stack
    }

    override fun playerWillDestroy(
        level: Level,
        pos: BlockPos,
        state: BlockState,
        player: Player
    ): BlockState {
        if (!level.isClientSide && player.isCreative) {
            Block.popResource(level, pos, getCloneItemStack(level, pos, state))
        }
        return super.playerWillDestroy(level, pos, state, player)
    }

    override fun getDrops(state: BlockState, builder: LootParams.Builder): List<ItemStack> {
        val blockEntity = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY)
            as? ComputerControlDeskBlockEntity
            ?: return super.getDrops(state, builder)
        val stack = ItemStack(
            if (family == ComputerFamily.ADVANCED) CCItems.ADVANCED_COMPUTER_CONTROL_DESK.get()
            else CCItems.COMPUTER_CONTROL_DESK.get()
        )
        blockEntity.writeToItem(stack)
        return listOf(stack)
    }
}
