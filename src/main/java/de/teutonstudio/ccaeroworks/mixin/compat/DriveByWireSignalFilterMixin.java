package de.teutonstudio.ccaeroworks.mixin.compat;

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity;
import com.mred231.aeroworks.content.controls.MountedModule;
import de.teutonstudio.ccaeroworks.compat.drivebywire.NativeDriveByWireChannel;
import de.teutonstudio.ccaeroworks.compat.drivebywire.NativeDriveByWireChannels;
import de.teutonstudio.ccaeroworks.computer.channel.ControlChannelSemantics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Final runtime guard for Aeroworks -> Drive By Wire signal publication.
 *
 * Display x/y must remain real Aeroworks values for Combined pseudo-finger input, but they are not
 * physical vehicle outputs. Custom ComputerControlDesk wire names do not parse as Aeroworks native
 * channel ids and therefore pass through unchanged.
 */
@Pseudo
@Mixin(targets = "edn.stratodonut.drivebywire.wire.WireNetworkManager", remap = false, priority = 2000)
public abstract class DriveByWireSignalFilterMixin {
    @Inject(method = "trySetSignalAt", at = @At("HEAD"), cancellable = true, require = 0)
    private static void ccaeroworks$blockDisplayPointerSignal(
        final Level level,
        final BlockPos source,
        final String channel,
        final int value,
        final CallbackInfo ci
    ) {
        if (!(level.getBlockEntity(source) instanceof final ConsoleBlockEntity desk)) {
            return;
        }
        final NativeDriveByWireChannel parsed = NativeDriveByWireChannels.INSTANCE.parse(channel);
        if (parsed == null || parsed.getSocket() < 0 || parsed.getSocket() >= desk.socketCount()) {
            return;
        }
        final MountedModule module = desk.module(parsed.getSocket());
        if (module == null) {
            return;
        }
        if (!ControlChannelSemantics.INSTANCE.isDriveByWireExposed(module, parsed.getChannelId())) {
            ci.cancel();
        }
    }
}
