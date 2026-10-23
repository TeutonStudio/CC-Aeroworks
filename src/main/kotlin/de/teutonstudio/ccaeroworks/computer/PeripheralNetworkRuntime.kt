package de.teutonstudio.ccaeroworks.computer

import com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity
import dan200.computercraft.api.lua.IArguments
import dan200.computercraft.api.lua.IComputerSystem
import dan200.computercraft.api.lua.LuaException
import dan200.computercraft.api.peripheral.IPeripheral
import de.TeutonStudio.TeutonLib.api.computer.ComputerContext
import de.TeutonStudio.TeutonLib.api.computer.peripheral.PeripheralAttachmentRuntime
import de.TeutonStudio.TeutonLib.api.computer.peripheral.PeripheralEndpoint
import de.TeutonStudio.TeutonLib.api.computer.peripheral.PeripheralEndpointMetadata
import de.TeutonStudio.TeutonLib.api.computer.peripheral.PeripheralHost
import de.TeutonStudio.TeutonLib.api.computer.peripheral.PeripheralTopology
import de.TeutonStudio.TeutonLib.api.computer.peripheral.PeripheralTopologyProvider
import de.TeutonStudio.TeutonLib.api.computer.peripheral.PeripheralProviders
import de.teutonstudio.ccaeroworks.CCAeroworks
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import java.util.Locale
import java.util.WeakHashMap

internal object PeripheralNetworkRuntimes {
    private val runtimes = WeakHashMap<ConsoleComputer, MutableSet<PeripheralNetworkRuntime>>()

    @Synchronized
    fun register(owner: ConsoleComputer, runtime: PeripheralNetworkRuntime) {
        runtimes.getOrPut(owner, ::linkedSetOf).add(runtime)
    }

    @Synchronized
    fun unregister(owner: ConsoleComputer, runtime: PeripheralNetworkRuntime) {
        val current = runtimes[owner] ?: return
        current.remove(runtime)
        if (current.isEmpty()) runtimes.remove(owner)
    }

    fun tick(owner: ConsoleComputer) {
        val current = synchronized(this) { runtimes[owner]?.toList().orEmpty() }
        current.forEach(PeripheralNetworkRuntime::tick)
    }
}

