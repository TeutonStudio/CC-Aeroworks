package de.teutonstudio.ccaeroworks.compat.computercraft

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.compat.aeroworks.DeskInputSnapshot
import de.teutonstudio.ccaeroworks.display.DeskDisplayTouch
import de.teutonstudio.ccaeroworks.network.DisplayPointerAction
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

    /**
     * Combined control samples already run on the server thread, so publish their CC event
     * immediately instead of waiting for the next 20 Hz snapshot diff. The snapshot cache is
     * patched when it exists so the fallback poll does not emit the same change twice.
     */
    fun queueImmediateInput(
        desk: ConsoleBlockEntity,
        socket: Int,
        moduleId: String,
        channel: String,
        value: Int
    ) {
        active.forEach { peripheral ->
            if (peripheral.validDesk() !== desk) return@forEach
            peripheral.computers.forEach { computer ->
                computer.queueEvent(
                    CCAeroworks.INPUT_EVENT,
                    *DeskInputEventArguments.create(
                        computer.attachmentName,
                        socket,
                        moduleId,
                        value,
                        channel
                    )
                )
            }

            val previous = peripheral.lastInputs ?: return@forEach
            val updated = previous.toMutableMap()
            val existing = updated[socket]
            val channels = existing?.channels.orEmpty().toMutableMap()
            channels[channel] = value
            updated[socket] = DeskInputSnapshot(moduleId, channels)
            peripheral.lastInputs = updated
        }
    }

    internal fun queueDisplayInput(
        desk: ConsoleBlockEntity,
        touch: DeskDisplayTouch,
        action: DisplayPointerAction
    ) {
        active.forEach { peripheral ->
            if (peripheral.validDesk() !== desk) return@forEach
            peripheral.computers.forEach { computer ->
                computer.queueEvent(
                    CCAeroworks.DESK_DISPLAY_INPUT_EVENT,
                    computer.attachmentName,
                    touch.socket,
                    touch.socketName,
                    touch.moduleId,
                    action.eventName,
                    touch.x,
                    touch.y,
                    touch.width,
                    touch.height
                )
                if (action == DisplayPointerAction.TAP) {
                    queueCompatibleTouch(computer, touch)
                }
            }
        }
    }

    internal fun queueDisplayTouch(desk: ConsoleBlockEntity, touch: DeskDisplayTouch) {
        active.forEach { peripheral ->
            if (peripheral.validDesk() !== desk) return@forEach
            peripheral.computers.forEach { computer ->
                queueCompatibleTouch(computer, touch)
            }
        }
    }

    private fun queueCompatibleTouch(
        computer: dan200.computercraft.api.peripheral.IComputerAccess,
        touch: DeskDisplayTouch
    ) {
        computer.queueEvent(
            CCAeroworks.DESK_TOUCH_EVENT,
            computer.attachmentName,
            touch.socket,
            touch.socketName,
            touch.moduleId,
            touch.x,
            touch.y,
            touch.width,
            touch.height
        )
        computer.queueEvent(
            "monitor_touch",
            computer.attachmentName,
            touch.x,
            touch.y
        )
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
