package de.teutonstudio.ccaeroworks.mixin.client;

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity;
import com.mred231.aeroworks.content.controls.MountedModule;
import de.teutonstudio.ccaeroworks.computer.channel.ControlChannelKind;
import de.teutonstudio.ccaeroworks.computer.channel.ControlChannelSemantics;
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

/** Prevents local display-pointer controls from becoming standalone DBW sources. */
@Pseudo
@Mixin(targets = "edn.stratodonut.drivebywire.client.ClientWireNetworkHandler", remap = false, priority = 2100)
public abstract class DriveByWireDisplaySourceMixin {
    @Shadow
    private static BlockPos selectedSource;

    @Inject(method = "handleWireUse", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void ccaeroworks$rejectStandaloneDisplaySource(
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
        if (snapshot.getState() == ConsoleNetworkState.ACTIVE && snapshot.getOwner() != null) {
            return;
        }

        for (int socket = 0; socket < desk.socketCount(); socket++) {
            final MountedModule module = desk.module(socket);
            if (module != null && ControlChannelSemantics.INSTANCE.kind(module) == ControlChannelKind.DISPLAY_POINTER) {
                callback.cancel();
                return;
            }
        }
    }
}
