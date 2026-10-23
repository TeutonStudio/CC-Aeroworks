package de.teutonstudio.ccaeroworks.gametest;

import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import com.mred231.aeroworks.content.controls.channel.ControlTransmitter;
import com.mred231.aeroworks.content.controls.console.ConsoleBlock;
import com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity;
import dan200.computercraft.shared.ModRegistry;
import dan200.computercraft.shared.computer.core.ComputerFamily;
import dan200.computercraft.shared.computer.core.TerminalSize;
import dan200.computercraft.shared.util.NonNegativeId;
import dan200.computercraft.shared.util.StorageCapacity;
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksTypes;
import de.teutonstudio.ccaeroworks.compat.aeroworks.DeskIdentityAccess;
import de.teutonstudio.ccaeroworks.computer.*;
import de.teutonstudio.ccaeroworks.computer.control.ControlOverrideCommand;
import de.teutonstudio.ccaeroworks.computer.control.ControlOverrideManager;
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager;
import de.teutonstudio.ccaeroworks.multiblock.ConsoleNetworkState;
import de.teutonstudio.ccaeroworks.multiblock.ConsoleAssemblyAdapters;
import de.teutonstudio.ccaeroworks.multiblock.ConsoleAssemblyPlacement;
import de.teutonstudio.ccaeroworks.multiblock.ConsoleAssemblyRuntime;
import de.teutonstudio.ccaeroworks.multiblock.ConsoleAssemblyStateAccess;
import de.teutonstudio.ccaeroworks.multiblock.ConsoleAssemblyStyleAccess;
import de.teutonstudio.ccaeroworks.multiblock.ConsoleAssemblyStyles;
import de.teutonstudio.ccaeroworks.registry.CCBlocks;
import de.teutonstudio.ccaeroworks.registry.CCDataComponents;
import de.teutonstudio.ccaeroworks.train.TrainControlConsoleGeometry;
import de.teutonstudio.ccaeroworks.train.TrainControlConsoleIntegration;
import de.teutonstudio.ccaeroworks.train.TrainControlConsoleUpdater;
import de.teutonstudio.ccaeroworks.train.ConsoleControlKind;
import de.teutonstudio.ccaeroworks.contraption.ContraptionControlIdentityAccess;
import com.simibubi.create.content.contraptions.actors.contraptionControls.ContraptionControlsBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.UUID;

/** Runs only through NeoForge's GameTest harness; exercises real mixed-in console entities. */
@GameTestHolder("cc_aeroworks")
@PrefixGameTestTemplate(false)
public final class ConsoleComputerGameTests {
    private static final BlockPos ORIGIN = new BlockPos(2, 2, 2);

    private static ConsoleBlockEntity desk(GameTestHelper h, BlockPos p, String variant) {
        var state = AeroworksTypes.INSTANCE.controlDeskBlock(variant).defaultBlockState()
            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        BlockPos absolute = h.absolutePos(p);
        BlockPos target = absolute.west();
        if (!ConsoleAssemblyRuntime.samePhysicalProfile(state, h.getLevel().getBlockState(target))) target = null;
        ConsoleAssemblyPlacement.capture(h.getLevel(), absolute, target, false);
        h.setBlock(p, state);
        ConsoleAssemblyRuntime.INSTANCE.initializePlaced(h.getLevel(), absolute, null);
        return (ConsoleBlockEntity) h.getBlockEntity(p);
    }

    private static void control(
        GameTestHelper h,
        BlockPos p,
        net.minecraft.world.level.block.state.BlockState state,
        BlockPos target
    ) {
        BlockPos absolute = h.absolutePos(p);
        ConsoleAssemblyPlacement.capture(h.getLevel(), absolute, target == null ? null : h.absolutePos(target), false);
        h.setBlock(p, state);
        ConsoleAssemblyRuntime.INSTANCE.initializePlaced(h.getLevel(), absolute, null);
    }

