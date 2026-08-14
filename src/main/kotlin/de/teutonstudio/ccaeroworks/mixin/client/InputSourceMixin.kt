package de.teutonstudio.ccaeroworks.mixin.client

import com.mred231.aeroworks.foundation.input.InputSource
import de.teutonstudio.ccaeroworks.input.CombinedInputSource
import net.minecraft.network.chat.Component
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(value = [InputSource::class], remap = false)
abstract class InputSourceMixin {
    private companion object {
        @JvmStatic
        @Inject(method = ["displayName(Ljava/lang/String;)Lnet/minecraft/network/chat/Component;"], at = [At("HEAD")], cancellable = true)
        private fun displayCombinedName(source: String, callback: CallbackInfoReturnable<Component>) {
            if (CombinedInputSource.isCombinedSource(source)) {
                callback.returnValue = Component.translatable("input.cc_aeroworks.combined")
            }
        }
    }
}
