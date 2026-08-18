package de.teutonstudio.ccaeroworks.computer

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import dan200.computercraft.api.lua.IArguments
import dan200.computercraft.api.lua.IComputerSystem
import dan200.computercraft.api.lua.LuaException
import dan200.computercraft.api.peripheral.IPeripheral
import de.teutonstudio.ccaeroworks.CCAeroworks
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import java.util.Locale
import java.util.WeakHashMap

internal object PeripheralNetworkRuntimes {
    private val runtimes = WeakHashMap<ComputerControlDeskBlockEntity, MutableSet<PeripheralNetworkRuntime>>()

    @Synchronized
    fun register(owner: ComputerControlDeskBlockEntity, runtime: PeripheralNetworkRuntime) {
        runtimes.getOrPut(owner, ::linkedSetOf).add(runtime)
    }

    @Synchronized
    fun unregister(owner: ComputerControlDeskBlockEntity, runtime: PeripheralNetworkRuntime) {
        val current = runtimes[owner] ?: return
        current.remove(runtime)
        if (current.isEmpty()) runtimes.remove(owner)
    }

    fun tick(owner: ComputerControlDeskBlockEntity) {
        val current = synchronized(this) { runtimes[owner]?.toList().orEmpty() }
        current.forEach(PeripheralNetworkRuntime::tick)
    }
}

