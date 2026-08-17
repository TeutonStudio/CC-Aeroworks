package de.teutonstudio.ccaeroworks.mixin.client

import de.teutonstudio.ccaeroworks.client.SourceSelectorOverlayOwner
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/** Prevents vanilla container item tooltips from leaking through an open source-selector popup. */
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
        val owner = (this as Any) as? SourceSelectorOverlayOwner ?: return
        if (owner.ccaeroworks_isSourceSelectorPopupHovered(mouseX.toDouble(), mouseY.toDouble())) {
            callback.cancel()
        }
    }
}
