package de.teutonstudio.ccaeroworks.contraption

import com.simibubi.create.api.behaviour.movement.MovementBehaviour
import com.simibubi.create.content.contraptions.Contraption
import com.simibubi.create.content.contraptions.actors.contraptionControls.ContraptionControlsMovement
import com.simibubi.create.content.contraptions.actors.contraptionControls.ContraptionDisableActorPacket
import com.simibubi.create.content.contraptions.elevator.ElevatorColumn
import com.simibubi.create.content.contraptions.elevator.ElevatorContactBlock
import com.simibubi.create.content.contraptions.elevator.ElevatorContraption
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity
import dan200.computercraft.api.lua.IArguments
import dan200.computercraft.api.lua.LuaException
import dan200.computercraft.api.lua.LuaFunction
import dan200.computercraft.api.peripheral.AttachedComputerSet
import dan200.computercraft.api.peripheral.IComputerAccess
import dan200.computercraft.api.peripheral.IPeripheral
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.api.mobile.MobilePeripheralContext
import de.teutonstudio.ccaeroworks.api.mobile.MobilePeripheralInstance
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockSkinState
import de.teutonstudio.ccaeroworks.train.TrainControlConsoleGeometry
import de.teutonstudio.ccaeroworks.train.TrainControlConsoleIntegration
import java.util.UUID
import net.createmod.catnip.platform.CatnipServices
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.tags.ItemTags
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.properties.BlockStateProperties

