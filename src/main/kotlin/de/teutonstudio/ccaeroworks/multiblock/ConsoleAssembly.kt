package de.teutonstudio.ccaeroworks.multiblock

import com.mred231.aeroworks.content.controls.console.ConsoleBlock
import com.mred231.aeroworks.content.controls.console.DeskSkin
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.computer.ComputerControlDeskBlock
import de.teutonstudio.ccaeroworks.train.TrainControlConsoleGeometry
import de.teutonstudio.ccaeroworks.train.TrainControlConsoleIntegration
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.chunk.LevelChunk
import java.util.UUID
import java.util.WeakHashMap

/** Persistent row membership shared by native Aeroworks desks and Create controls. */
interface ConsoleAssemblyStateAccess {
    fun ccaeroworks_getAssemblyId(): UUID?
    fun ccaeroworks_setAssemblyId(id: UUID?)
    fun ccaeroworks_isLegacyAssembly(): Boolean
    fun ccaeroworks_setLegacyAssembly(legacy: Boolean)
}

/** Copycat state which belongs to a row segment, including Create control inserts. */
interface ConsoleAssemblyStyleAccess {
    fun ccaeroworks_getAssemblySkin(): DeskSkin
    fun ccaeroworks_getStyleRevision(): Long
    fun ccaeroworks_setStyleRevision(revision: Long)
}

/** Public Java-facing extension point. Implementations must classify exact registry IDs. */
interface ConsoleAssemblyAdapter {
    val id: ResourceLocation
    val geometry: TrainControlConsoleGeometry
    val copycat: Boolean
    val ceilingAllowed: Boolean
    val placementItemId: ResourceLocation get() = id
    val shapeBlockId: ResourceLocation get() = id
    val renderAnchor: ConsoleInsertAnchor
        get() = if (geometry == TrainControlConsoleGeometry.STAND) ConsoleInsertAnchor.STAND else ConsoleInsertAnchor.DESK
}

enum class ConsoleInsertAnchor { DESK, STAND }

data class ConsoleAssemblyProfile(
    override val id: ResourceLocation,
    override val geometry: TrainControlConsoleGeometry,
    override val copycat: Boolean,
    override val ceilingAllowed: Boolean,
    override val placementItemId: ResourceLocation = id,
    override val shapeBlockId: ResourceLocation = id,
    override val renderAnchor: ConsoleInsertAnchor =
        if (geometry == TrainControlConsoleGeometry.STAND) ConsoleInsertAnchor.STAND else ConsoleInsertAnchor.DESK
) : ConsoleAssemblyAdapter

/**
 * Exact, extensible classification. Aeroworks 1.5.0's normal ControlStandBlock inherits
 * standGeometry() == false, so class/virtual-method inference is deliberately forbidden here.
 */
object ConsoleAssemblyAdapters {
    private val profiles = linkedMapOf<ResourceLocation, ConsoleAssemblyAdapter>()

    init {
        register("aeroworks", "control_desk", TrainControlConsoleGeometry.DESK, false, true)
        register("aeroworks", "copycat_control_desk", TrainControlConsoleGeometry.DESK, true, true)
        register("aeroworks", "control_stand", TrainControlConsoleGeometry.STAND, false, false)
        register("aeroworks", "copycat_control_stand", TrainControlConsoleGeometry.STAND, true, false)

        register(CCAeroworks.MOD_ID, "computer_control_desk", TrainControlConsoleGeometry.DESK, false, true)
        register(CCAeroworks.MOD_ID, "advanced_computer_control_desk", TrainControlConsoleGeometry.DESK, false, true)
        register(CCAeroworks.MOD_ID, "computer_copycat_control_desk", TrainControlConsoleGeometry.DESK, true, true)
        register(CCAeroworks.MOD_ID, "advanced_computer_copycat_control_desk", TrainControlConsoleGeometry.DESK, true, true)
        register(CCAeroworks.MOD_ID, "computer_control_stand", TrainControlConsoleGeometry.STAND, false, false)
        register(CCAeroworks.MOD_ID, "advanced_computer_control_stand", TrainControlConsoleGeometry.STAND, false, false)
        register(CCAeroworks.MOD_ID, "computer_copycat_control_stand", TrainControlConsoleGeometry.STAND, true, false)
        register(CCAeroworks.MOD_ID, "advanced_computer_copycat_control_stand", TrainControlConsoleGeometry.STAND, true, false)
    }

    @JvmStatic
    fun register(profile: ConsoleAssemblyAdapter) {
        require(profile.geometry != TrainControlConsoleGeometry.NONE) { "Console profile must have desk or stand geometry" }
        profiles[profile.id] = profile
    }

