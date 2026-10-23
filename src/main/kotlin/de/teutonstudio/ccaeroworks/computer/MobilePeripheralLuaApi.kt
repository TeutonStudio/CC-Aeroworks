package de.teutonstudio.ccaeroworks.computer

import dan200.computercraft.api.filesystem.Mount
import dan200.computercraft.api.filesystem.WritableMount
import dan200.computercraft.api.lua.IArguments
import dan200.computercraft.api.lua.IComputerSystem
import dan200.computercraft.api.lua.IDynamicLuaObject
import dan200.computercraft.api.lua.ILuaAPI
import dan200.computercraft.api.lua.ILuaContext
import dan200.computercraft.api.lua.LuaException
import dan200.computercraft.api.lua.LuaFunction
import dan200.computercraft.api.lua.MethodResult
import dan200.computercraft.api.peripheral.IComputerAccess
import dan200.computercraft.api.peripheral.IPeripheral
import dan200.computercraft.api.peripheral.NotAttachedException
import dan200.computercraft.api.peripheral.WorkMonitor
import dan200.computercraft.core.computer.GuardedLuaContext
import dan200.computercraft.core.methods.PeripheralMethod
import dan200.computercraft.shared.computer.core.ServerContext
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.contraption.MobileContraptionControlPeripheral
import de.teutonstudio.ccaeroworks.contraption.MobileConsoleComputer
import de.teutonstudio.ccaeroworks.api.mobile.MobilePeripheralInstance

class MobilePeripheralLuaApi(
    access: MobileConsoleAccess,
    private val system: IComputerSystem
) : ILuaAPI {
    private val owner = access.owner() ?: throw IllegalStateException("Mobile console is no longer loaded")
    private val bindings = linkedMapOf<String, MobileBinding>()

    init {
        owner.peripheralInstances().forEach { instance ->
            val target = instance.peripheral()
            val local = owner.localPosition(instance)
            val address = when (target) {
                is MobileContraptionControlPeripheral -> target.address
                else -> if (PeripheralTypeNames.isControlDesk(target.type)) {
                    "${local.x},${local.y},${local.z}"
                } else {
                    "${target.type}_${local.x}_${local.y}_${local.z}"
                }
            }
            val binding = MobileBinding(system, owner, instance, address)
            bindings[address] = binding
            binding.attach()
            system.queueEvent("peripheral", address)
            system.queueEvent(CCAeroworks.PERIPHERAL_ATTACHED_EVENT, address, target.type)
        }
    }

    override fun getNames(): Array<String> = arrayOf("peripherals")
    override fun getModuleName(): String = "cc_aeroworks.peripherals"

    @LuaFunction(mainThread = true)
    fun find(type: String): Any? {
        val matches = matching(type)
        if (PeripheralTypeNames.isControlDesk(type)) {
            return matches.mapValues { MobileHandle(it.value) }
        }
        return when (matches.size) {
            0 -> null
            1 -> MobileHandle(matches.values.first())
            else -> matches.mapValues { MobileHandle(it.value) }
        }
    }

    @LuaFunction(mainThread = true)
    fun findAll(type: String): Map<String, Any> = matching(type)
        .mapValuesTo(linkedMapOf()) { MobileHandle(it.value) }

    @LuaFunction(mainThread = true)
    fun getTypes(): Map<String, Int> = linkedMapOf<String, Int>().apply {
        bindings.values.forEach { binding ->
            binding.types.forEach { type -> put(type, (get(type) ?: 0) + 1) }
        }
    }

    @LuaFunction(mainThread = true)
    fun getNetwork(): Map<String, Any> = linkedMapOf(
        "id" to owner.entity.uuid.toString(),
        "state" to "active",
        "space" to "contraption",
        "entity" to owner.entity.uuid.toString(),
        "dimension" to owner.level.dimension().location().toString(),
        "deskCount" to owner.deskPositions.size,
        "peripheralCount" to bindings.size
    )

    @LuaFunction(mainThread = true)
    fun refresh(): Map<String, Any> = getNetwork()

    override fun shutdown() {
        bindings.values.forEach { binding ->
            system.queueEvent("peripheral_detach", binding.address)
            system.queueEvent(CCAeroworks.PERIPHERAL_DETACHED_EVENT, binding.address, binding.target.type)
            runCatching { binding.close() }
        }
        bindings.clear()
    }

    private fun matching(type: String): LinkedHashMap<String, MobileBinding> {
        val keys = PeripheralTypeNames.lookupKeys(type)
        return bindings.filterTo(linkedMapOf()) { (_, binding) ->
            binding.types.any { candidate -> PeripheralTypeNames.lookupKeys(candidate).any(keys::contains) }
        }
    }
}

