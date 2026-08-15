package de.teutonstudio.ccaeroworks.mixin.client;

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity;
import de.teutonstudio.ccaeroworks.client.DriveByWireDeskEndpoint;
import de.teutonstudio.ccaeroworks.client.DriveByWireDeskSelection;
import de.teutonstudio.ccaeroworks.client.DriveByWireDeskSelectionResolver;
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
 * Makes every active ComputerControlDesk multiblock one scrollable DBW catalogue. Each endpoint
 * keeps its native physical source position, while user channels use the computer owner position.
 */
@Pseudo
@Mixin(targets = "edn.stratodonut.drivebywire.client.ClientWireNetworkHandler", remap = false)
public abstract class DriveByWireClientWireNetworkHandlerMixin {
    @Shadow
    private static BlockPos selectedSource;

    @Shadow
    private static String currentChannel;

    @Unique
    private static BlockPos ccaeroworks$selectionAnchor;

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
            final DriveByWireDeskSelection selection = DriveByWireDeskSelectionResolver.INSTANCE.resolve(level, pos);
            if (selection == null) {
                return;
            }
            final DriveByWireDeskEndpoint endpoint = selection.startAt(pos);
            if (endpoint == null) {
                player.displayClientMessage(Component.literal("No Drive By Wire channels on this control desk network."), true);
                ci.cancel();
                return;
            }

            ccaeroworks$selectionAnchor = selection.getAnchor();
            selectedSource = endpoint.getSourcePos().immutable();
            currentChannel = endpoint.getChannel();
            player.displayClientMessage(
                Component.translatable("drivebywire.wire.channel.selected", Component.literal(currentChannel)),
                true
            );
            ci.cancel();
            return;
        }

        if (ccaeroworks$selectionAnchor == null) {
            return;
        }
        final DriveByWireDeskSelection selection =
            DriveByWireDeskSelectionResolver.INSTANCE.resolve(level, ccaeroworks$selectionAnchor);
        if (selection == null || !selection.contains(selectedSource, currentChannel)) {
            ccaeroworks$clearDeskSelection();
            player.displayClientMessage(
                Component.literal("The selected control-desk channel is no longer available. Select the source again."),
                true
            );
            ci.cancel();
            return;
        }

        // The visual source is the complete multiblock, so clicking any of its members again clears
        // the source instead of accidentally wiring one desk segment into another segment.
        if (selection.getMemberPositions().contains(pos)) {
            ccaeroworks$clearDeskSelection();
            ci.cancel();
        }
        // Otherwise the original DBW method sends its connection packet with the currently selected
        // physical source position/channel. We deliberately do not replace that server protocol.
    }

    @Inject(method = "changeChannel", at = @At("HEAD"), cancellable = true, require = 0)
    private static void ccaeroworks$scrollWholeDeskNetwork(
        final Block source,
        final boolean forward,
        final CallbackInfo ci
    ) {
        if (ccaeroworks$selectionAnchor == null || selectedSource == null) {
            return;
        }
        final Minecraft minecraft = Minecraft.getInstance();
        final Level level = minecraft.level;
        if (level == null) {
            return;
        }

        final DriveByWireDeskSelection selection =
            DriveByWireDeskSelectionResolver.INSTANCE.resolve(level, ccaeroworks$selectionAnchor);
        final DriveByWireDeskEndpoint endpoint = selection == null
            ? null
            : selection.next(selectedSource, currentChannel, forward);
        if (endpoint == null) {
            ccaeroworks$clearDeskSelection();
            ci.cancel();
            return;
        }

        selectedSource = endpoint.getSourcePos().immutable();
        currentChannel = endpoint.getChannel();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(
                Component.translatable("drivebywire.wire.channel.selected", Component.literal(currentChannel)),
                true
            );
        }
        ci.cancel();
    }

    @Inject(method = "clearSource", at = @At("TAIL"), require = 0)
    private static void ccaeroworks$clearDeskAnchor(final CallbackInfo ci) {
        ccaeroworks$selectionAnchor = null;
    }

    @Inject(method = "drawOutline", at = @At("HEAD"), cancellable = true, require = 0)
    private static void ccaeroworks$drawDeskMultiblockOutline(
        final Level level,
        final BlockPos pos,
        final int color,
        final CallbackInfo ci
    ) {
        if (!(level.getBlockEntity(pos) instanceof ConsoleBlockEntity)) {
            return;
        }
        final AABB bounds = ConsoleMultiblockDisplayBounds.resolve(level, pos);
        if (bounds == null) {
            return;
        }
        Outliner.getInstance()
            .showAABB(Pair.of("ccaeroworksWireDesk", pos), bounds)
            .colored(color)
            .lineWidth(0.0625F);
        ci.cancel();
    }

    @Unique
    private static void ccaeroworks$clearDeskSelection() {
        selectedSource = null;
        currentChannel = "world";
        ccaeroworks$selectionAnchor = null;
    }
}
