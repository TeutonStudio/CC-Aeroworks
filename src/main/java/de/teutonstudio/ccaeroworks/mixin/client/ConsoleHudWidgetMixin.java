package de.teutonstudio.ccaeroworks.mixin.client;

import com.mred231.aeroworks.content.controls.module.MountedModule;
import de.teutonstudio.ccaeroworks.client.display.DisplayPointerHud;
import de.teutonstudio.ccaeroworks.input.CombinedInputSource;
import java.util.List;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.mred231.aeroworks.content.controls.console.ConsoleHudOverlay$Widget", remap = false)
public abstract class ConsoleHudWidgetMixin implements DisplayPointerHud.Widget {
    @Shadow @Final private int socket;
    @Shadow @Final private MountedModule module;
    @Shadow @Final private List<Integer> axes;

    @Override
    public int ccaeroworks$socket() { return socket; }

    @Override
    public MountedModule ccaeroworks$module() { return module; }

    @Override
    public List<Integer> ccaeroworks$axes() { return axes; }

    @Inject(method = "width()I", at = @At("HEAD"), cancellable = true)
    private void ccaeroworks$displayWidth(CallbackInfoReturnable<Integer> callback) {
        if (CombinedInputSource.INSTANCE.isDisplayPointerModule(module)) {
            callback.setReturnValue(DisplayPointerHud.width(this));
        }
    }
}
