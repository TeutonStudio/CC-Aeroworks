package de.teutonstudio.ccaeroworks.compat.computercraft

import java.util.concurrent.ConcurrentHashMap
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent

/** Detects native clicks and redstone changes without replacing Create's interaction code. */
object ContraptionControlPeripheralState {
    private val active = ConcurrentHashMap.newKeySet<ContraptionControlPeripheral>()

    internal fun activate(peripheral: ContraptionControlPeripheral) {
        active.add(peripheral)
    }

    internal fun deactivate(peripheral: ContraptionControlPeripheral) {
        active.remove(peripheral)
    }

    @SubscribeEvent
    fun onServerTick(event: ServerTickEvent.Post) {
        active.removeIf { peripheral ->
            val be = peripheral.validController()
            if (be == null || !peripheral.computers.hasComputers()) return@removeIf true
            val enabled = !be.disabled
            val powered = be.powered
            val previousEnabled = peripheral.lastEnabled
            val previousPowered = peripheral.lastPowered
            peripheral.lastEnabled = enabled
            peripheral.lastPowered = powered
            if (previousEnabled != null && previousEnabled != enabled) {
                peripheral.queueStateEvent(if (previousPowered != powered) "redstone" else "manual")
            }
            false
        }
    }
}
