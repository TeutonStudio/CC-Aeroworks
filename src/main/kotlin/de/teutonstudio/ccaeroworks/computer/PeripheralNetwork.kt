package de.teutonstudio.ccaeroworks.computer

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import dan200.computercraft.api.filesystem.Mount
import dan200.computercraft.api.filesystem.WritableMount
import dan200.computercraft.api.lua.IArguments
import dan200.computercraft.api.lua.IComputerSystem
import dan200.computercraft.api.lua.IDynamicLuaObject
import dan200.computercraft.api.lua.ILuaContext
import dan200.computercraft.api.lua.LuaException
import dan200.computercraft.api.lua.LuaFunction
import dan200.computercraft.api.lua.MethodResult
import dan200.computercraft.api.peripheral.IComputerAccess
import dan200.computercraft.api.peripheral.IPeripheral
import dan200.computercraft.api.peripheral.PeripheralCapability
import dan200.computercraft.api.peripheral.WorkMonitor
import dan200.computercraft.core.methods.PeripheralMethod
import dan200.computercraft.shared.computer.core.ServerContext
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksDeskService
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMember
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager
import de.teutonstudio.ccaeroworks.multiblock.ConsoleNetworkState
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import java.util.Locale

internal data class DeskNetworkNode(
    val member: ConsoleMember,
    val address: String
) {
    val id: String get() = member.id
    val pos: BlockPos get() = member.pos
    val desk: ConsoleBlockEntity get() = member.desk
}

internal data class PeripheralNetworkNode(
    val desk: DeskNetworkNode,
    val pos: BlockPos,
    val side: Direction,
    val address: String,
    val target: IPeripheral,
    val primaryType: String,
    val types: Set<String>,
    val aliases: Set<String>
) {
    fun matches(type: String): Boolean = PeripheralTypeNames.lookupKeys(type).any(aliases::contains)
}

internal data class PeripheralNetworkGraph(
    val revision: Long,
    val state: ConsoleNetworkState,
    val desks: List<DeskNetworkNode>,
    val peripherals: List<PeripheralNetworkNode>,
    val dimension: String
)

internal object PeripheralTypeNames {
    private val separators = Regex("[\\s_-]+")

    fun lookupKeys(value: String): Set<String> {
        val lower = value.trim().lowercase(Locale.ROOT)
        if (lower.isEmpty()) return emptySet()
        val path = lower.substringAfter(':', lower)
        return linkedSetOf(lower, compact(lower), path, compact(path))
    }

    fun aliases(types: Iterable<String>): Set<String> = buildSet {
        types.forEach { type -> addAll(lookupKeys(type)) }
    }

    fun isControlDesk(value: String): Boolean = compact(value.trim().lowercase(Locale.ROOT)) == "controldesk"

    private fun compact(value: String): String = separators.replace(value, "")
}

internal object PeripheralNetworkBuilder {
    fun build(owner: ComputerControlDeskBlockEntity): PeripheralNetworkGraph {
        val level = owner.level as? ServerLevel
            ?: throw LuaException("The computer control desk is not in a server level")
        val snapshot = ConsoleMultiblockManager.resolve(level, owner.blockPos)
        when (snapshot.state) {
            ConsoleNetworkState.TOO_LARGE ->
                throw LuaException("The control desk network exceeds 64 desks")
            ConsoleNetworkState.PARTIALLY_LOADED ->
                throw LuaException("The control desk network is only partially loaded")
            ConsoleNetworkState.CONFLICT ->
                throw LuaException("Multiple computer control desks are connected")
            else -> Unit
        }
        if (snapshot.owner !== owner) {
            throw LuaException("This computer does not own the connected control desk network")
        }

        val deskPositions = snapshot.members.mapTo(hashSetOf()) { it.pos }
        val desks = snapshot.members.map { member -> DeskNetworkNode(member, address(member.pos)) }
        val peripherals = mutableListOf<PeripheralNetworkNode>()
        for (desk in desks) {
            for (side in Direction.values()) {
                val targetPos = desk.pos.relative(side)
                if (targetPos in deskPositions || !level.isLoaded(targetPos)) continue
                val peripheral = level.getCapability(
                    PeripheralCapability.get(),
                    targetPos,
                    side.opposite
                ) ?: continue
                val types = linkedSetOf(peripheral.type).apply {
                    addAll(peripheral.additionalTypes)
                }
                peripherals += PeripheralNetworkNode(
                    desk = desk,
                    pos = targetPos.immutable(),
                    side = side,
                    address = "${desk.address}/${side.name.lowercase(Locale.ROOT)}",
                    target = peripheral,
                    primaryType = peripheral.type,
                    types = types,
                    aliases = PeripheralTypeNames.aliases(types)
                )
            }
        }
        return PeripheralNetworkGraph(
            revision = snapshot.revision,
            state = snapshot.state,
            desks = desks,
            peripherals = peripherals,
            dimension = level.dimension().location().toString()
        )
    }

