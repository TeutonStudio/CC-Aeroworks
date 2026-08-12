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
        private fun suppressAeroworksMouseRouting(deltaX: Double, deltaY: Double, callback: CallbackInfo) {
            if (CombinedLeverController.isActive()) {
                // CalculatePlayerTurnEvent is the single authoritative combined-input mouse path.
                // Aeroworks must not feed the same physical delta into the accumulator a second time.
                callback.cancel()
            }
        }
    }
}
