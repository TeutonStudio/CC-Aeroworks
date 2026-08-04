package de.teutonstudio.ccaeroworks.compat.computercraft

import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.compat.aeroworks.DeskSockets
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
                val sockets = previous.keys + current.keys
                sockets.forEach { socket ->
                    val oldModule = previous[socket]
                    val newModule = current[socket]
                    val channels = oldModule?.channels.orEmpty().keys + newModule?.channels.orEmpty().keys
                    channels.forEach { channel ->
                        val oldValue = oldModule?.channels?.get(channel)
                        val newValue = newModule?.channels?.get(channel)
                        if (oldValue != newValue) {
                            peripheral.computers.forEach { computer ->
                                computer.queueEvent(
                                    CCAeroworks.INPUT_EVENT,
                                    computer.attachmentName,
                                    socket,
                                    newModule?.moduleId ?: oldModule?.moduleId.orEmpty(),
                                    newValue,
                                    channel,
                                    DeskSockets.name(socket)
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
