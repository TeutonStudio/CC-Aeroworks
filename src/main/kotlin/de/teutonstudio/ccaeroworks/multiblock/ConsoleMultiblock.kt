package de.teutonstudio.ccaeroworks.multiblock

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import dan200.computercraft.shared.ModRegistry
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksTypes
import de.teutonstudio.ccaeroworks.compat.aeroworks.DeskIdentityAccess
import de.teutonstudio.ccaeroworks.computer.ComputerControlDeskBlock
import de.teutonstudio.ccaeroworks.computer.ComputerControlDeskBlockEntity
import de.teutonstudio.ccaeroworks.registry.CCItems
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
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
        if (!AeroworksTypes.isControlDesk(event.placedBlock.block)) return
        invalidate(level)

        val serverLevel = level as? ServerLevel ?: return
        if (event.placedBlock.block is ComputerControlDeskBlock) return
        val player = event.entity as? Player ?: return
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

    private fun reconcileMergedComputers(
        level: ServerLevel,
        placedPos: BlockPos,
        player: Player
    ) {
        invalidate(level)
        val network = resolve(level, placedPos)
        if (network.state != ConsoleNetworkState.CONFLICT) return

        val preferredComputer = network.computers.minWithOrNull(
            compareBy<ComputerControlDeskBlockEntity>(
                { if (it.isAdvanced) 0 else 1 },
                { network.memberAt(it.blockPos)?.index ?: Int.MAX_VALUE }
            )
        ) ?: return

        var ejected = 0
        network.computers
            .filterNot { it.blockPos == preferredComputer.blockPos }
            .forEach { duplicate ->
                if (ejectComputer(level, duplicate)) ejected++
            }

        if (ejected == 0) return
        invalidate(level)
        resolve(level, preferredComputer.blockPos)
        player.displayClientMessage(
            Component.translatable("message.cc_aeroworks.computer_ejected"),
            true
        )
    }

    private fun ejectComputer(
        level: ServerLevel,
        desk: ComputerControlDeskBlockEntity
    ): Boolean {
        val pos = desk.blockPos
        if (level.getBlockEntity(pos) !== desk) return false

        val savedDesk = desk.saveWithFullMetadata(level.registryAccess())
        val combinedStack = ItemStack(
            if (desk.isAdvanced) CCItems.ADVANCED_COMPUTER_CONTROL_DESK.get()
            else CCItems.COMPUTER_CONTROL_DESK.get()
        )
        desk.writeToItem(combinedStack)
        val computerStack = standaloneComputer(combinedStack, desk.isAdvanced)

        var replacement = AeroworksTypes.vanillaControlDeskBlock().defaultBlockState()
        val currentState = desk.blockState
        if (replacement.hasProperty(BlockStateProperties.HORIZONTAL_FACING) &&
            currentState.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
        ) {
            replacement = replacement.setValue(
                BlockStateProperties.HORIZONTAL_FACING,
                currentState.getValue(BlockStateProperties.HORIZONTAL_FACING)
            )
        }

        if (!level.setBlock(pos, replacement, Block.UPDATE_ALL)) return false
        val replacementEntity = level.getBlockEntity(pos) as? ConsoleBlockEntity
        replacementEntity?.loadWithComponents(savedDesk, level.registryAccess())
        replacementEntity?.setChanged()
        level.sendBlockUpdated(pos, replacement, replacement, Block.UPDATE_ALL)

        Block.popResource(level, pos.above(), computerStack)
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6f, 0.9f)
        return true
    }

    private fun standaloneComputer(source: ItemStack, advanced: Boolean): ItemStack {
        val result = ItemStack(
            if (advanced) ModRegistry.Items.COMPUTER_ADVANCED.get()
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
