package de.teutonstudio.ccaeroworks.mixin.client;

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity;
import de.teutonstudio.ccaeroworks.client.DriveByWireDeskEndpoint;
import de.teutonstudio.ccaeroworks.client.DriveByWireDeskSelectionSession;
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockDisplayBounds;
import net.createmod.catnip.data.Pair;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Treats one active ControlDesk multiblock as a logical DBW source while retaining the exact
 * physical endpoint expected by Drive By Wire's server protocol.
 */
@Pseudo
@Mixin(targets = "edn.stratodonut.drivebywire.client.ClientWireNetworkHandler", remap = false, priority = 2000)
public abstract class DriveByWireClientWireNetworkHandlerMixin {
    @Shadow
    private static BlockPos selectedSource;

    @Shadow
    private static String currentChannel;

    @Shadow
    private static void syncManager() {
        throw new AssertionError();
    }

    @Shadow
    public static void clearSource() {
        throw new AssertionError();
    }

    @Inject(method = "handleWireUse", at = @At("HEAD"), cancellable = true, require = 0)
    private static void ccaeroworks$handleDeskMultiblockSource(
        final Player player,
        final ItemStack heldItem,
        final Level level,
        final BlockPos pos,
        final Direction face,
        final CallbackInfo ci
    ) {
        if (selectedSource == null) {
            final DriveByWireDeskEndpoint endpoint = DriveByWireDeskSelectionSession.INSTANCE.begin(level, pos);
            if (endpoint == null) {
                return;
            }
            ccaeroworks$mirrorEndpoint(endpoint);
            syncManager();
            ccaeroworks$showChannel(player, endpoint.getChannel());
            ci.cancel();
            return;
        }

        if (!DriveByWireDeskSelectionSession.INSTANCE.isActive()) {
            return;
        }

        final DriveByWireDeskEndpoint endpoint = DriveByWireDeskSelectionSession.INSTANCE.current(level);
        if (endpoint == null) {
            ccaeroworks$clearDeskSelection();
            player.displayClientMessage(
                Component.literal("The selected control-desk channel is no longer available. Select the source again."),
                true
            );
            ci.cancel();
            return;
        }

        if (DriveByWireDeskSelectionSession.INSTANCE.containsMember(level, pos)) {
            ccaeroworks$clearDeskSelection();
            ci.cancel();
            return;
        }

        // Let DBW create/remove its normal packet, but only after mirroring the current physical
        // endpoint. The logical multiblock session itself remains stable while scrolling.
        ccaeroworks$mirrorEndpoint(endpoint);
    }

    @Inject(method = "changeChannel", at = @At("HEAD"), cancellable = true, require = 0)
    private static void ccaeroworks$scrollWholeDeskNetwork(
        final Block source,
        final boolean forward,
        final CallbackInfo ci
    ) {
        if (!DriveByWireDeskSelectionSession.INSTANCE.isActive()) {
            return;
        }
        final Minecraft minecraft = Minecraft.getInstance();
        final Level level = minecraft.level;
        if (level == null) {
            ccaeroworks$clearDeskSelection();
            ci.cancel();
            return;
        }

        final DriveByWireDeskEndpoint endpoint = DriveByWireDeskSelectionSession.INSTANCE.cycle(level, forward);
        if (endpoint == null) {
            ccaeroworks$clearDeskSelection();
            ci.cancel();
            return;
        }
        ccaeroworks$mirrorEndpoint(endpoint);
        if (minecraft.player != null) {
            ccaeroworks$showChannel(minecraft.player, endpoint.getChannel());
        }
        ci.cancel();
    }

    @Inject(method = "clearSource", at = @At("TAIL"), require = 0)
    private static void ccaeroworks$clearDeskSession(final CallbackInfo ci) {
        DriveByWireDeskSelectionSession.INSTANCE.clear();
    }

    @Inject(method = "drawOutline", at = @At("HEAD"), cancellable = true, require = 0)
    private static void ccaeroworks$drawDeskMultiblockOutline(
        final Level level,
        final BlockPos pos,
        final int color,
        final CallbackInfo ci
    ) {
        if (!DriveByWireDeskSelectionSession.INSTANCE.isActive()) {
            return;
        }
        final DriveByWireDeskEndpoint endpoint = DriveByWireDeskSelectionSession.INSTANCE.current(level);
        if (endpoint == null || !endpoint.getSourcePos().equals(pos)) {
            return;
        }
        final BlockPos anchor = DriveByWireDeskSelectionSession.INSTANCE.anchor(level);
        if (anchor == null || !(level.getBlockEntity(anchor) instanceof ConsoleBlockEntity)) {
            return;
        }
        final AABB bounds = ConsoleMultiblockDisplayBounds.resolve(level, anchor);
        if (bounds == null) {
            return;
        }
        Outliner.getInstance()
            .showAABB(Pair.of("ccaeroworksWireDesk", anchor), bounds)
            .colored(color)
            .lineWidth(0.0625F);
        ci.cancel();
    }

    @Unique
    private static void ccaeroworks$mirrorEndpoint(final DriveByWireDeskEndpoint endpoint) {
        selectedSource = endpoint.getSourcePos().immutable();
        currentChannel = endpoint.getChannel();
    }

    @Unique
    private static void ccaeroworks$showChannel(final Player player, final String channel) {
        player.displayClientMessage(
            Component.translatable("drivebywire.wire.channel.selected", Component.literal(channel)),
            true
        );
    }

    @Unique
    private static void ccaeroworks$clearDeskSelection() {
        clearSource();
    }
}
