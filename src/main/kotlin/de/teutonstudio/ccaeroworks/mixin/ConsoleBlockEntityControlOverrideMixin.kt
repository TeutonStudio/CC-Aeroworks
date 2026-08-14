package de.teutonstudio.ccaeroworks.mixin

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.computer.control.ControlOverrideManager
import de.teutonstudio.ccaeroworks.computer.control.ControlWriteContext
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/**
 * Makes a HARD computer override authoritative at the same server-side Aeroworks setter used by
 * normal controller input. ComputerOverrideManager writes are explicitly marked and therefore pass
 * through; all other writes to the owned channel are ignored until the override is released.
 */
@Mixin(value = [ConsoleBlockEntity::class], remap = false)
abstract class ConsoleBlockEntityControlOverrideMixin {
    @Inject(method = ["setChannelFromController"], at = [At("HEAD")], cancellable = true)
    private fun ccaeroworks_guardControlOverride(
        socket: Int,
        channel: String,
        value: Int,
        callback: CallbackInfo
    ) {
        if (ControlWriteContext.isComputerOverrideWrite()) return
        val desk = this as ConsoleBlockEntity
        if (ControlOverrideManager.isHardOverridden(desk, socket, channel)) {
            callback.cancel()
            // The client may have locally predicted a control movement before the server write
            // reaches this guard. Re-send the authoritative Aeroworks module state so the visible
            // lever/yoke snaps back to the computer-commanded position instead of lingering there.
            desk.notifyUpdate()
        }
    }
}
