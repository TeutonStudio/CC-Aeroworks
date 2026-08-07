package de.teutonstudio.ccaeroworks.mixin.compat;

import de.teutonstudio.ccaeroworks.CCAeroworks;
import de.teutonstudio.ccaeroworks.compat.createradar.CreateRadarCompat;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Constructor;

/**
 * Extends Create: Radars' existing filterer-first Data Link flow by classifying an Aeroworks
 * desk carrying a Radar Display as the same private MONITOR target used by MonitorBlockEntity.
 * The original item then performs its own range checks, places the physical Data Link block,
 * calls NetworkData.attachMonitor(), and records the link for native removal cleanup.
 */
@Pseudo
@Mixin(
    targets = "com.happysg.radar.block.datalink.DataLinkBlockItem",
    remap = false
)
public abstract class CreateRadarDataLinkItemMixin {
    @Unique
    private static final String CCAEROWORKS_FILTER_TARGET_CLASS =
        "com.happysg.radar.block.datalink.DataLinkBlockItem$FilterTarget";
    @Unique
    private static final String CCAEROWORKS_FILTER_TARGET_KIND_CLASS =
        "com.happysg.radar.block.datalink.DataLinkBlockItem$FilterTargetKind";

    @Unique
    private static volatile Object ccaeroworks_monitorTarget;
    @Unique
    private static volatile boolean ccaeroworks_monitorTargetResolved;

    @Inject(method = "getFilterTarget", at = @At("RETURN"), cancellable = true)
    private static void ccaeroworks$acceptRadarDisplayDesk(
        BlockEntity blockEntity,
        BlockState state,
        CallbackInfoReturnable<Object> callback
    ) {
        if (callback.getReturnValue() != null) return;
        if (!CreateRadarCompat.isRadarDeskTarget(blockEntity)) return;

        Object monitorTarget = ccaeroworks$getMonitorTarget();
        if (monitorTarget != null) {
            callback.setReturnValue(monitorTarget);
        }
    }

    @Unique
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object ccaeroworks$getMonitorTarget() {
        if (ccaeroworks_monitorTargetResolved) return ccaeroworks_monitorTarget;

        synchronized (CreateRadarDataLinkItemMixin.class) {
            if (ccaeroworks_monitorTargetResolved) return ccaeroworks_monitorTarget;
            ccaeroworks_monitorTargetResolved = true;

            try {
                Class<?> kindClass = Class.forName(CCAEROWORKS_FILTER_TARGET_KIND_CLASS);
                Object monitorKind = Enum.valueOf((Class) kindClass.asSubclass(Enum.class), "MONITOR");

                Class<?> targetClass = Class.forName(CCAEROWORKS_FILTER_TARGET_CLASS);
                Constructor<?> constructor = targetClass.getDeclaredConstructor(kindClass);
                if (!constructor.trySetAccessible()) {
                    throw new IllegalAccessException(
                        "Cannot access Create: Radars DataLinkBlockItem.FilterTarget constructor"
                    );
                }

                ccaeroworks_monitorTarget = constructor.newInstance(monitorKind);
            } catch (Throwable throwable) {
                CCAeroworks.LOGGER.error(
                    "[CC-Aeroworks] Could not extend the Create: Radars Data Link monitor target",
                    throwable
                );
            }
            return ccaeroworks_monitorTarget;
        }
    }
}
