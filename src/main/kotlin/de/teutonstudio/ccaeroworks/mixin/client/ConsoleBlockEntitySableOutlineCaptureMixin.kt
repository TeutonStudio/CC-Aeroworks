package de.teutonstudio.ccaeroworks.mixin.client

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.compat.sable.SableControlOutlineBridge
import dev.ryanhcode.sable.Sable
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

/**
 * Observes Aeroworks' native mount geometry without changing it.
 *
 * The previous Sable outline attempt replaced mountSpots() and nearestMount(), coupling
 * a rendering workaround to gameplay selection. This hook only remembers the original
 * double-precision centers next to their Matrix4f frames so the client outline can use
 * the precise translation later.
 */
@Mixin(value = [ConsoleBlockEntity::class], remap = false)
abstract class ConsoleBlockEntitySableOutlineCaptureMixin {
    @Inject(
        method = ["mountSpots()Ljava/util/List;"],
        at = [At("RETURN")],
        remap = false
    )
    private fun ccaeroworks_captureSableMountFrames(
        callback: CallbackInfoReturnable<List<ConsoleBlockEntity.MountSpot>>
    ) {
        val desk = this as ConsoleBlockEntity
        if (Sable.HELPER.getContainingClient(desk) == null) return
        SableControlOutlineBridge.capture(desk, callback.returnValue)
    }
}
