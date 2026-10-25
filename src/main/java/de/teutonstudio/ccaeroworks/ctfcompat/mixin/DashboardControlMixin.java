package de.teutonstudio.ccaeroworks.ctfcompat.mixin;

import createtrainfunctionality.DashboardControl;
import createtrainfunctionality.DashboardControllerBlockEntity;
import de.teutonstudio.ccaeroworks.ctfcompat.control.TrainControlBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = DashboardControl.class, remap = false)
public abstract class DashboardControlMixin {
    @Inject(
        method = "readPanel(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lcreatetrainfunctionality/DashboardControllerBlockEntity;)Lcreatetrainfunctionality/DashboardControl$Ask;",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void ccaeroworks$readBoundDesk(
        ServerLevel level,
        BlockPos controllerPos,
        DashboardControllerBlockEntity controller,
        CallbackInfoReturnable<DashboardControl.Ask> callback
    ) {
        DashboardControl.Ask result = TrainControlBridge.read(level, controllerPos, controller);
        if (result != null) callback.setReturnValue(result);
    }
}