internal class PeripheralNetworkRuntime(
    private val access: ComputerConsoleAccess,
    private val system: IComputerSystem
) {
    private val topologyProvider = AeroworksPeripheralTopologyProvider(access)
    private val attachments = PeripheralAttachmentRuntime(
        system = system,
        context = ::computerContext,
        provider = topologyProvider,
        describe = ::describePeripheral,
        onAttached = { endpoint -> queueAttached(endpoint.node()) },
        onDetached = { endpoint -> queueDetached(endpoint.node()) }
    )
    private var graph: PeripheralNetworkGraph? = null

    init {
        access.owner()?.let { PeripheralNetworkRuntimes.register(it, this) }
    }

    @Synchronized
    fun graph(force: Boolean = false): PeripheralNetworkGraph {
        attachments.refresh(force)
        return topologyProvider.latestGraph.also { graph = it }
            ?: throw LuaException("The peripheral topology provider returned no graph")
    }

    fun tick() {
        val owner = access.owner() ?: return
        val level = owner.level as? ServerLevel ?: return
        if (level.gameTime % GRAPH_REFRESH_INTERVAL != 0L) return
        try {
            graph()
        } catch (failure: RuntimeException) {
            runCatching { invalidateGraph(queueEvents = true) }
                .exceptionOrNull()
                ?.let(failure::addSuppressed)
            CCAeroworks.LOGGER.debug("Peripheral topology is not ready yet; retrying", failure)
        }
    }

    @Synchronized
    fun close() {
        access.owner()?.let { PeripheralNetworkRuntimes.unregister(it, this) }
        attachments.close()
        graph = null
    }

    fun deskHandle(node: DeskNetworkNode): DeskLuaHandle = DeskLuaHandle(this, node.address)

    @Synchronized
    fun peripheralHandle(node: PeripheralNetworkNode): PeripheralLuaHandle {
        return PeripheralLuaHandle(attachments.binding(node.address))
    }

    fun find(type: String, deskAddress: String? = null, alwaysCollection: Boolean = false): Any? {
        val current = graph()
        if (PeripheralTypeNames.isControlDesk(type)) {
            return current.desks
                .filter { deskAddress == null || it.address == deskAddress }
                .associateTo(linkedMapOf()) { it.address to deskHandle(it) }
        }
        val matches = current.peripherals.filter { node ->
            (deskAddress == null || node.desk.address == deskAddress) && node.matches(type)
        }
        val handles = matches.associateTo(linkedMapOf()) { it.address to peripheralHandle(it) }
        return when {
            alwaysCollection -> handles
            handles.isEmpty() -> null
            handles.size == 1 -> handles.values.first()
            else -> handles
        }
    }

    fun peripheralsForDesk(deskAddress: String): Map<String, Any> = graph().peripherals
        .filter { it.desk.address == deskAddress }
        .associateTo(linkedMapOf()) { it.address to peripheralHandle(it) }

    fun wrap(arguments: IArguments, deskAddress: String? = null): Any? {
        val current = graph()
        if (deskAddress != null) {
            val sideName = arguments.getString(0)
            val side = Direction.values().firstOrNull { it.name.equals(sideName, ignoreCase = true) }
                ?: throw LuaException("Unknown side '$sideName'")
            val node = current.peripherals.firstOrNull {
                it.desk.address == deskAddress && it.side != null && it.side == side
            }
            return node?.let(::peripheralHandle)
        }

        val parsed = parsePosition(arguments)
        current.desks.firstOrNull { it.pos == parsed.pos }?.let { desk ->
            if (parsed.type == null || PeripheralTypeNames.isControlDesk(parsed.type)) {
                return deskHandle(desk)
            }
        }
        val matches = current.peripherals.filter { node ->
            node.pos == parsed.pos && (parsed.type == null || node.matches(parsed.type))
        }
        return when (matches.size) {
            0 -> null
            1 -> peripheralHandle(matches.first())
            else -> throw LuaException(
                "Multiple peripherals exist at ${PeripheralNetworkBuilder.address(parsed.pos)}; provide a type"
            )
        }
    }

    fun describeNetwork(): Map<String, Any> {
        val current = graph()
        return linkedMapOf(
            "id" to current.networkId,
            "assemblyId" to current.networkId,
            "state" to current.state.name.lowercase(Locale.ROOT),
            "revision" to current.revision,
            "dimension" to current.dimension,
            "space" to current.space,
            "deskCount" to current.desks.size,
            "peripheralCount" to current.peripherals.size
        )
    }

    fun typeCounts(): Map<String, Int> {
        val counts = linkedMapOf<String, Int>()
        graph().peripherals.forEach { node ->
            node.types.forEach { type -> counts[type] = (counts[type] ?: 0) + 1 }
        }
        return counts
    }

    fun describeDesk(address: String): Map<String, Any> {
        val current = graph()
        val node = current.desks.firstOrNull { it.address == address }
            ?: throw LuaException("Control desk '$address' is no longer part of the network")
        return linkedMapOf(
            "id" to node.id,
            "assemblyId" to current.networkId,
            "address" to node.address,
            "index" to node.member.index,
            "x" to node.pos.x,
            "y" to node.pos.y,
            "z" to node.pos.z,
            "dimension" to current.dimension,
            "computer" to (ConsoleComputers.get(node.desk) != null),
            "variant" to node.member.kind.name.lowercase(Locale.ROOT),
            "facing" to node.member.facing.name.lowercase(Locale.ROOT),
            "loaded" to true
        )
    }

    fun desk(address: String): ConsoleBlockEntity {
        val owner = access.owner() ?: throw LuaException("The computer control desk is no longer loaded")
        val node = graph().desks.firstOrNull { it.address == address }
            ?: throw LuaException("Control desk '$address' is no longer part of the network")
        val level = owner.level ?: throw LuaException("The control desk network is no longer loaded")
        return (level.getBlockEntity(node.pos) as? ConsoleBlockEntity)
            ?.takeIf { it === node.desk && !it.isRemoved }
            ?: throw LuaException("Control desk '$address' is no longer loaded")
    }

    fun describePeripheral(endpoint: PeripheralEndpoint): Map<String, Any> {
        val node = endpoint.node()
        return linkedMapOf<String, Any>().apply {
            putAll(describePeripheral(node))
            endpoint.metadata.forEach { (key, value) ->
                if (!key.startsWith("teutonlib:") && value != null) put(key, value)
            }
        }
    }

    fun describePeripheral(node: PeripheralNetworkNode): Map<String, Any> {
        val current = graph ?: throw LuaException("The peripheral network is not initialized")
        return linkedMapOf<String, Any>(
            "address" to node.address,
            "assemblyId" to current.networkId,
            "type" to node.primaryType,
            "types" to node.types.toList(),
            "deskId" to node.desk.id,
            "deskAddress" to node.desk.address,
            "deskPosition" to position(node.desk.pos, current.dimension),
            "position" to position(node.pos, current.dimension),
            "side" to (node.side?.name?.lowercase(Locale.ROOT) ?: "internal"),
            "loaded" to true
        ).apply {
            topologyProvider.latestMetadata[node.address].orEmpty().forEach { (key, value) ->
                if (!key.startsWith("teutonlib:") && value != null) put(key, value)
            }
        }
    }

    @Synchronized
    fun availablePeripherals(): Map<String, IPeripheral> =
        attachments.availablePeripherals()

    @Synchronized
    private fun invalidateGraph(queueEvents: Boolean) {
        attachments.invalidate(queueEvents)
        graph = null
    }

    private fun queueAttached(node: PeripheralNetworkNode) {
        system.queueEvent("peripheral", node.address)
        system.queueEvent(CCAeroworks.PERIPHERAL_ATTACHED_EVENT, node.address, node.primaryType)
    }

    private fun queueDetached(node: PeripheralNetworkNode) {
        system.queueEvent("peripheral_detach", node.address)
        system.queueEvent(CCAeroworks.PERIPHERAL_DETACHED_EVENT, node.address, node.primaryType)
    }

    private fun computerContext(): ComputerContext {
        val owner = access.owner() ?: throw LuaException("The computer control desk is no longer loaded")
        val level = owner.level as? ServerLevel ?: throw LuaException("The computer control desk is not in a server level")
        return object : ComputerContext {
            override fun host(): BlockEntity = owner.host
            override fun computerId(): Int = system.id
            override fun level(): ServerLevel = level
            override fun position(): BlockPos = owner.blockPos
            override fun peripherals(): Map<String, IPeripheral> = system.availablePeripherals
            override fun queueEvent(event: String, vararg arguments: Any?) = system.queueEvent(event, *arguments)
        }
    }

    private data class ParsedPosition(val pos: BlockPos, val type: String?)

    private fun parsePosition(arguments: IArguments): ParsedPosition {
        val first = arguments.get(0)
        if (first is Number) {
            return ParsedPosition(
                BlockPos(arguments.getInt(0), arguments.getInt(1), arguments.getInt(2)),
                arguments.optString(3).orElse(null)
            )
        }
        if (first is Map<*, *>) {
            fun coordinate(name: String): Int {
                val value = first[name] as? Number
                    ?: throw LuaException("Position table must contain numeric '$name'")
                val number = value.toDouble()
                if (!number.isFinite() || number % 1.0 != 0.0 ||
                    number < Int.MIN_VALUE.toDouble() || number > Int.MAX_VALUE.toDouble()
                ) {
                    throw LuaException("Position coordinate '$name' must be a 32-bit integer")
                }
                return number.toInt()
            }
            return ParsedPosition(
                BlockPos(coordinate("x"), coordinate("y"), coordinate("z")),
                arguments.optString(1).orElse(null)
            )
        }
        throw LuaException("Expected x, y, z or a position table")
    }

    private fun position(pos: BlockPos, dimension: String): Map<String, Any> = linkedMapOf(
        "x" to pos.x,
        "y" to pos.y,
        "z" to pos.z,
        "dimension" to dimension
    )

    companion object {
        private const val GRAPH_REFRESH_INTERVAL = 5L
    }
}

