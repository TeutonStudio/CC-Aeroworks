package de.teutonstudio.ccaeroworks.compat.computercraft

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import dan200.computercraft.api.peripheral.PeripheralCapability
import de.teutonstudio.ccaeroworks.CCAeroworks
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.entity.BlockEntityType
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent
import java.util.WeakHashMap

object ControlDeskPeripheralRegistry {
    private val peripherals = WeakHashMap<ConsoleBlockEntity, ControlDeskPeripheral>()

    fun register(bus: IEventBus) {
        bus.addListener(::registerCapabilities)
    }

    @Suppress("UNCHECKED_CAST")
    private fun registerCapabilities(event: RegisterCapabilitiesEvent) {
        val id = ResourceLocation.fromNamespaceAndPath("aeroworks", "console")
        val type = BuiltInRegistries.BLOCK_ENTITY_TYPE.get(id) as? BlockEntityType<ConsoleBlockEntity>
            ?: error("[CC-Aeroworks] Missing verified block entity type $id")
        event.registerBlockEntity(PeripheralCapability.get(), type) { blockEntity, _ ->
            synchronized(peripherals) {
                peripherals.getOrPut(blockEntity) { ControlDeskPeripheral(blockEntity) }
            }
        }
        CCAeroworks.LOGGER.info("[CC-Aeroworks] Registered CC:Tweaked capability for {}", id)
    }
}
