package de.teutonstudio.ccaeroworks.gametest;

import com.mred231.aeroworks.content.controls.console.ConsoleBlock;
import com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity;
import com.mred231.aeroworks.content.controls.console.DeskSkin;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import dan200.computercraft.shared.ModRegistry;
import de.teutonstudio.ccaeroworks.api.mobile.MobilePeripheralContext;
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksTypes;
import de.teutonstudio.ccaeroworks.computer.ComputerConsoleAccess;
import de.teutonstudio.ccaeroworks.computer.ComputerWireAdminLuaApi;
import de.teutonstudio.ccaeroworks.computer.ConsoleComputers;
import de.teutonstudio.ccaeroworks.contraption.MobileConsoleComputer;
import de.teutonstudio.ccaeroworks.contraption.MobileContraptionRuntime;
import de.teutonstudio.ccaeroworks.contraption.VirtualAeroworksDeskInstance;
import de.teutonstudio.ccaeroworks.multiblock.ConsoleAssemblyStateAccess;
import de.teutonstudio.ccaeroworks.multiblock.ConsoleAssemblyStyleAccess;
import de.teutonstudio.ccaeroworks.multiblock.ConsoleAssemblyPlacement;
import de.teutonstudio.ccaeroworks.multiblock.ConsoleAssemblyRuntime;
import de.teutonstudio.ccaeroworks.registry.CCDataComponents;
import de.teutonstudio.ccaeroworks.train.TrainControlConsoleGeometry;
import de.teutonstudio.ccaeroworks.train.TrainControlConsoleIntegration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Persistence and removal regressions which require real registries and mixed-in blocks. */
@GameTestHolder("cc_aeroworks")
@PrefixGameTestTemplate(false)
public final class RuntimeRegressionGameTests {
    private static final BlockPos ORIGIN = new BlockPos(2, 2, 2);

    @GameTest(template = "empty_console")
    public static void deletedWireChannelsStayDeletedAfterReloadAndExtraction(GameTestHelper h) {
        var host = desk(h, ORIGIN, false, UUID.randomUUID());
        var source = computer();
        source.set(CCDataComponents.WIRE_CHANNELS.get(), UUID.randomUUID() + "|engine");
        ConsoleComputers.INSTANCE.install(host, source);
        var before = ConsoleComputers.get(host);
        new ComputerWireAdminLuaApi(new ComputerConsoleAccess(before)).remove("engine");
        check(!before.item().has(CCDataComponents.WIRE_CHANNELS.get()), "Deleted channels remain on the item");

        var saved = host.saveWithFullMetadata(h.getLevel().registryAccess());
        host.loadWithComponents(saved, h.getLevel().registryAccess());
        check(ConsoleComputers.get(host).wireChannelNames().isEmpty(), "Deleted channel returned after reload");
        var extracted = ConsoleComputers.INSTANCE.remove(host);
        ConsoleComputers.INSTANCE.install(host, extracted);
        check(ConsoleComputers.get(host).wireChannelNames().isEmpty(), "Deleted channel returned after reinstallation");
        h.succeed();
    }

    @GameTest(template = "empty_console")
    public static void movingComputerSavesLatestStateAfterEveryAdapterSave(GameTestHelper h) {
        var host = desk(h, ORIGIN, false, UUID.randomUUID());
        ConsoleComputers.INSTANCE.install(host, computer());
        var entity = new TestContraption(h.getLevel(), new BearingContraption(false, Direction.UP));
        var context = new TestContext(entity, host, BlockPos.ZERO);
        var adapter = new VirtualAeroworksDeskInstance(context);
        var mobile = new MobileConsoleComputer(entity, h.getLevel(), Set.of(BlockPos.ZERO), BlockPos.ZERO, context, List.of());
        try {
            var serverComputer = mobile.createServerComputer();
            for (String label : new String[]{"first-save", "second-save", "third-save"}) {
                serverComputer.setLabel(label);
                mobile.save();
                adapter.save(context);
                var restored = ItemStack.parseOptional(h.getLevel().registryAccess(),
                    context.blockEntityData().getCompound(MobileConsoleComputer.INSTALLED_TAG));
                check(label.equals(restored.getHoverName().getString()), "Mobile label reverted to a previous save");
                check(restored.has(ModRegistry.DataComponents.COMPUTER_ID.get()), "Mobile computer ID was not saved");
            }
        } finally {
            mobile.close();
            adapter.close();
        }
        h.succeed();
    }

