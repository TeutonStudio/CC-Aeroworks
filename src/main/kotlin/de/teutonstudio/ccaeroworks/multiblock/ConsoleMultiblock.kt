package de.teutonstudio.ccaeroworks.multiblock

import com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity
import com.mred231.aeroworks.content.controls.console.ConsoleDeskBlock
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksTypes
import de.teutonstudio.ccaeroworks.compat.aeroworks.DeskIdentityAccess
import de.teutonstudio.ccaeroworks.computer.ComputerControlDeskBlock
import de.teutonstudio.ccaeroworks.computer.ConsoleComputers
import de.teutonstudio.ccaeroworks.computer.ConsoleComputerLifecycle
import de.teutonstudio.ccaeroworks.computer.ConsoleComputer
import de.teutonstudio.ccaeroworks.train.TrainControlConsoleGeometry
import de.teutonstudio.ccaeroworks.train.TrainControlConsoleIntegration
import de.teutonstudio.ccaeroworks.train.TrainControlConsoleUpdater
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.level.BlockEvent
import net.neoforged.neoforge.event.level.ChunkEvent
import net.neoforged.neoforge.event.level.LevelEvent
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicLong

enum class ConsoleNetworkState {
    NONE,
    ACTIVE,
    CONFLICT,
    TOO_LARGE,
    PARTIALLY_LOADED
}

enum class ConsoleMemberKind {
    CONTROL_DESK,
    COMPUTER,
    ADVANCED_COMPUTER
}

data class ConsoleMember(
    val index: Int,
    val pos: BlockPos,
    val desk: ConsoleBlockEntity,
    val id: String,
    val kind: ConsoleMemberKind,
    val facing: Direction
)

data class ConsoleMultiblockSnapshot(
    val anchor: BlockPos,
    val segmentPositions: List<BlockPos>,
    val members: List<ConsoleMember>,
    val computers: List<ConsoleComputer>,
    val state: ConsoleNetworkState,
    val revision: Long
) {
    val owner: ConsoleComputer?
        get() = computers.singleOrNull()

    fun memberAt(pos: BlockPos): ConsoleMember? = members.firstOrNull { it.pos == pos }
}

object ConsoleMultiblockResolver {
    const val MAX_MEMBERS: Int = 64

