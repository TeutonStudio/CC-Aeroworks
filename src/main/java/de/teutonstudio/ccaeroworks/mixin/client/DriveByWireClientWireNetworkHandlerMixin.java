package de.teutonstudio.ccaeroworks.mixin.client;

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity;
import com.mred231.aeroworks.content.controls.MountedModule;
import de.teutonstudio.ccaeroworks.computer.ComputerControlDeskBlock;
import de.teutonstudio.ccaeroworks.computer.ComputerControlDeskBlockEntity;
import de.teutonstudio.ccaeroworks.input.CombinedInputSource;
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockDisplayBounds;
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager;
import de.teutonstudio.ccaeroworks.multiblock.ConsoleNetworkState;
import java.util.List;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds ComputerControlDesk virtual channels to Drive By Wire without changing DBW's saved graph
 * format. Empty/display-only members of an active console multiblock resolve to the owning
 * ComputerControlDesk source, while desks with native continuous controls keep their native DBW
 * source semantics. Visual source outlines cover the whole desk multiblock.
 */
@Pseudo
@Mixin(targets = "edn.stratodonut.drivebywire.client.ClientWireNetworkHandler", remap = false)
public abstract class DriveByWireClientWireNetworkHandlerMixin {
    @Shadow
    private static BlockPos selectedSource;

    @Shadow
    private static String currentChannel;

    @Inject(method = "handleWireUse", at = @At("HEAD"), cancellable = true, require = 0)
    private static void ccaeroworks$validateDeskChannelSelection(
        final Player player,
        final ItemStack heldItem,
        final Level level,
        final BlockPos pos,
        final Direction face,
        final CallbackInfo ci
    ) {
        if (selectedSource == null) {
            final ComputerControlDeskBlockEntity owner = ccaeroworks$virtualSourceOwner(level, pos);
            if (owner != null) {
                final List<String> channels = owner.wireChannelNames();
                if (channels.isEmpty()) {
                    player.displayClientMessage(Component.literal("No ComputerControlDesk wire channels configured."), true);
                    ci.cancel();
                    return;
                }

                selectedSource = owner.getBlockPos().immutable();
                currentChannel = channels.getFirst();
                player.displayClientMessage(
                    Component.translatable("drivebywire.wire.channel.selected", Component.literal(currentChannel)),
                    true
                );
                // DBW's next client tick requests its normal network mirror. Cancelling here is
                // intentional: letting the original method continue would overwrite selectedSource
                // with the physical member that happened to be clicked.
                ci.cancel();
                return;
            }

            if (!(level.getBlockState(pos).getBlock() instanceof ComputerControlDeskBlock)) {
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
            return;
        }

        if (!(level.getBlockState(selectedSource).getBlock() instanceof ComputerControlDeskBlock)) {
            return;
        }

        if (ccaeroworks$isSameDeskNetwork(level, selectedSource, pos)) {
            selectedSource = null;
            currentChannel = "world";
            ci.cancel();
            return;
        }

        final List<String> channels = ccaeroworks$channels(level, selectedSource);
        if (channels.contains(currentChannel)) {
            return;
        }

        selectedSource = null;
        currentChannel = "world";
        player.displayClientMessage(
            Component.literal("The selected ComputerControlDesk wire channel no longer exists. Select the source again."),
            true
        );
        ci.cancel();
    }

    @Inject(method = "changeChannel", at = @At("HEAD"), cancellable = true, require = 0)
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
                player.displayClientMessage(Component.literal("No ComputerControlDesk wire channels configured."), true);
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
                Component.translatable("drivebywire.wire.channel.selected", Component.literal(currentChannel)),
                true
            );
        }
        ci.cancel();
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

    private static ComputerControlDeskBlockEntity ccaeroworks$virtualSourceOwner(final Level level, final BlockPos pos) {
        final BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof final ConsoleBlockEntity desk)) {
            return null;
        }

        if (desk instanceof final ComputerControlDeskBlockEntity direct && !direct.wireChannelNames().isEmpty()) {
            return direct;
        }
        if (ccaeroworks$hasNativeControlChannels(desk)) {
            return null;
        }

        final var snapshot = ConsoleMultiblockManager.INSTANCE.resolve(level, pos);
        if (snapshot.getState() != ConsoleNetworkState.ACTIVE || snapshot.getOwner() == null) {
            return null;
        }
        return snapshot.getOwner().wireChannelNames().isEmpty() ? null : snapshot.getOwner();
    }

    private static boolean ccaeroworks$hasNativeControlChannels(final ConsoleBlockEntity desk) {
        for (int socket = 0; socket < desk.socketCount(); socket++) {
            final MountedModule module = desk.module(socket);
            if (module == null || CombinedInputSource.INSTANCE.isDisplayPointerModule(module)) {
                continue;
            }
            if (!CombinedInputSource.INSTANCE.channels(module).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static boolean ccaeroworks$isSameDeskNetwork(
        final Level level,
        final BlockPos source,
        final BlockPos clicked
    ) {
        if (!(level.getBlockEntity(clicked) instanceof ConsoleBlockEntity)) {
            return false;
        }
        final var snapshot = ConsoleMultiblockManager.INSTANCE.resolve(level, clicked);
        return snapshot.getState() == ConsoleNetworkState.ACTIVE
            && snapshot.getOwner() != null
            && snapshot.getOwner().getBlockPos().equals(source);
    }

    private static List<String> ccaeroworks$channels(final Level level, final BlockPos pos) {
        final BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof final ComputerControlDeskBlockEntity desk) {
            return desk.wireChannelNames();
        }
        return List.of();
    }
}