    @GameTest(template = "empty_console")
    public static void movingComputersKeepExplicitAssembliesAndCeilingRows(GameTestHelper h) throws Exception {
        for (boolean ceiling : new boolean[]{false, true}) {
            var firstId = UUID.randomUUID();
            var first = desk(h, ORIGIN, ceiling, firstId);
            var second = desk(h, ORIGIN.east(), ceiling, UUID.randomUUID());
            ConsoleComputers.INSTANCE.install(first, computer());
            ConsoleComputers.INSTANCE.install(second, computer());
            checkMobileComputerCount(h, first, second, 2);

            ConsoleComputers.INSTANCE.remove(second);
            ((ConsoleAssemblyStateAccess) second).ccaeroworks_setAssemblyId(firstId);
            checkMobileComputerCount(h, first, second, 1);
            h.setBlock(ORIGIN, Blocks.AIR);
            h.setBlock(ORIGIN.east(), Blocks.AIR);
        }
        h.succeed();
    }

    @GameTest(template = "empty_console")
    public static void canceledControlBreakKeepsStyleAndAcceptedBreakReturnsItOnce(GameTestHelper h) {
        var player = FakePlayerFactory.getMinecraft(h.getLevel());
        desk(h, ORIGIN.west(), false, UUID.randomUUID());
        for (String path : new String[]{"controls", "contraption_controls"}) {
            BlockState state = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", path))
                .defaultBlockState().setValue(TrainControlConsoleIntegration.GEOMETRY, TrainControlConsoleGeometry.DESK);
            placeStyledControl(h, state);
            var pos = h.absolutePos(ORIGIN);
            var style = (ConsoleAssemblyStyleAccess) h.getBlockEntity(ORIGIN);
            int initialDrops = goldDrops(h);
            player.setGameMode(GameType.SURVIVAL);
            Consumer<BlockEvent.BreakEvent> cancel = event -> {
                if (event.getLevel() == h.getLevel() && event.getPos().equals(pos)) event.setCanceled(true);
            };
            // Cancel after the normal-priority updater, exposing premature BreakEvent mutations.
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, BlockEvent.BreakEvent.class, cancel);
            try {
                player.gameMode.destroyBlock(pos);
                check(!h.getBlockState(ORIGIN).isAir(), "Canceled break removed the control");
                check(style.ccaeroworks_getAssemblySkin().material(DeskSkin.Zone.FRAME).is(Blocks.GOLD_BLOCK),
                    "Canceled break cleared the control material");
                check(goldDrops(h) == initialDrops, "Canceled break dropped material");
            } finally {
                NeoForge.EVENT_BUS.unregister(cancel);
            }
            check(TrainControlConsoleIntegration.isIntegrated(h.getBlockState(ORIGIN)),
                "Test control lost its integrated geometry before the accepted break");
            player.gameMode.destroyBlock(pos);
            check(h.getBlockState(ORIGIN).isAir(), "Accepted break did not remove the control");
            check(style.ccaeroworks_getAssemblySkin().item(DeskSkin.Zone.FRAME).isEmpty(),
                "Accepted break did not clear paid material");
            check(goldDrops(h) == initialDrops + 1, "Survival break must return the material exactly once for " + path
                + ": expected " + (initialDrops + 1) + ", got " + goldDrops(h));

            placeStyledControl(h, state);
            player.setGameMode(GameType.CREATIVE);
            player.gameMode.destroyBlock(pos);
            check(goldDrops(h) == initialDrops + 1, "Creative break returned paid material");

            placeStyledControl(h, state);
            h.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_MOVE_BY_PISTON);
            check(goldDrops(h) == initialDrops + 1, "Moving the control duplicated its captured material");
        }
        player.setGameMode(GameType.SURVIVAL);
        h.succeed();
    }

    private static void checkMobileComputerCount(GameTestHelper h, ConsoleBlockEntity first,
                                                ConsoleBlockEntity second, int expected) throws Exception {
        var contraption = new BearingContraption(false, Direction.UP);
        for (var host : List.of(first, second)) {
            var local = host.getBlockPos().subtract(first.getBlockPos());
            contraption.getBlocks().put(local, new StructureBlockInfo(local, host.getBlockState(),
                host.saveWithFullMetadata(h.getLevel().registryAccess())));
        }
        var entity = new TestContraption(h.getLevel(), contraption);
        var runtime = MobileContraptionRuntime.create(entity);
        check(runtime != null, "Mobile runtime failed to initialize");
        try {
            var field = MobileContraptionRuntime.class.getDeclaredField("computers");
            field.setAccessible(true);
            var computers = (List<?>) field.get(runtime);
            check(computers.size() == expected, "Mobile runtime changed explicit assembly boundaries");
        } finally {
            runtime.close();
        }
    }

    private static ConsoleBlockEntity desk(GameTestHelper h, BlockPos pos, boolean ceiling, UUID assemblyId) {
        var state = AeroworksTypes.INSTANCE.controlDeskBlock("control_desk").defaultBlockState()
            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH).setValue(ConsoleBlock.CEILING, ceiling);
        h.setBlock(pos, state);
        var host = (ConsoleBlockEntity) h.getBlockEntity(pos);
        ((ConsoleAssemblyStateAccess) host).ccaeroworks_setAssemblyId(assemblyId);
        return host;
    }

    private static ItemStack computer() {
        return new ItemStack(ModRegistry.Items.COMPUTER_NORMAL.get());
    }

    private static void setGoldStyle(ConsoleAssemblyStyleAccess target) {
        target.ccaeroworks_getAssemblySkin().set(DeskSkin.Zone.FRAME, Blocks.GOLD_BLOCK.defaultBlockState(),
            new ItemStack(Items.GOLD_BLOCK));
    }

    private static void placeStyledControl(GameTestHelper h, BlockState state) {
        var pos = h.absolutePos(ORIGIN);
        ConsoleAssemblyPlacement.capture(h.getLevel(), pos, h.absolutePos(ORIGIN.west()), false);
        h.setBlock(ORIGIN, state);
        ConsoleAssemblyRuntime.INSTANCE.initializePlaced(h.getLevel(), pos, null);
        check(TrainControlConsoleIntegration.isIntegrated(h.getBlockState(ORIGIN)), "Test control did not join its anchor");
        setGoldStyle((ConsoleAssemblyStyleAccess) h.getBlockEntity(ORIGIN));
    }

    private static int goldDrops(GameTestHelper h) {
        return h.getEntities(EntityType.ITEM).stream().filter(e -> e.getItem().is(Items.GOLD_BLOCK))
            .mapToInt(e -> e.getItem().getCount()).sum();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class TestContraption extends ControlledContraptionEntity {
        private TestContraption(ServerLevel level, BearingContraption contraption) {
            super(BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.fromNamespaceAndPath("create", "stationary_contraption")), level);
            contraption.anchor = BlockPos.ZERO;
            contraption.bounds = new AABB(0, 0, 0, 2, 1, 1);
            setRotationAxis(Direction.Axis.Y);
            setContraption(contraption);
        }
    }

    private static final class TestContext implements MobilePeripheralContext {
        private final TestContraption entity;
        private final ConsoleBlockEntity host;
        private final BlockPos local;
        private CompoundTag data;

        private TestContext(TestContraption entity, ConsoleBlockEntity host, BlockPos local) {
            this.entity = entity;
            this.host = host;
            this.local = local;
            this.data = host.saveWithFullMetadata(level().registryAccess());
        }

        @Override public TestContraption entity() { return entity; }
        @Override public ServerLevel level() { return (ServerLevel) host.getLevel(); }
        @Override public BlockPos localPosition() { return local; }
        @Override public BlockPos worldPosition() { return host.getBlockPos(); }
        @Override public BlockState blockState() { return host.getBlockState(); }
        @Override public CompoundTag blockEntityData() { return data; }
        @Override public void setBlockEntityData(CompoundTag data) { this.data = data; }
    }
}