    @JvmStatic
    fun profile(block: Block): ConsoleAssemblyAdapter? {
        val id = BuiltInRegistries.BLOCK.getKey(block)
        profiles[id]?.let { return it }
        val computer = block as? ComputerControlDeskBlock ?: return null
        return ConsoleAssemblyProfile(
            id,
            if (computer.variant.standGeometry) TrainControlConsoleGeometry.STAND else TrainControlConsoleGeometry.DESK,
            computer.variant.name.startsWith("COPYCAT"),
            !computer.variant.standGeometry
        )
    }

    @JvmStatic
    fun profile(state: BlockState): ConsoleAssemblyAdapter? = profile(state.block)

    private fun register(
        namespace: String,
        path: String,
        geometry: TrainControlConsoleGeometry,
        copycat: Boolean,
        ceilingAllowed: Boolean
    ) = register(
        ConsoleAssemblyProfile(
            ResourceLocation.fromNamespaceAndPath(namespace, path),
            geometry,
            copycat,
            ceilingAllowed
        )
    )
}

data class ConsolePlacementIntent(
    val placedPos: BlockPos,
    val targetPos: BlockPos?,
    val forceSeparate: Boolean
)

/** Captures the clicked row before BlockItem creates the new block entity. */
object ConsoleAssemblyPlacement {
    private val intents = WeakHashMap<Level, MutableMap<BlockPos, ConsolePlacementIntent>>()

    @JvmStatic
    fun capture(context: BlockPlaceContext) {
        val level = context.level
        if (level.isClientSide) return
        val placedPos = context.clickedPos.immutable()
        val separate = context.player?.isShiftKeyDown == true
        val target = if (separate || context.replacingClickedOnBlock()) {
            null
        } else {
            val candidate = placedPos.relative(context.clickedFace.opposite).immutable()
            val targetState = level.getBlockState(candidate)
            val facing = targetState.takeIf { it.hasProperty(BlockStateProperties.HORIZONTAL_FACING) }
                ?.getValue(BlockStateProperties.HORIZONTAL_FACING)
            if (facing != null &&
                ConsoleAssemblyPlacementHelper.lateralDirection(facing, context.clickedFace) != null
            ) candidate else null
        }
        capture(level, placedPos, target, separate)
    }

    @JvmStatic
    fun capture(level: Level, placedPos: BlockPos, targetPos: BlockPos?, forceSeparate: Boolean) {
        if (level.isClientSide) return
        synchronized(intents) {
            intents.getOrPut(level) { hashMapOf() }[placedPos.immutable()] = ConsolePlacementIntent(
                placedPos.immutable(),
                targetPos?.immutable(),
                forceSeparate
            )
        }
    }

    fun consume(level: Level, pos: BlockPos): ConsolePlacementIntent? = synchronized(intents) {
        val byPos = intents[level] ?: return@synchronized null
        val result = byPos.remove(pos)
        if (byPos.isEmpty()) intents.remove(level)
        result
    }
}

object ConsoleAssemblyRuntime {
    const val MAX_SEGMENTS: Int = 64

    @JvmStatic
    fun access(level: Level, pos: BlockPos): ConsoleAssemblyStateAccess? {
        (blockEntityIfPresent(level, pos) as? ConsoleAssemblyStateAccess)?.let { return it }
        val server = level as? ServerLevel ?: return null
        val state = level.getBlockState(pos)
        if (!TrainControlConsoleIntegration.isTrainControls(state)) return null
        // Worlds created before explicit assembly IDs have no BE tag for create:controls.
        val migrated = ConsoleAssemblyBlockEntity(pos, state)
        server.setBlockEntity(migrated)
        return migrated
    }

