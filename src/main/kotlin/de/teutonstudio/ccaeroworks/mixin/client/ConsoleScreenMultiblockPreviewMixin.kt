package de.teutonstudio.ccaeroworks.mixin.client

import com.mred231.aeroworks.content.controls.ConsoleScreen
import de.teutonstudio.ccaeroworks.client.ConsoleMultiblockPreviewRenderer
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/** Replaces only Aeroworks' private 3D preview, leaving the rest of ConsoleScreen native. */
@Mixin(value = [ConsoleScreen::class], remap = false)
abstract class ConsoleScreenMultiblockPreviewMixin(title: Component) : Screen(title) {
    @Inject(
        method = ["renderConsolePreview(Lnet/minecraft/client/gui/GuiGraphics;)V"],
        at = [At("HEAD")],
        cancellable = true
    )
    private fun ccaeroworks_renderMultiblockPreview(
        graphics: GuiGraphics,
        callback: CallbackInfo
    ) {
        val accessor = this as ConsoleScreenAccessor
        if (
            ConsoleMultiblockPreviewRenderer.render(
                graphics,
                accessor.ccaeroworks_getConsole(),
                accessor.ccaeroworks_getWindowLeft(),
                accessor.ccaeroworks_getWindowTop()
            )
        ) {
            callback.cancel()
        }
    }
}