    fun resolve(level: Level, start: BlockPos, revision: Long): ConsoleMultiblockSnapshot {
        if (!level.isLoaded(start)) {
            return ConsoleMultiblockSnapshot(
                start.immutable(),
                emptyList(),
                emptyList(),
                emptyList(),
                ConsoleNetworkState.PARTIALLY_LOADED,
                revision
            )
        }

        val startState = level.getBlockState(start)
        if (!AeroworksTypes.isControlDesk(startState.block) ||
            !startState.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
        ) {
            return ConsoleMultiblockSnapshot(
                start.immutable(),
                emptyList(),
                emptyList(),
                emptyList(),
                ConsoleNetworkState.NONE,
                revision
            )
        }

        val facing = startState.getValue(BlockStateProperties.HORIZONTAL_FACING)
        val ceiling = booleanPropertyValue(startState, "ceiling")
        if (level is ServerLevel) ConsoleAssemblyRuntime.ensureLegacyComponent(level, start)
        val standGeometry = ConsoleAssemblyAdapters.profile(startState)?.geometry == TrainControlConsoleGeometry.STAND
        val left = facing.counterClockWise
        val right = facing.clockWise
        val positions = mutableListOf(start.immutable())
        var partiallyLoaded = false
        var tooLarge = false

        fun scan(direction: Direction): List<BlockPos> {
            val found = mutableListOf<BlockPos>()
            var cursor = start.relative(direction)
            while (found.size + positions.size < MAX_MEMBERS) {
                if (!level.isLoaded(cursor)) {
                    partiallyLoaded = true
                    break
                }
                val state = level.getBlockState(cursor)
                val previous = cursor.relative(direction.opposite)
                if (!ConsoleAssemblyRuntime.connected(level, previous, cursor)) break
                found += cursor.immutable()
                cursor = cursor.relative(direction)
            }
            if (found.size + positions.size >= MAX_MEMBERS && level.isLoaded(cursor) &&
                compatibleSegment(level.getBlockState(cursor), facing, ceiling, standGeometry)
            ) {
                tooLarge = true
            }
            return found
        }

        val leftPositions = scan(left).asReversed()
        positions.addAll(0, leftPositions)
        positions.addAll(scan(right))

        var deskIndex = 0
        val members = positions.mapNotNull { pos ->
            if (!AeroworksTypes.isControlDesk(level.getBlockState(pos).block)) {
                return@mapNotNull null
            }
            val desk = ConsoleAssemblyRuntime.blockEntityIfPresent(level, pos) as? ConsoleBlockEntity ?: run {
                partiallyLoaded = true
                return@mapNotNull null
            }
            val kind = when {
                ConsoleComputers.get(desk)?.isAdvanced == true ->
                    ConsoleMemberKind.ADVANCED_COMPUTER

                ConsoleComputers.get(desk) != null -> ConsoleMemberKind.COMPUTER
                else -> ConsoleMemberKind.CONTROL_DESK
            }
            val id = (desk as DeskIdentityAccess).ccaeroworks_getDeskId().toString()
            deskIndex += 1
            ConsoleMember(deskIndex, pos, desk, id, kind, facing)
        }

        val computers = members.mapNotNull { ConsoleComputers.get(it.desk) }
        val state = when {
            tooLarge -> ConsoleNetworkState.TOO_LARGE
            partiallyLoaded -> ConsoleNetworkState.PARTIALLY_LOADED
            computers.size > 1 -> ConsoleNetworkState.CONFLICT
            computers.size == 1 -> ConsoleNetworkState.ACTIVE
            else -> ConsoleNetworkState.NONE
        }
        return ConsoleMultiblockSnapshot(
            members.firstOrNull()?.pos ?: start.immutable(),
            positions.toList(),
            members,
            computers,
            state,
            revision
        )
    }

    fun compatible(state: BlockState, facing: Direction): Boolean =
        compatible(state, facing, null, null)

    fun compatibleSegment(
        state: BlockState,
        facing: Direction,
        ceiling: Boolean?,
        standGeometry: Boolean?
    ): Boolean {
        if (compatible(state, facing, ceiling, standGeometry)) return true
        if (ceiling == true || standGeometry == null) return false
        val geometry = if (standGeometry) TrainControlConsoleGeometry.STAND else TrainControlConsoleGeometry.DESK
        return TrainControlConsoleIntegration.compatible(state, facing, geometry)
    }

    private fun compatible(
        state: BlockState,
        facing: Direction,
        ceiling: Boolean?,
        standGeometry: Boolean?
    ): Boolean =
        AeroworksTypes.isControlDesk(state.block) &&
            state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) &&
            state.getValue(BlockStateProperties.HORIZONTAL_FACING) == facing &&
            (ceiling == null || booleanPropertyValue(state, "ceiling") == ceiling) &&
            (standGeometry == null ||
                (ConsoleAssemblyAdapters.profile(state)?.geometry == TrainControlConsoleGeometry.STAND) == standGeometry)

    private fun booleanPropertyValue(state: BlockState, name: String): Boolean? {
        val property = state.properties
            .filterIsInstance<BooleanProperty>()
            .firstOrNull { it.name == name }
            ?: return null
        return state.getValue(property)
    }
}

object ConsoleMultiblockManager {
    private data class Cached(val snapshot: ConsoleMultiblockSnapshot)

    private val caches = WeakHashMap<Level, MutableMap<BlockPos, Cached>>()
    private val revision = AtomicLong()

    @Synchronized
    fun resolve(level: Level, pos: BlockPos): ConsoleMultiblockSnapshot {
        val levelCache = caches.getOrPut(level) { hashMapOf() }
        val key = pos.immutable()
        levelCache[key]?.let { return it.snapshot }

        val snapshot = ConsoleMultiblockResolver.resolve(level, pos, revision.incrementAndGet())
        val cached = Cached(snapshot)
        snapshot.segmentPositions.forEach { levelCache[it] = cached }
        if (snapshot.segmentPositions.isEmpty()) levelCache[key] = cached
        return snapshot
    }