    fun address(pos: BlockPos): String = "${pos.x},${pos.y},${pos.z}"
}

internal class PeripheralNetworkRuntime(
    private val access: ComputerConsoleAccess,
    private val system: IComputerSystem
) {
    private var graph: PeripheralNetworkGraph? = null
    private val bindings = linkedMapOf<String, PeripheralBinding>()
    private var initialized = false

    @Synchronized
    fun graph(force: Boolean = false): PeripheralNetworkGraph {
        val owner = access.owner()
            ?: throw LuaException("The computer control desk is no longer loaded")
        val next = PeripheralNetworkBuilder.build(owner)
        if (!force && graph?.revision == next.revision && sameTargets(graph, next)) {
            return graph!!
        }

        val desired = next.peripherals.associateBy { it.address }
        val detached = mutableListOf<PeripheralNetworkNode>()
        val iterator = bindings.iterator()
        while (iterator.hasNext()) {
            val (address, binding) = iterator.next()
            val node = desired[address]
            if (node == null || node.target !== binding.node.target) {
                detached += binding.node
                binding.close()
                iterator.remove()
            }
        }

        val attached = mutableListOf<PeripheralNetworkNode>()
        for (node in next.peripherals) {
            if (node.address !in bindings) {
                bindings[node.address] = PeripheralBinding(this, system, node)
                attached += node
            }
        }
        graph = next
        if (initialized) {
            detached.forEach { node ->
                system.queueEvent(CCAeroworks.PERIPHERAL_DETACHED_EVENT, node.address, node.primaryType)
            }
            attached.forEach { node ->
                system.queueEvent(CCAeroworks.PERIPHERAL_ATTACHED_EVENT, node.address, node.primaryType)
            }
        }
        initialized = true
        return next
    }

    @Synchronized
    fun close() {
        bindings.values.forEach(PeripheralBinding::close)
        bindings.clear()
        graph = null
        initialized = false
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

    private fun sameTargets(previous: PeripheralNetworkGraph?, next: PeripheralNetworkGraph): Boolean {
        if (previous == null || previous.peripherals.size != next.peripherals.size) return false
        val old = previous.peripherals.associateBy { it.address }
        return next.peripherals.all { node -> old[node.address]?.target === node.target }
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
                if (!number.isFinite() || number % 1.0 != 0.0) {
                    throw LuaException("Position coordinate '$name' must be an integer")
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
}

internal class PeripheralBinding(
    private val runtime: PeripheralNetworkRuntime,
    private val system: IComputerSystem,
    val node: PeripheralNetworkNode
) : IComputerAccess {
    private val methods: Map<String, PeripheralMethod> =
        ServerContext.get(system.level.server).peripheralMethods().getSelfMethods(node.target)
    private var attached = true

    init {
        node.target.attach(this)
    }

    val methodNames: Set<String> get() = methods.keys

    fun call(context: ILuaContext, name: String, arguments: IArguments): MethodResult {
        if (!attached) throw LuaException("Peripheral '${node.address}' is detached")
        val method = methods[name] ?: throw LuaException("No such method $name")
        return method.apply(node.target, context, this, arguments).adjustError(1)
    }

    fun info(): Map<String, Any> = runtime.describePeripheral(node)

    fun close() {
        if (!attached) return
        attached = false
        node.target.detach(this)
    }

    override fun mount(desiredLocation: String, mount: Mount, driveName: String): String? =
        system.mount(desiredLocation, mount, driveName)

    override fun mountWritable(desiredLocation: String, mount: WritableMount, driveName: String): String? =
        system.mountWritable(desiredLocation, mount, driveName)

    override fun unmount(location: String?) = system.unmount(location)

    override fun getID(): Int = system.id

    override fun queueEvent(event: String, vararg arguments: Any?) = system.queueEvent(event, *arguments)

    override fun getAttachmentName(): String = node.address

    override fun getAvailablePeripherals(): Map<String, IPeripheral> = runtime.availablePeripherals()

    override fun getAvailablePeripheral(name: String): IPeripheral? = runtime.availablePeripherals()[name]

    override fun getMainThreadMonitor(): WorkMonitor = system.mainThreadMonitor
}

internal class PeripheralLuaHandle(private val binding: PeripheralBinding) : IDynamicLuaObject {
    private val names = buildList {
        addAll(binding.methodNames.sorted())
        if ("getPeripheralInfo" !in binding.methodNames) add("getPeripheralInfo")
    }.toTypedArray()

    override fun getMethodNames(): Array<String> = names

    override fun callMethod(context: ILuaContext, method: Int, arguments: IArguments): MethodResult {
        val name = names.getOrNull(method) ?: throw LuaException("Unknown peripheral method index $method")
        return if (name == "getPeripheralInfo" && name !in binding.methodNames) {
            MethodResult.of(binding.info())
        } else {
            binding.call(context, name, arguments)
        }
    }
}

internal class DeskLuaHandle(
    private val runtime: PeripheralNetworkRuntime,
    private val address: String
) {
    @LuaFunction(mainThread = true)
    fun getInfo(): Map<String, Any> = runtime.describeDesk(address)

    @LuaFunction(mainThread = true)
    fun getSocketCount(): Int = runtime.desk(address).socketCount()

    @LuaFunction(mainThread = true)
    fun getSockets(): List<Map<String, Any>> = AeroworksDeskService.getSockets(runtime.desk(address))

    @LuaFunction(mainThread = true)
    fun getModules(): List<Map<String, Any>> = AeroworksDeskService.getModules(runtime.desk(address))

    @LuaFunction(mainThread = true)
    fun getModule(arguments: IArguments): Map<String, Any>? =
        AeroworksDeskService.getModule(runtime.desk(address), arguments.get(0))

    @LuaFunction(mainThread = true)
    fun getInput(arguments: IArguments): Any =
        AeroworksDeskService.getInput(runtime.desk(address), arguments.get(0))

    @LuaFunction(mainThread = true)
    fun getInputs(): Map<Int, Any> = AeroworksDeskService.getInputs(runtime.desk(address))

    @LuaFunction(mainThread = true)
    fun getDisplays(): List<Map<String, Any>> = AeroworksDeskService.getDisplays(runtime.desk(address))

    @LuaFunction(mainThread = true)
    fun getDisplay(arguments: IArguments): Map<String, Any> =
        AeroworksDeskService.getDisplay(runtime.desk(address), arguments.get(0))

    @LuaFunction(mainThread = true)
    fun setDisplayText(arguments: IArguments): String =
        AeroworksDeskService.setDisplayText(runtime.desk(address), arguments.get(0), arguments.getString(1))

    @LuaFunction(mainThread = true)
    fun setDisplayNumber(arguments: IArguments): String =
        AeroworksDeskService.setDisplayNumber(
            runtime.desk(address),
            arguments.get(0),
            arguments.getDouble(1),
            arguments.optBoolean(2).orElse(false)
        )

    @LuaFunction(mainThread = true)
    fun clearDisplay(arguments: IArguments) =
        AeroworksDeskService.clearDisplay(runtime.desk(address), arguments.get(0))

    @LuaFunction(mainThread = true)
    fun clearDisplays(): Int = AeroworksDeskService.clearDisplays(runtime.desk(address))

    @LuaFunction(mainThread = true)
    fun getDisplaySize(arguments: IArguments): Map<String, Int> =
        AeroworksDeskService.getDisplaySize(runtime.desk(address), arguments.get(0))

    @LuaFunction(mainThread = true)
    fun getDisplayPixel(arguments: IArguments): Boolean = AeroworksDeskService.getDisplayPixel(
        runtime.desk(address), arguments.get(0), arguments.getInt(1), arguments.getInt(2)
    )

    @LuaFunction(mainThread = true)
    fun setDisplayPixel(arguments: IArguments): Boolean = AeroworksDeskService.setDisplayPixel(
        runtime.desk(address),
        arguments.get(0),
        arguments.getInt(1),
        arguments.getInt(2),
        arguments.getBoolean(3)
    )

    @LuaFunction(mainThread = true)
    fun setDisplayPixels(arguments: IArguments): List<String> {
        val table = arguments.getTableUnsafe(1)
        val rows = (1..table.length()).map(table::getString)
        return AeroworksDeskService.setDisplayPixels(runtime.desk(address), arguments.get(0), rows)
    }

    @LuaFunction(mainThread = true)
    fun clearDisplayPixels(arguments: IArguments) =
        AeroworksDeskService.clearDisplayPixels(runtime.desk(address), arguments.get(0))

    @LuaFunction(mainThread = true)
    fun getPeripherals(): Map<String, Any> = runtime.peripheralsForDesk(address)

    @LuaFunction(mainThread = true)
    fun find(type: String): Any? = runtime.find(type, address, false)

    @LuaFunction(mainThread = true)
    fun findAll(type: String): Map<String, Any> =
        runtime.find(type, address, true) as Map<String, Any>

    @LuaFunction(mainThread = true)
    fun wrap(arguments: IArguments): Any? = runtime.wrap(arguments, address)
}
