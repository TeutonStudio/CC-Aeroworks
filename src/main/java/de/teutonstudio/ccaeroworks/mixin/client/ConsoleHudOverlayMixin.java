package de.teutonstudio.ccaeroworks.mixin.client;

import com.mred231.aeroworks.content.controls.console.ConsoleHudOverlay;
import de.teutonstudio.ccaeroworks.client.display.DisplayPointerHud;
import de.teutonstudio.ccaeroworks.client.hud.ControlHud;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ConsoleHudOverlay.class, remap = false)
public abstract class ConsoleHudOverlayMixin {
    @Shadow
    private static List<?> collectWidgets() {
        throw new AssertionError("Mixin shadow");
    }

    @Inject(
        method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",
        at = @At("HEAD"), cancellable = true
    )
    private void ccaeroworks$renderControls(GuiGraphics graphics, DeltaTracker delta, CallbackInfo callback) {
        ControlHud.render(graphics, collectWidgets().stream()
            .map(widget -> (DisplayPointerHud.Widget) widget).toList());
        callback.cancel();
    }
}
