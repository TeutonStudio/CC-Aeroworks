package de.teutonstudio.ccaeroworks.mixin.client

import de.teutonstudio.ccaeroworks.input.CombinedLeverController
import de.teutonstudio.ccaeroworks.input.DisplayCombinedInputController
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/**
 * Retires CC-Aeroworks Combined focus on the same lifecycle method that Aeroworks itself uses to
 * end a modular ControlDesk session. ConsoleControlClient.exit(String) is reached by requestExit,
 * server/world invalidation and disconnect cleanup, so this does not depend on which key or event
 * happened to trigger the exit.
 */
@Mixin(targets = ["com.mred231.aeroworks.content.controls.ConsoleControlClient"], remap = false)
abstract class AeroworksControlSessionMixin {
    private companion object {
        @JvmStatic
        @Inject(method = ["exit(Ljava/lang/String;)V"], at = [At("HEAD")], remap = false)
        private fun ccaeroworks_abortCombinedControl(reason: String, callback: CallbackInfo) {
            CombinedLeverController.abortControlMode()
            DisplayCombinedInputController.abortControlMode()
        }
    }
}