class MobileContraptionControlPeripheral(
    private val context: MobilePeripheralContext
) : IPeripheral, MobilePeripheralInstance {
    private val computers = AttachedComputerSet()
    private val id: UUID = context.blockEntityData().let { tag ->
        if (tag.hasUUID(ID_TAG)) tag.getUUID(ID_TAG) else UUID.randomUUID().also { tag.putUUID(ID_TAG, it) }
    }
    private var lastEnabled: Boolean? = null
    val address: String = "contraption_control_$id"

    override fun peripheral(): IPeripheral = this
    override fun getType(): String = "ContraptionControl"
    override fun getAdditionalTypes(): Set<String> = setOf(
        "contraption_control", "create:contraption_controls", "cc_aeroworks:contraption_control"
    )
    override fun getTarget(): Any = context.entity()
    override fun attach(computer: IComputerAccess) { computers.add(computer) }
    override fun detach(computer: IComputerAccess) { computers.remove(computer) }
    override fun equals(other: IPeripheral?): Boolean =
        other is MobileContraptionControlPeripheral && other.id == id && other.context.entity() === context.entity()

    @LuaFunction(mainThread = true)
    fun getInfo(): Map<String, Any> {
        val local = context.localPosition()
        val world = context.worldPosition()
        val geometry = TrainControlConsoleIntegration.geometry(context.blockState())
        return linkedMapOf(
            "id" to id.toString(),
            "address" to address,
            "type" to type,
            "mode" to mode(),
            "space" to "contraption",
            "roomType" to "contraption",
            "entity" to context.entity().uuid.toString(),
            "localPosition" to position(local),
            "worldPosition" to position(world),
            "geometry" to when (geometry) {
                TrainControlConsoleGeometry.DESK -> "desk"
                TrainControlConsoleGeometry.STAND -> "stand"
                else -> "native"
            },
            "facing" to context.blockState().getValue(BlockStateProperties.HORIZONTAL_FACING).name.lowercase(),
            "skin" to if (context.blockState().hasProperty(ConsoleMultiblockSkinState.SKIN)) {
                context.blockState().getValue(ConsoleMultiblockSkinState.SKIN).serializedName
            } else "default",
            "filter" to filterInfo(),
            "redstone" to false,
            "enabled" to isEnabled(),
            "active" to isEnabled(),
            "loaded" to true
        )
    }

    @LuaFunction(mainThread = true)
    fun getState(): Map<String, Any> = getInfo()

    @LuaFunction(mainThread = true)
    fun getFilter(): Map<String, Any> = filterInfo()

    @LuaFunction(mainThread = true)
    fun isEnabled(): Boolean {
        if (mode() == "elevator") return true
        val contraption = context.entity().contraption
        val filter = filter()
        return !contraption.isActorTypeDisabled(filter) && !contraption.isActorTypeDisabled(ItemStack.EMPTY)
    }

    @LuaFunction(mainThread = true)
    fun setEnabled(enabled: Boolean): Boolean {
        if (mode() == "elevator") throw LuaException("setEnabled is unavailable in elevator mode")
        targets().forEach { (entity, contraption) ->
            val filter = filter()
            val disabled = contraption.disabledActors
            if (filter.isEmpty) disabled.clear()
            disabled.removeIf { ContraptionControlsMovement.isSameFilter(it, filter) }
            if (!enabled) disabled.add(filter.copy())
            contraption.setActorsActive(filter, enabled)
            CatnipServices.NETWORK.sendToClientsTrackingEntity(
                entity,
                ContraptionDisableActorPacket(entity.id, filter, enabled)
            )
        }
        context.blockEntityData().putBoolean("Disabled", !enabled)
        lastEnabled = enabled
        queueEvent("lua", enabled)
        return enabled
    }

    @LuaFunction(mainThread = true)
    fun toggle(): Boolean = setEnabled(!isEnabled())

    @LuaFunction(mainThread = true)
    fun getFloors(): List<Map<String, Any>> = elevator().namesList.mapIndexed { index, floor ->
        val names = floor.second
        linkedMapOf(
            "index" to index + 1,
            "y" to floor.first,
            "shortName" to names.first,
            "name" to names.second,
            "reachable" to !elevator().isTargetUnreachable(floor.first)
        )
    }

    @LuaFunction(mainThread = true)
    fun getSelectedFloor(): Map<String, Any> {
        val elevator = elevator()
        val target = elevator.getCurrentTargetY(context.level())
            ?: throw LuaException("The elevator has no selected floor")
        return getFloors().firstOrNull { it["y"] == target }
            ?: throw LuaException("The selected elevator floor is no longer available")
    }

    @LuaFunction(mainThread = true)
    fun selectFloor(arguments: IArguments): Map<String, Any> {
        val elevator = elevator()
        val raw = arguments.get(0)
        val candidates = when (raw) {
            is Number -> {
                val index = raw.toDouble()
                if (!index.isFinite() || index % 1.0 != 0.0) throw LuaException("Floor index must be an integer")
                listOfNotNull(elevator.namesList.getOrNull(index.toInt() - 1))
            }
            is String -> elevator.namesList.filter {
                it.second.first.equals(raw, true) || it.second.second.equals(raw, true)
            }
            else -> throw LuaException("Expected a floor index or name")
        }
        if (candidates.isEmpty()) throw LuaException("Elevator floor was not found")
        if (candidates.size > 1) throw LuaException("Elevator floor name is ambiguous")
        val targetY = candidates.single().first
        if (elevator.isTargetUnreachable(targetY)) throw LuaException("Elevator floor is unreachable")
        val column = ElevatorColumn.get(context.level(), elevator.globalColumn)
            ?: throw LuaException("Elevator column is no longer loaded")
        if (column.contacts.none { it.y == targetY }) throw LuaException("Elevator contact is no longer available")
        val contactPos = column.contactAt(targetY)
        val state = context.level().getBlockState(contactPos)
        val block = state.block as? ElevatorContactBlock
            ?: throw LuaException("Elevator contact is no longer available")
        block.callToContactAndUpdate(column, state, context.level(), contactPos, false)
        queueEvent("elevator", true)
        return getFloors().first { it["y"] == targetY }
    }

    override fun save(context: MobilePeripheralContext) {
        context.blockEntityData().putUUID(ID_TAG, id)
        if (mode() != "elevator") context.blockEntityData().putBoolean("Disabled", !isEnabled())
    }

    override fun tick(context: MobilePeripheralContext) {
        if (mode() == "elevator") return
        val enabled = isEnabled()
        val previous = lastEnabled
        if (previous != null && previous != enabled) queueEvent("manual", enabled)
        lastEnabled = enabled
        context.blockEntityData().putBoolean("Disabled", !enabled)
    }

    private fun elevator(): ElevatorContraption = context.entity().contraption as? ElevatorContraption
        ?: throw LuaException("Elevator methods are only available while mounted on an elevator")

    private fun mode(): String = when {
        context.entity().contraption is ElevatorContraption -> "elevator"
        filter().`is`(ItemTags.DOORS) -> "doors"
        else -> "actors"
    }

    private fun filter(): ItemStack = ItemStack.parseOptional(
        context.level().registryAccess(),
        context.blockEntityData().getCompound("Filter")
    )

    private fun filterInfo(): Map<String, Any> {
        val stack = filter()
        if (stack.isEmpty) return linkedMapOf("all" to true, "empty" to true)
        return linkedMapOf(
            "all" to false,
            "empty" to false,
            "item" to BuiltInRegistries.ITEM.getKey(stack.item).toString(),
            "name" to stack.hoverName.string,
            "count" to stack.count
        )
    }

    private fun targets(): List<Pair<com.simibubi.create.content.contraptions.AbstractContraptionEntity, Contraption>> {
        val entity = context.entity()
        if (mode() != "doors" || entity !is CarriageContraptionEntity) return listOf(entity to entity.contraption)
        return entity.carriage.train.carriages.mapNotNull { carriage ->
            carriage.anyAvailableEntity()?.let { it to it.contraption }
        }
    }

    private fun queueEvent(cause: String, enabled: Boolean) {
        computers.forEach { computer ->
            computer.queueEvent(
                CCAeroworks.CONTRAPTION_CONTROL_EVENT,
                address, id.toString(), mode(), enabled, cause
            )
        }
    }

    private fun position(pos: BlockPos): Map<String, Int> = linkedMapOf("x" to pos.x, "y" to pos.y, "z" to pos.z)

    companion object {
        private const val ID_TAG = "CCAeroworksContraptionControlId"
    }
}
