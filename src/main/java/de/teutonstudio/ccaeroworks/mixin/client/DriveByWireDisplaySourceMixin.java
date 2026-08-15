package de.teutonstudio.ccaeroworks.mixin.client;

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity;
import com.mred231.aeroworks.content.controls.MountedModule;
import de.teutonstudio.ccaeroworks.input.CombinedInputSource;
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager;
import de.teutonstudio.ccaeroworks.multiblock.ConsoleNetworkState;
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
 * Display pointer ControlChannels are configuration-only and must not become native Drive By Wire
 * outputs. A display-only desk may still participate in the owning ComputerControlDesk's explicit
 * user wire channels; the companion DBW hook resolves that virtual source to the multiblock owner.
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
        if (selectedSource != null) {
            return;
        }

        if (!(level.getBlockEntity(pos) instanceof final ConsoleBlockEntity desk)) {
            return;
        }

        final var snapshot = ConsoleMultiblockManager.INSTANCE.resolve(level, pos);
        if (snapshot.getState() == ConsoleNetworkState.ACTIVE
            && snapshot.getOwner() != null
            && !snapshot.getOwner().wireChannelNames().isEmpty()) {
            // User-defined channels are a real ComputerControlDesk source even when this physical
            // member only contains pointer/display channels.
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
