package de.teutonstudio.ccaeroworks.compat.computercraft

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import dan200.computercraft.api.peripheral.PeripheralCapability
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksTypes
import de.teutonstudio.ccaeroworks.registry.CCBlockEntities
import net.minecraft.world.level.block.entity.BlockEntityType
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent
import java.util.WeakHashMap

object ControlDeskPeripheralRegistry {
    private val peripherals = WeakHashMap<ConsoleBlockEntity, ControlDeskPeripheral>()

    fun register(bus: IEventBus) {
        bus.addListener(::registerCapabilities)
    }

    private fun registerCapabilities(event: RegisterCapabilitiesEvent) {
        registerType(event, AeroworksTypes.consoleBlockEntityType())
        registerType(event, CCBlockEntities.COMPUTER_CONTROL_DESK.get())
        CCAeroworks.LOGGER.info(
            "[CC-Aeroworks] Registered desk peripherals for Aeroworks and computer control desks"
        )
    }

    private fun <T : ConsoleBlockEntity> registerType(
        event: RegisterCapabilitiesEvent,
        type: BlockEntityType<T>
    ) {
        event.registerBlockEntity(PeripheralCapability.get(), type) { blockEntity, _ ->
            synchronized(peripherals) {
                peripherals.getOrPut(blockEntity) { ControlDeskPeripheral(blockEntity) }
            }
        }
    }
}
