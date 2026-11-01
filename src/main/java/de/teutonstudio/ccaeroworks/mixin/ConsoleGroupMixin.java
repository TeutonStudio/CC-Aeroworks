package de.teutonstudio.ccaeroworks.mixin;

import com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity;
import com.mred231.aeroworks.content.controls.console.ConsoleGroup;
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksTypes;
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep native client socket addresses and server input routing on the same assembly. */
@Mixin(value = ConsoleGroup.class, remap = false)
public abstract class ConsoleGroupMixin {
    @Inject(
        method = "of(Lcom/mred231/aeroworks/content/controls/console/ConsoleBlockEntity;)Ljava/util/List;",
        at = @At("HEAD"), cancellable = true
    )
    private static void ccaeroworks$assemblyMembers(
        ConsoleBlockEntity origin, CallbackInfoReturnable<List<ConsoleBlockEntity>> callback
    ) {
        final var level = origin.getLevel();
        if (level == null || !AeroworksTypes.INSTANCE.isControlDesk(origin.getBlockState().getBlock())) {
            return;
        }
        final var members = ConsoleMultiblockManager.INSTANCE.resolve(level, origin.getBlockPos()).getMembers();
        if (!members.isEmpty()) {
            callback.setReturnValue(members.stream().map(member -> member.getDesk()).toList());
        }
    }
}
