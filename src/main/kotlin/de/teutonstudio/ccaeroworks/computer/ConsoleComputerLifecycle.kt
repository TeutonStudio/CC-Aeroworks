package de.teutonstudio.ccaeroworks.computer

import com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksTypes
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockResolver
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockSkinUpdater
import de.teutonstudio.ccaeroworks.multiblock.ConsoleAssemblyRuntime
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.Property

object ConsoleComputerLifecycle {
    private val migrating = hashSetOf<ConsoleBlockEntity>()

    fun scheduleMigration(host: ConsoleBlockEntity) {
        val level = host.level as? ServerLevel ?: return
        if (host.blockState.block !is ComputerControlDeskBlock) return
        level.server.tell(net.minecraft.server.TickTask(level.server.tickCount + 1) { migrate(host) })
    }

    private fun migrate(host: ConsoleBlockEntity) {
        val level = host.level as? ServerLevel ?: return
        if (level.getBlockEntity(host.blockPos) !== host) return
        val legacy = host.blockState.block as? ComputerControlDeskBlock ?: return
        // Serialize native modules, copycat skins, desk identity and the installed computer together.
        val saved = host.saveWithFullMetadata(level.registryAccess())
        var replacement = AeroworksTypes.controlDeskBlock(legacy.variant.aeroworksPath).defaultBlockState()
        for (property in host.blockState.properties) replacement = copyProperty(host.blockState, replacement, property)
        migrating += host
        try {
            if (!level.setBlock(host.blockPos, replacement, Block.UPDATE_ALL or Block.UPDATE_MOVE_BY_PISTON)) return
            val target = level.getBlockEntity(host.blockPos) as? ConsoleBlockEntity ?: error("Missing replacement console")
            target.loadWithComponents(saved, level.registryAccess())
            ConsoleComputers.changed(target)
        } finally { migrating -= host }
        reconcile(level, host.blockPos)
    }

    private fun <T : Comparable<T>> copyProperty(source: BlockState, target: BlockState, property: Property<T>): BlockState =
        if (target.hasProperty(property)) target.setValue(property, source.getValue(property)) else target

    /** Runs on actual replacement, never BreakEvent (which may still be canceled) or chunk unload. */
    fun removing(level: ServerLevel, pos: BlockPos, old: BlockState, replacement: BlockState) {
        if (old.block === replacement.block) return
        val host = level.getBlockEntity(pos) as? ConsoleBlockEntity ?: return
        if (host in migrating || ConsoleComputers.get(host) == null) return
        val facing = old.getValue(BlockStateProperties.HORIZONTAL_FACING)
        val rowId = (host as? de.teutonstudio.ccaeroworks.multiblock.ConsoleAssemblyStateAccess)
            ?.ccaeroworks_getAssemblyId()
        fun scan(direction: Direction): List<ConsoleBlockEntity> {
            val desks = mutableListOf<ConsoleBlockEntity>()
            var cursor = pos.relative(direction)
            var segments = 0
            while (level.isLoaded(cursor) && segments < ConsoleMultiblockResolver.MAX_MEMBERS) {
                val state = level.getBlockState(cursor)
                if (!ConsoleAssemblyRuntime.samePhysicalProfile(old, state)) break
                val candidateId = ConsoleAssemblyRuntime.id(level, cursor)
                if (rowId != null && candidateId != rowId) break
                if (rowId == null && candidateId != null) break
                segments += 1
                if (AeroworksTypes.isControlDesk(state.block)) {
                    val desk = level.getBlockEntity(cursor) as? ConsoleBlockEntity ?: break
                    desks += desk
                }
                cursor = cursor.relative(direction)
            }
            return desks
        }
        val left = scan(facing.counterClockWise)
        val right = scan(facing.clockWise)
        val target = ComputerTransferSelection.target(left, right)
        val stack = ConsoleComputers.remove(host)
        if (target != null && (left + right).none { ConsoleComputers.get(it) != null }) {
            ConsoleComputers.install(target, stack)
        } else Block.popResource(level, pos, stack)
        ConsoleMultiblockManager.invalidate(level, pos)
        ConsoleMultiblockSkinUpdater.scheduleRefresh(level, pos)
    }

    fun reconcile(level: ServerLevel, pos: BlockPos): Boolean {
        ConsoleMultiblockManager.invalidate(level, pos)
        val snapshot = ConsoleMultiblockManager.resolve(level, pos)
        if (snapshot.computers.size < 2) return false
        val keep = snapshot.computers.minWithOrNull(compareBy<ConsoleComputer>(
            { if (it.isAdvanced) 0 else 1 }, { snapshot.memberAt(it.blockPos)?.index ?: Int.MAX_VALUE }
        )) ?: return false
        snapshot.computers.filter { it !== keep }.forEach {
            Block.popResource(level, it.blockPos, ConsoleComputers.remove(it.host))
        }
        ConsoleMultiblockManager.invalidate(level, pos)
        return true
    }
}
