package de.teutonstudio.ccaeroworks.ctfcompat.computer

import createtrainfunctionality.DashboardControl
import createtrainfunctionality.DashboardControllerBlockEntity
import dan200.computercraft.api.lua.LuaException
import dan200.computercraft.api.lua.LuaFunction
import dan200.computercraft.api.peripheral.AttachedComputerSet
import dan200.computercraft.api.peripheral.IComputerAccess
import dan200.computercraft.api.peripheral.IPeripheral
import de.teutonstudio.ccaeroworks.ctfcompat.DashboardControllerIdentityAccess
import de.teutonstudio.ccaeroworks.ctfcompat.control.DashboardControllerLookup
import de.teutonstudio.ccaeroworks.ctfcompat.control.DashboardSourceStatus
import de.teutonstudio.ccaeroworks.ctfcompat.control.TrainSignal
import de.teutonstudio.ccaeroworks.multiblock.ConsoleAssemblyRuntime
import java.lang.ref.WeakReference
import java.util.Locale
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.state.properties.BlockStateProperties

class DashboardControllerPeripheral(blockEntity: DashboardControllerBlockEntity) : IPeripheral {
    private val blockEntity = WeakReference(blockEntity)
    private val computers = AttachedComputerSet()
    private var signal = TrainSignal()

    val id: String
        get() = (controller() as DashboardControllerIdentityAccess)
            .ccaeroworks_getDashboardControllerId().toString()
    val address: String get() = "dashboard_controller_$id"

    override fun getType(): String = "DashboardController"

    override fun getAdditionalTypes(): Set<String> = setOf(
        "dashboard_controller",
        "create_train_functionality:dashboard_controller",
        "cc_aeroworks:dashboard_controller"
    )

    override fun getTarget(): Any? = validController()

    override fun attach(computer: IComputerAccess) {
        computers.add(computer)
    }

    override fun detach(computer: IComputerAccess) {
        computers.remove(computer)
        if (!computers.hasComputers()) reset()
    }

    override fun equals(other: IPeripheral?): Boolean =
        other is DashboardControllerPeripheral && validController() != null &&
            validController() === other.validController()

    @LuaFunction(mainThread = true)
    fun setThrottle(value: Double): Double {
        val normalized = requireUnit(value, "Throttle")
        requirePanelMode()
        signal = signal.copy(throttle = normalized.toFloat())
        return normalized
    }

    @LuaFunction(mainThread = true)
    fun setSteer(value: Double): Double {
        val normalized = requireUnit(value, "Steer")
        requirePanelMode()
        signal = signal.copy(steer = normalized.toFloat())
        return normalized
    }

    @LuaFunction(mainThread = true)
    fun setBrake(active: Boolean): Boolean {
        requirePanelMode()
        signal = signal.copy(brake = active)
        return active
    }

    @LuaFunction(mainThread = true)
    fun setStation(active: Boolean): Boolean {
        requirePanelMode()
        signal = signal.copy(station = active)
        return active
    }

    @LuaFunction(mainThread = true)
    fun getState(): Map<String, Any> {
        val be = controller()
        val level = be.level as? ServerLevel ?: throw LuaException("Dashboard Controller is not in a server level")
        if (DashboardControl.frequencyMode(be.blockState)) reset()
        val resolution = DashboardControllerLookup.forController(level, be)
        return linkedMapOf(
            "throttle" to signal.throttle.toDouble(),
            "steer" to signal.steer.toDouble(),
            "brake" to signal.brake,
            "station" to signal.station,
            "mode" to mode(be),
            "available" to (resolution.status == DashboardSourceStatus.AVAILABLE && resolution.controller === be),
            "status" to resolution.status.name.lowercase(Locale.ROOT)
        )
    }

    @LuaFunction(mainThread = true)
    fun getInfo(): Map<String, Any> {
        val be = controller()
        val level = be.level as? ServerLevel ?: throw LuaException("Dashboard Controller is not in a server level")
        val pos = be.blockPos
        val top = pos.above()
        val state = be.blockState
        val result = linkedMapOf<String, Any>(
            "id" to id,
            "address" to address,
            "type" to type,
            "mode" to mode(be),
            "x" to pos.x,
            "y" to pos.y,
            "z" to pos.z,
            "position" to linkedMapOf("x" to pos.x, "y" to pos.y, "z" to pos.z),
            "dimension" to level.dimension().location().toString(),
            "facing" to if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                state.getValue(BlockStateProperties.HORIZONTAL_FACING).name.lowercase(Locale.ROOT)
            } else "unknown"
        )
        ConsoleAssemblyRuntime.id(level, top)?.let { result["assemblyId"] = it.toString() }
        result.putAll(getState())
        return result
    }

    internal fun currentSignal(): TrainSignal {
        val be = validController() ?: return TrainSignal()
        if (DashboardControl.frequencyMode(be.blockState)) {
            reset()
            return TrainSignal()
        }
        return signal
    }

    internal fun reset() {
        signal = TrainSignal()
    }

    internal fun validController(): DashboardControllerBlockEntity? = blockEntity.get()?.takeIf {
        !it.isRemoved && it.level != null && it.level?.isLoaded(it.blockPos) == true
    }

    private fun controller(): DashboardControllerBlockEntity =
        validController() ?: throw LuaException("Dashboard Controller is no longer loaded")

    private fun requirePanelMode(): DashboardControllerBlockEntity {
        val be = controller()
        if (DashboardControl.frequencyMode(be.blockState)) {
            reset()
            throw LuaException("Dashboard Controller Lua controls are unavailable in frequency mode")
        }
        return be
    }

    private fun mode(be: DashboardControllerBlockEntity): String =
        if (DashboardControl.frequencyMode(be.blockState)) "frequency" else "panel"

    private fun requireUnit(value: Double, name: String): Double {
        if (!value.isFinite() || value < -1.0 || value > 1.0) {
            throw LuaException("$name must be a finite number between -1 and 1")
        }
        return value
    }
}
