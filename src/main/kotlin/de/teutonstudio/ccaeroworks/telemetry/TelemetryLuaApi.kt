package de.teutonstudio.ccaeroworks.telemetry

import dan200.computercraft.api.lua.IComputerSystem
import dan200.computercraft.api.lua.ILuaAPI
import dan200.computercraft.api.lua.LuaException
import dan200.computercraft.api.lua.LuaFunction
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.computer.ComputerConsoleAccess
import de.teutonstudio.ccaeroworks.computer.ComputerControlDeskBlockEntity
import de.teutonstudio.ccaeroworks.config.CCServerConfig
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.entity.BlockEntity
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import java.util.concurrent.ConcurrentHashMap

class TelemetryLuaApi(
    access: ComputerConsoleAccess,
    system: IComputerSystem
) : ILuaAPI {
    private val runtime = TelemetryComputerRuntime(access, system)

    override fun getNames(): Array<String> = arrayOf("telemetry")

    override fun getModuleName(): String = "cc_aeroworks.telemetry"

    override fun shutdown() {
        runtime.close()
    }

    @LuaFunction(mainThread = true)
    fun list(): Map<String, Any> = runtime.list()

    @LuaFunction(mainThread = true)
    fun get(nameOrId: String): Map<String, Any>? = runtime.get(nameOrId)

    @LuaFunction(mainThread = true)
    fun find(type: String): Map<String, Any> = runtime.find(type)

    @LuaFunction(mainThread = true)
    fun rename(nameOrId: String, alias: String): Map<String, Any> = runtime.rename(nameOrId, alias)

    @LuaFunction(mainThread = true)
    fun clearName(nameOrId: String): Map<String, Any> = runtime.clearName(nameOrId)

    @LuaFunction(mainThread = true)
    fun getStatus(): Map<String, Any> = runtime.status()

    @LuaFunction(mainThread = true)
    fun getDocks(): Map<String, Any> = runtime.getDocks()

    @LuaFunction(mainThread = true)
    fun getDock(nameOrId: String): DockLuaHandle? = runtime.getDock(nameOrId)

    @LuaFunction(mainThread = true)
    fun renameDock(nameOrId: String, alias: String): Map<String, Any> = runtime.renameDock(nameOrId, alias)

    @LuaFunction(mainThread = true)
    fun clearDockName(nameOrId: String): Map<String, Any> = runtime.clearDockName(nameOrId)
}

object TelemetryComputerRuntimes {
    private val active = ConcurrentHashMap.newKeySet<TelemetryComputerRuntime>()

    internal fun register(runtime: TelemetryComputerRuntime) {
        active.add(runtime)
    }

    internal fun unregister(runtime: TelemetryComputerRuntime) {
        active.remove(runtime)
    }

    @SubscribeEvent
    fun onServerTick(event: ServerTickEvent.Post) {
        active.removeIf { !it.tick() }
    }
}

