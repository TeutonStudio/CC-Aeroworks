package de.teutonstudio.ccaeroworks.telemetry

import dan200.computercraft.api.lua.IComputerSystem
import dan200.computercraft.api.lua.ILuaAPI
import dan200.computercraft.api.lua.LuaException
import dan200.computercraft.api.lua.LuaFunction
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.computer.ComputerConsoleAccess
import de.teutonstudio.ccaeroworks.computer.ComputerControlDeskBlockEntity
import net.minecraft.server.level.ServerLevel
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

    fun status(): Map<String, Any> = TelemetryRuntime.status(owner())

    fun tick(): Boolean {
        val owner = access.owner() ?: return false
        val level = owner.level as? ServerLevel ?: return false
        TelemetryRuntime.validate(level)

        val current = TelemetryRuntime.sourceRevisions(owner)
        val computerOn = owner.getServerComputer()?.isOn == true
        if (initialized && computerOn) {
            revisions.forEach { (id, _) ->
                if (id !in current) {
                    system.queueEvent(CCAeroworks.TELEMETRY_REMOVED_EVENT, id)
                }
            }
            current.forEach { (id, revision) ->
                val previous = revisions[id]
                when {
                    previous == null -> system.queueEvent(CCAeroworks.TELEMETRY_ADDED_EVENT, id, revision)
                    previous != revision -> system.queueEvent(CCAeroworks.TELEMETRY_CHANGED_EVENT, id, revision)
                }
            }
        }
        revisions = current
        initialized = true
        return true
    }

    fun close() {
        TelemetryComputerRuntimes.unregister(this)
        initialized = false
        revisions = emptyMap()
    }

    private fun owner(): ComputerControlDeskBlockEntity =
        access.owner() ?: throw LuaException("The computer control desk is no longer loaded")
}
