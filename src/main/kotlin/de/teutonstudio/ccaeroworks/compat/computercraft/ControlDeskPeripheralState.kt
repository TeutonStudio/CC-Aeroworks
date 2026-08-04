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

            publishAttachedDeskEvents(peripheral)
            publishMultiblockEvents(peripheral)
            false
        }
    }

    private fun publishAttachedDeskEvents(peripheral: ControlDeskPeripheral) {
        val current = peripheral.snapshotInputs()
        val previous = peripheral.lastInputs
        peripheral.lastInputs = current

        if (previous == null) return
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

    private fun publishMultiblockEvents(peripheral: ControlDeskPeripheral) {
        val current = peripheral.snapshotNetwork() ?: return
        val previous = peripheral.lastNetwork
        peripheral.lastNetwork = current

        if (previous == null) return

        if (previous.signature != current.signature) {
            peripheral.computers.forEach { computer ->
                computer.queueEvent(
                    CCAeroworks.MULTIBLOCK_CHANGED_EVENT,
                    computer.attachmentName,
                    current.state.name.lowercase(),
                    current.memberCount,
                    current.revision
                )
            }
        }

        (previous.desks.keys + current.desks.keys)
            .toSortedSet()
            .forEach { deskId ->
                val oldDesk = previous.desks[deskId]
                val newDesk = current.desks[deskId]
                val deskIndex = newDesk?.index ?: oldDesk?.index ?: return@forEach
                InputSnapshotDiff.changed(
                    oldDesk?.inputs.orEmpty(),
                    newDesk?.inputs.orEmpty()
                ).forEach { change ->
                    peripheral.computers.forEach { computer ->
                        computer.queueEvent(
                            CCAeroworks.MULTIBLOCK_INPUT_EVENT,
                            computer.attachmentName,
                            deskId,
                            deskIndex,
                            change.socket,
                            change.moduleId,
                            change.value,
                            change.channel,
                            de.teutonstudio.ccaeroworks.compat.aeroworks.DeskSockets.name(
                                change.socket
                            )
                        )
                    }
                }
            }
    }
}