private class MobileHandle(private val binding: MobileBinding) : IDynamicLuaObject {
    private val names = buildList {
        addAll(binding.methodNames.sorted())
        if ("getPeripheralInfo" !in binding.methodNames) add("getPeripheralInfo")
    }.toTypedArray()

    override fun getMethodNames(): Array<String> = names
    override fun callMethod(context: ILuaContext, method: Int, arguments: IArguments): MethodResult {
        val name = names.getOrNull(method) ?: throw LuaException("Unknown peripheral method index $method")
        return if (name == "getPeripheralInfo" && name !in binding.methodNames) {
            MethodResult.of(binding.info())
        } else binding.call(context, name, arguments)
    }
}

private class MobileBinding(
    private val system: IComputerSystem,
    private val owner: MobileConsoleComputer,
    private val instance: MobilePeripheralInstance,
    val address: String
) : IComputerAccess, GuardedLuaContext.Guard {
    val target: IPeripheral = instance.peripheral()
    val types: Set<String> = linkedSetOf(target.type).apply { addAll(target.additionalTypes) }
    private val methods: Map<String, PeripheralMethod> =
        ServerContext.get(system.level.server).peripheralMethods().getSelfMethods(target)
    private val mounts = PeripheralMountRegistry()
    private var attached = false
    private var wrapper: GuardedLuaContext? = null
    val methodNames: Set<String> get() = methods.keys

    fun attach() {
        attached = true
        try { target.attach(this) } catch (failure: Throwable) {
            attached = false
            throw failure
        }
    }

    fun call(context: ILuaContext, name: String, arguments: IArguments): MethodResult {
        val method = methods[name] ?: throw LuaException("No such method $name")
        checkAttached()
        val guarded = wrapper?.takeIf { it.wraps(context) } ?: GuardedLuaContext(context, this).also { wrapper = it }
        return method.apply(target, guarded, this, arguments).adjustError(1)
    }

    fun info(): Map<String, Any> {
        val local = owner.localPosition(instance)
        val world = owner.worldPosition(local)
        return linkedMapOf(
            "address" to address,
            "type" to target.type,
            "types" to types.toList(),
            "space" to "contraption",
            "entity" to owner.entity.uuid.toString(),
            "localPosition" to mapOf("x" to local.x, "y" to local.y, "z" to local.z),
            "worldPosition" to mapOf("x" to world.x, "y" to world.y, "z" to world.z),
            "loaded" to true
        )
    }

    fun close() {
        if (attached) target.detach(this)
        attached = false
        mounts.drain().forEach(system::unmount)
    }

    override fun checkValid(): Boolean = attached && !owner.closed
    override fun mount(desiredLocation: String, mount: Mount, driveName: String): String? =
        system.mount(desiredLocation, mount, driveName)?.also(mounts::add)
    override fun mountWritable(desiredLocation: String, mount: WritableMount, driveName: String): String? =
        system.mountWritable(desiredLocation, mount, driveName)?.also(mounts::add)
    override fun unmount(location: String?) { system.unmount(location); if (location != null) mounts.remove(location) }
    override fun getID(): Int = system.id
    override fun queueEvent(event: String, vararg arguments: Any?) = system.queueEvent(event, *arguments)
    override fun getAttachmentName(): String = address
    override fun getAvailablePeripherals(): Map<String, IPeripheral> = system.availablePeripherals
    override fun getAvailablePeripheral(name: String): IPeripheral? = system.getAvailablePeripheral(name)
    override fun getMainThreadMonitor(): WorkMonitor = system.mainThreadMonitor
    private fun checkAttached() { if (!checkValid()) throw NotAttachedException() }
}
