package de.teutonstudio.ccaeroworks.computer

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import dan200.computercraft.api.ComputerCraftAPI
import dan200.computercraft.api.lua.IComputerSystem
import dan200.computercraft.api.lua.ILuaAPI
import dan200.computercraft.api.lua.LuaException
import dan200.computercraft.api.lua.LuaFunction
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksDeskAccess
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksDeskService
import de.teutonstudio.ccaeroworks.compat.aeroworks.DeskSockets
import de.teutonstudio.ccaeroworks.computer.reactive.ReactiveDependencyRuntime
import de.teutonstudio.ccaeroworks.computer.reactive.ReactivePhase
import de.teutonstudio.ccaeroworks.display.DisplayBindings
import de.teutonstudio.ccaeroworks.display.reactive.ReactiveDisplayFrameBuilder
import de.teutonstudio.ccaeroworks.display.reactive.ReactiveDisplayFrames
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager
import de.teutonstudio.ccaeroworks.multiblock.ConsoleNetworkState

class DisplayUiLuaApi(
    private val access: ComputerConsoleAccess,
    private val system: IComputerSystem
) : ILuaAPI {
    private val reactive: ReactiveDependencyRuntime = access.reactive(system)
    private var mountedPath: String? = null

    override fun getNames(): Array<String> = emptyArray()

    override fun getModuleName(): String = "cc_aeroworks.ui_native"

    override fun startup() {
        if (mountedPath != null) return
        val mount = ComputerCraftAPI.createResourceMount(
            system.level.server,
            CCAeroworks.MOD_ID,
            "lua"
        ) ?: return
        mountedPath = system.mount("cc_aeroworks", mount, CCAeroworks.MOD_ID)
    }

    override fun shutdown() {
        mountedPath?.let(system::unmount)
        mountedPath = null
        reactive.reset()
    }

    @LuaFunction
    fun beginScope(id: String, phase: String) {
        try {
            reactive.beginScope(id, ReactivePhase.parse(phase))
        } catch (error: IllegalArgumentException) {
            throw LuaException(error.message ?: "Invalid reactive scope")
        }
    }

    @LuaFunction
    fun endScope() {
        try {
            reactive.endScope()
        } catch (error: IllegalStateException) {
            throw LuaException(error.message ?: "No reactive scope is active")
        }
    }

    @LuaFunction
    fun read(dependency: String) {
        reactive.read(dependency)
    }

    @LuaFunction
    fun changed(dependency: String) {
        reactive.changed(dependency)
    }

    @LuaFunction
    fun forgetScope(id: String) {
        reactive.forgetScope(id)
    }

    @LuaFunction
    fun consumeInvalidations(): List<Map<String, Any>> = reactive.consumeInvalidations()

    @LuaFunction
    fun getDependencies(): Map<String, List<Map<String, String>>> = reactive.describeDependencies()

    @LuaFunction(mainThread = true)
    fun listDisplays(): List<Map<String, Any>> {
        val owner = owner()
        val snapshot = ConsoleMultiblockManager.resolve(owner.level ?: return emptyList(), owner.blockPos)
        if (snapshot.state != ConsoleNetworkState.ACTIVE || snapshot.owner !== owner) return emptyList()
        return snapshot.members.flatMap { member ->
            AeroworksDeskAccess.displays(member.desk).map { display ->
                val binding = DisplayBindings.get(member.desk, display.socket)
                linkedMapOf<String, Any>(
                    "deskId" to member.id,
                    "deskIndex" to member.index,
                    "socket" to display.socket,
                    "socketName" to DeskSockets.name(display.socket),
                    "width" to display.type.pixelWidth,
                    "height" to display.type.pixelHeight,
                    "runtime" to (ReactiveDisplayFrames.snapshot(member.desk, display.socket) != null),
                    "controller" to DisplayBindings.controllerPath(binding),
                    "bootProgram" to DisplayBindings.runtimeBootProgramPath(binding)
                )
            }
        }
    }

    @LuaFunction(mainThread = true)
    fun beginFrame(deskId: String, socket: Any?): ReactiveFrameLuaHandle {
        val desk = requiredDesk(deskId)
        val parsedSocket = AeroworksDeskService.parseSocket(desk, socket)
        val display = AeroworksDeskAccess.display(desk, parsedSocket)
            ?: throw LuaException("Module at socket $parsedSocket is not a CC-Aeroworks display")
        val builder = ReactiveDisplayFrames.begin(
            desk,
            parsedSocket,
            display.type.pixelWidth,
            display.type.pixelHeight
        )
        return ReactiveFrameLuaHandle(desk, parsedSocket, builder)
    }

    @LuaFunction(mainThread = true)
    fun clearFrame(deskId: String, socket: Any?) {
        val desk = requiredDesk(deskId)
        val parsedSocket = AeroworksDeskService.parseSocket(desk, socket)
        ReactiveDisplayFrames.clear(desk, parsedSocket)
    }

    private fun owner(): ComputerControlDeskBlockEntity =
        access.owner() ?: throw LuaException("The computer control desk is no longer loaded")

    private fun requiredDesk(id: String): ConsoleBlockEntity {
        val owner = owner()
        val level = owner.level ?: throw LuaException("The computer control desk is no longer in a level")
        val snapshot = ConsoleMultiblockManager.resolve(level, owner.blockPos)
        if (snapshot.state != ConsoleNetworkState.ACTIVE || snapshot.owner !== owner) {
            throw LuaException("The control desk network is not active")
        }
        return snapshot.members.firstOrNull { it.id == id }?.desk
            ?: throw LuaException("Control desk '$id' was not found in this multiblock")
    }
}

class ReactiveFrameLuaHandle internal constructor(
    private val desk: ConsoleBlockEntity,
    private val socket: Int,
    private val builder: ReactiveDisplayFrameBuilder
) {
    private var finished: Boolean = false

    @LuaFunction
    fun getSize(): Map<String, Int> = linkedMapOf(
        "width" to builder.width,
        "height" to builder.height
    )

    @LuaFunction
    fun clear() {
        requireOpen()
        builder.clear()
    }

    @LuaFunction
    fun setPixel(x: Int, y: Int, enabled: Boolean) {
        requireOpen()
        try {
            builder.setPixel(x - 1, y - 1, enabled)
        } catch (error: IllegalArgumentException) {
            throw LuaException(error.message ?: "Invalid pixel")
        }
    }

    @LuaFunction
    fun fillRect(x: Int, y: Int, width: Int, height: Int, enabled: Boolean) {
        requireOpen()
        try {
            builder.fillRect(x - 1, y - 1, width, height, enabled)
        } catch (error: IllegalArgumentException) {
            throw LuaException(error.message ?: "Invalid rectangle")
        }
    }

    @LuaFunction(mainThread = true)
    fun commit(): Map<String, Any> {
        requireOpen()
        val snapshot = ReactiveDisplayFrames.commit(desk, socket, builder)
        finished = true
        return linkedMapOf(
            "revision" to snapshot.revision,
            "width" to snapshot.width,
            "height" to snapshot.height,
            "nonEmptyTiles" to snapshot.nonEmptyTileCount()
        )
    }

    private fun requireOpen() {
        if (finished) throw LuaException("This display frame has already been committed")
    }
}
