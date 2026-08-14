package de.teutonstudio.ccaeroworks.mixin.client;

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity;
import com.mred231.aeroworks.content.controls.MountedModule;
import de.teutonstudio.ccaeroworks.input.CombinedInputSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drive By Wire asks its client handler to select a source block first and only then cycles the
 * source's MultiChannelWireSource channels. Large CC-Aeroworks displays use real Aeroworks
 * ControlChannels purely so ModuleScreen can configure their Combined X/Y activation keys. Those
 * channels are pointer input, not vehicle/wire outputs, so a desk carrying one must never become a
 * Drive By Wire source.
 *
 * @Pseudo plus the string target keep Drive By Wire genuinely optional. There is no compile-time
 * reference to a Drive By Wire class, and require=0 makes the hook tolerant if the optional mod is
 * absent. The target and shadow match the supported Drive By Wire 0.2.9 baseline.
 */
@Pseudo
@Mixin(targets = "edn.stratodonut.drivebywire.client.ClientWireNetworkHandler", remap = false)
public abstract class DriveByWireDisplaySourceMixin {
    @Shadow
    private static BlockPos selectedSource;

    @Inject(method = "handleWireUse", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void ccaeroworks_rejectDisplaySource(
        final Player player,
        final ItemStack heldItem,
        final Level level,
        final BlockPos pos,
        final Direction face,
        final CallbackInfo callback
    ) {
        // The second click chooses a sink. Only source selection is restricted; this avoids changing
        // Drive By Wire semantics for a normal source merely because its destination is a desk.
        if (selectedSource != null) {
            return;
        }

        if (!(level.getBlockEntity(pos) instanceof final ConsoleBlockEntity desk)) {
            return;
        }

        for (int socket = 0; socket < desk.socketCount(); socket++) {
            final MountedModule module = desk.module(socket);
            if (module != null && CombinedInputSource.INSTANCE.isDisplayPointerModule(module)) {
                callback.cancel();
                return;
            }
        }
    }
}
