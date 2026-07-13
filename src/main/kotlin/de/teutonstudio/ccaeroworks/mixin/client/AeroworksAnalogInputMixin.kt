package de.teutonstudio.ccaeroworks.mixin.client

import de.teutonstudio.ccaeroworks.input.CombinedLeverController
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(
    targets = [
        "com.mred231.aeroworks.content.controls.ConsoleControlClient",
        "com.mred231.aeroworks.content.joystick.JoystickControlClient"
    ],
    remap = false
)
abstract class AeroworksAnalogInputMixin {
    private companion object {
        @JvmStatic
        @Inject(method = ["feedMouseDelta(DD)V"], at = [At("HEAD")], cancellable = true)
        private fun routeCombinedLever(deltaX: Double, deltaY: Double, callback: CallbackInfo) {
            if (CombinedLeverController.isActive()) {
                CombinedLeverController.consumeMouseDelta(deltaX, deltaY)
                callback.cancel()
            }
        }
    }
}
