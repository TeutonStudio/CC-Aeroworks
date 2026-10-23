package de.teutonstudio.ccaeroworks.train

import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksTypes
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockSkinUpdater
import de.teutonstudio.ccaeroworks.multiblock.ConsoleAssemblyRuntime
import de.teutonstudio.ccaeroworks.multiblock.ConsoleAssemblyStyles
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.level.BlockEvent
import java.util.WeakHashMap

/** Keeps native Create control geometry anchored to a real Aeroworks console family. */
object TrainControlConsoleUpdater {
    private val pending = WeakHashMap<ServerLevel, MutableSet<BlockPos>>()
    private val refreshing = WeakHashMap<ServerLevel, Boolean>()

    @SubscribeEvent
    fun onPlace(event: BlockEvent.EntityPlaceEvent) {
        val level = event.level as? ServerLevel ?: return
        if (!relevant(event.placedBlock)) return
        ConsoleAssemblyRuntime.initializePlaced(level, event.pos, event.entity as? net.minecraft.world.entity.LivingEntity)
        schedule(level, event.pos)
    }

    @SubscribeEvent
    fun onBreak(event: BlockEvent.BreakEvent) {
        val level = event.level as? ServerLevel ?: return
        if (!relevant(event.state)) return
        if (TrainControlConsoleIntegration.isIntegrated(event.state)) {
            ConsoleAssemblyStyles.releaseControlStyle(level, event.pos, event.player)
        }
        schedule(level, event.pos)
    }

    @SubscribeEvent
    fun onNeighbourNotify(event: BlockEvent.NeighborNotifyEvent) {
        val level = event.level as? ServerLevel ?: return
        if (!relevant(event.state) && HORIZONTAL.none { direction ->
                level.isLoaded(event.pos.relative(direction)) &&
                    TrainControlConsoleIntegration.isIntegrated(
                        level.getBlockState(event.pos.relative(direction))
                    )
            }
        ) {
            return
        }
        schedule(level, event.pos)
    }

    fun schedule(level: ServerLevel, pos: BlockPos) {
        val immutable = pos.immutable()
        val added = synchronized(pending) {
            pending.getOrPut(level) { linkedSetOf() }.add(immutable)
        }
        if (!added) return
        level.server.execute {
            val shouldRun = synchronized(pending) {
                val positions = pending[level] ?: return@synchronized false
                val removed = positions.remove(immutable)
                if (positions.isEmpty()) pending.remove(level)
                removed
            }
            if (shouldRun) normalizeAround(level, immutable)
        }
    }

    fun anchorDesks(level: Level, controlsPos: BlockPos): List<BlockPos> {
        val state = level.getBlockState(controlsPos)
        if (!TrainControlConsoleIntegration.isIntegrated(state)) return emptyList()
        val component = component(level, controlsPos, state)
        return component.anchors.toList()
    }

