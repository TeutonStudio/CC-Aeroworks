package de.teutonstudio.ccaeroworks.multiblock

import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksTypes
import de.teutonstudio.ccaeroworks.train.TrainControlConsoleIntegration
import de.teutonstudio.ccaeroworks.train.TrainControlConsoleUpdater
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.block.state.properties.DirectionProperty
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.level.BlockEvent
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicLong

object ConsoleMultiblockSkinUpdater {
    private data class PendingRefresh(var normalizeComputer: Boolean)

    private val revision = AtomicLong()
    private val pending = WeakHashMap<ServerLevel, MutableMap<BlockPos, PendingRefresh>>()
    private val refreshing = WeakHashMap<ServerLevel, Boolean>()

    @SubscribeEvent
    fun onPlace(event: BlockEvent.EntityPlaceEvent) {
        val level = event.level as? ServerLevel ?: return
        if (!isRelevant(event.placedBlock)) return
        schedule(
            level,
            event.pos,
            normalizeComputer = AeroworksTypes.isComputerControlDesk(event.placedBlock.block)
        )
    }

    @SubscribeEvent
    fun onBreak(event: BlockEvent.BreakEvent) {
        val level = event.level as? ServerLevel ?: return
        if (!isRelevant(event.state)) return
        schedule(level, event.pos)
    }

    @SubscribeEvent
    fun onNeighbourNotify(event: BlockEvent.NeighborNotifyEvent) {
        val level = event.level as? ServerLevel ?: return
        if (!isRelevant(event.state)) return
        schedule(level, event.pos)
    }

    fun scheduleRefresh(level: ServerLevel, pos: BlockPos) = schedule(level, pos)

    private fun schedule(
        level: ServerLevel,
        pos: BlockPos,
        normalizeComputer: Boolean = false
    ) {
        val immutablePos = pos.immutable()
        val added = synchronized(pending) {
            val updates = pending.getOrPut(level) { hashMapOf() }
            val existing = updates[immutablePos]
            if (existing != null) {
                existing.normalizeComputer = existing.normalizeComputer || normalizeComputer
                false
            } else {
                updates[immutablePos] = PendingRefresh(normalizeComputer)
                true
            }
        }
        if (!added) return

        level.server.execute {
            val refresh = synchronized(pending) {
                val updates = pending[level] ?: return@synchronized null
                val result = updates.remove(immutablePos)
                if (updates.isEmpty()) pending.remove(level)
                result
            }
            refresh?.let {
                refreshAround(level, immutablePos, it.normalizeComputer)
            }
        }
    }

    private fun refreshAround(
        level: ServerLevel,
        origin: BlockPos,
        normalizeComputer: Boolean
    ) {
        synchronized(refreshing) {
            if (refreshing[level] == true) return
            refreshing[level] = true
        }

        try {
            ConsoleMultiblockManager.invalidate(level, origin)
            if (normalizeComputer && normalizeDuplicateComputer(level, origin)) {
                ConsoleMultiblockManager.invalidate(level, origin)
            }

            val starts = linkedSetOf<BlockPos>()
            addIfDesk(level, origin, starts)
            HORIZONTAL_DIRECTIONS.forEach { direction ->
                addIfDesk(level, origin.relative(direction), starts)
            }
            addControlAnchors(level, origin, starts)
            HORIZONTAL_DIRECTIONS.forEach { direction ->
                addControlAnchors(level, origin.relative(direction), starts)
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
                snapshot.segmentPositions.forEach memberLoop@{ segmentPos ->
                    val state = level.getBlockState(segmentPos)
                    var nextState = if (AeroworksTypes.isControlDesk(state.block)) {
                        ConsoleDeskConnections.apply(level, segmentPos, state)
                    } else {
                        TrainControlConsoleIntegration.applyConnections(level, segmentPos, state)
                    }
                    if (nextState.hasProperty(ConsoleMultiblockSkinState.SKIN)) {
                        nextState = nextState.setValue(ConsoleMultiblockSkinState.SKIN, skin)
                    }
                    if (nextState == state) return@memberLoop

                    level.setBlock(
                        segmentPos,
                        nextState,
                        Block.UPDATE_CLIENTS
                    )
                }
            }

            starts.forEach { ConsoleMultiblockManager.invalidate(level, it) }
        } finally {
            synchronized(refreshing) {
                refreshing.remove(level)
            }
        }
    }

    private fun normalizeDuplicateComputer(level: ServerLevel, origin: BlockPos): Boolean {
        return de.teutonstudio.ccaeroworks.computer.ConsoleComputerLifecycle.reconcile(level, origin)
    }

    private fun copyDeskState(source: BlockState, initialTarget: BlockState): BlockState {
        var target = initialTarget
        target = copyDirectionProperty(source, target, "facing")
        target = copyBooleanProperty(source, target, "ceiling")
        target = copyBooleanProperty(source, target, "open_east")
        target = copyBooleanProperty(source, target, "open_west")
        if (source.hasProperty(ConsoleMultiblockSkinState.SKIN) &&
            target.hasProperty(ConsoleMultiblockSkinState.SKIN)
        ) {
            target = target.setValue(
                ConsoleMultiblockSkinState.SKIN,
                source.getValue(ConsoleMultiblockSkinState.SKIN)
            )
        }
        return target
    }

    private fun copyDirectionProperty(
        source: BlockState,
        target: BlockState,
        name: String
    ): BlockState {
        val sourceProperty = source.properties
            .filterIsInstance<DirectionProperty>()
            .firstOrNull { it.name == name }
            ?: return target
        val targetProperty = target.properties
            .filterIsInstance<DirectionProperty>()
            .firstOrNull { it.name == name }
            ?: return target
        return target.setValue(targetProperty, source.getValue(sourceProperty))
    }

    private fun copyBooleanProperty(
        source: BlockState,
        target: BlockState,
        name: String
    ): BlockState {
        val sourceProperty = source.properties
            .filterIsInstance<BooleanProperty>()
            .firstOrNull { it.name == name }
            ?: return target
        val targetProperty = target.properties
            .filterIsInstance<BooleanProperty>()
            .firstOrNull { it.name == name }
            ?: return target
        return target.setValue(targetProperty, source.getValue(sourceProperty))
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

    private fun addControlAnchors(
        level: ServerLevel,
        pos: BlockPos,
        starts: MutableSet<BlockPos>
    ) {
        if (!level.isLoaded(pos) || !TrainControlConsoleIntegration.isIntegrated(level.getBlockState(pos))) return
        starts += TrainControlConsoleUpdater.anchorDesks(level, pos)
    }

    private fun isRelevant(state: BlockState): Boolean =
        AeroworksTypes.isControlDesk(state.block) || TrainControlConsoleIntegration.isTrainControls(state)

    private val HORIZONTAL_DIRECTIONS = arrayOf(
        Direction.NORTH,
        Direction.EAST,
        Direction.SOUTH,
        Direction.WEST
    )
}
