package de.teutonstudio.ccaeroworks.telemetry

import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.computer.ComputerControlDeskBlockEntity
import dev.ryanhcode.sable.Sable
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.entity.BlockEntity
import net.neoforged.fml.ModList
import net.neoforged.neoforge.capabilities.Capabilities
import java.lang.reflect.Method

internal data class DockSnapshot(
    val id: String,
    val alias: String?,
    val pos: BlockPos,
    val state: String,
    val connected: Boolean,
    val locked: Boolean,
    val extended: Boolean,
    val retracted: Boolean,
    val localSubLevelId: String?,
    val remote: BlockEntity?,
    val remoteSubLevelId: String?,
    val remoteName: String?
) {
    fun fingerprint(): String = listOf(
        state,
        connected,
        locked,
        extended,
        retracted,
        remoteSubLevelId.orEmpty(),
        remoteName.orEmpty(),
        remote?.blockPos?.asLong() ?: Long.MIN_VALUE
    ).joinToString("|")

    fun toLua(level: ServerLevel): Map<String, Any> = linkedMapOf<String, Any>(
        "id" to id,
        "state" to state,
        "connected" to connected,
        "locked" to locked,
        "extended" to extended,
        "retracted" to retracted,
        "position" to position(pos, level.dimension().location().toString(), localSubLevelId),
        "telemetryAvailable" to (remote?.let { TelemetryRuntime.endpoint(it) } != null)
    ).apply {
        alias?.let { put("alias", it) }
        localSubLevelId?.let { put("subLevelId", it) }
        remote?.let { remoteBe ->
            put(
                "remote",
                linkedMapOf<String, Any>(
                    "position" to position(
                        remoteBe.blockPos,
                        level.dimension().location().toString(),
                        remoteSubLevelId
                    )
                ).apply {
                    remoteSubLevelId?.let { put("subLevelId", it) }
                    remoteName?.let { put("name", it) }
                }
            )
        }
    }
}

internal object SimulatedDockDiscovery {
    fun available(): Boolean =
        ModList.get().isLoaded("simulated") && ModList.get().isLoaded("sable") && SimulatedDockAccess.available()

    fun discover(owner: ComputerControlDeskBlockEntity): List<DockSnapshot> {
        if (!available()) return emptyList()
        val level = owner.level as? ServerLevel ?: return emptyList()
        val subLevel = runCatching { Sable.HELPER.getContaining(owner) }.getOrNull() ?: return emptyList()
        val localSubLevelId = subLevel.uniqueId.toString()
        val found = linkedMapOf<BlockPos, BlockEntity>()

        subLevel.plot.loadedChunks.forEach { holder ->
            holder.chunk.blockEntities.values.forEach { blockEntity ->
                if (SimulatedDockAccess.isDock(blockEntity)) {
                    found[blockEntity.blockPos.immutable()] = blockEntity
                }
            }
        }

        return found.values
            .sortedWith(compareBy({ it.blockPos.x }, { it.blockPos.y }, { it.blockPos.z }))
            .mapNotNull { dock -> snapshot(level, dock, localSubLevelId) }
    }

    fun snapshot(level: ServerLevel, dock: BlockEntity, localSubLevelId: String? = TelemetryIdentity.subLevelId(level, dock.blockPos)): DockSnapshot? {
        if (!SimulatedDockAccess.isDock(dock)) return null
        val locked = SimulatedDockAccess.isLocked(dock)
        val extended = SimulatedDockAccess.isExtended(dock)
        val retracted = SimulatedDockAccess.isRetracted(dock)
        val connected = SimulatedDockAccess.hasOtherConnector(dock)
        val remote = SimulatedDockAccess.otherConnector(dock)
        val remoteSubLevel = remote?.let { runCatching { Sable.HELPER.getContaining(it) }.getOrNull() }
        val state = when {
            locked -> "locked"
            connected && extended -> "locking"
            extended -> "extended"
            retracted -> "retracted"
            else -> "unpowered"
        }
        return DockSnapshot(
            id = TelemetryIdentity.endpointId(level, dock.blockPos),
            alias = dockAlias(dock),
            pos = dock.blockPos.immutable(),
            state = state,
            connected = connected,
            locked = locked,
            extended = extended,
            retracted = retracted,
            localSubLevelId = localSubLevelId,
            remote = remote,
            remoteSubLevelId = remoteSubLevel?.uniqueId?.toString(),
            remoteName = remoteSubLevel?.name
        )
    }

