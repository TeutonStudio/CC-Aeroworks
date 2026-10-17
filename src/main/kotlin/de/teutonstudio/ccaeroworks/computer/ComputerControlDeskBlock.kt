package de.teutonstudio.ccaeroworks.computer

import com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity
import com.mred231.aeroworks.content.controls.console.CopycatConsoleDeskBlock
import com.mred231.aeroworks.content.controls.console.ConsoleType
import dan200.computercraft.shared.computer.core.ComputerFamily
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksTypes
import de.teutonstudio.ccaeroworks.registry.CCBlockEntities
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.loot.LootParams
import net.minecraft.world.level.storage.loot.parameters.LootContextParams

enum class ComputerConsoleVariant(
    val aeroworksPath: String,
    val itemPath: String,
    val standGeometry: Boolean
) {
    DESK("control_desk", "computer_control_desk", false),
    COPYCAT_DESK("copycat_control_desk", "computer_copycat_control_desk", false),
    STAND("control_stand", "computer_control_stand", true),
    COPYCAT_STAND("copycat_control_stand", "computer_copycat_control_stand", true)
}

class ComputerControlDeskBlock(
    properties: Properties,
    consoleType: ConsoleType,
    val family: ComputerFamily,
    val variant: ComputerConsoleVariant = ComputerConsoleVariant.DESK
) : CopycatConsoleDeskBlock(properties, consoleType) {
    override fun getBlockEntityClass(): Class<ConsoleBlockEntity> = ConsoleBlockEntity::class.java

    override fun getBlockEntityType(): BlockEntityType<out ConsoleBlockEntity> =
        CCBlockEntities.COMPUTER_CONTROL_DESK.get()

    override fun newBlockEntity(pos: BlockPos, state: BlockState): ComputerControlDeskBlockEntity =
        ComputerControlDeskBlockEntity(CCBlockEntities.COMPUTER_CONTROL_DESK.get(), pos, state)

    override fun setPlacedBy(level: Level, pos: BlockPos, state: BlockState, placer: LivingEntity?, stack: ItemStack) {
        super.setPlacedBy(level, pos, state, placer, stack)
        val host = level.getBlockEntity(pos) as? ConsoleBlockEntity ?: return
        if (!level.isClientSide) {
            val clean = ConsoleComputers.legacyItem(stack, family)
            if (ConsoleComputers.get(host) != null) ConsoleComputers.remove(host)
            ConsoleComputers.install(host, clean)
            ConsoleComputerLifecycle.scheduleMigration(host)
        }
    }

    override fun getCloneItemStack(level: LevelReader, pos: BlockPos, state: BlockState): ItemStack =
        ItemStack(AeroworksTypes.controlDeskBlock(variant.aeroworksPath)).also { stack ->
            (level.getBlockEntity(pos) as? ConsoleBlockEntity)?.let { stack.applyComponents(it.collectComponents()) }
        }

    override fun getDrops(state: BlockState, builder: LootParams.Builder): List<ItemStack> {
        val host = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY) as? ConsoleBlockEntity
        return listOf(ItemStack(AeroworksTypes.controlDeskBlock(variant.aeroworksPath)).also { stack ->
            host?.let { stack.applyComponents(it.collectComponents()) }
        })
    }

    override fun playerWillDestroy(level: Level, pos: BlockPos, state: BlockState, player: Player): BlockState {
        if (player.isCreative) {
            if (!level.isClientSide && (level.getBlockEntity(pos) as? ConsoleBlockEntity)?.hasModules() == true)
                Block.popResource(level, pos, getCloneItemStack(level, pos, state))
            return state
        }
        return super.playerWillDestroy(level, pos, state, player)
    }
}
