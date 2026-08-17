package de.teutonstudio.ccaeroworks.radarcompat.mixin.createradar;

import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mred231.aeroworks.content.controls.ConsoleBlockEntity;
import de.teutonstudio.ccaeroworks.radarcompat.compat.aeroworks.RadarDeskAccess;
import de.teutonstudio.ccaeroworks.radarcompat.createradar.RadarTrace;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Extends only Create: Radars' native monitor classification. The original
 * DataLinkBlockItem.useOn path still creates its private MONITOR target and owns
 * validation, placement, native registration, messages, item cleanup, and
 * physical Data-Link removal cleanup.
 *
 * Sponge Mixin itself has no INSTANCEOF injection point. NeoForge bundles
 * MixinExtras, whose expression injector can safely modify the boolean result of
 * the native instanceof without replacing the surrounding method. The pinned
 * runtime-bytecode verifier guarantees that ordinal 0 remains the native
 * MonitorBlockEntity check.
 */
@Pseudo
@Mixin(
    targets = "com.happysg.radar.block.datalink.DataLinkBlockItem",
    remap = false
)
public abstract class CreateRadarDataLinkTargetMixin {
    @Expression("? instanceof ?")
    @ModifyExpressionValue(
        method = "getFilterTarget(Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/level/block/state/BlockState;)Lcom/happysg/radar/block/datalink/DataLinkBlockItem$FilterTarget;",
        at = @At(value = "MIXINEXTRAS:EXPRESSION", ordinal = 0),
        require = 1,
        expect = 1
    )
    private static boolean ccaeroworks$acceptRadarDisplayDesk(
        boolean nativeMonitor,
        BlockEntity candidate,
        BlockState state
    ) {
        boolean isDesk = candidate instanceof ConsoleBlockEntity;
        boolean hasRadarDisplay = isDesk && RadarDeskAccess.hasRadarDisplay((ConsoleBlockEntity) candidate);
        boolean accepted = nativeMonitor || hasRadarDisplay;

        RadarTrace.event(
            "DL_CLASSIFY",
            candidate == null ? null : candidate.getLevel(),
            candidate == null ? null : candidate.getBlockPos(),
            "candidate=" + (candidate == null ? "null" : candidate.getClass().getName())
                + " block=" + (state == null ? "null" : state.getBlock())
                + " nativeMonitor=" + nativeMonitor
                + " isConsoleDesk=" + isDesk
                + " hasRadarDisplay=" + hasRadarDisplay
                + " acceptedAsMonitor=" + accepted
        );

        return accepted;
    }
}
