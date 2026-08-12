package de.teutonstudio.ccaeroworks.compat.computercraft

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.display.DeskDisplayTouch
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

    internal fun queueDisplayTouch(desk: ConsoleBlockEntity, touch: DeskDisplayTouch) {
        active.forEach { peripheral ->
            if (peripheral.validDesk() !== desk) return@forEach
            peripheral.computers.forEach { computer ->
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
                // Match CC:Tweaked's advanced-monitor event shape for programs which only
                // care about an attachment name and 1-based touch coordinates.
                computer.queueEvent(
                    "monitor_touch",
                    computer.attachmentName,
                    touch.x,
                    touch.y
                )
            }
        }
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
