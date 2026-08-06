package de.teutonstudio.ccaeroworks.computer

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import com.mred231.aeroworks.content.controls.ConsoleDeskBlock
import com.mred231.aeroworks.content.controls.ConsoleType
import dan200.computercraft.shared.ModRegistry
import dan200.computercraft.shared.common.IBundledRedstoneBlock
import dan200.computercraft.shared.computer.core.ComputerFamily
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksTypes
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager
import de.teutonstudio.ccaeroworks.multiblock.ConsoleNetworkState
import de.teutonstudio.ccaeroworks.registry.CCBlockEntities
import de.teutonstudio.ccaeroworks.registry.CCItems
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
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

    override fun newBlockEntity(pos: BlockPos, state: BlockState): ComputerControlDeskBlockEntity =
        ComputerControlDeskBlockEntity(CCBlockEntities.COMPUTER_CONTROL_DESK.get(), pos, state)

    override fun setPlacedBy(
        level: Level,
        pos: BlockPos,
        state: BlockState,
        placer: LivingEntity?,
        stack: ItemStack
    ) {
        super.setPlacedBy(level, pos, state, placer, stack)
        val serverLevel = level as? ServerLevel ?: return
        val player = placer as? Player ?: return

        // Placement components are applied after the block entity is created. Resolve the network
        // on the next server task so a rejected computer keeps its ID, label and filesystem.
        serverLevel.server.execute {
            val placedDesk = serverLevel.getBlockEntity(pos) as? ComputerControlDeskBlockEntity
                ?: return@execute
            ConsoleMultiblockManager.invalidate(serverLevel)
            val network = ConsoleMultiblockManager.resolve(serverLevel, pos)
            if (network.state != ConsoleNetworkState.CONFLICT) return@execute

            splitDuplicateComputer(serverLevel, pos, placedDesk, player)
        }
    }

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

    private fun splitDuplicateComputer(
        level: ServerLevel,
        pos: BlockPos,
        placedDesk: ComputerControlDeskBlockEntity,
        player: Player
    ) {
        val savedDesk = placedDesk.saveWithFullMetadata(level.registryAccess())
        val combinedStack = ItemStack(
            if (family == ComputerFamily.ADVANCED) CCItems.ADVANCED_COMPUTER_CONTROL_DESK.get()
            else CCItems.COMPUTER_CONTROL_DESK.get()
        )
        placedDesk.writeToItem(combinedStack)
        val computerStack = standaloneComputer(combinedStack)

        var replacement = AeroworksTypes.vanillaControlDeskBlock().defaultBlockState()
        val currentState = placedDesk.blockState
        if (replacement.hasProperty(BlockStateProperties.HORIZONTAL_FACING) &&
            currentState.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
        ) {
            replacement = replacement.setValue(
                BlockStateProperties.HORIZONTAL_FACING,
                currentState.getValue(BlockStateProperties.HORIZONTAL_FACING)
            )
        }

        if (!level.setBlock(pos, replacement, Block.UPDATE_ALL)) return
        val replacementEntity = level.getBlockEntity(pos) as? ConsoleBlockEntity
        replacementEntity?.loadWithComponents(savedDesk, level.registryAccess())
        replacementEntity?.setChanged()
        level.sendBlockUpdated(pos, replacement, replacement, Block.UPDATE_ALL)

        Block.popResource(level, pos.above(), computerStack)
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6f, 0.9f)
        ConsoleMultiblockManager.invalidate(level)
        player.displayClientMessage(
            Component.translatable("message.cc_aeroworks.computer_ejected"),
            true
        )
    }

    private fun standaloneComputer(source: ItemStack): ItemStack {
        val result = ItemStack(
            if (family == ComputerFamily.ADVANCED) ModRegistry.Items.COMPUTER_ADVANCED.get()
            else ModRegistry.Items.COMPUTER_NORMAL.get()
        )
        copyComponent(source, result, ModRegistry.DataComponents.COMPUTER_ID.get())
        copyComponent(source, result, ModRegistry.DataComponents.STORAGE_CAPACITY.get())
        copyComponent(source, result, ModRegistry.DataComponents.TERMINAL_SIZE.get())
        copyComponent(source, result, DataComponents.CUSTOM_NAME)
        return result
    }

    private fun <T> copyComponent(
        source: ItemStack,
        target: ItemStack,
        type: DataComponentType<T>
    ) {
        source.get(type)?.let { target.set(type, it) }
    }
}