    /**
     * Avoids instantiating a pending block entity while TeutonLib is inside BlockEntity#setLevel.
     * Level#getBlockEntity uses IMMEDIATE creation and would otherwise recursively enter its
     * peripheral-owner resolver before the entity has reached the chunk's live entity map.
     */
    @JvmStatic
    fun blockEntityIfPresent(level: Level, pos: BlockPos): BlockEntity? {
        if (!level.isLoaded(pos)) return null
        return level.getChunkAt(pos).getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK)
    }

    @JvmStatic
    fun id(level: Level, pos: BlockPos): UUID? = access(level, pos)?.ccaeroworks_getAssemblyId()

    @JvmStatic
    fun connected(level: Level, first: BlockPos, second: BlockPos): Boolean {
        if (!level.isLoaded(first) || !level.isLoaded(second)) return false
        val firstState = level.getBlockState(first)
        val secondState = level.getBlockState(second)
        if (!samePhysicalProfile(firstState, secondState)) return false
        val firstId = id(level, first)
        val secondId = id(level, second)
        if (firstId != null || secondId != null) return firstId != null && firstId == secondId

        // A pair without IDs is an unmigrated legacy row. Keeping its old physical edge open
        // lets the resolver discover and atomically stamp the complete component. Newly placed
        // rows receive an ID in EntityPlaceEvent, so adjacent explicit rows never use this path.
        val facing = firstState.getValue(BlockStateProperties.HORIZONTAL_FACING)
        val geometry = geometry(firstState) ?: return false
        return legacyConnected(level, first, firstState, second, secondState, facing, geometry)
    }

    @JvmStatic
    fun samePhysicalProfile(first: BlockState, second: BlockState): Boolean {
        if (!first.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ||
            !second.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ||
            first.getValue(BlockStateProperties.HORIZONTAL_FACING) != second.getValue(BlockStateProperties.HORIZONTAL_FACING)
        ) return false
        val firstGeometry = geometry(first) ?: return false
        if (firstGeometry != geometry(second)) return false
        val firstCeiling = if (first.hasProperty(ConsoleBlock.CEILING)) first.getValue(ConsoleBlock.CEILING) else false
        val secondCeiling = if (second.hasProperty(ConsoleBlock.CEILING)) second.getValue(ConsoleBlock.CEILING) else false
        return firstCeiling == secondCeiling
    }

    @JvmStatic
    fun geometry(state: BlockState): TrainControlConsoleGeometry? {
        val profile = ConsoleAssemblyAdapters.profile(state)
        if (profile != null) {
            val ceiling = state.hasProperty(ConsoleBlock.CEILING) && state.getValue(ConsoleBlock.CEILING)
            return if (!ceiling || profile.ceilingAllowed) profile.geometry else null
        }
        return TrainControlConsoleIntegration.integratedGeometry(state)
    }

    /** Create control inserts are floor-only even though desk profiles may form native ceiling rows. */
    @JvmStatic
    fun acceptsCreateControl(state: BlockState): Boolean =
        ConsoleAssemblyAdapters.profile(state) != null &&
            (!state.hasProperty(ConsoleBlock.CEILING) || !state.getValue(ConsoleBlock.CEILING))

    fun initializePlaced(level: ServerLevel, pos: BlockPos, placer: LivingEntity? = null) {
        if (!level.isLoaded(pos)) return
        val state = level.getBlockState(pos)
        val isDesk = ConsoleAssemblyAdapters.profile(state) != null
        val isControl = TrainControlConsoleIntegration.isConsoleControl(state)
        if (!isDesk && !isControl) return
        val access = access(level, pos) ?: return
        val intent = ConsoleAssemblyPlacement.consume(level, pos)
        if (intent == null && access.ccaeroworks_getAssemblyId() != null) return

        if (intent?.forceSeparate == true) {
            assign(level, pos, access, if (isDesk) UUID.randomUUID() else null, false)
            return
        }

        val targetPos = intent?.targetPos
        if (targetPos != null && level.isLoaded(targetPos)) {
            val targetState = level.getBlockState(targetPos)
            val targetId = id(level, targetPos)
            if (targetId != null && samePhysicalProfile(state, targetState)) {
                val targetRow = members(level, targetPos)
                if (targetRow.complete && !targetRow.tooLong && targetRow.positions.size < MAX_SEGMENTS) {
                    if (ConsoleAssemblyStyles.inherit(level, targetPos, pos, placer)) {
                        if (isControl) {
                            val profiled = level.getBlockState(pos).setValue(
                                TrainControlConsoleIntegration.COPYCAT,
                                TrainControlConsoleIntegration.copycat(targetState)
                            )
                            if (profiled != level.getBlockState(pos)) {
                                setStatePreservingBlockEntity(level, pos, profiled)
                            }
                        }
                        assign(level, pos, access(level, pos) ?: access, targetId, false)
                        return
                    }
                    if (isDesk) {
                        assign(level, pos, access, UUID.randomUUID(), false)
                    } else {
                        assign(level, pos, access, null, false)
                        level.setBlock(pos, TrainControlConsoleIntegration.reset(state), Block.UPDATE_ALL)
                    }
                    return
                }
            }
            if (isDesk && acceptsCreateControl(state) && TrainControlConsoleIntegration.isConsoleControl(targetState) &&
                !TrainControlConsoleIntegration.isIntegrated(targetState)
            ) {
                val newId = UUID.randomUUID()
                val native = nativeControlRun(level, targetPos, state)
                if (native.complete && !native.tooLong && native.positions.size < MAX_SEGMENTS) {
                    if (!ConsoleAssemblyStyles.inheritAll(level, pos, native.positions, placer)) {
                        assign(level, pos, access, newId, false)
                        return
                    }
                    assign(level, pos, access, newId, false)
                    recruitNativeControls(level, native.positions, state, newId)
                    return
                }
            }
        }

        if (isDesk) {
            assign(level, pos, access, UUID.randomUUID(), false)
        } else if (TrainControlConsoleIntegration.isIntegrated(state)) {
            val candidates = lateralPositions(state, pos).mapNotNull { neighbour ->
                if (level.isLoaded(neighbour) && samePhysicalProfile(state, level.getBlockState(neighbour))) {
                    id(level, neighbour)?.let { it to neighbour }
                } else null
            }.distinctBy { it.first }
            val inherited = candidates.singleOrNull()
            if (inherited != null) {
                val profiled = state.setValue(
                    TrainControlConsoleIntegration.COPYCAT,
                    TrainControlConsoleIntegration.copycat(level.getBlockState(inherited.second))
                )
                if (profiled != state) setStatePreservingBlockEntity(level, pos, profiled)
            }
            assign(level, pos, access(level, pos) ?: access, inherited?.first, false)
            if (inherited == null) {
                level.setBlock(pos, TrainControlConsoleIntegration.reset(state), Block.UPDATE_ALL)
            }
        } else {
            assign(level, pos, access, null, false)
        }
    }

    /** Assign one UUID to old rows which predate explicit assembly state. */
    fun ensureLegacyComponent(level: ServerLevel, start: BlockPos): UUID? {
        val startAccess = access(level, start) ?: return null
        val currentId = startAccess.ccaeroworks_getAssemblyId()
        if (currentId != null && !startAccess.ccaeroworks_isLegacyAssembly()) return currentId
        val startState = level.getBlockState(start)
        val geometry = geometry(startState) ?: return null
        if (!startState.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return null
        if (TrainControlConsoleIntegration.isConsoleControl(startState) && !TrainControlConsoleIntegration.isIntegrated(startState)) return null
        val facing = startState.getValue(BlockStateProperties.HORIZONTAL_FACING)
        val positions = linkedSetOf(start.immutable())
        for (direction in arrayOf(facing.counterClockWise, facing.clockWise)) {
            var cursor = start.relative(direction)
            while (positions.size < 64 && level.isLoaded(cursor)) {
                val candidate = level.getBlockState(cursor)
                val candidateAccess = access(level, cursor) ?: break
                if (candidateAccess.ccaeroworks_getAssemblyId() != null &&
                    !candidateAccess.ccaeroworks_isLegacyAssembly()
                ) break
                if (geometry(candidate) != geometry ||
                    !legacyConnected(level, start, startState, cursor, candidate, facing, geometry)
                ) break
                positions += cursor.immutable()
                cursor = cursor.relative(direction)
            }
        }
        val existing = positions.mapNotNull { id(level, it) }.minByOrNull(UUID::toString)
        val assigned = existing ?: UUID.randomUUID()
        positions.forEach { member ->
            access(level, member)?.let { assign(level, member, it, assigned, true) }
        }
        return assigned
    }

    data class RowMembers(
        val positions: List<BlockPos>,
        val complete: Boolean,
        val tooLong: Boolean
    )

    /** Resolve one straight UUID row without crossing unloaded chunks or a 65th segment. */
    @JvmStatic
    fun members(level: Level, start: BlockPos): RowMembers {
        if (!level.isLoaded(start)) return RowMembers(emptyList(), false, false)
        val state = level.getBlockState(start)
        val rowId = id(level, start) ?: return RowMembers(listOf(start.immutable()), true, false)
        if (!state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return RowMembers(listOf(start.immutable()), true, false)
        }
        val facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING)
        val positions = linkedSetOf(start.immutable())
        var complete = true
        var tooLong = false
        for (direction in arrayOf(facing.counterClockWise, facing.clockWise)) {
            var cursor = start.relative(direction)
            while (true) {
                if (!level.isLoaded(cursor)) {
                    complete = false
                    break
                }
                if (id(level, cursor) != rowId || !samePhysicalProfile(state, level.getBlockState(cursor))) break
                if (positions.size >= MAX_SEGMENTS) {
                    tooLong = true
                    break
                }
                positions += cursor.immutable()
                cursor = cursor.relative(direction)
            }
        }
        return RowMembers(positions.toList(), complete, tooLong)
    }

    private fun recruitNativeControls(
        level: ServerLevel,
        positions: Collection<BlockPos>,
        deskState: BlockState,
        id: UUID
    ) {
        val profile = ConsoleAssemblyAdapters.profile(deskState) ?: return
        val facing = deskState.getValue(BlockStateProperties.HORIZONTAL_FACING)
        positions.forEach { pos ->
            if (!level.isLoaded(pos)) return@forEach
            var state = level.getBlockState(pos)
            if (!TrainControlConsoleIntegration.isConsoleControl(state)) return@forEach
            if (state.getValue(BlockStateProperties.HORIZONTAL_FACING) != facing) return@forEach
            state = state
                .setValue(TrainControlConsoleIntegration.GEOMETRY, profile.geometry)
                .setValue(TrainControlConsoleIntegration.COPYCAT, profile.copycat)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
            setStatePreservingBlockEntity(
                level,
                pos,
                TrainControlConsoleIntegration.applyConnections(level, pos, state)
            )
            access(level, pos)?.let { assign(level, pos, it, id, false) }
        }
    }

    private fun nativeControlRun(level: Level, start: BlockPos, deskState: BlockState): RowMembers {
        val facing = deskState.getValue(BlockStateProperties.HORIZONTAL_FACING)
        val result = linkedSetOf<BlockPos>()
        var complete = true
        var tooLong = false
        for (direction in arrayOf(facing.counterClockWise, facing.clockWise)) {
            var cursor = if (direction == facing.counterClockWise) start else start.relative(direction)
            while (true) {
                if (!level.isLoaded(cursor)) {
                    complete = false
                    break
                }
                val state = level.getBlockState(cursor)
                if (!TrainControlConsoleIntegration.isConsoleControl(state) ||
                    TrainControlConsoleIntegration.isIntegrated(state) ||
                    !state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ||
                    state.getValue(BlockStateProperties.HORIZONTAL_FACING) != facing
                ) break
                if (result.size >= MAX_SEGMENTS) {
                    tooLong = true
                    break
                }
                result += cursor.immutable()
                cursor = cursor.relative(direction)
            }
        }
        return RowMembers(result.toList(), complete, tooLong)
    }

    private fun assign(
        level: ServerLevel,
        pos: BlockPos,
        access: ConsoleAssemblyStateAccess,
        id: UUID?,
        legacy: Boolean
    ) {
        access.ccaeroworks_setAssemblyId(id)
        access.ccaeroworks_setLegacyAssembly(legacy)
        (access as? net.minecraft.world.level.block.entity.BlockEntity)?.let { blockEntity ->
            blockEntity.setChanged()
            level.sendBlockUpdated(pos, blockEntity.blockState, blockEntity.blockState, Block.UPDATE_CLIENTS)
        }
    }

    private fun setStatePreservingBlockEntity(level: ServerLevel, pos: BlockPos, state: BlockState) {
        val before = blockEntityIfPresent(level, pos)
        val saved = before?.saveWithoutMetadata(level.registryAccess())
        level.setBlock(pos, state, Block.UPDATE_ALL)
        if (saved != null) {
            level.getBlockEntity(pos)?.loadWithComponents(saved, level.registryAccess())
        }
    }

    private fun legacyConnected(
        level: Level,
        startPos: BlockPos,
        startState: BlockState,
        candidatePos: BlockPos,
        state: BlockState,
        facing: Direction,
        geometry: TrainControlConsoleGeometry
    ): Boolean {
        if (!state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ||
            state.getValue(BlockStateProperties.HORIZONTAL_FACING) != facing ||
            this.geometry(state) != geometry
        ) return false
        val firstProfile = ConsoleAssemblyAdapters.profile(startState)
        val secondProfile = ConsoleAssemblyAdapters.profile(state)
        if (firstProfile != null && secondProfile != null && firstProfile.copycat != secondProfile.copycat) return false
        if (firstProfile?.copycat != true || secondProfile == null) return true
        val firstSkin = (level.getBlockEntity(startPos) as? ConsoleAssemblyStyleAccess)
            ?.ccaeroworks_getAssemblySkin()
        val secondSkin = (level.getBlockEntity(candidatePos) as? ConsoleAssemblyStyleAccess)
            ?.ccaeroworks_getAssemblySkin()
        return DeskSkin.Zone.entries.all { zone ->
            firstSkin?.material(zone) == secondSkin?.material(zone)
        }
    }

    private fun lateralPositions(state: BlockState, pos: BlockPos): List<BlockPos> {
        val facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING)
        return listOf(pos.relative(facing.counterClockWise), pos.relative(facing.clockWise))
    }
}
