package de.teutonstudio.ccaeroworks.mixin.client;

import de.teutonstudio.ccaeroworks.computer.ComputerControlDeskBlock;
import de.teutonstudio.ccaeroworks.computer.ComputerControlDeskBlockEntity;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drive By Wire 0.2.9 asks only the source Block for its channel list. ComputerControlDesk
 * channels are per BlockEntity, so intercept DBW's channel selection and resolve the selected
 * source position back to the synced ComputerControlDeskBlockEntity.
 *
 * @Pseudo keeps CC-Aeroworks loadable when the optional drivebywire mod is absent.
 */
@Pseudo
@Mixin(targets = "edn.stratodonut.drivebywire.client.ClientWireNetworkHandler", remap = false)
public abstract class DriveByWireClientWireNetworkHandlerMixin {
    @Shadow
    private static BlockPos selectedSource;

    @Shadow
    private static String currentChannel;

    @Inject(method = "handleWireUse", at = @At("HEAD"), cancellable = true)
    private static void ccaeroworks$rejectDeskWithoutChannels(
        final Player player,
        final ItemStack heldItem,
        final Level level,
        final BlockPos pos,
        final Direction face,
        final CallbackInfo ci
    ) {
        if (selectedSource != null || !(level.getBlockState(pos).getBlock() instanceof ComputerControlDeskBlock)) {
            return;
        }

        if (!ccaeroworks$channels(level, pos).isEmpty()) {
            return;
        }

        player.displayClientMessage(
            Component.literal("No ComputerControlDesk wire channels configured. Use: wires add <name>"),
            true
        );
        ci.cancel();
    }

    @Inject(method = "changeChannel", at = @At("HEAD"), cancellable = true)
    private static void ccaeroworks$selectDeskChannel(
        final Block source,
        final boolean forward,
        final CallbackInfo ci
    ) {
        if (!(source instanceof ComputerControlDeskBlock)) {
            return;
        }

        final Minecraft minecraft = Minecraft.getInstance();
        final Level level = minecraft.level;
        final Player player = minecraft.player;
        final List<String> channels = level == null || selectedSource == null
            ? List.of()
            : ccaeroworks$channels(level, selectedSource);

        if (channels.isEmpty()) {
            currentChannel = "world";
            selectedSource = null;
            if (player != null) {
                player.displayClientMessage(
                    Component.literal("No ComputerControlDesk wire channels configured."),
                    true
                );
            }
            ci.cancel();
            return;
        }

        final int currentIndex = channels.indexOf(currentChannel);
        currentChannel = currentIndex < 0
            ? channels.getFirst()
            : channels.get(Math.floorMod(currentIndex + (forward ? 1 : -1), channels.size()));

        if (player != null) {
            player.displayClientMessage(
                Component.translatable(
                    "drivebywire.wire.channel.selected",
                    Component.literal(currentChannel)
                ),
                true
            );
        }
        ci.cancel();
    }

    private static List<String> ccaeroworks$channels(final Level level, final BlockPos pos) {
        final BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof final ComputerControlDeskBlockEntity desk) {
            return desk.wireChannelNames();
        }
        return List.of();
    }
}