    private static ItemStack computer(boolean advanced) {
        ItemStack stack = new ItemStack(advanced ? ModRegistry.Items.COMPUTER_ADVANCED.get() : ModRegistry.Items.COMPUTER_NORMAL.get());
        stack.set(ModRegistry.DataComponents.COMPUTER_ID.get(), NonNegativeId.of(937));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("slot-test"));
        stack.set(ModRegistry.DataComponents.STORAGE_CAPACITY.get(), new StorageCapacity(2000000));
        stack.set(ModRegistry.DataComponents.TERMINAL_SIZE.get(), new TerminalSize(42, 17));
        stack.set(CCDataComponents.WIRE_CHANNELS.get(), "aabbccdd-1234-4567-8899-aabbccddeeff|engine");
        return stack;
    }

    private static net.minecraft.world.level.block.state.BlockState integratedControls(
        TrainControlConsoleGeometry geometry
    ) {
        var block = BuiltInRegistries.BLOCK.get(
            ResourceLocation.fromNamespaceAndPath("create", "controls")
        );
        if (!(block instanceof ControlsBlock)) throw new AssertionError("Create Train Controls are unavailable");
        return block.defaultBlockState()
            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
            .setValue(TrainControlConsoleIntegration.GEOMETRY, geometry);
    }

    private static net.minecraft.world.level.block.state.BlockState integratedContraptionControls(
        TrainControlConsoleGeometry geometry
    ) {
        var block = BuiltInRegistries.BLOCK.get(
            ResourceLocation.fromNamespaceAndPath("create", "contraption_controls")
        );
        return block.defaultBlockState()
            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
            .setValue(TrainControlConsoleIntegration.GEOMETRY, geometry);
    }

    private static void check(GameTestHelper h, boolean condition, String message) { h.assertTrue(condition, message); }

    private static List<ControlTransmitter> transmitters(ConsoleBlockEntity desk) {
        try {
            Field field = ConsoleBlockEntity.class.getDeclaredField("transmitters");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<ControlTransmitter> transmitters = (List<ControlTransmitter>) field.get(desk);
            return transmitters;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to inspect Aeroworks ControlTransmitters", exception);
        }
    }

    private static int maximumTransmittedStrength(ConsoleBlockEntity desk) {
        return transmitters(desk).stream().mapToInt(transmitter -> transmitter.lastStrength).max().orElse(0);
    }

    @GameTest(template = "empty_console")
    public static void nativeTrainControlsBridgeDeskMultiblock(GameTestHelper h) {
        ConsoleBlockEntity ownerDesk = desk(h, ORIGIN, "control_desk");
        control(h, ORIGIN.east(), integratedControls(TrainControlConsoleGeometry.DESK), ORIGIN);
        desk(h, ORIGIN.east(2), "control_desk");
        ConsoleComputers.INSTANCE.install(ownerDesk, computer(false));

        BlockPos controlsPos = h.absolutePos(ORIGIN.east());
        ConsoleMultiblockManager.INSTANCE.invalidate(h.getLevel(), ownerDesk.getBlockPos());
        var snapshot = ConsoleMultiblockManager.INSTANCE.resolve(h.getLevel(), ownerDesk.getBlockPos());
        check(h, snapshot.getState() == ConsoleNetworkState.ACTIVE, "Train Controls did not bridge the active row");
        check(h, snapshot.getSegmentPositions().size() == 3, "Physical Train Controls segment was not counted");
        check(h, snapshot.getMembers().size() == 2, "Train Controls became an addressable desk member");
        check(h, snapshot.memberAt(controlsPos) == null, "Train Controls exposed a desk API node");
        check(h, snapshot.getOwner() != null, "Embedded computer ownership was lost across Train Controls");
        h.succeed();
    }

    @GameTest(template = "empty_console")
    public static void nativeControlKindsAreClassifiedExactly(GameTestHelper h) {
        check(h, TrainControlConsoleIntegration.kind(integratedControls(TrainControlConsoleGeometry.DESK))
            == ConsoleControlKind.TRAIN, "Create Train Controls were not classified exactly");
        check(h, TrainControlConsoleIntegration.kind(integratedContraptionControls(TrainControlConsoleGeometry.DESK))
            == ConsoleControlKind.CONTRAPTION, "Contraption Controls inherited the Train classification");
        check(h, !TrainControlConsoleIntegration.isTrainControls(
            integratedContraptionControls(TrainControlConsoleGeometry.DESK)),
            "Contraption Controls still pass the exact Train Controls predicate");
        h.succeed();
    }

    @GameTest(template = "empty_console")
    public static void exactProfilesKeepCopperStandOutOfDeskFamily(GameTestHelper h) {
        var deskState = AeroworksTypes.INSTANCE.controlDeskBlock("control_desk").defaultBlockState();
        var standState = AeroworksTypes.INSTANCE.controlDeskBlock("control_stand").defaultBlockState();
        var copycatStand = AeroworksTypes.INSTANCE.controlDeskBlock("copycat_control_stand").defaultBlockState();
        check(h, ConsoleAssemblyAdapters.profile(deskState).getGeometry() == TrainControlConsoleGeometry.DESK,
            "Copper control desk was not classified as desk geometry");
        check(h, ConsoleAssemblyAdapters.profile(standState).getGeometry() == TrainControlConsoleGeometry.STAND,
            "Copper control stand inherited Aeroworks' incorrect desk geometry");
        check(h, ConsoleAssemblyAdapters.profile(copycatStand).getGeometry() == TrainControlConsoleGeometry.STAND,
            "Copycat control stand was not classified as stand geometry");
        check(h, ConsoleAssemblyAdapters.profile(CCBlocks.COMPUTER_CONTROL_STAND.get()).getGeometry()
                == TrainControlConsoleGeometry.STAND,
            "Computer control stand was not classified by its declared variant");
        h.succeed();
    }

    @GameTest(template = "empty_console")
    public static void integratedControlsKeepTheExactCopycatFamily(GameTestHelper h) {
        String[] variants = {
            "control_desk", "copycat_control_desk", "control_stand", "copycat_control_stand"
        };
        for (int i = 0; i < variants.length; i++) {
            BlockPos anchor = ORIGIN.south(i * 2);
            desk(h, anchor, variants[i]);
            BlockPos controls = anchor.east();
            var state = i < 2
                ? integratedControls(TrainControlConsoleGeometry.DESK)
                : integratedContraptionControls(TrainControlConsoleGeometry.STAND);
            control(h, controls, state, anchor);

            boolean expectedCopycat = variants[i].startsWith("copycat_");
            check(h, h.getBlockState(controls).getValue(TrainControlConsoleIntegration.COPYCAT)
                    == expectedCopycat,
                "Integrated Create control selected the wrong chassis family for " + variants[i]);
        }
        h.succeed();
    }

    @GameTest(template = "empty_console")
    public static void adjacentUuidRowsRemainIndependent(GameTestHelper h) {
        ConsoleBlockEntity a = desk(h, ORIGIN, "control_desk");
        ConsoleBlockEntity b = desk(h, ORIGIN.east(), "control_desk");
        ConsoleBlockEntity c = desk(h, ORIGIN.east(2), "control_desk");
        ConsoleBlockEntity d = desk(h, ORIGIN.east(3), "control_desk");
        UUID leftId = UUID.randomUUID();
        UUID rightId = UUID.randomUUID();
        ((ConsoleAssemblyStateAccess)a).ccaeroworks_setAssemblyId(leftId);
        ((ConsoleAssemblyStateAccess)b).ccaeroworks_setAssemblyId(leftId);
        ((ConsoleAssemblyStateAccess)c).ccaeroworks_setAssemblyId(rightId);
        ((ConsoleAssemblyStateAccess)d).ccaeroworks_setAssemblyId(rightId);

        ConsoleMultiblockManager.INSTANCE.invalidate(h.getLevel(), a.getBlockPos());
        ConsoleMultiblockManager.INSTANCE.invalidate(h.getLevel(), c.getBlockPos());
        var left = ConsoleMultiblockManager.INSTANCE.resolve(h.getLevel(), a.getBlockPos());
        var right = ConsoleMultiblockManager.INSTANCE.resolve(h.getLevel(), c.getBlockPos());
        check(h, left.getSegmentPositions().size() == 2, "Left row crossed into an adjacent UUID row");
        check(h, right.getSegmentPositions().size() == 2, "Right row crossed into an adjacent UUID row");
        check(h, !ConsoleAssemblyRuntime.connected(h.getLevel(), b.getBlockPos(), c.getBlockPos()),
            "Adjacent different UUIDs opened a row connection");
        h.succeed();
    }

    @GameTest(template = "empty_console")
    public static void bothControlPlacementOrdersJoinTheClickedRow(GameTestHelper h) {
        BlockPos firstControl = ORIGIN;
        BlockPos firstDesk = ORIGIN.east();
        h.setBlock(firstControl, integratedControls(TrainControlConsoleGeometry.NONE));
        ConsoleAssemblyPlacement.capture(
            h.getLevel(), h.absolutePos(firstDesk), h.absolutePos(firstControl), false
        );
        h.setBlock(firstDesk, AeroworksTypes.INSTANCE.controlDeskBlock("control_stand").defaultBlockState()
            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        ConsoleBlockEntity deskAfter = (ConsoleBlockEntity)h.getBlockEntity(firstDesk);
        ConsoleAssemblyRuntime.INSTANCE.initializePlaced(h.getLevel(), deskAfter.getBlockPos(), null);
        var recruitedState = h.getBlockState(firstControl);
        check(h, recruitedState.getValue(TrainControlConsoleIntegration.GEOMETRY)
                == TrainControlConsoleGeometry.STAND,
            "Desk-after-controls placement did not recruit the native control as stand geometry");
        check(h, ConsoleAssemblyRuntime.id(h.getLevel(), h.absolutePos(firstControl)).equals(
                ConsoleAssemblyRuntime.id(h.getLevel(), deskAfter.getBlockPos())),
            "Desk-after-controls placement did not assign one row UUID");

        BlockPos secondDesk = ORIGIN.south(3);
        BlockPos secondControl = secondDesk.east();
        ConsoleBlockEntity deskBefore = desk(h, secondDesk, "control_desk");
        UUID rowId = UUID.randomUUID();
        ((ConsoleAssemblyStateAccess)deskBefore).ccaeroworks_setAssemblyId(rowId);
        ConsoleAssemblyPlacement.capture(
            h.getLevel(), h.absolutePos(secondControl), deskBefore.getBlockPos(), false
        );
        h.setBlock(secondControl, integratedControls(TrainControlConsoleGeometry.DESK));
        ConsoleAssemblyRuntime.INSTANCE.initializePlaced(h.getLevel(), h.absolutePos(secondControl), null);
        check(h, rowId.equals(ConsoleAssemblyRuntime.id(h.getLevel(), h.absolutePos(secondControl))),
            "Controls-after-desk placement did not inherit the clicked UUID");
        h.succeed();
    }

    @GameTest(template = "empty_console")
    public static void ceilingDesksRejectCreateControlIntegration(GameTestHelper h) {
        var ceilingState = AeroworksTypes.INSTANCE.controlDeskBlock("control_desk").defaultBlockState()
            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
            .setValue(ConsoleBlock.CEILING, true);
        BlockPos ceiling = h.absolutePos(ORIGIN);
        ConsoleAssemblyPlacement.capture(h.getLevel(), ceiling, null, false);
        h.setBlock(ORIGIN, ceilingState);
        ConsoleAssemblyRuntime.INSTANCE.initializePlaced(h.getLevel(), ceiling, null);
        check(h, ConsoleAssemblyRuntime.geometry(ceilingState) == TrainControlConsoleGeometry.DESK,
            "Native ceiling desk lost its own assembly geometry");

        BlockPos adjacent = ORIGIN.east();
        control(h, adjacent, integratedControls(TrainControlConsoleGeometry.NONE), ORIGIN);
        check(h, h.getBlockState(adjacent).getValue(TrainControlConsoleIntegration.GEOMETRY)
                == TrainControlConsoleGeometry.NONE,
            "Ceiling desk accepted a Create control insert");
        check(h, ConsoleAssemblyRuntime.id(h.getLevel(), h.absolutePos(adjacent)) == null,
            "Rejected ceiling control received the desk assembly UUID");

        BlockPos invalid = ORIGIN.south(3);
        control(h, invalid, integratedControls(TrainControlConsoleGeometry.DESK), null);
        check(h, h.getBlockState(invalid).getValue(TrainControlConsoleIntegration.GEOMETRY)
                == TrainControlConsoleGeometry.NONE,
            "Unanchored integrated control did not fall back to native Create geometry");
        h.succeed();
    }

    @GameTest(template = "empty_console")
    public static void trainControlAssemblyDataSurvivesNbtRoundTrip(GameTestHelper h) {
        h.setBlock(ORIGIN, integratedControls(TrainControlConsoleGeometry.DESK));
        var blockEntity = h.getBlockEntity(ORIGIN);
        UUID id = UUID.randomUUID();
        ((ConsoleAssemblyStateAccess)blockEntity).ccaeroworks_setAssemblyId(id);
        ((ConsoleAssemblyStyleAccess)blockEntity).ccaeroworks_setStyleRevision(17L);
        var saved = blockEntity.saveWithFullMetadata(h.getLevel().registryAccess());
        ((ConsoleAssemblyStateAccess)blockEntity).ccaeroworks_setAssemblyId(null);
        ((ConsoleAssemblyStyleAccess)blockEntity).ccaeroworks_setStyleRevision(0L);
        blockEntity.loadWithComponents(saved, h.getLevel().registryAccess());
        check(h, id.equals(((ConsoleAssemblyStateAccess)blockEntity).ccaeroworks_getAssemblyId()),
            "Train control row UUID was lost during NBT round-trip");
        check(h, ((ConsoleAssemblyStyleAccess)blockEntity).ccaeroworks_getStyleRevision() == 17L,
            "Train control style revision was lost during NBT round-trip");
        check(h, saved.contains("CCAeroworksAssemblyProfile"), "Assembly profile ID was not persisted");
        h.succeed();
    }

    @GameTest(template = "empty_console")
    public static void copycatStyleTransactionCoversTheCompleteAssembly(GameTestHelper h) {
        ConsoleBlockEntity source = desk(h, ORIGIN, "copycat_control_desk");
        control(h, ORIGIN.east(), integratedControls(TrainControlConsoleGeometry.DESK), ORIGIN);
        var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);

        ItemStack paid = new ItemStack(Items.GOLD_BLOCK, 2);
        check(h, ConsoleAssemblyStyles.applyPaidSkin(
                source,
                com.mred231.aeroworks.content.controls.console.DeskSkin.Zone.FRAME,
                Blocks.GOLD_BLOCK.defaultBlockState(),
                paid,
                player),
            "Explicit assembly style update was not handled");
        check(h, paid.isEmpty(), "Copycat row did not consume one material per visible segment");
        var sourceStyle = (ConsoleAssemblyStyleAccess)source;
        var controlStyle = (ConsoleAssemblyStyleAccess)h.getBlockEntity(ORIGIN.east());
        check(h, sourceStyle.ccaeroworks_getAssemblySkin().material(
                com.mred231.aeroworks.content.controls.console.DeskSkin.Zone.FRAME).is(Blocks.GOLD_BLOCK),
            "Source Copycat frame did not receive the selected material");
        check(h, controlStyle.ccaeroworks_getAssemblySkin().material(
                com.mred231.aeroworks.content.controls.console.DeskSkin.Zone.FRAME).is(Blocks.GOLD_BLOCK),
            "Integrated Create controls did not inherit the row's Copycat frame");
        check(h, sourceStyle.ccaeroworks_getStyleRevision() == controlStyle.ccaeroworks_getStyleRevision(),
            "Copycat style revision diverged inside one assembly");

        ItemStack insufficient = new ItemStack(Items.DIAMOND_BLOCK);
        check(h, ConsoleAssemblyStyles.applyPaidSkin(
                source,
                com.mred231.aeroworks.content.controls.console.DeskSkin.Zone.PANEL,
                Blocks.DIAMOND_BLOCK.defaultBlockState(),
                insufficient,
                player),
            "Insufficient Copycat transaction escaped to native one-block handling");
        check(h, insufficient.getCount() == 1, "Rejected Copycat transaction consumed material");
        check(h, sourceStyle.ccaeroworks_getAssemblySkin().material(
                com.mred231.aeroworks.content.controls.console.DeskSkin.Zone.PANEL) == null,
            "Rejected Copycat transaction partially changed the source");
        check(h, controlStyle.ccaeroworks_getAssemblySkin().material(
                com.mred231.aeroworks.content.controls.console.DeskSkin.Zone.PANEL) == null,
            "Rejected Copycat transaction partially changed an integrated control");

        ItemStack sourceReturn = ConsoleAssemblyStyles.clearPaidSkin(
            source,
            com.mred231.aeroworks.content.controls.console.DeskSkin.Zone.FRAME,
            player
        );
        check(h, sourceReturn != null && sourceReturn.is(Items.GOLD_BLOCK) && sourceReturn.getCount() == 1,
            "Removing a row style did not return the source segment's material exactly once");
        int returnedToPlayer = player.getInventory().items.stream()
            .filter(stack -> stack.is(Items.GOLD_BLOCK))
            .mapToInt(ItemStack::getCount)
            .sum();
        check(h, returnedToPlayer == 1,
            "Removing a row style did not return the integrated control's material exactly once");
        h.succeed();
    }

    @GameTest(template = "empty_console")
    public static void mixedNativeControlsBridgeOneDeskRow(GameTestHelper h) {
        ConsoleBlockEntity ownerDesk = desk(h, ORIGIN, "control_desk");
        control(h, ORIGIN.east(), integratedControls(TrainControlConsoleGeometry.DESK), ORIGIN);
        control(h, ORIGIN.east(2), integratedContraptionControls(TrainControlConsoleGeometry.DESK), ORIGIN.east());
        desk(h, ORIGIN.east(3), "control_desk");
        ConsoleComputers.INSTANCE.install(ownerDesk, computer(false));
        ConsoleMultiblockManager.INSTANCE.invalidate(h.getLevel(), ownerDesk.getBlockPos());
        var snapshot = ConsoleMultiblockManager.INSTANCE.resolve(h.getLevel(), ownerDesk.getBlockPos());
        check(h, snapshot.getState() == ConsoleNetworkState.ACTIVE, "Mixed native controls did not bridge the row");
        check(h, snapshot.getSegmentPositions().size() == 4, "Mixed controls were not counted as physical segments");
        check(h, snapshot.getMembers().size() == 2, "A native control became a desk member");
        h.succeed();
    }

    @GameTest(template = "empty_console", timeoutTicks = 20)
    public static void contraptionControlOrphanFallbackPreservesNativeState(GameTestHelper h) {
        desk(h, ORIGIN, "control_desk");
        control(h, ORIGIN.east(), integratedContraptionControls(TrainControlConsoleGeometry.DESK), ORIGIN);
        var controls = (ContraptionControlsBlockEntity)h.getBlockEntity(ORIGIN.east());
        controls.disabled = true;
        controls.filtering.setFilter(new ItemStack(Items.DISPENSER));
        check(h, controls.filtering.getFilter().is(Items.DISPENSER), "Test setup could not set the native filter");
        var id = ((ContraptionControlIdentityAccess)controls).ccaeroworks_getContraptionControlId();
        h.setBlock(ORIGIN, Blocks.AIR);
        TrainControlConsoleUpdater.INSTANCE.schedule(h.getLevel(), h.absolutePos(ORIGIN.east()));
        h.runAfterDelay(2, () -> {
            var after = (ContraptionControlsBlockEntity)h.getBlockEntity(ORIGIN.east());
            check(h, h.getBlockState(ORIGIN.east()).getValue(TrainControlConsoleIntegration.GEOMETRY)
                == TrainControlConsoleGeometry.NONE, "Orphaned Contraption Control kept console geometry");
            check(h, after.disabled, "Orphan fallback lost the native disabled state");
            check(h, after.filtering.getFilter().is(Items.DISPENSER), "Orphan fallback lost the native filter");
            check(h, ((ContraptionControlIdentityAccess)after).ccaeroworks_getContraptionControlId().equals(id),
                "Orphan fallback changed the controller UUID");
            h.succeed();
        });
    }

    @GameTest(template = "empty_console")
    public static void nativeTrainControlsDoNotBridgeDifferentConsoleFamily(GameTestHelper h) {
        ConsoleBlockEntity firstDesk = desk(h, ORIGIN, "control_desk");
        control(h, ORIGIN.east(), integratedControls(TrainControlConsoleGeometry.STAND), ORIGIN);
        desk(h, ORIGIN.east(2), "control_desk");

        ConsoleMultiblockManager.INSTANCE.invalidate(h.getLevel(), firstDesk.getBlockPos());
        var snapshot = ConsoleMultiblockManager.INSTANCE.resolve(h.getLevel(), firstDesk.getBlockPos());
        check(h, snapshot.getSegmentPositions().size() == 1, "Stand Train Controls joined a desk row");
        check(h, snapshot.getMembers().size() == 1, "Mismatched console families were merged");
        h.succeed();
    }

    @GameTest(template = "empty_console", timeoutTicks = 20)
    public static void orphanedNativeTrainControlsReturnToCreateGeometry(GameTestHelper h) {
        desk(h, ORIGIN, "control_desk");
        control(h, ORIGIN.east(), integratedControls(TrainControlConsoleGeometry.DESK), ORIGIN);
        control(h, ORIGIN.east(2), integratedControls(TrainControlConsoleGeometry.DESK), ORIGIN.east());
        h.setBlock(ORIGIN, Blocks.AIR);
        TrainControlConsoleUpdater.INSTANCE.schedule(h.getLevel(), h.absolutePos(ORIGIN.east()));

        h.runAfterDelay(2, () -> {
            check(h, h.getBlockState(ORIGIN.east()).getValue(TrainControlConsoleIntegration.GEOMETRY)
                == TrainControlConsoleGeometry.NONE, "First orphaned Train Controls kept console geometry");
            check(h, h.getBlockState(ORIGIN.east(2)).getValue(TrainControlConsoleIntegration.GEOMETRY)
                == TrainControlConsoleGeometry.NONE, "Connected orphaned Train Controls kept console geometry");
            h.succeed();
        });
    }

    @GameTest(template = "empty_console")
    public static void variantsSaveAndRestore(GameTestHelper h) {
        String[] variants = {"control_desk", "copycat_control_desk", "control_stand", "copycat_control_stand"};
        for (int i = 0; i < variants.length; i++) for (boolean advanced : new boolean[]{false, true}) {
            BlockPos p = ORIGIN.offset(i * 2, 0, advanced ? 3 : 0);
            ConsoleBlockEntity host = desk(h, p, variants[i]);
            ItemStack source = computer(advanced);
            ConsoleComputers.INSTANCE.install(host, source);
            ConsoleComputer before = ConsoleComputers.get(host);
            before.getGroups().addGroup("retained");
            var saved = host.saveWithFullMetadata(h.getLevel().registryAccess());
            host.loadWithComponents(saved, h.getLevel().registryAccess());
            ConsoleComputer after = ConsoleComputers.get(host);
            check(h, after != null && after.isAdvanced() == advanced, "Computer tier lost for " + variants[i]);
            check(h, NonNegativeId.getId(after.item().get(ModRegistry.DataComponents.COMPUTER_ID.get())) == 937, "Computer ID lost");
            check(h, after.item().getHoverName().getString().equals("slot-test"), "Computer label lost");
            check(h, after.wireChannelNames().contains("engine"), "Wire channels lost");
            check(h, after.getGroups().definitions().size() == 1, "Channel groups lost");
            check(h, after.item().get(ModRegistry.DataComponents.TERMINAL_SIZE.get()).width() == 42, "Terminal size lost");
            check(h, StorageCapacity.getOrDefault(after.item().get(ModRegistry.DataComponents.STORAGE_CAPACITY.get()), -1) == 2000000, "Capacity lost");
        }
        h.succeed();
    }

    @GameTest(template = "empty_console")
    public static void transferToLargerPieceAndDropLast(GameTestHelper h) {
        ConsoleBlockEntity left = desk(h, ORIGIN, "control_desk");
        ConsoleBlockEntity host = desk(h, ORIGIN.east(), "control_desk");
        ConsoleBlockEntity right = desk(h, ORIGIN.east(2), "control_desk");
        ConsoleBlockEntity farRight = desk(h, ORIGIN.east(3), "control_desk");
        var rightId = ((DeskIdentityAccess)right).ccaeroworks_getDeskId();
        ConsoleComputers.INSTANCE.install(host, computer(true));
        h.setBlock(ORIGIN.east(), Blocks.AIR);
        check(h, ConsoleComputers.get(left) == null && ConsoleComputers.get(right) != null, "Must choose larger right piece");
        check(h, ((DeskIdentityAccess)right).ccaeroworks_getDeskId().equals(rightId), "Destination identity changed");
        h.setBlock(ORIGIN.east(2), Blocks.AIR);
        check(h, ConsoleComputers.get(farRight) != null, "Computer did not move to last console");
        h.setBlock(ORIGIN.east(3), Blocks.AIR);
        var drops = h.getEntities(EntityType.ITEM).stream().filter(e -> e.getItem().is(ModRegistry.Items.COMPUTER_ADVANCED.get())).toList();
        check(h, drops.size() == 1 && drops.getFirst().getItem().getCount() == 1, "Last computer must drop exactly once");
        check(h, NonNegativeId.getId(drops.getFirst().getItem().get(ModRegistry.DataComponents.COMPUTER_ID.get())) == 937, "Dropped ID lost");
        h.succeed();
    }

    @GameTest(template = "empty_console")
    public static void transferTieGoesLeft(GameTestHelper h) {
        ConsoleBlockEntity left = desk(h, ORIGIN, "control_desk");
        ConsoleBlockEntity host = desk(h, ORIGIN.east(), "control_desk");
        ConsoleBlockEntity right = desk(h, ORIGIN.east(2), "control_desk");
        ConsoleComputers.INSTANCE.install(host, computer(false));
        h.setBlock(ORIGIN.east(), Blocks.AIR);
        check(h, ConsoleComputers.get(left) != null && ConsoleComputers.get(right) == null, "Equal pieces must choose left");
        h.succeed();
    }

    @GameTest(template = "empty_console")
    public static void sharedSlotRejectsInvalidItemsAndConcurrentDuplicate(GameTestHelper h) {
        ConsoleBlockEntity first = desk(h, ORIGIN, "control_desk");
        ConsoleBlockEntity second = desk(h, ORIGIN.east(), "control_desk");
        var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.setPos(first.getBlockPos().getX() + .5, first.getBlockPos().getY() + .5, first.getBlockPos().getZ() + .5);
        ConsoleInventoryMenu a = new ConsoleInventoryMenu(1, player.getInventory(), first.getBlockPos(), first);
        ConsoleInventoryMenu b = new ConsoleInventoryMenu(2, player.getInventory(), second.getBlockPos(), second);
        check(h, !a.getSlot(0).mayPlace(new ItemStack(Items.STONE)), "Slot accepts non-computer");
        player.getInventory().setItem(9, computer(false));
        a.quickMoveStack(player, 1);
        check(h, player.getInventory().getItem(9).isEmpty() && b.getSlot(0).hasItem(), "Shift insert not shared");
        player.getInventory().setItem(9, computer(true));
        check(h, b.quickMoveStack(player, 1).isEmpty(), "Second shift insert must be rejected");
        check(h, player.getInventory().getItem(9).is(ModRegistry.Items.COMPUTER_ADVANCED.get()), "Rejected item consumed");
        check(h, ConsoleMultiblockManager.INSTANCE.resolve(h.getLevel(), first.getBlockPos()).getComputers().size() == 1, "Duplicate installed");
        // A normal cursor swap must atomically return the previous computer, not duplicate it.
        b.setCarried(computer(true));
        b.clicked(0, 0, ClickType.PICKUP, player);
        check(h, b.getCarried().is(ModRegistry.Items.COMPUTER_NORMAL.get()), "Swap did not return old computer");
        check(h, ConsoleMultiblockManager.INSTANCE.resolve(h.getLevel(), first.getBlockPos()).getOwner().isAdvanced(), "Swap tier not installed");
        a.quickMoveStack(player, 0);
        check(h, !b.getSlot(0).hasItem(), "Extraction not reflected in other menu");
        h.succeed();
    }

    @GameTest(template = "empty_console", timeoutTicks = 40)
    public static void legacyMigrationKeepsVariantAndComputer(GameTestHelper h) {
        var state = CCBlocks.COMPUTER_COPYCAT_CONTROL_STAND.get().defaultBlockState()
            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        h.setBlock(ORIGIN, state);
        var host = (ConsoleBlockEntity)h.getBlockEntity(ORIGIN);
        host.deskSkin().set(com.mred231.aeroworks.content.controls.console.DeskSkin.Zone.FRAME, Blocks.GOLD_BLOCK.defaultBlockState(), new ItemStack(Items.GOLD_BLOCK));
        var tag = host.saveWithFullMetadata(h.getLevel().registryAccess());
        tag.putInt("CCAeroworksComputerId", 937);
        tag.putString("CCAeroworksLabel", "legacy-test");
        host.loadWithComponents(tag, h.getLevel().registryAccess());
        ConsoleComputerLifecycle.INSTANCE.scheduleMigration(host);
        h.runAfterDelay(3, () -> {
            h.assertBlockPresent(AeroworksTypes.INSTANCE.controlDeskBlock("copycat_control_stand"), ORIGIN);
            ConsoleComputer computer = ConsoleComputers.get(h.getBlockEntity(ORIGIN));
            check(h, computer != null && !computer.isAdvanced(), "Legacy computer missing");
            check(h, NonNegativeId.getId(computer.item().get(ModRegistry.DataComponents.COMPUTER_ID.get())) == 937, "Legacy ID lost");
            check(h, computer.item().getHoverName().getString().equals("legacy-test"), "Legacy label lost");
            var migrated = (ConsoleBlockEntity)h.getBlockEntity(ORIGIN);
            check(h, migrated.deskSkin().material(com.mred231.aeroworks.content.controls.console.DeskSkin.Zone.FRAME).is(Blocks.GOLD_BLOCK), "Copycat material lost");
            h.assertItemEntityNotPresent(Items.GOLD_BLOCK);
            h.succeed();
        });
    }

    @GameTest(template = "empty_console")
    public static void mergeKeepsAdvancedAndEjectsNormal(GameTestHelper h) {
        ConsoleBlockEntity normal = desk(h, ORIGIN, "control_desk");
        ConsoleBlockEntity advanced = desk(h, ORIGIN.east(2), "control_desk");
        ((ConsoleAssemblyStateAccess)advanced).ccaeroworks_setAssemblyId(
            ((ConsoleAssemblyStateAccess)normal).ccaeroworks_getAssemblyId()
        );
        ConsoleComputers.INSTANCE.install(normal, computer(false));
        ConsoleComputers.INSTANCE.install(advanced, computer(true));
        desk(h, ORIGIN.east(), "control_desk");
        ConsoleComputerLifecycle.INSTANCE.reconcile(h.getLevel(), normal.getBlockPos());
        check(h, ConsoleComputers.get(normal) == null && ConsoleComputers.get(advanced) != null, "Wrong merge winner");
        h.assertItemEntityCountIs(ModRegistry.Items.COMPUTER_NORMAL.get(), ORIGIN, 4, 1);
        h.succeed();
    }
    @GameTest(template = "empty_console")
    public static void everyVariantTransfersWithoutChangingModulesOrIdentity(GameTestHelper h) {
        for (String variant : new String[]{"control_desk", "copycat_control_desk", "control_stand", "copycat_control_stand"}) {
            for (boolean advanced : new boolean[]{false, true}) {
                ConsoleBlockEntity host = desk(h, ORIGIN, variant);
                ConsoleBlockEntity target = desk(h, ORIGIN.east(), variant);
                var id = ((DeskIdentityAccess)target).ccaeroworks_getDeskId();
                ConsoleComputers.INSTANCE.install(host, computer(advanced));
                ConsoleComputers.get(host).getGroups().addGroup("transfer");
                h.setBlock(ORIGIN, Blocks.AIR);
                var moved = ConsoleComputers.get(target);
                check(h, moved != null && moved.isAdvanced() == advanced, "Transfer failed for " + variant);
                check(h, moved.getGroups().definitions().size() == 1 && moved.wireChannelNames().contains("engine"), "Channel state lost on transfer");
                check(h, ((DeskIdentityAccess)target).ccaeroworks_getDeskId().equals(id), "Target desk ID changed");
                h.setBlock(ORIGIN.east(), Blocks.AIR);
            }
        }
        int normal = h.getEntities(EntityType.ITEM).stream().filter(e -> e.getItem().is(ModRegistry.Items.COMPUTER_NORMAL.get())).mapToInt(e -> e.getItem().getCount()).sum();
        int advanced = h.getEntities(EntityType.ITEM).stream().filter(e -> e.getItem().is(ModRegistry.Items.COMPUTER_ADVANCED.get())).mapToInt(e -> e.getItem().getCount()).sum();
        check(h, normal == 4 && advanced == 4, "Lost or duplicated computers across variants");
        h.succeed();
    }

    @GameTest(template = "empty_console")
    public static void canceledBreakAndUnloadDoNotTransfer(GameTestHelper h) {
        ConsoleBlockEntity host = desk(h, ORIGIN, "control_desk");
        ConsoleBlockEntity target = desk(h, ORIGIN.east(), "control_desk");
        ConsoleComputers.INSTANCE.install(host, computer(false));
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel());
        java.util.function.Consumer<net.neoforged.neoforge.event.level.BlockEvent.BreakEvent> cancel = event -> {
            if (event.getPos().equals(host.getBlockPos())) event.setCanceled(true);
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGHEST, false,
            net.neoforged.neoforge.event.level.BlockEvent.BreakEvent.class, cancel);
        try {
            player.gameMode.destroyBlock(host.getBlockPos());
            check(h, h.getLevel().getBlockEntity(host.getBlockPos()) == host, "Canceled break removed console");
            check(h, ConsoleComputers.get(host) != null && ConsoleComputers.get(target) == null, "Canceled break moved computer");
        } finally { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(cancel); }
        // Create's invalidation is also used when chunks unload; it must only stop the runtime.
        host.invalidate();
        check(h, ConsoleComputers.get(host) != null && ConsoleComputers.get(target) == null, "Invalidation transferred computer");
        h.assertItemEntityNotPresent(ModRegistry.Items.COMPUTER_NORMAL.get());
        h.succeed();
    }

    @GameTest(template = "empty_console")
    public static void wrenchRemovalTransfersThenDropsComputer(GameTestHelper h) {
        ConsoleBlockEntity host = desk(h, ORIGIN, "control_desk");
        ConsoleBlockEntity target = desk(h, ORIGIN.east(), "control_desk");
        ConsoleComputers.INSTANCE.install(host, computer(false));
        var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var wrench = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("create", "wrench"));
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(wrench));
        for (ConsoleBlockEntity desk : new ConsoleBlockEntity[]{host, target}) {
            var hit = new net.minecraft.world.phys.BlockHitResult(desk.getBlockPos().getCenter(), Direction.UP, desk.getBlockPos(), false);
            var context = new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
            ((com.simibubi.create.content.equipment.wrench.IWrenchable)desk.getBlockState().getBlock()).onSneakWrenched(desk.getBlockState(), context);
            check(h, h.getLevel().getBlockState(desk.getBlockPos()).isAir(), "Wrench did not remove console");
            if (desk == host) check(h, ConsoleComputers.get(target) != null, "Wrench lost computer during transfer");
        }
        h.assertItemEntityCountIs(ModRegistry.Items.COMPUTER_NORMAL.get(), ORIGIN, 4, 1);
        h.succeed();
    }

    @GameTest(template = "empty_console", timeoutTicks = 40)
    public static void allLegacyItemsBecomeOrdinaryConsoles(GameTestHelper h) {
        for (ComputerConsoleVariant variant : ComputerConsoleVariant.values()) for (ComputerFamily family : new ComputerFamily[]{ComputerFamily.NORMAL, ComputerFamily.ADVANCED}) {
            BlockPos p = ORIGIN.offset(variant.ordinal() * 2, 0, family == ComputerFamily.ADVANCED ? 3 : 0);
            var block = CCBlocks.INSTANCE.computerConsole(variant, family).get();
            h.setBlock(p, block.defaultBlockState());
            var host = (ConsoleBlockEntity) h.getBlockEntity(p);
            ItemStack legacy = new ItemStack(block);
            legacy.applyComponents(computer(family == ComputerFamily.ADVANCED).getComponents());
            legacy.set(CCDataComponents.DESK_ID.get(), "12345678-1111-2222-3333-123456789abc");
            block.setPlacedBy(h.getLevel(), host.getBlockPos(), host.getBlockState(), h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL), legacy);
            var installed = ConsoleComputers.get(host);
            check(h, installed != null && installed.isAdvanced() == (family == ComputerFamily.ADVANCED), "Legacy item family lost");
            check(h, NonNegativeId.getId(installed.item().get(ModRegistry.DataComponents.COMPUTER_ID.get())) == 937, "Legacy item ID lost");
            check(h, !installed.item().has(CCDataComponents.DESK_ID.get()), "Desk identity leaked into computer item");
        }
        h.runAfterDelay(3, () -> {
            for (ComputerConsoleVariant variant : ComputerConsoleVariant.values()) for (boolean advanced : new boolean[]{false, true}) {
                BlockPos p = ORIGIN.offset(variant.ordinal() * 2, 0, advanced ? 3 : 0);
                h.assertBlockPresent(AeroworksTypes.INSTANCE.controlDeskBlock(variant.getAeroworksPath()), p);
                check(h, ConsoleComputers.get(h.getBlockEntity(p)) != null, "Migrated item computer missing");
            }
            h.succeed();
        });
    }

    @GameTest(template = "empty_console")
    public static void menuRejectsOutOfRangeMutation(GameTestHelper h) {
        var host = desk(h, ORIGIN, "control_desk");
        ConsoleComputers.INSTANCE.install(host, computer(false));
        var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.setPos(host.getBlockPos().getX() + 40, host.getBlockPos().getY(), host.getBlockPos().getZ());
        var menu = new ConsoleInventoryMenu(1, player.getInventory(), host.getBlockPos(), host);
        check(h, !menu.stillValid(player), "Out of range menu remained valid");
        menu.clicked(0, 0, ClickType.PICKUP, player);
        check(h, menu.getCarried().isEmpty() && ConsoleComputers.get(host) != null, "Out of range pickup succeeded");
        check(h, menu.quickMoveStack(player, 0).isEmpty(), "Out of range shift pickup succeeded");
        h.succeed();
    }

    @GameTest(template = "empty_console", timeoutTicks = 1000, batch = "control_overrides")
    public static void computerOverridePublishesAfterAeroworksSpringing(GameTestHelper h) {
        ConsoleBlockEntity ownerDesk = desk(h, ORIGIN, "control_desk");
        desk(h, ORIGIN.east(), "control_desk");
        ConsoleBlockEntity targetDesk = desk(h, ORIGIN.east(2), "control_desk");
        var yokeItem = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
            ResourceLocation.fromNamespaceAndPath("aeroworks", "yoke_module"));
        check(h, targetDesk.mount(2, new ItemStack(yokeItem)), "Yoke module did not mount");

        List<AtomicInteger> receivedStrengths = new java.util.ArrayList<>();
        List<IRedstoneLinkable> receivers = new java.util.ArrayList<>();
        for (ControlTransmitter transmitter : transmitters(targetDesk)) {
            AtomicInteger received = new AtomicInteger();
            IRedstoneLinkable receiver = new IRedstoneLinkable() {
                @Override public int getTransmittedStrength() { return 0; }
                @Override public void setReceivedStrength(int strength) { received.set(strength); }
                @Override public boolean isListening() { return true; }
                @Override public boolean isAlive() { return true; }
                @Override public net.createmod.catnip.data.Couple<RedstoneLinkNetworkHandler.Frequency> getNetworkKey() {
                    return transmitter.getNetworkKey();
                }
                @Override public BlockPos getLocation() { return targetDesk.getBlockPos().above(); }
            };
            receivedStrengths.add(received);
            receivers.add(receiver);
            Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(h.getLevel(), receiver);
        }
        check(h, !receivers.isEmpty(), "Yoke module did not create Aeroworks transmitters");

        ItemStack poweredComputer = computer(false);
        poweredComputer.set(CCDataComponents.COMPUTER_POWERED.get(), true);
        ConsoleComputers.INSTANCE.install(ownerDesk, poweredComputer);
        ConsoleComputer owner = ConsoleComputers.get(ownerDesk);
        check(h, owner != null, "Computer was not installed");
        owner.createServerComputer().turnOn();

        // GameTest ticks can advance much faster than CC:Tweaked's worker thread initializes.
        // A single tick-driven state machine keeps every settling period relative to the
        // preceding action and waits for the real asynchronous power transitions.
        String targetId = ((DeskIdentityAccess) targetDesk).ccaeroworks_getDeskId().toString();
        AtomicInteger stage = new AtomicInteger();
        AtomicInteger stageTicks = new AtomicInteger();
        h.onEachTick(() -> {
            switch (stage.get()) {
                case 0 -> {
                    if (owner.getServerComputer() == null || !owner.getServerComputer().isOn()) return;
                    ControlOverrideManager.INSTANCE.override(owner, new ControlOverrideCommand(targetId, 2, "turn", 12));
                    check(h, ControlOverrideManager.INSTANCE.isHardOverridden(targetDesk, 2, "turn"),
                        "HARD authority was not recorded");
                    stage.set(1);
                    stageTicks.set(0);
                }
                case 1 -> {
                    if (stageTicks.incrementAndGet() < 4) return;
                    check(h, targetDesk.module(2).value("turn") == 12,
                        "Aeroworks springing replaced the positive computer command");
                    check(h, maximumTransmittedStrength(targetDesk) == 12,
                        "Positive computer command did not reach the Aeroworks transmitter");
                    check(h, receivedStrengths.stream().mapToInt(AtomicInteger::get).max().orElse(0) == 12,
                        "Positive computer command did not reach a Redstone Link receiver");
                    check(h, ControlOverrideManager.INSTANCE.isHardOverridden(targetDesk, 2, "turn"),
                        "HARD authority was released while the computer and network were active");
                    targetDesk.setChannelFromController(2, "turn", -6);
                    check(h, targetDesk.module(2).value("turn") == 12,
                        "Manual input displaced a HARD override");
                    ControlOverrideManager.INSTANCE.override(owner, new ControlOverrideCommand(targetId, 2, "turn", -9));
                    stage.set(2);
                    stageTicks.set(0);
                }
                case 2 -> {
                    if (stageTicks.incrementAndGet() < 4) return;
                    check(h, targetDesk.module(2).value("turn") == -9,
                        "Negative computer command was not retained");
                    check(h, maximumTransmittedStrength(targetDesk) == 9,
                        "Negative computer command did not reach its Aeroworks transmitter direction");
                    check(h, receivedStrengths.stream().mapToInt(AtomicInteger::get).max().orElse(0) == 9,
                        "Negative computer command did not reach a Redstone Link receiver");
                    check(h, ControlOverrideManager.INSTANCE.release(owner, targetId, 2, "turn", "released"),
                        "Override release was rejected");
                    stage.set(3);
                    stageTicks.set(0);
                }
                case 3 -> {
                    if (stageTicks.incrementAndGet() < 4) return;
                    check(h, targetDesk.module(2).value("turn") == 0,
                        "Released unattended control did not spring back");
                    check(h, maximumTransmittedStrength(targetDesk) == 0,
                        "Released control continued publishing a signal");
                    ControlOverrideManager.INSTANCE.override(owner, new ControlOverrideCommand(targetId, 2, "turn", 6));
                    h.setBlock(ORIGIN.east(), Blocks.AIR);
                    ConsoleComputerLifecycle.INSTANCE.reconcile(h.getLevel(), ownerDesk.getBlockPos());
                    stage.set(4);
                    stageTicks.set(0);
                }
                case 4 -> {
                    if (stageTicks.incrementAndGet() < 12) return;
                    check(h, !ControlOverrideManager.INSTANCE.isHardOverridden(targetDesk, 2, "turn"),
                        "Target desk outside the owner network retained HARD authority");
                    check(h, maximumTransmittedStrength(targetDesk) == 0,
                        "Target desk outside the owner network retained a transmitted signal");
                    check(h, receivedStrengths.stream().mapToInt(AtomicInteger::get).max().orElse(0) == 0,
                        "Target desk outside the owner network retained a received signal");
                    desk(h, ORIGIN.east(), "control_desk");
                    ConsoleComputerLifecycle.INSTANCE.reconcile(h.getLevel(), ownerDesk.getBlockPos());
                    ControlOverrideManager.INSTANCE.override(owner, new ControlOverrideCommand(targetId, 2, "turn", 7));
                    owner.getServerComputer().shutdown();
                    ControlOverrideManager.INSTANCE.tick(owner, false);
                    stage.set(5);
                    stageTicks.set(0);
                }
                case 5 -> {
                    if (stageTicks.incrementAndGet() < 4) return;
                    check(h, targetDesk.module(2).value("turn") == 0,
                        "Computer shutdown left a commanded control value active");
                    check(h, maximumTransmittedStrength(targetDesk) == 0,
                        "Computer shutdown left an Aeroworks signal active");
                    check(h, !ControlOverrideManager.INSTANCE.isHardOverridden(targetDesk, 2, "turn"),
                        "Computer shutdown retained HARD authority");
                    targetDesk.setChannelFromController(2, "turn", 5);
                    check(h, targetDesk.module(2).value("turn") == 5,
                        "Manual input was not restored after fail-safe release");
                    receivers.forEach(receiver ->
                        Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(h.getLevel(), receiver));
                    stage.set(6);
                    h.succeed();
                }
                default -> { }
            }
        });
    }

}