    fun setDockAlias(dock: BlockEntity, alias: String?) {
        val normalized = alias?.trim()?.takeIf(String::isNotEmpty)
        val root = dock.persistentData.getCompound(PERSISTENT_ROOT)
        if (normalized == null) root.remove(PERSISTENT_DOCK_ALIAS) else root.putString(PERSISTENT_DOCK_ALIAS, normalized)
        dock.persistentData.put(PERSISTENT_ROOT, root)
        dock.setChanged()
    }

    fun transferBuffers(dock: BlockEntity): Map<String, Any> {
        val level = dock.level as? ServerLevel ?: return emptyMap()
        val result = linkedMapOf<String, Any>()

        level.getCapability(Capabilities.ItemHandler.BLOCK, dock.blockPos, null)?.let { inventory ->
            var occupied = 0
            var count = 0L
            for (slot in 0 until inventory.slots) {
                val stack = inventory.getStackInSlot(slot)
                if (!stack.isEmpty) {
                    occupied++
                    count += stack.count.toLong()
                }
            }
            result["item"] = linkedMapOf(
                "slots" to inventory.slots,
                "occupiedSlots" to occupied,
                "count" to count
            )
        }

        level.getCapability(Capabilities.FluidHandler.BLOCK, dock.blockPos, null)?.let { tank ->
            var amount = 0L
            var capacity = 0L
            for (index in 0 until tank.tanks) {
                amount += tank.getFluidInTank(index).amount.toLong()
                capacity += tank.getTankCapacity(index).toLong()
            }
            result["fluid"] = linkedMapOf(
                "tanks" to tank.tanks,
                "amount" to amount,
                "capacity" to capacity,
                "buckets" to amount / 1000.0
            )
        }

        level.getCapability(Capabilities.EnergyStorage.BLOCK, dock.blockPos, null)?.let { energy ->
            result["energy"] = linkedMapOf(
                "stored" to energy.energyStored,
                "capacity" to energy.maxEnergyStored
            )
        }

        return result
    }

    private fun dockAlias(dock: BlockEntity): String? {
        val data = dock.persistentData
        if (!data.contains(PERSISTENT_ROOT)) return null
        return data.getCompound(PERSISTENT_ROOT)
            .getString(PERSISTENT_DOCK_ALIAS)
            .trim()
            .takeIf(String::isNotEmpty)
    }

    private const val PERSISTENT_ROOT = "cc_aeroworks_telemetry"
    private const val PERSISTENT_DOCK_ALIAS = "dock_alias"
}

internal object SimulatedDockAccess {
    private const val CLASS_NAME =
        "dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity"

    private data class Accessors(
        val type: Class<*>,
        val isLocked: Method,
        val isExtended: Method,
        val isRetracted: Method,
        val hasOtherConnector: Method,
        val getOtherConnector: Method
    )

    private val accessors: Accessors? by lazy {
        if (!ModList.get().isLoaded("simulated")) return@lazy null
        runCatching {
            val type = Class.forName(CLASS_NAME)
            Accessors(
                type = type,
                isLocked = type.getMethod("isLocked"),
                isExtended = type.getMethod("isExtended"),
                isRetracted = type.getMethod("isRetracted"),
                hasOtherConnector = type.getMethod("hasOtherConnector"),
                getOtherConnector = type.getMethod("getOtherConnector")
            )
        }.onFailure { error ->
            CCAeroworks.LOGGER.warn("[CC-Aeroworks] Failed to initialize Simulated docking compatibility", error)
        }.getOrNull()
    }

    fun available(): Boolean = accessors != null

    fun isDock(blockEntity: BlockEntity): Boolean {
        val current = accessors ?: return false
        return current.type.isInstance(blockEntity) ||
            BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.type).toString() == "simulated:docking_connector"
    }

    fun isLocked(blockEntity: BlockEntity): Boolean = boolean(blockEntity) { it.isLocked }
    fun isExtended(blockEntity: BlockEntity): Boolean = boolean(blockEntity) { it.isExtended }
    fun isRetracted(blockEntity: BlockEntity): Boolean = boolean(blockEntity) { it.isRetracted }
    fun hasOtherConnector(blockEntity: BlockEntity): Boolean = boolean(blockEntity) { it.hasOtherConnector }

    fun otherConnector(blockEntity: BlockEntity): BlockEntity? {
        val current = accessors ?: return null
        if (!current.type.isInstance(blockEntity)) return null
        return runCatching { current.getOtherConnector.invoke(blockEntity) as? BlockEntity }.getOrNull()
    }

    private inline fun boolean(blockEntity: BlockEntity, method: (Accessors) -> Method): Boolean {
        val current = accessors ?: return false
        if (!current.type.isInstance(blockEntity)) return false
        return runCatching { method(current).invoke(blockEntity) as Boolean }.getOrDefault(false)
    }
}
