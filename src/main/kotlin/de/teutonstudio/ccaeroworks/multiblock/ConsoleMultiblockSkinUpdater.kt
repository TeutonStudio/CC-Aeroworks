package de.teutonstudio.ccaeroworks.multiblock

import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksTypes
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Block
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.level.BlockEvent
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicLong

object ConsoleMultiblockSkinUpdater {
    private val revision = AtomicLong()
    private val pending = WeakHashMap<ServerLevel, MutableSet<BlockPos>>()
    private val refreshing = WeakHashMap<ServerLevel, Boolean>()

    @SubscribeEvent
    fun onPlace(event: BlockEvent.EntityPlaceEvent) {
        val level = event.level as? ServerLevel ?: return
        if (!AeroworksTypes.isControlDesk(event.placedBlock.block)) return
        schedule(level, event.pos)
    }

    @SubscribeEvent
    fun onBreak(event: BlockEvent.BreakEvent) {
        val level = event.level as? ServerLevel ?: return
        if (!AeroworksTypes.isControlDesk(event.state.block)) return
        schedule(level, event.pos)
    }

    @SubscribeEvent
    fun onNeighbourNotify(event: BlockEvent.NeighborNotifyEvent) {
        val level = event.level as? ServerLevel ?: return
        if (!AeroworksTypes.isControlDesk(event.state.block)) return
        schedule(level, event.pos)
    }

    private fun schedule(level: ServerLevel, pos: BlockPos) {
        val immutablePos = pos.immutable()
        val added = synchronized(pending) {
            pending.getOrPut(level) { hashSetOf() }.add(immutablePos)
        }
        if (!added) return

        level.server.execute {
            synchronized(pending) {
                pending[level]?.let { positions ->
                    positions.remove(immutablePos)
                    if (positions.isEmpty()) pending.remove(level)
                }
            }
            refreshAround(level, immutablePos)
        }
    }

    private fun refreshAround(level: ServerLevel, origin: BlockPos) {
        synchronized(refreshing) {
            if (refreshing[level] == true) return
            refreshing[level] = true
        }

        try {
            ConsoleMultiblockManager.invalidate(level)

            val starts = linkedSetOf<BlockPos>()
            addIfDesk(level, origin, starts)
            HORIZONTAL_DIRECTIONS.forEach { direction ->
                addIfDesk(level, origin.relative(direction), starts)
            }

            val processedAnchors = hashSetOf<BlockPos>()
            starts.forEach startLoop@{ start ->
                val snapshot = ConsoleMultiblockResolver.resolve(
                    level,
                    start,
                    revision.incrementAndGet()
                )
                if (!processedAnchors.add(snapshot.anchor)) return@startLoop

                val skin = ConsoleMultiblockSkinState.forMembers(snapshot.members)
                snapshot.members.forEach memberLoop@{ member ->
                    val state = level.getBlockState(member.pos)
                    if (!state.hasProperty(ConsoleMultiblockSkinState.SKIN)) return@memberLoop
                    if (state.getValue(ConsoleMultiblockSkinState.SKIN) == skin) return@memberLoop

                    level.setBlock(
                        member.pos,
                        state.setValue(ConsoleMultiblockSkinState.SKIN, skin),
                        Block.UPDATE_CLIENTS
                    )
                }
            }

            ConsoleMultiblockManager.invalidate(level)
        } finally {
            synchronized(refreshing) {
                refreshing.remove(level)
            }
        }
    }

    private fun addIfDesk(
        level: ServerLevel,
        pos: BlockPos,
        starts: MutableSet<BlockPos>
    ) {
        if (!level.isLoaded(pos)) return
        if (AeroworksTypes.isControlDesk(level.getBlockState(pos).block)) {
            starts += pos.immutable()
        }
    }

    private val HORIZONTAL_DIRECTIONS = arrayOf(
        Direction.NORTH,
        Direction.EAST,
        Direction.SOUTH,
        Direction.WEST
    )
}
