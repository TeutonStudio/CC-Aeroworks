package de.teutonstudio.ccaeroworks.mixin.client

import de.teutonstudio.ccaeroworks.client.SourceSelectorOverlayExtensions
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(AbstractContainerScreen::class)
abstract class AbstractContainerScreenSourceSelectorTooltipMixin {
    @Inject(
        method = ["renderTooltip(Lnet/minecraft/client/gui/GuiGraphics;II)V"],
        at = [At("HEAD")],
        cancellable = true
    )
    private fun ccaeroworks_hideTooltipBelowSourceSelector(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        callback: CallbackInfo
    ) {
        if (SourceSelectorOverlayExtensions.isPopupHovered(this, mouseX.toDouble(), mouseY.toDouble())) {
  callback.cancel()
        }
    }
}