internal class TelemetryComputerRuntime(
    private val access: ComputerConsoleAccess,
    private val system: IComputerSystem
) {
    private var initialized = false
    private var revisions: Map<String, Long> = emptyMap()
    private var dockCache: List<DockSnapshot> = emptyList()
    private var dockFingerprints: Map<String, String> = emptyMap()
    private var remoteRevisions: Map<String, Long> = emptyMap()
    private var lastDockScanTick = Long.MIN_VALUE

    init {
        TelemetryComputerRuntimes.register(this)
    }

    fun list(): Map<String, Any> = TelemetryRuntime.describeSources(owner())

    fun get(nameOrId: String): Map<String, Any>? = TelemetryRuntime.describeSource(owner(), nameOrId)

    fun find(type: String): Map<String, Any> {
        val normalized = type.trim().lowercase()
        return list().filterValues { raw ->
            val source = raw as? Map<*, *> ?: return@filterValues false
            val sourceType = source["sourceType"]?.toString()?.lowercase().orEmpty()
            val kind = source["kind"]?.toString()?.lowercase().orEmpty()
            kind == normalized || sourceType == normalized || sourceType.substringAfter(':') == normalized
        }
    }

    fun rename(nameOrId: String, alias: String): Map<String, Any> {
        val owner = owner()
        try {
            TelemetryRuntime.rename(owner, nameOrId, alias)
                ?: throw LuaException("Telemetry source '$nameOrId' was not found")
        } catch (error: IllegalArgumentException) {
            throw LuaException(error.message ?: "Invalid telemetry alias")
        }
        return TelemetryRuntime.describeSource(owner, alias)
            ?: throw LuaException("Telemetry source '$alias' was not found after renaming")
    }

    fun clearName(nameOrId: String): Map<String, Any> {
        val owner = owner()
        val source = TelemetryRuntime.clearAlias(owner, nameOrId)
            ?: throw LuaException("Telemetry source '$nameOrId' was not found")
        return TelemetryRuntime.describeSource(owner, source.id)
            ?: throw LuaException("Telemetry source '${source.id}' is no longer available")
    }

    fun status(): Map<String, Any> = linkedMapOf<String, Any>().apply {
        putAll(TelemetryRuntime.status(owner()))
        put("dockCount", refreshDocks().size)
        put("simulatedDockingAvailable", SimulatedDockDiscovery.available())
    }

    fun getDocks(): Map<String, Any> {
        val docks = refreshDocks()
        val aliases = docks.mapNotNull { it.alias }
            .groupingBy { it.lowercase() }
            .eachCount()
        return docks.associateTo(linkedMapOf()) { dock ->
            val key = dock.alias
                ?.takeIf { aliases[it.lowercase()] == 1 }
                ?: dock.id
            key to DockLuaHandle(this, dock.id)
        }
    }

    fun getDock(nameOrId: String): DockLuaHandle? =
        resolveDock(nameOrId)?.let { DockLuaHandle(this, it.id) }

    fun renameDock(nameOrId: String, alias: String): Map<String, Any> {
        val dock = resolveDock(nameOrId, force = true)
            ?: throw LuaException("Dock '$nameOrId' was not found on this Sable sublevel")
        val normalized = alias.trim()
        if (normalized.isEmpty()) throw LuaException("Dock alias must not be blank")
        if (refreshDocks().any { it.id != dock.id && it.alias?.equals(normalized, ignoreCase = true) == true }) {
            throw LuaException("Dock alias '$normalized' is already used on this Sable sublevel")
        }
        val target = localDockBlockEntity(dock)
        SimulatedDockDiscovery.setDockAlias(target, normalized)
        refreshDocks(force = true)
        return resolveDock(normalized)?.toLua(level())
            ?: throw LuaException("Dock '$normalized' was not found after renaming")
    }

    fun clearDockName(nameOrId: String): Map<String, Any> {
        val dock = resolveDock(nameOrId, force = true)
            ?: throw LuaException("Dock '$nameOrId' was not found on this Sable sublevel")
        SimulatedDockDiscovery.setDockAlias(localDockBlockEntity(dock), null)
        refreshDocks(force = true)
        return resolveDock(dock.id)?.toLua(level())
            ?: throw LuaException("Dock '${dock.id}' is no longer available")
    }

    fun dockInfo(id: String): Map<String, Any> = requireDock(id).toLua(level())

    fun remoteTelemetry(id: String): Map<String, Any> {
        val remote = remoteTarget(requireDock(id)) ?: return emptyMap()
        return TelemetryRuntime.describeSources(remote)
    }

    fun remoteTelemetry(id: String, nameOrId: String): Map<String, Any>? {
        val remote = remoteTarget(requireDock(id)) ?: return null
        return TelemetryRuntime.describeSource(remote, nameOrId)
    }

    fun renameRemoteTelemetry(id: String, nameOrId: String, alias: String): Map<String, Any> {
        val dock = requireDock(id)
        val remote = remoteTarget(dock, requireLocked = true)
            ?: throw LuaException("Dock '${dock.alias ?: dock.id}' is not locked to a remote connector")
        try {
            TelemetryRuntime.rename(remote, nameOrId, alias)
                ?: throw LuaException("Remote telemetry source '$nameOrId' was not found")
        } catch (error: IllegalArgumentException) {
            throw LuaException(error.message ?: "Invalid remote telemetry alias")
        }
        return TelemetryRuntime.describeSource(remote, alias)
            ?: throw LuaException("Remote telemetry source '$alias' was not found after renaming")
    }

    fun clearRemoteTelemetryName(id: String, nameOrId: String): Map<String, Any> {
        val dock = requireDock(id)
        val remote = remoteTarget(dock, requireLocked = true)
            ?: throw LuaException("Dock '${dock.alias ?: dock.id}' is not locked to a remote connector")
        val source = TelemetryRuntime.clearAlias(remote, nameOrId)
            ?: throw LuaException("Remote telemetry source '$nameOrId' was not found")
        return TelemetryRuntime.describeSource(remote, source.id)
            ?: throw LuaException("Remote telemetry source '${source.id}' is no longer available")
    }

    fun transferBuffers(id: String): Map<String, Any> =
        SimulatedDockDiscovery.transferBuffers(localDockBlockEntity(requireDock(id)))

    fun tick(): Boolean {
        val owner = access.owner() ?: return false
        val level = owner.level as? ServerLevel ?: return false
        TelemetryRuntime.validate(level)
        val computerOn = owner.getServerComputer()?.isOn == true

        val current = TelemetryRuntime.sourceRevisions(owner)
        if (initialized && computerOn) publishLocalEvents(current)
        revisions = current

        val docks = refreshDocks()
        val nextDockFingerprints = docks.associate { it.id to it.fingerprint() }
        if (initialized && computerOn) publishDockEvents(docks, nextDockFingerprints)
        dockFingerprints = nextDockFingerprints

        val nextRemoteRevisions = linkedMapOf<String, Long>()
        docks.filter { it.locked }.forEach { dock ->
            val remote = dock.remote ?: return@forEach
            TelemetryRuntime.sourceRevisions(remote).forEach { (sourceId, revision) ->
                nextRemoteRevisions[remoteKey(dock.id, sourceId)] = revision
            }
        }
        if (initialized && computerOn) publishRemoteEvents(nextRemoteRevisions)
        remoteRevisions = nextRemoteRevisions

        initialized = true
        return true
    }

    fun close() {
        TelemetryComputerRuntimes.unregister(this)
        initialized = false
        revisions = emptyMap()
        dockCache = emptyList()
        dockFingerprints = emptyMap()
        remoteRevisions = emptyMap()
        lastDockScanTick = Long.MIN_VALUE
    }

    internal fun resolveDock(nameOrId: String, force: Boolean = false): DockSnapshot? {
        val docks = refreshDocks(force)
        docks.firstOrNull { it.id == nameOrId }?.let { return it }
        return docks.firstOrNull { it.alias?.equals(nameOrId, ignoreCase = true) == true }
    }

    private fun refreshDocks(force: Boolean = false): List<DockSnapshot> {
        val owner = access.owner() ?: return emptyList()
        val level = owner.level as? ServerLevel ?: return emptyList()
        val interval = CCServerConfig.telemetryDockScanIntervalTicksValue().toLong()
        if (!force && lastDockScanTick != Long.MIN_VALUE && level.gameTime - lastDockScanTick < interval) {
            return dockCache
        }
        dockCache = SimulatedDockDiscovery.discover(owner)
        lastDockScanTick = level.gameTime
        return dockCache
    }

    private fun publishLocalEvents(current: Map<String, Long>) {
        revisions.forEach { (id, _) ->
            if (id !in current) system.queueEvent(CCAeroworks.TELEMETRY_REMOVED_EVENT, id)
        }
        current.forEach { (id, revision) ->
            val previous = revisions[id]
            when {
                previous == null -> system.queueEvent(CCAeroworks.TELEMETRY_ADDED_EVENT, id, revision)
                previous != revision -> system.queueEvent(CCAeroworks.TELEMETRY_CHANGED_EVENT, id, revision)
            }
        }
    }

    private fun publishDockEvents(docks: List<DockSnapshot>, current: Map<String, String>) {
        dockFingerprints.keys.forEach { id ->
            if (id !in current) system.queueEvent(CCAeroworks.DOCK_CHANGED_EVENT, id, "removed", false, "")
        }
        val byId = docks.associateBy(DockSnapshot::id)
        current.forEach { (id, fingerprint) ->
            if (dockFingerprints[id] != fingerprint) {
                val dock = byId.getValue(id)
                system.queueEvent(
                    CCAeroworks.DOCK_CHANGED_EVENT,
                    id,
                    dock.state,
                    dock.locked,
                    dock.remoteSubLevelId.orEmpty()
                )
            }
        }
    }

    private fun publishRemoteEvents(current: Map<String, Long>) {
        remoteRevisions.forEach { (key, _) ->
            if (key !in current) {
                val (dockId, sourceId) = splitRemoteKey(key)
                system.queueEvent(CCAeroworks.REMOTE_TELEMETRY_CHANGED_EVENT, dockId, sourceId, "removed", 0L)
            }
        }
        current.forEach { (key, revision) ->
            val previous = remoteRevisions[key]
            if (previous == revision) return@forEach
            val (dockId, sourceId) = splitRemoteKey(key)
            system.queueEvent(
                CCAeroworks.REMOTE_TELEMETRY_CHANGED_EVENT,
                dockId,
                sourceId,
                if (previous == null) "added" else "changed",
                revision
            )
        }
    }

    private fun requireDock(id: String): DockSnapshot =
        resolveDock(id)?.takeIf { it.id == id }
            ?: throw LuaException("Dock '$id' is no longer available")

    private fun localDockBlockEntity(dock: DockSnapshot): BlockEntity =
        level().getBlockEntity(dock.pos)
            ?.takeIf(SimulatedDockAccess::isDock)
            ?: throw LuaException("Dock '${dock.alias ?: dock.id}' is no longer loaded")

    private fun remoteTarget(dock: DockSnapshot, requireLocked: Boolean = true): BlockEntity? {
        if (requireLocked && !dock.locked) return null
        return dock.remote?.takeIf { !it.isRemoved }
    }

    private fun owner(): ComputerControlDeskBlockEntity =
        access.owner() ?: throw LuaException("The computer control desk is no longer loaded")

    private fun level(): ServerLevel =
        owner().level as? ServerLevel ?: throw LuaException("The computer control desk is not in a server level")

    private fun remoteKey(dockId: String, sourceId: String): String = "$dockId|$sourceId"

    private fun splitRemoteKey(value: String): Pair<String, String> {
        val split = value.indexOf('|')
        return if (split < 0) value to "" else value.substring(0, split) to value.substring(split + 1)
    }
}

class DockLuaHandle internal constructor(
    private val runtime: TelemetryComputerRuntime,
    private val id: String
) {
    @LuaFunction(mainThread = true)
    fun getInfo(): Map<String, Any> = runtime.dockInfo(id)

    @LuaFunction(mainThread = true)
    fun listTelemetry(): Map<String, Any> = runtime.remoteTelemetry(id)

    @LuaFunction(mainThread = true)
    fun getTelemetry(nameOrId: String): Map<String, Any>? = runtime.remoteTelemetry(id, nameOrId)

    @LuaFunction(mainThread = true)
    fun renameTelemetry(nameOrId: String, alias: String): Map<String, Any> =
        runtime.renameRemoteTelemetry(id, nameOrId, alias)

    @LuaFunction(mainThread = true)
    fun clearTelemetryName(nameOrId: String): Map<String, Any> =
        runtime.clearRemoteTelemetryName(id, nameOrId)

    @LuaFunction(mainThread = true)
    fun getTransferBuffers(): Map<String, Any> = runtime.transferBuffers(id)
}