    @Synchronized
    fun invalidate(level: Level) {
        caches[level]?.clear()
    }

    @Synchronized
    fun invalidate(level: Level, pos: BlockPos) {
        val levelCache = caches[level] ?: return
        val affected = levelCache.values.filterTo(hashSetOf()) { cached ->
            cached.snapshot.segmentPositions.any { segment ->
                segment.y == pos.y &&
                    kotlin.math.abs(segment.x - pos.x) + kotlin.math.abs(segment.z - pos.z) <= 1
            } || cached.snapshot.segmentPositions.isEmpty() && levelCache[pos] === cached
        }
        if (affected.isNotEmpty()) levelCache.entries.removeIf { it.value in affected }
        levelCache.remove(pos)
    }

    @Synchronized
    private fun invalidateChunk(level: Level, chunkX: Int, chunkZ: Int) {
        val levelCache = caches[level] ?: return
        val minX = chunkX shl 4
        val minZ = chunkZ shl 4
        val maxX = minX + 15
        val maxZ = minZ + 15
        levelCache.entries.removeIf { (key, cached) ->
            key.x in (minX - 1)..(maxX + 1) && key.z in (minZ - 1)..(maxZ + 1) ||
                cached.snapshot.segmentPositions.any { segment ->
                    segment.x in (minX - 1)..(maxX + 1) && segment.z in (minZ - 1)..(maxZ + 1)
                }
        }
    }

    @SubscribeEvent
    fun onPlace(event: BlockEvent.EntityPlaceEvent) {
        val level = event.level as? Level ?: return
        if (!isNetworkSegment(event.placedBlock)) return
        invalidate(level, event.pos)

        val serverLevel = level as? ServerLevel ?: return
        val player = event.entity as? Player ?: return
        if (TrainControlConsoleIntegration.isIntegrated(event.placedBlock)) {
            val anchor = TrainControlConsoleUpdater.anchorDesks(serverLevel, event.pos).firstOrNull() ?: return
            serverLevel.server.execute {
                reconcileMergedComputers(serverLevel, anchor, player)
            }
            return
        }
        if (event.placedBlock.block is ComputerControlDeskBlock) return
        val placedPos = event.pos.immutable()

        // A normal control desk can join two previously valid console networks. Resolve the
        // resulting network after placement and collapse every duplicate embedded computer.
        serverLevel.server.execute {
            reconcileMergedComputers(serverLevel, placedPos, player)
        }
    }

    @SubscribeEvent
    fun onBreak(event: BlockEvent.BreakEvent) {
        val level = event.level as? Level ?: return
        if (isNetworkSegment(event.state)) invalidate(level, event.pos)
    }

    @SubscribeEvent
    fun onNeighbourNotify(event: BlockEvent.NeighborNotifyEvent) {
        val level = event.level as? Level ?: return
        if (isNetworkSegment(event.state)) invalidate(level, event.pos)
    }

    @SubscribeEvent
    fun onChunkLoad(event: ChunkEvent.Load) {
        val level = event.level as? Level ?: return
        invalidateChunk(level, event.chunk.pos.x, event.chunk.pos.z)
    }

    @SubscribeEvent
    fun onChunkUnload(event: ChunkEvent.Unload) {
        val level = event.level as? Level ?: return
        invalidateChunk(level, event.chunk.pos.x, event.chunk.pos.z)
    }

    @SubscribeEvent
    fun onLevelUnload(event: LevelEvent.Unload) {
        val level = event.level as? Level ?: return
        synchronized(this) {
            caches.remove(level)
        }
    }

    private fun reconcileMergedComputers(level: ServerLevel, placedPos: BlockPos, player: Player) {
        ConsoleComputerLifecycle.reconcile(level, placedPos)
    }

    private fun isNetworkSegment(state: BlockState): Boolean =
        AeroworksTypes.isControlDesk(state.block) || TrainControlConsoleIntegration.isIntegrated(state)
}
