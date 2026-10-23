package de.teutonstudio.ccaeroworks.compat.computercraft

import com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity
import com.simibubi.create.content.contraptions.actors.contraptionControls.ContraptionControlsBlockEntity
import dan200.computercraft.api.peripheral.PeripheralCapability
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksTypes
import de.teutonstudio.ccaeroworks.registry.CCBlockEntities
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent
import java.util.WeakHashMap

object ControlDeskPeripheralRegistry {
    private val peripherals = WeakHashMap<ConsoleBlockEntity, ControlDeskPeripheral>()
    private val contraptionControls = WeakHashMap<ContraptionControlsBlockEntity, ContraptionControlPeripheral>()

    fun register(bus: IEventBus) {
        bus.addListener(::registerCapabilities)
    }

    private fun registerCapabilities(event: RegisterCapabilitiesEvent) {
        registerType(event, AeroworksTypes.consoleBlockEntityType())
        registerType(event, CCBlockEntities.COMPUTER_CONTROL_DESK.get())
        @Suppress("UNCHECKED_CAST")
        val contraptionControlsType = BuiltInRegistries.BLOCK_ENTITY_TYPE.get(
            ResourceLocation.fromNamespaceAndPath("create", "contraption_controls")
        ) as BlockEntityType<ContraptionControlsBlockEntity>
        event.registerBlockEntity(PeripheralCapability.get(), contraptionControlsType) { blockEntity, _ ->
            contraptionControl(blockEntity)
        }
        CCAeroworks.LOGGER.info(
            "[CC-Aeroworks] Registered desk and native contraption-control peripherals"
        )
    }

    private fun <T : ConsoleBlockEntity> registerType(
        event: RegisterCapabilitiesEvent,
        type: BlockEntityType<T>
    ) {
        event.registerBlockEntity(PeripheralCapability.get(), type) { blockEntity, _ ->
            synchronized(peripherals) {
                peripherals.getOrPut(blockEntity) { ControlDeskPeripheralFactory.create(blockEntity) }
            }
        }
    }

    @JvmStatic
    fun contraptionControl(blockEntity: ContraptionControlsBlockEntity): ContraptionControlPeripheral =
        synchronized(contraptionControls) {
            contraptionControls.getOrPut(blockEntity) { ContraptionControlPeripheral(blockEntity) }
        }
}
