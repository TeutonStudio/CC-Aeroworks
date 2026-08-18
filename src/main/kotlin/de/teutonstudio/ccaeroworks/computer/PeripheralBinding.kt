package de.teutonstudio.ccaeroworks.computer

import dan200.computercraft.api.filesystem.Mount
import dan200.computercraft.api.filesystem.WritableMount
import dan200.computercraft.api.lua.IArguments
import dan200.computercraft.api.lua.IComputerSystem
import dan200.computercraft.api.lua.ILuaContext
import dan200.computercraft.api.lua.LuaException
import dan200.computercraft.api.lua.MethodResult
import dan200.computercraft.api.peripheral.IComputerAccess
import dan200.computercraft.api.peripheral.IPeripheral
import dan200.computercraft.api.peripheral.NotAttachedException
import dan200.computercraft.api.peripheral.WorkMonitor
import dan200.computercraft.core.computer.GuardedLuaContext
import dan200.computercraft.core.methods.PeripheralMethod
import dan200.computercraft.shared.computer.core.ServerContext

internal class PeripheralBinding(
    private val runtime: PeripheralNetworkRuntime,
    private val system: IComputerSystem,
    node: PeripheralNetworkNode
) : IComputerAccess, GuardedLuaContext.Guard {
    var node: PeripheralNetworkNode = node
        private set

    private val methods: Map<String, PeripheralMethod> =
        ServerContext.get(system.getLevel().server).peripheralMethods().getSelfMethods(node.target)
    private val mounts = PeripheralMountRegistry()
    private var attached = false
    private var contextWrapper: GuardedLuaContext? = null

    val methodNames: Set<String> get() = methods.keys

    @Synchronized
    fun attach() {
        if (attached) return
        // IPeripheral.attach() is allowed to use IComputerAccess immediately, including mount().
        // Therefore the binding must be considered attached during the callback. If the callback
        // fails later, unwind every resource it may already have acquired before exposing failure.
        attached = true
        try {
            node.target.attach(this)
        } catch (throwable: Throwable) {
            runCatching { node.target.detach(this) }
                .exceptionOrNull()
                ?.let(throwable::addSuppressed)
            cleanupMounts(throwable)
            attached = false
            contextWrapper = null
            throw throwable
        }
    }

    @Synchronized
    fun updateNode(next: PeripheralNetworkNode) {
        checkAttached()
        node = next
    }

    fun call(context: ILuaContext, name: String, arguments: IArguments): MethodResult {
        val method = methods[name] ?: throw LuaException("No such method $name")
        val guarded = synchronized(this) {
            checkAttached()
            val current = contextWrapper
            if (current != null && current.wraps(context)) {
                current
            } else {
                GuardedLuaContext(context, this).also { contextWrapper = it }
            }
        }
        return method.apply(node.target, guarded, this, arguments).adjustError(1)
    }

    fun info(): Map<String, Any> {
        checkAttached()
        return runtime.describePeripheral(node)
    }

    @Synchronized
    fun close() {
        if (!attached && mounts.isEmpty()) {
            contextWrapper = null
            return
        }

        var failure: Throwable? = null
        if (attached) {
            failure = runCatching { node.target.detach(this) }.exceptionOrNull()
        }
        failure = cleanupMounts(failure)
        attached = false
        contextWrapper = null
        if (failure != null) throw failure
    }

    override fun checkValid(): Boolean = synchronized(this) { attached }

    @Synchronized
    override fun mount(desiredLocation: String, mount: Mount, driveName: String): String? {
        checkAttached()
        return system.mount(desiredLocation, mount, driveName)?.also(mounts::add)
    }

    @Synchronized
    override fun mountWritable(desiredLocation: String, mount: WritableMount, driveName: String): String? {
        checkAttached()
        return system.mountWritable(desiredLocation, mount, driveName)?.also(mounts::add)
    }

    @Synchronized
    override fun unmount(location: String?) {
        checkAttached()
        system.unmount(location)
        if (location != null) mounts.remove(location)
    }

    override fun getID(): Int {
        checkAttached()
        return system.getID()
    }

    override fun queueEvent(event: String, vararg arguments: Any?) {
        checkAttached()
        system.queueEvent(event, *arguments)
    }

    override fun getAttachmentName(): String {
        checkAttached()
        return node.address
    }

    override fun getAvailablePeripherals(): Map<String, IPeripheral> {
        checkAttached()
        return linkedMapOf<String, IPeripheral>().apply {
            putAll(system.getAvailablePeripherals())
            putAll(runtime.availablePeripherals())
        }
    }

    override fun getAvailablePeripheral(name: String): IPeripheral? {
        checkAttached()
        return runtime.availablePeripherals()[name] ?: system.getAvailablePeripheral(name)
    }

    override fun getMainThreadMonitor(): WorkMonitor {
        checkAttached()
        return system.getMainThreadMonitor()
    }

    private fun cleanupMounts(primary: Throwable?): Throwable? {
        var failure = primary
        mounts.drain().forEach { location ->
            val unmountFailure = runCatching { system.unmount(location) }.exceptionOrNull() ?: return@forEach
            if (failure == null) {
                failure = unmountFailure
            } else if (failure !== unmountFailure) {
                failure.addSuppressed(unmountFailure)
            }
        }
        return failure
    }

    @Synchronized
    private fun checkAttached() {
        if (!attached) throw NotAttachedException()
    }
}
