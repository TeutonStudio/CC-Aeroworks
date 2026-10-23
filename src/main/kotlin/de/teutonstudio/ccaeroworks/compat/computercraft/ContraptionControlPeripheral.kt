package de.teutonstudio.ccaeroworks.compat.computercraft

import com.simibubi.create.content.contraptions.actors.contraptionControls.ContraptionControlsBlockEntity
import dan200.computercraft.api.lua.LuaException
import dan200.computercraft.api.lua.LuaFunction
import dan200.computercraft.api.peripheral.AttachedComputerSet
import dan200.computercraft.api.peripheral.IComputerAccess
import dan200.computercraft.api.peripheral.IPeripheral
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.contraption.ContraptionControlIdentityAccess
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockSkinState
import de.teutonstudio.ccaeroworks.multiblock.ConsoleAssemblyStateAccess
import de.teutonstudio.ccaeroworks.telemetry.TelemetryIdentity
import de.teutonstudio.ccaeroworks.train.TrainControlConsoleGeometry
import de.teutonstudio.ccaeroworks.train.TrainControlConsoleIntegration
import java.lang.ref.WeakReference
import java.util.Locale
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.ItemTags
import net.minecraft.world.level.block.state.properties.BlockStateProperties

/** Stationary CC:Tweaked surface for Create's native contraption controller. */
class ContraptionControlPeripheral(blockEntity: ContraptionControlsBlockEntity) : IPeripheral {
    private val blockEntity = WeakReference(blockEntity)
    internal val computers = AttachedComputerSet()
    internal var lastEnabled: Boolean? = null
    internal var lastPowered: Boolean? = null

    val id: String get() = (controller() as ContraptionControlIdentityAccess)
        .ccaeroworks_getContraptionControlId().toString()
    val address: String get() = "contraption_control_$id"

    override fun getType(): String = "ContraptionControl"

    override fun getAdditionalTypes(): Set<String> = setOf(
        "contraption_control",
        "create:contraption_controls",
        "cc_aeroworks:contraption_control"
    )

    override fun getTarget(): Any? = validController()

    override fun attach(computer: IComputerAccess) {
        computers.add(computer)
        lastEnabled = validController()?.let { !it.disabled }
        lastPowered = validController()?.powered
        ContraptionControlPeripheralState.activate(this)
    }

    override fun detach(computer: IComputerAccess) {
        computers.remove(computer)
        if (!computers.hasComputers()) ContraptionControlPeripheralState.deactivate(this)
    }

    override fun equals(other: IPeripheral?): Boolean =
        other is ContraptionControlPeripheral && validController() != null &&
            validController() === other.validController()

    @LuaFunction(mainThread = true)
    fun getInfo(): Map<String, Any> {
        val be = controller()
        val state = be.blockState
        val level = be.level as? ServerLevel ?: throw LuaException("Contraption control is not in a server level")
        val pos = be.blockPos
        val geometry = TrainControlConsoleIntegration.geometry(state)
        val subLevelId = TelemetryIdentity.subLevelId(level, pos)
        val space = if (subLevelId == null) "world" else "sable"
        val position = linkedMapOf("x" to pos.x, "y" to pos.y, "z" to pos.z)
        return linkedMapOf<String, Any>(
            "id" to id,
            "address" to address,
            "type" to type,
            "mode" to mode(be),
            "space" to space,
            "roomType" to space,
            "x" to pos.x,
            "y" to pos.y,
            "z" to pos.z,
            "localPosition" to position,
            "worldPosition" to position,
            "dimension" to level.dimension().location().toString(),
            "geometry" to when (geometry) {
                TrainControlConsoleGeometry.DESK -> "desk"
                TrainControlConsoleGeometry.STAND -> "stand"
                else -> "native"
            },
            "integrated" to TrainControlConsoleIntegration.isIntegrated(state),
            "facing" to state.getValue(BlockStateProperties.HORIZONTAL_FACING).name.lowercase(Locale.ROOT),
            "skin" to if (state.hasProperty(ConsoleMultiblockSkinState.SKIN)) {
                state.getValue(ConsoleMultiblockSkinState.SKIN).serializedName
            } else "default",
            "filter" to filter(be),
            "redstone" to be.powered,
            "enabled" to !be.disabled,
            "active" to !be.disabled,
            "loaded" to true
        ).apply {
            (be as? ConsoleAssemblyStateAccess)?.ccaeroworks_getAssemblyId()?.let {
                put("assemblyId", it.toString())
            }
            subLevelId?.let { put("subLevelId", it) }
        }
    }

    @LuaFunction(mainThread = true)
    fun getState(): Map<String, Any> = getInfo()

    @LuaFunction(mainThread = true)
    fun getFilter(): Map<String, Any> = filter(controller())

    @LuaFunction(mainThread = true)
    fun isEnabled(): Boolean = !controller().disabled

    @LuaFunction(mainThread = true)
    fun setEnabled(enabled: Boolean): Boolean {
        val be = controller()
        if (!supportsSwitching(be)) throw LuaException("setEnabled is unavailable in elevator mode")
        val changed = be.disabled == enabled
        if (!changed) return enabled
        be.disabled = !enabled
        be.pressButton()
        be.setChanged()
        be.notifyUpdate()
        queueStateEvent("lua")
        lastEnabled = enabled
        return enabled
    }

    @LuaFunction(mainThread = true)
    fun toggle(): Boolean = setEnabled(!isEnabled())

    @LuaFunction(mainThread = true)
    fun getFloors(): List<Map<String, Any>> =
        throw LuaException("getFloors is only available while mounted on an elevator")

    @LuaFunction(mainThread = true)
    fun getSelectedFloor(): Map<String, Any> =
        throw LuaException("getSelectedFloor is only available while mounted on an elevator")

    @LuaFunction(mainThread = true)
    fun selectFloor(indexOrName: Any): Map<String, Any> =
        throw LuaException("selectFloor is only available while mounted on an elevator")

    internal fun validController(): ContraptionControlsBlockEntity? = blockEntity.get()?.takeIf {
        !it.isRemoved && it.level != null && it.level?.isLoaded(it.blockPos) == true
    }

    internal fun queueStateEvent(cause: String) {
        val be = validController() ?: return
        val enabled = !be.disabled
        computers.forEach { computer ->
            computer.queueEvent(
                CCAeroworks.CONTRAPTION_CONTROL_EVENT,
                address,
                id,
                mode(be),
                enabled,
                cause
            )
        }
    }

    private fun controller(): ContraptionControlsBlockEntity =
        validController() ?: throw LuaException("Contraption control is no longer loaded")

    private fun supportsSwitching(be: ContraptionControlsBlockEntity): Boolean = mode(be) != "elevator"

    private fun mode(be: ContraptionControlsBlockEntity): String =
        if (be.filtering.filter.`is`(ItemTags.DOORS)) "doors" else "actors"

    private fun filter(be: ContraptionControlsBlockEntity): Map<String, Any> {
        val stack = be.filtering.filter
        if (stack.isEmpty) return linkedMapOf("all" to true, "empty" to true)
        return linkedMapOf(
            "all" to false,
            "empty" to false,
            "item" to BuiltInRegistries.ITEM.getKey(stack.item).toString(),
            "name" to stack.hoverName.string,
            "count" to stack.count
        )
    }
}
