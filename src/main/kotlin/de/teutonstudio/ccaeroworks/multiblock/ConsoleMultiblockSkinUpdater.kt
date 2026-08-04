package de.teutonstudio.ccaeroworks.multiblock

import dan200.computercraft.shared.ModRegistry
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksTypes
import de.teutonstudio.ccaeroworks.computer.ComputerControlDeskBlockEntity
import de.teutonstudio.ccaeroworks.mixin.BlockEntityComponentInvoker
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.component.DataComponents
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
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
        if (!AeroworksTypes.isControlDesk(event.placedBlock.block)) return
        schedule(
            level,
            event.pos,
            normalizeComputer = AeroworksTypes.isComputerControlDesk(event.placedBlock.block)
        )
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
            ConsoleMultiblockManager.invalidate(level)
            if (normalizeComputer && normalizeDuplicateComputer(level, origin)) {
                ConsoleMultiblockManager.invalidate(level)
            }

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
                val lastIndex = snapshot.members.lastIndex
                snapshot.members.forEachIndexed memberLoop@{ index, member ->
                    val state = level.getBlockState(member.pos)
                    var nextState = state
                    val connectedWest = index > 0
                    val connectedEast = index < lastIndex
                    nextState = setBooleanProperty(
                        nextState,
                        "open_west",
                        connectedWest
                    )
                    nextState = setBooleanProperty(
                        nextState,
                        "open_east",
                        connectedEast
                    )
                    if (nextState.hasProperty(ConsoleMultiblockSkinState.SKIN)) {
                        nextState = nextState.setValue(ConsoleMultiblockSkinState.SKIN, skin)
                    }
                    if (nextState == state) return@memberLoop

                    level.setBlock(
                        member.pos,
                        nextState,
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

    private fun normalizeDuplicateComputer(level: ServerLevel, origin: BlockPos): Boolean {
        val placed = level.getBlockEntity(origin) as? ComputerControlDeskBlockEntity ?: return false
        val snapshot = ConsoleMultiblockResolver.resolve(
            level,
            origin,
            revision.incrementAndGet()
        )
        if (snapshot.computers.none { it.blockPos != origin }) return false

        val normalBlock = AeroworksTypes.vanillaControlDeskBlock()
        val preservedDesk = ItemStack(normalBlock)
        placed.writeToItem(preservedDesk)
        val computerDrop = createComputerDrop(placed, preservedDesk)
        val replacementState = copyDeskState(
            level.getBlockState(origin),
            normalBlock.defaultBlockState()
        )

        if (!level.setBlock(origin, replacementState, Block.UPDATE_ALL)) return false

        level.getBlockEntity(origin)?.let { replacementEntity ->
            (replacementEntity as BlockEntityComponentInvoker)
                .ccaeroworks_applyComponentsFromItemStack(preservedDesk)
            replacementEntity.setChanged()
            level.sendBlockUpdated(
                origin,
                replacementState,
                replacementState,
                Block.UPDATE_CLIENTS
            )
        }

        Block.popResource(level, origin, computerDrop)
        return true
    }

    private fun createComputerDrop(
        placed: ComputerControlDeskBlockEntity,
        source: ItemStack
    ): ItemStack {
        val computer = ItemStack(
            if (placed.isAdvanced) ModRegistry.Items.COMPUTER_ADVANCED.get()
            else ModRegistry.Items.COMPUTER_NORMAL.get()
        )
        copyComponent(source, computer, ModRegistry.DataComponents.COMPUTER_ID.get())
        copyComponent(source, computer, ModRegistry.DataComponents.STORAGE_CAPACITY.get())
        copyComponent(source, computer, ModRegistry.DataComponents.TERMINAL_SIZE.get())
        copyComponent(source, computer, DataComponents.CUSTOM_NAME)
        return computer
    }

    private fun <T : Any> copyComponent(
        source: ItemStack,
        target: ItemStack,
        type: DataComponentType<T>
    ) {
        target.set(type, source.get(type))
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

    private val HORIZONTAL_DIRECTIONS = arrayOf(
        Direction.NORTH,
        Direction.EAST,
        Direction.SOUTH,
        Direction.WEST
    )
}
