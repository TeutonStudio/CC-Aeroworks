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
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape
import com.mred231.aeroworks.content.controls.console.DeskSkin

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
    override fun standGeometry(): Boolean = variant.standGeometry

    override fun getShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext
    ): VoxelShape = projectedBaseState(state).getShape(level, pos, context)

    override fun zoneAt(state: BlockState, pos: BlockPos, hit: Vec3): DeskSkin.Zone =
        AeroworksTypes.controlDeskBlock(variant.aeroworksPath).zoneAt(projectedBaseState(state), pos, hit)

    private fun projectedBaseState(source: BlockState): BlockState {
        var target = AeroworksTypes.controlDeskBlock(variant.aeroworksPath).defaultBlockState()
        source.properties.forEach { property -> target = copyProperty(source, target, property) }
        return target
    }

    private fun <T : Comparable<T>> copyProperty(
        source: BlockState,
        target: BlockState,
        property: net.minecraft.world.level.block.state.properties.Property<T>
    ): BlockState = if (target.hasProperty(property)) target.setValue(property, source.getValue(property)) else target

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
