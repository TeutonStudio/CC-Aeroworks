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
 * Aeroworks integrates modular ControlDesk values into DBW's signal lookup. Interactive displays
 * deliberately reuse real Aeroworks x/y channels for Combined pointer configuration, so this
 * low-priority RETURN guard runs after normal integrations and forces those local pointer values
 * back to zero before DBW can apply them to sinks. Custom ComputerControlDesk wire names do not
 * parse as Aeroworks native IDs and pass through unchanged.
 */
@Pseudo
@Mixin(targets = "edn.stratodonut.drivebywire.wire.WireNetworkManager", remap = false, priority = 500)
public abstract class DriveByWireSignalFilterMixin {
    @Inject(method = "getCurrentSignal", at = @At("RETURN"), cancellable = true, require = 0)
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
