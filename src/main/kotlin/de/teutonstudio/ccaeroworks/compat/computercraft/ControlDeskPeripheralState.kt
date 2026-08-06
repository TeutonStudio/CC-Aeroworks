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
            if (!peripheral.computers.hasComputers() || peripheral.validDesk() == null) {
                return@removeIf true
            }

            val current = peripheral.snapshotInputs()
            val previous = peripheral.lastInputs
            peripheral.lastInputs = current
            if (previous != null) {
                InputSnapshotDiff.changed(previous, current).forEach { change ->
                    peripheral.computers.forEach { computer ->
                        computer.queueEvent(
                            CCAeroworks.INPUT_EVENT,
                            *DeskInputEventArguments.create(
                                computer.attachmentName,
                                change.socket,
                                change.moduleId,
                                change.value,
                                change.channel
                            )
                        )
                    }
                }
            }
            false
        }
    }
}
