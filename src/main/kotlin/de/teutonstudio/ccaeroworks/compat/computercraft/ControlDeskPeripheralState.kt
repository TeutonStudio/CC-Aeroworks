package de.teutonstudio.ccaeroworks.compat.computercraft

import de.teutonstudio.ccaeroworks.CCAeroworks
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import java.util.concurrent.ConcurrentHashMap

object ControlDeskPeripheralState {
    private val active = ConcurrentHashMap.newKeySet<ControlDeskPeripheral>()

    internal fun activate(peripheral: ControlDeskPeripheral) {
        active.add(peripheral)
    }

    internal fun deactivate(peripheral: ControlDeskPeripheral) {
        active.remove(peripheral)
    }

    @SubscribeEvent
    fun onServerTick(event: ServerTickEvent.Post) {
        active.removeIf { peripheral ->
            if (!peripheral.computers.hasComputers() || peripheral.validDesk() == null) return@removeIf true
            val current = peripheral.snapshotInputs()
            val previous = peripheral.lastInputs
            peripheral.lastInputs = current
            if (previous != null) {
                current.forEach { (socket, channels) ->
                    val oldChannels = previous[socket]
                    channels.forEach { (channel, value) ->
                        if (oldChannels?.get(channel) != value) {
                            val module = peripheral.validDesk()?.module(socket) ?: return@forEach
                            peripheral.computers.forEach { computer ->
                                computer.queueEvent(
                                    CCAeroworks.INPUT_EVENT,
                                    computer.attachmentName,
                                    socket,
                                    de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksModuleAccess.id(module).toString(),
                                    value,
                                    channel,
                                    de.teutonstudio.ccaeroworks.compat.aeroworks.DeskSockets.name(socket)
                                )
                            }
                        }
                    }
                }
            }
            false
        }
    }
}
