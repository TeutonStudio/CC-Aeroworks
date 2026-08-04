package de.teutonstudio.ccaeroworks.multiblock

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksTypes
import de.teutonstudio.ccaeroworks.compat.aeroworks.DeskIdentityAccess
import de.teutonstudio.ccaeroworks.computer.ComputerControlDeskBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
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
    val members: List<ConsoleMember>,
    val computers: List<ComputerControlDeskBlockEntity>,
    val state: ConsoleNetworkState,
    val revision: Long
) {
    val owner: ComputerControlDeskBlockEntity?
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
                ConsoleNetworkState.NONE,
                revision
            )
        }

        val facing = startState.getValue(BlockStateProperties.HORIZONTAL_FACING)
        val ceiling = booleanPropertyValue(startState, "ceiling")
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
                if (!compatible(state, facing, ceiling)) break
                found += cursor.immutable()
                cursor = cursor.relative(direction)
            }
            if (found.size + positions.size >= MAX_MEMBERS && level.isLoaded(cursor) &&
                compatible(level.getBlockState(cursor), facing, ceiling)
            ) {
                tooLarge = true
            }
            return found
        }

        val leftPositions = scan(left).asReversed()
        positions.addAll(0, leftPositions)
        positions.addAll(scan(right))

        val members = positions.mapIndexedNotNull { index, pos ->
            val desk = level.getBlockEntity(pos) as? ConsoleBlockEntity ?: run {
                partiallyLoaded = true
                return@mapIndexedNotNull null
            }
            val kind = when {
                desk is ComputerControlDeskBlockEntity && desk.isAdvanced ->
                    ConsoleMemberKind.ADVANCED_COMPUTER

                desk is ComputerControlDeskBlockEntity -> ConsoleMemberKind.COMPUTER
                else -> ConsoleMemberKind.CONTROL_DESK
            }
            val id = (desk as DeskIdentityAccess).ccaeroworks_getDeskId().toString()
            ConsoleMember(index + 1, pos, desk, id, kind, facing)
        }

        val computers = members.mapNotNull { it.desk as? ComputerControlDeskBlockEntity }
        val state = when {
            tooLarge -> ConsoleNetworkState.TOO_LARGE
            partiallyLoaded || members.size != positions.size -> ConsoleNetworkState.PARTIALLY_LOADED
            computers.size > 1 -> ConsoleNetworkState.CONFLICT
            computers.size == 1 -> ConsoleNetworkState.ACTIVE
            else -> ConsoleNetworkState.NONE
        }
        return ConsoleMultiblockSnapshot(
            members.firstOrNull()?.pos ?: start.immutable(),
            members,
            computers,
            state,
            revision
        )
    }

    fun compatible(state: BlockState, facing: Direction): Boolean =
        compatible(state, facing, null)

    private fun compatible(
        state: BlockState,
        facing: Direction,
        ceiling: Boolean?
    ): Boolean =
        AeroworksTypes.isControlDesk(state.block) &&
            state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) &&
            state.getValue(BlockStateProperties.HORIZONTAL_FACING) == facing &&
            (ceiling == null || booleanPropertyValue(state, "ceiling") == ceiling)

    private fun booleanPropertyValue(state: BlockState, name: String): Boolean? {
        val property = state.properties
            .filterIsInstance<BooleanProperty>()
            .firstOrNull { it.name == name }
            ?: return null
        return state.getValue(property)
    }
}

object ConsoleMultiblockManager {
    private data class Cached(val generation: Long, val snapshot: ConsoleMultiblockSnapshot)

    private val caches = WeakHashMap<Level, MutableMap<BlockPos, Cached>>()
    private val generations = WeakHashMap<Level, Long>()
    private val revision = AtomicLong()

    @Synchronized
    fun resolve(level: Level, pos: BlockPos): ConsoleMultiblockSnapshot {
        val generation = generations[level] ?: 0L
        val levelCache = caches.getOrPut(level) { hashMapOf() }
        val key = pos.immutable()
        levelCache[key]?.takeIf { it.generation == generation }?.let { return it.snapshot }

        val snapshot = ConsoleMultiblockResolver.resolve(level, pos, revision.incrementAndGet())
        val cached = Cached(generation, snapshot)
        snapshot.members.forEach { levelCache[it.pos] = cached }
        if (snapshot.members.isEmpty()) levelCache[key] = cached
        return snapshot
    }

    @Synchronized
    fun invalidate(level: Level) {
        generations[level] = (generations[level] ?: 0L) + 1L
        caches[level]?.clear()
    }

    @SubscribeEvent
    fun onPlace(event: BlockEvent.EntityPlaceEvent) {
        val level = event.level as? Level ?: return
        if (AeroworksTypes.isControlDesk(event.placedBlock.block)) invalidate(level)
    }

    @SubscribeEvent
    fun onBreak(event: BlockEvent.BreakEvent) {
        val level = event.level as? Level ?: return
        if (AeroworksTypes.isControlDesk(event.state.block)) invalidate(level)
    }

    @SubscribeEvent
    fun onNeighbourNotify(event: BlockEvent.NeighborNotifyEvent) {
        val level = event.level as? Level ?: return
        if (AeroworksTypes.isControlDesk(event.state.block)) invalidate(level)
    }

    @SubscribeEvent
    fun onChunkLoad(event: ChunkEvent.Load) {
        (event.level as? Level)?.let(::invalidate)
    }

    @SubscribeEvent
    fun onChunkUnload(event: ChunkEvent.Unload) {
        (event.level as? Level)?.let(::invalidate)
    }

    @SubscribeEvent
    fun onLevelUnload(event: LevelEvent.Unload) {
        val level = event.level as? Level ?: return
        synchronized(this) {
            caches.remove(level)
            generations.remove(level)
        }
    }
}
