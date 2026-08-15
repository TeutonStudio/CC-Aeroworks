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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Final value-read guard for Drive By Wire.
 *
 * Aeroworks integrates modular ControlDesk values by teaching DBW's signal lookup about its native
 * socket/channel/sign IDs. Interactive displays deliberately reuse real Aeroworks x/y channels for
 * Combined pointer configuration, so this higher-priority guard returns zero before those local
 * pointer values can be interpreted as vehicle DBW output. Custom ComputerControlDesk wire channels
 * are stored values and do not parse as Aeroworks native IDs, so they pass through unchanged.
 */
@Pseudo
@Mixin(targets = "edn.stratodonut.drivebywire.wire.WireNetworkManager", remap = false, priority = 2100)
public abstract class DriveByWireSignalFilterMixin {
    @Inject(method = "getCurrentSignal", at = @At("HEAD"), cancellable = true, require = 0)
    private void ccaeroworks$zeroDisplayPointerSignal(
        final Level level,
        final BlockPos source,
        final String channel,
        final CallbackInfoReturnable<Integer> cir
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
            cir.setReturnValue(0);
        }
    }
}