private class AeroworksPeripheralTopologyProvider(
    private val access: ComputerConsoleAccess
) : PeripheralTopologyProvider {
    var latestGraph: PeripheralNetworkGraph? = null
        private set
    var latestMetadata: Map<String, Map<String, Any?>> = emptyMap()
        private set

    override fun snapshot(computer: ComputerContext): PeripheralTopology {
        val owner = access.owner() ?: throw LuaException("The computer control desk is no longer loaded")
        val base = PeripheralNetworkBuilder.build(owner)
        val occupied = base.peripherals.mapTo(linkedSetOf(), PeripheralNetworkNode::address)
        val externalMetadata = linkedMapOf<String, Map<String, Any?>>()
        val external = PeripheralProviders.endpoints(computer).mapNotNull { endpoint ->
            if (!occupied.add(endpoint.address)) return@mapNotNull null
            val anchorPos = endpoint.metadata[PeripheralEndpointMetadata.ANCHOR_POSITION] as? BlockPos
                ?: return@mapNotNull null
            val desk = base.desks.firstOrNull { it.pos == anchorPos } ?: return@mapNotNull null
            val position = endpoint.metadata[PeripheralEndpointMetadata.DEVICE_POSITION] as? BlockPos
                ?: return@mapNotNull null
            val side = endpoint.metadata[PeripheralEndpointMetadata.SIDE] as? Direction
            externalMetadata[endpoint.address] = endpoint.metadata
            PeripheralNetworkNode(
                desk = desk,
                pos = position,
                side = side,
                address = endpoint.address,
                target = endpoint.peripheral,
                primaryType = endpoint.primaryType,
                types = endpoint.types,
                aliases = PeripheralTypeNames.aliases(endpoint.types)
            )
        }
        val next = base.copy(peripherals = base.peripherals + external)
        latestGraph = next
        latestMetadata = externalMetadata.toMap()
        return PeripheralTopology(
            revision = next.revision,
            state = next.state.name,
            hosts = next.desks.map { PeripheralHost(it.id, it.address, mapOf("node" to it)) },
            endpoints = next.peripherals.map { node ->
                PeripheralEndpoint(
                    address = node.address,
                    primaryType = node.primaryType,
                    types = node.types,
                    peripheral = node.target,
                    ownerKey = node.desk.id,
                    descriptor = node,
                    metadata = externalMetadata[node.address].orEmpty()
                )
            },
            metadata = mapOf("networkId" to next.networkId, "dimension" to next.dimension, "space" to next.space)
        )
    }
}

private fun PeripheralEndpoint.node(): PeripheralNetworkNode =
    descriptor as? PeripheralNetworkNode ?: error("Aeroworks peripheral endpoint lost its descriptor")