    private fun normalizeAround(level: ServerLevel, origin: BlockPos) {
        synchronized(refreshing) {
            if (refreshing[level] == true) return
            refreshing[level] = true
        }
        try {
            if (level.isLoaded(origin) && relevant(level.getBlockState(origin))) {
                ConsoleAssemblyRuntime.initializePlaced(level, origin)
                ConsoleAssemblyRuntime.ensureLegacyComponent(level, origin)
            }
            val seeds = linkedSetOf<BlockPos>()
            addIntegrated(level, origin, seeds)
            HORIZONTAL.forEach { addIntegrated(level, origin.relative(it), seeds) }
            val visited = hashSetOf<BlockPos>()
            val refreshAnchors = linkedSetOf<BlockPos>()

            seeds.forEach { seed ->
                if (seed in visited) return@forEach
                val state = level.getBlockState(seed)
                if (!TrainControlConsoleIntegration.isIntegrated(state)) return@forEach
                val component = component(level, seed, state)
                visited += component.controls
                refreshAnchors += component.anchors

                if (component.anchors.isEmpty() && component.complete) {
                    component.controls.forEach { pos ->
                        val current = level.getBlockState(pos)
                        val reset = TrainControlConsoleIntegration.reset(current)
                        if (reset != current) {
                            ConsoleAssemblyStyles.releaseControlStyle(level, pos)
                            setStatePreservingBlockEntity(level, pos, reset)
                        }
                    }
                } else if (component.anchors.isNotEmpty()) {
                    val anchorProfiles = component.anchors.mapNotNull { anchor ->
                        de.teutonstudio.ccaeroworks.multiblock.ConsoleAssemblyAdapters
                            .profile(level.getBlockState(anchor))
                    }
                    val copycat = anchorProfiles.firstOrNull()?.copycat == true
                    val styleAnchor = component.anchors.firstOrNull { anchor ->
                        de.teutonstudio.ccaeroworks.multiblock.ConsoleAssemblyAdapters
                            .profile(level.getBlockState(anchor))?.copycat == true
                    }
                    if (styleAnchor != null) ConsoleAssemblyStyles.propagate(level, styleAnchor)
                    component.controls.forEach { pos ->
                        val current = level.getBlockState(pos)
                        val profiled = current.setValue(TrainControlConsoleIntegration.COPYCAT, copycat)
                        val updated = TrainControlConsoleIntegration.applyConnections(level, pos, profiled)
                        if (updated != current) setStatePreservingBlockEntity(level, pos, updated)
                    }
                }
            }

            ConsoleMultiblockManager.invalidate(level, origin)
            refreshAnchors.forEach { anchor ->
                ConsoleMultiblockManager.invalidate(level, anchor)
                ConsoleMultiblockSkinUpdater.scheduleRefresh(level, anchor)
            }
            HORIZONTAL.forEach { direction ->
                val candidate = origin.relative(direction)
                if (level.isLoaded(candidate) && AeroworksTypes.isControlDesk(level.getBlockState(candidate).block)) {
                    ConsoleMultiblockSkinUpdater.scheduleRefresh(level, candidate)
                }
            }
        } finally {
            synchronized(refreshing) { refreshing.remove(level) }
        }
    }

    private fun component(level: Level, start: BlockPos, startState: BlockState): Component {
        val facing = startState.getValue(BlockStateProperties.HORIZONTAL_FACING)
        val geometry = TrainControlConsoleIntegration.geometry(startState)
            ?: return Component(emptySet(), emptySet(), true)
        val controls = linkedSetOf<BlockPos>()
        val anchors = linkedSetOf<BlockPos>()
        val queue = ArrayDeque<BlockPos>()
        queue += start.immutable()
        var complete = true

        while (queue.isNotEmpty()) {
            val pos = queue.removeFirst()
            if (!controls.add(pos)) continue
            for (direction in arrayOf(facing.counterClockWise, facing.clockWise)) {
                val neighbourPos = pos.relative(direction)
                if (!level.isLoaded(neighbourPos)) {
                    complete = false
                    continue
                }
                val neighbour = level.getBlockState(neighbourPos)
                if (!ConsoleAssemblyRuntime.connected(level, pos, neighbourPos)) continue
                if (TrainControlConsoleIntegration.isIntegrated(neighbour)) {
                    if (neighbourPos !in controls) queue += neighbourPos.immutable()
                } else if (AeroworksTypes.isControlDesk(neighbour.block)) {
                    anchors += neighbourPos.immutable()
                }
            }
        }
        return Component(controls, anchors, complete)
    }

    private fun addIntegrated(level: Level, pos: BlockPos, result: MutableSet<BlockPos>) {
        if (level.isLoaded(pos) && TrainControlConsoleIntegration.isIntegrated(level.getBlockState(pos))) {
            result += pos.immutable()
        }
    }

    /** Create may rebuild behaviour state while its block-state shape changes. Preserve the
     * native block entity payload (filter, button state and our persistent controller UUID)
     * across both integration and orphan fallback. */
    private fun setStatePreservingBlockEntity(level: ServerLevel, pos: BlockPos, state: BlockState) {
        val before = level.getBlockEntity(pos)
        val saved = before?.saveWithoutMetadata(level.registryAccess())
        level.setBlock(pos, state, Block.UPDATE_ALL)
        if (saved != null) {
            level.getBlockEntity(pos)?.loadWithComponents(saved, level.registryAccess())
        }
    }

    private fun relevant(state: BlockState): Boolean =
        AeroworksTypes.isControlDesk(state.block) || TrainControlConsoleIntegration.isConsoleControl(state)

    private data class Component(
        val controls: Set<BlockPos>,
        val anchors: Set<BlockPos>,
        val complete: Boolean
    )

    private val HORIZONTAL = arrayOf(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)
}