internal class PeripheralNetworkRuntime(
    private val access: ComputerConsoleAccess,
    private val system: IComputerSystem
) {
    private var graph: PeripheralNetworkGraph? = null
    private val bindings = linkedMapOf<String, PeripheralBinding>()
    private var initialized = false
    private var lastScanTick = Long.MIN_VALUE

    init {
        access.owner()?.let { PeripheralNetworkRuntimes.register(it, this) }
    }

    @Synchronized
    fun graph(force: Boolean = false): PeripheralNetworkGraph {
        val owner = access.owner()
            ?: throw LuaException("The computer control desk is no longer loaded")
        val level = owner.level as? ServerLevel
            ?: throw LuaException("The computer control desk is not in a server level")
        if (!force && graph != null && lastScanTick == level.gameTime) return graph!!

        val next = PeripheralNetworkBuilder.build(owner)
        lastScanTick = level.gameTime
        if (!force && graph?.revision == next.revision && sameTargets(graph, next)) {
            return graph!!
        }

        val desired = next.peripherals.associateBy { it.address }
        val detached = mutableListOf<PeripheralNetworkNode>()
        val iterator = bindings.iterator()
        while (iterator.hasNext()) {
            val (address, binding) = iterator.next()
            val node = desired[address]
            if (node == null || !samePeripheral(binding.node, node)) {
                detached += binding.node
                binding.close()
                iterator.remove()
            } else {
                binding.updateNode(node)
            }
        }

        // Publish the complete new topology before calling IPeripheral.attach(). Some peripherals
        // immediately inspect getAvailablePeripherals() during attach, and must not observe the old graph.
        graph = next

        val attached = mutableListOf<PeripheralNetworkNode>()
        try {
            for (node in next.peripherals) {
                if (node.address in bindings) continue
                val binding = PeripheralBinding(this, system, node)
                bindings[node.address] = binding
                binding.attach()
                attached += node
            }
        } catch (throwable: Throwable) {
            runCatching { invalidateGraph(queueEvents = false) }
                .exceptionOrNull()
                ?.let(throwable::addSuppressed)
            throw throwable
        }

        if (initialized) {
            detached.forEach(::queueDetached)
            attached.forEach(::queueAttached)
        }
        initialized = true
        return next
    }

    fun tick() {
        val owner = access.owner() ?: return
        val level = owner.level as? ServerLevel ?: return
        if (level.gameTime % GRAPH_REFRESH_INTERVAL != 0L) return
        try {
            graph()
        } catch (failure: LuaException) {
            runCatching { invalidateGraph(queueEvents = true) }
                .exceptionOrNull()
                ?.let(failure::addSuppressed)
        }
    }

    @Synchronized
    fun close() {
        access.owner()?.let { PeripheralNetworkRuntimes.unregister(it, this) }
        try {
            invalidateGraph(queueEvents = false)
        } finally {
            initialized = false
        }
    }

    fun deskHandle(node: DeskNetworkNode): DeskLuaHandle = DeskLuaHandle(this, node.address)

    @Synchronized
    fun peripheralHandle(node: PeripheralNetworkNode): PeripheralLuaHandle {
        val binding = bindings[node.address]
            ?: throw LuaException("Peripheral '${node.address}' is no longer attached")
        return PeripheralLuaHandle(binding)
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
                it.desk.address == deskAddress && it.side == side
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
            "state" to current.state.name.lowercase(Locale.ROOT),
            "revision" to current.revision,
            "dimension" to current.dimension,
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
            "address" to node.address,
            "index" to node.member.index,
            "x" to node.pos.x,
            "y" to node.pos.y,
            "z" to node.pos.z,
            "dimension" to current.dimension,
            "computer" to (node.desk is ComputerControlDeskBlockEntity),
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

    fun describePeripheral(node: PeripheralNetworkNode): Map<String, Any> {
        val current = graph ?: throw LuaException("The peripheral network is not initialized")
        return linkedMapOf(
            "address" to node.address,
            "type" to node.primaryType,
            "types" to node.types.toList(),
            "deskId" to node.desk.id,
            "deskAddress" to node.desk.address,
            "deskPosition" to position(node.desk.pos, current.dimension),
            "position" to position(node.pos, current.dimension),
            "side" to node.side.name.lowercase(Locale.ROOT),
            "loaded" to true
        )
    }

    @Synchronized
    fun availablePeripherals(): Map<String, IPeripheral> =
        bindings.mapValuesTo(linkedMapOf()) { it.value.node.target }

    @Synchronized
    private fun invalidateGraph(queueEvents: Boolean) {
        val detached = bindings.values.map { it.node }
        var failure: Throwable? = null
        bindings.values.forEach { binding ->
            val closeFailure = runCatching { binding.close() }.exceptionOrNull() ?: return@forEach
            if (failure == null) {
                failure = closeFailure
            } else if (failure !== closeFailure) {
                failure.addSuppressed(closeFailure)
            }
        }
        bindings.clear()
        graph = null
        lastScanTick = Long.MIN_VALUE
        if (queueEvents && initialized) detached.forEach(::queueDetached)
        if (failure != null) throw failure
    }

    private fun queueAttached(node: PeripheralNetworkNode) {
        system.queueEvent("peripheral", node.address)
        system.queueEvent(CCAeroworks.PERIPHERAL_ATTACHED_EVENT, node.address, node.primaryType)
    }

    private fun queueDetached(node: PeripheralNetworkNode) {
        system.queueEvent("peripheral_detach", node.address)
        system.queueEvent(CCAeroworks.PERIPHERAL_DETACHED_EVENT, node.address, node.primaryType)
    }

    private fun sameTargets(previous: PeripheralNetworkGraph?, next: PeripheralNetworkGraph): Boolean {
        if (previous == null || previous.peripherals.size != next.peripherals.size) return false
        val old = previous.peripherals.associateBy { it.address }
        return next.peripherals.all { node ->
            old[node.address]?.let { previousNode -> samePeripheral(previousNode, node) } == true
        }
    }

    private fun samePeripheral(first: PeripheralNetworkNode, second: PeripheralNetworkNode): Boolean =
        first.primaryType == second.primaryType &&
            first.types == second.types &&
            equivalent(first.target, second.target)

    private fun equivalent(first: IPeripheral, second: IPeripheral): Boolean =
        first === second || runCatching { first.equals(second) && second.equals(first) }.getOrDefault(false)

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
