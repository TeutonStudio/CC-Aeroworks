package de.teutonstudio.ccaeroworks.contraption

import com.simibubi.create.content.contraptions.AbstractContraptionEntity
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.api.mobile.MobilePeripheralAdapters
import de.teutonstudio.ccaeroworks.api.mobile.MobilePeripheralContext
import de.teutonstudio.ccaeroworks.api.mobile.MobilePeripheralInstance
import de.teutonstudio.ccaeroworks.config.CCServerConfig
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksTypes
import de.teutonstudio.ccaeroworks.computer.ConsoleComputers
import de.teutonstudio.ccaeroworks.network.MobileContraptionSnapshotPayload
import de.teutonstudio.ccaeroworks.train.TrainControlConsoleIntegration
import java.util.UUID
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate
import net.neoforged.neoforge.network.PacketDistributor

/** Owns only explicitly registered adapters. A failure closes the whole runtime fail-safe. */
class MobileContraptionRuntime private constructor(
    private val entity: AbstractContraptionEntity,
    private val level: ServerLevel,
    private val contexts: Map<BlockPos, Context>,
    private val entries: List<Entry>,
    private val computers: List<MobileConsoleComputer>
) : AutoCloseable {
    private var closed = false
    private var clientRevision = 0L
    private var lastFullClientSync = Long.MIN_VALUE

    val entityId: UUID get() = entity.uuid

    fun peripherals(): List<MobilePeripheralInstance> = if (closed) emptyList() else entries.map(Entry::instance)

    fun tick() {
        if (closed) return
        if (!CCServerConfig.mobileContraptionRuntimeEnabled() || entity.isRemoved) {
            close()
            return
        }
        guarded("tick") {
            contexts.values.forEach(Context::refreshWorldPosition)
            val forceClientSync = lastFullClientSync == Long.MIN_VALUE ||
                level.gameTime - lastFullClientSync >= CLIENT_FULL_SYNC_INTERVAL
            val clientUpdates = linkedMapOf<BlockPos, CompoundTag>()
            entries.forEach { entry ->
                entry.instance.tick(entry.context)
                val snapshot = entry.instance.clientSync(entry.context)
                val previous = entry.lastClientSync
                if ((snapshot.isEmpty && previous == null) || (!forceClientSync && snapshot == previous)) {
                    return@forEach
                }
                entry.lastClientSync = snapshot.copy()
                clientUpdates[entry.context.localPosition()] = snapshot
            }
            computers.forEach(MobileConsoleComputer::tick)
            clientUpdates.entries.chunked(MobileContraptionSnapshotPayload.MAX_ENTRIES).forEach { chunk ->
                clientRevision++
                PacketDistributor.sendToPlayersTrackingEntity(
                    entity,
                    MobileContraptionSnapshotPayload(
                        entity.id,
                        clientRevision,
                        chunk.associateTo(linkedMapOf()) { it.key to it.value }
                    )
                )
            }
            if (forceClientSync) lastFullClientSync = level.gameTime
        }
    }

    fun save() {
        if (closed) return
        guarded("save", ::persist)
    }

    fun controllerAt(localPos: BlockPos): MobileContraptionControlPeripheral? = entries
        .firstOrNull { it.context.localPosition() == localPos }
        ?.instance as? MobileContraptionControlPeripheral

    fun openTerminal(localPos: BlockPos, player: Player): Boolean = computers
        .firstOrNull { localPos in it.deskPositions }
        ?.openTerminal(player) == true

    private inline fun guarded(phase: String, action: () -> Unit) {
        try {
            action()
        } catch (failure: Throwable) {
            CCAeroworks.LOGGER.error(
                "[CC-Aeroworks] Closing mobile runtime for contraption {} after {} failure",
                entity.uuid,
                phase,
                failure
            )
            close()
        }
    }

    override fun close() {
        if (closed) return
        // Entity removal and runtime failures do not invariably pass through Create's normal
        // serialization hook. Persist first, while adapters and the ServerComputer are alive.
        runCatching { persist() }.onFailure { failure ->
            CCAeroworks.LOGGER.error(
                "[CC-Aeroworks] Could not flush mobile runtime for contraption {} while closing",
                entity.uuid,
                failure
            )
        }
        closed = true
        computers.asReversed().forEach { runCatching { it.close() } }
        entries.asReversed().forEach { entry ->
            runCatching { entry.instance.releaseActorOutputs() }
            runCatching { entry.instance.close() }
        }
    }

    private fun persist() {
        computers.forEach(MobileConsoleComputer::save)
        entries.forEach { entry -> entry.instance.save(entry.context) }

        // Never add synthetic block-entity NBT to unrelated contraption blocks. Only explicit
        // adapter targets and desks belonging to an active mobile computer are mutable.
        val mutablePositions = linkedSetOf<BlockPos>()
        entries.forEach { mutablePositions += it.context.localPosition() }
        computers.forEach { mutablePositions += it.deskPositions }
        mutablePositions.forEach { contexts[it]?.flush() }
    }

    private class Entry(
        val context: Context,
        val instance: MobilePeripheralInstance
    ) {
        var lastClientSync: CompoundTag? = null
    }

    private class Context(
        private val owner: AbstractContraptionEntity,
        private val serverLevel: ServerLevel,
        private val local: BlockPos,
        private val state: net.minecraft.world.level.block.state.BlockState,
        data: CompoundTag
    ) : MobilePeripheralContext {
        private var data = data
        private var world = projected()

        override fun entity(): AbstractContraptionEntity = owner
        override fun level(): ServerLevel = serverLevel
        override fun localPosition(): BlockPos = local
        override fun worldPosition(): BlockPos = world
        override fun blockState() = state
        override fun blockEntityData(): CompoundTag = data
        override fun setBlockEntityData(data: CompoundTag) { this.data = data }

        fun refreshWorldPosition() { world = projected() }

        fun flush() {
            data.putInt("x", local.x)
            data.putInt("y", local.y)
            data.putInt("z", local.z)
            owner.setBlock(local, StructureTemplate.StructureBlockInfo(local, state, data.copy()))
        }

        private fun projected(): BlockPos = BlockPos.containing(
            owner.toGlobalVector(local.center, 1f)
        )
    }

    companion object {
        @JvmStatic
        fun create(entity: AbstractContraptionEntity): MobileContraptionRuntime? {
            if (!CCServerConfig.mobileContraptionRuntimeEnabled()) return null
            val level = entity.level() as? ServerLevel ?: return null
            val contraption = entity.contraption ?: return null
            val adapters = MobilePeripheralAdapters.all()
            if (adapters.isEmpty()) return null
            val entries = mutableListOf<Entry>()
            try {
                val contexts = contraption.blocks.entries.associate { (pos, info) ->
                    val data = info.nbt()?.copy() ?: CompoundTag()
                    pos.immutable() to Context(entity, level, pos.immutable(), info.state(), data)
                }
                contexts.entries.sortedWith(compareBy({ it.key.x }, { it.key.y }, { it.key.z })).forEach { (_, context) ->
                    val data = context.blockEntityData()
                    val state = context.blockState()
                    val adapter = adapters.firstOrNull { it.matches(state, data) } ?: return@forEach
                    entries += Entry(context, adapter.create(context))
                }
                connectInternalWired(entries)
                val computers = buildComputers(entity, level, contexts, entries)
                return MobileContraptionRuntime(entity, level, contexts, entries, computers)
            } catch (failure: Throwable) {
                entries.asReversed().forEach { runCatching { it.instance.close() } }
                CCAeroworks.LOGGER.error(
                    "[CC-Aeroworks] Could not initialize mobile runtime for contraption {}",
                    entity.uuid,
                    failure
                )
                return null
            }
        }

        private fun buildComputers(
            entity: AbstractContraptionEntity,
            level: ServerLevel,
            contexts: Map<BlockPos, Context>,
            entries: List<Entry>
        ): List<MobileConsoleComputer> {
            val visited = hashSetOf<BlockPos>()
            val result = mutableListOf<MobileConsoleComputer>()
            contexts.forEach { (origin, originContext) ->
                if (origin in visited || !AeroworksTypes.isControlDesk(originContext.blockState().block)) return@forEach
                val state = originContext.blockState()
                val geometry = TrainControlConsoleIntegration.geometry(state) ?: return@forEach
                val facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING)
                val queue = ArrayDeque<BlockPos>()
                val component = linkedSetOf<BlockPos>()
                queue += origin
                // Resolve the whole physical component before enforcing the limit. Stopping at
                // 65 would let the unvisited tail of an oversized row start a second runtime.
                while (queue.isNotEmpty()) {
                    val pos = queue.removeFirst()
                    if (!component.add(pos)) continue
                    visited += pos
                    arrayOf(facing.counterClockWise, facing.clockWise).forEach { direction ->
                        val next = pos.relative(direction)
                        val nextState = contexts[next]?.blockState() ?: return@forEach
                        if (TrainControlConsoleIntegration.compatible(nextState, facing, geometry) && next !in component) {
                            queue += next
                        }
                    }
                }
                if (component.size > MAX_SEGMENTS) return@forEach
                val desks = component.filterTo(linkedSetOf()) { pos ->
                    AeroworksTypes.isControlDesk(contexts.getValue(pos).blockState().block)
                }
                val installed = desks.mapNotNull { pos ->
                    val context = contexts.getValue(pos)
                    val stack = ItemStack.parseOptional(level.registryAccess(), context.blockEntityData().getCompound(MobileConsoleComputer.INSTALLED_TAG))
                    if (ConsoleComputers.accepts(stack)) Triple(pos, context, stack) else null
                }
                if (installed.size != 1) return@forEach
                val computer = installed.single()
                val peripherals = entries
                    .filter { entry ->
                        val peripheralPos = entry.context.localPosition()
                        peripheralPos in component || Direction.values().any { direction ->
                            peripheralPos.relative(direction) in component
                        }
                    }
                    .map { it.context.localPosition() to it.instance }
                result += MobileConsoleComputer(
                    entity, level, desks, computer.first, computer.second.blockEntityData(), peripherals
                )
            }
            return result
        }

        private fun connectInternalWired(entries: List<Entry>) {
            val wired = entries.mapNotNull { entry ->
                val instance = entry.instance as? VirtualCcPeripheralInstance ?: return@mapNotNull null
                instance.wiredNode()?.let { entry.context.localPosition() to it }
            }.toMap()
            wired.forEach { (pos, node) ->
                Direction.values().forEach { direction ->
                    val neighbour = wired[pos.relative(direction)] ?: return@forEach
                    if (node !== neighbour) node.connectTo(neighbour)
                }
            }
        }

        private const val MAX_SEGMENTS = 64
        private const val CLIENT_FULL_SYNC_INTERVAL = 20L
    }
}
