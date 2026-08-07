package de.teutonstudio.ccaeroworks.mixin.compat;

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity;
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksDeskAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Extends only Create: Radars' native monitor classification. The original
 * DataLinkBlockItem.useOn path still creates its private MONITOR target and owns
 * validation, placement, native registration, messages, item cleanup, and
 * physical Data-Link removal cleanup.
 */
@Pseudo
@Mixin(
    targets = "com.happysg.radar.block.datalink.DataLinkBlockItem",
    remap = false
)
public abstract class CreateRadarDataLinkTargetMixin {
    @Unique
    private static final String ccaeroworks$nativeMonitorClass =
        "com.happysg.radar.block.monitor.MonitorBlockEntity";

    @Redirect(
        method = "getFilterTarget(Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/level/block/state/BlockState;)Lcom/happysg/radar/block/datalink/DataLinkBlockItem$FilterTarget;",
        at = @At(
            value = "INSTANCEOF",
            target = "Lcom/happysg/radar/block/monitor/MonitorBlockEntity;"
        ),
        require = 1,
        expect = 1
    )
    private static boolean ccaeroworks$acceptRadarDisplayDesk(Object candidate) {
        if (ccaeroworks$isNativeMonitor(candidate)) {
            return true;
        }
        return candidate instanceof ConsoleBlockEntity desk
            && AeroworksDeskAccess.hasRadarDisplay(desk);
    }

    @Unique
    private static boolean ccaeroworks$isNativeMonitor(Object candidate) {
        if (candidate == null) {
            return false;
        }
        Class<?> type = candidate.getClass();
        while (type != null) {
            if (ccaeroworks$nativeMonitorClass.equals(type.getName())) {
                return true;
            }
            type = type.getSuperclass();
        }
        return false;
    }
}
