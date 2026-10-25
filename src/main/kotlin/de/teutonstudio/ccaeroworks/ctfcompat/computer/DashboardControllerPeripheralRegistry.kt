package de.teutonstudio.ccaeroworks.ctfcompat.computer

import createtrainfunctionality.DashboardControllerBlockEntity
import createtrainfunctionality.StickerContent
import dan200.computercraft.api.peripheral.PeripheralCapability
import de.teutonstudio.ccaeroworks.CCAeroworks
import java.util.WeakHashMap
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent

object DashboardControllerPeripheralRegistry {
    private val peripherals = WeakHashMap<DashboardControllerBlockEntity, DashboardControllerPeripheral>()

    fun register(bus: IEventBus) {
        bus.addListener(::registerCapabilities)
    }

    private fun registerCapabilities(event: RegisterCapabilitiesEvent) {
        event.registerBlockEntity(
            PeripheralCapability.get(),
            StickerContent.DASHBOARD_CONTROLLER_ENTITY.get()
        ) { blockEntity, _ -> peripheral(blockEntity) }
        CCAeroworks.LOGGER.info("[CC-Aeroworks CTF Compat] Registered Dashboard Controller peripheral")
    }

    @JvmStatic
    fun peripheral(blockEntity: DashboardControllerBlockEntity): DashboardControllerPeripheral =
        synchronized(peripherals) {
            peripherals.getOrPut(blockEntity) { DashboardControllerPeripheral(blockEntity) }
        }

    @JvmStatic
    fun reset(blockEntity: DashboardControllerBlockEntity) {
        synchronized(peripherals) { peripherals[blockEntity] }?.reset()
    }
}
