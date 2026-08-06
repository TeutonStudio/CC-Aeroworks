package de.teutonstudio.ccaeroworks.client.ponder;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksTypes;
import de.teutonstudio.ccaeroworks.registry.CCBlocks;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class ComputerControlDeskScenes {
    private static final ResourceLocation ADVANCED_MODEM =
        ResourceLocation.fromNamespaceAndPath("computercraft", "wireless_modem_advanced");
    private static final ResourceLocation SPEAKER =
        ResourceLocation.fromNamespaceAndPath("computercraft", "speaker");

    public static void network(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("desk_network", PonderText.get("ponder.cc_aeroworks.desk_network.header"));
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos computer = util.grid().at(1, 1, 2);
        BlockPos middle = util.grid().at(2, 1, 2);
        BlockPos right = util.grid().at(3, 1, 2);

        scene.world().showSection(util.select().position(computer), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(65)
            .attachKeyFrame()
            .colored(PonderPalette.GREEN)
            .text(PonderText.get("ponder.cc_aeroworks.desk_network.text_1"))
            .pointAt(util.vector().topOf(computer))
            .placeNearTarget();
        scene.idle(75);

        scene.world().showSection(util.select().position(middle), Direction.WEST);
        scene.idle(12);
        scene.world().showSection(util.select().position(right), Direction.WEST);
        scene.overlay().showText(75)
            .attachKeyFrame()
            .text(PonderText.get("ponder.cc_aeroworks.desk_network.text_2"))
            .pointAt(util.vector().centerOf(middle))
            .placeNearTarget();
        scene.idle(85);

        scene.overlay().showText(75)
            .attachKeyFrame()
            .text(PonderText.get("ponder.cc_aeroworks.desk_network.text_3"))
            .pointAt(util.vector().topOf(right))
            .placeNearTarget();
        scene.idle(85);

        scene.overlay().showText(80)
            .attachKeyFrame()
            .colored(PonderPalette.GREEN)
            .text(PonderText.get("ponder.cc_aeroworks.desk_network.text_4"))
            .pointAt(util.vector().centerOf(middle))
            .placeNearTarget();
        scene.idle(90);

        scene.overlay().showControls(util.vector().topOf(right), Pointing.DOWN, 55)
            .rightClick()
            .whileSneaking();
        scene.overlay().showText(70)
            .attachKeyFrame()
            .text(PonderText.get("ponder.cc_aeroworks.desk_network.text_5"))
            .pointAt(util.vector().topOf(right))
            .placeNearTarget();
        scene.idle(80);

        scene.overlay().showText(75)
            .attachKeyFrame()
            .colored(PonderPalette.GREEN)
            .text(PonderText.get("ponder.cc_aeroworks.desk_network.text_6"))
            .pointAt(util.vector().topOf(computer))
            .placeNearTarget();
        scene.idle(85);
        scene.markAsFinished();
    }

    public static void peripheralSearch(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("peripheral_search", PonderText.get("ponder.cc_aeroworks.peripheral_search.header"));
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos computer = util.grid().at(1, 1, 2);
        BlockPos middle = util.grid().at(2, 1, 2);
        BlockPos right = util.grid().at(3, 1, 2);
        scene.world().showSection(util.select().fromTo(computer, right), Direction.DOWN);
        scene.idle(20);

        scene.overlay().showControls(util.vector().blockSurface(middle, Direction.SOUTH), Pointing.UP, 75)
            .withItem(itemStack(ADVANCED_MODEM));
        scene.overlay().showText(70)
            .attachKeyFrame()
            .text(PonderText.get("ponder.cc_aeroworks.peripheral_search.text_1"))
            .pointAt(util.vector().blockSurface(middle, Direction.SOUTH))
            .placeNearTarget();
        scene.idle(80);

        scene.overlay().showControls(util.vector().blockSurface(right, Direction.EAST), Pointing.LEFT, 75)
            .withItem(itemStack(SPEAKER));
        scene.overlay().showText(75)
            .attachKeyFrame()
            .text(PonderText.get("ponder.cc_aeroworks.peripheral_search.text_2"))
            .pointAt(util.vector().blockSurface(right, Direction.EAST))
            .placeNearTarget();
        scene.idle(85);

        scene.effects().indicateSuccess(computer);
        scene.overlay().showText(85)
            .attachKeyFrame()
            .colored(PonderPalette.GREEN)
            .text(PonderText.get("ponder.cc_aeroworks.peripheral_search.text_3"))
            .pointAt(util.vector().topOf(computer))
            .placeNearTarget();
        scene.idle(95);

        scene.overlay().showText(95)
            .attachKeyFrame()
            .text(PonderText.get("ponder.cc_aeroworks.peripheral_search.text_4"))
            .pointAt(util.vector().centerOf(middle))
            .placeNearTarget();
        scene.idle(105);

        scene.overlay().showText(95)
            .attachKeyFrame()
            .text(PonderText.get("ponder.cc_aeroworks.peripheral_search.text_5"))
            .pointAt(util.vector().centerOf(right))
            .placeNearTarget();
        scene.idle(105);

        scene.overlay().showText(90)
            .attachKeyFrame()
            .text(PonderText.get("ponder.cc_aeroworks.peripheral_search.text_6"))
            .pointAt(util.vector().topOf(middle))
            .placeNearTarget();
        scene.idle(100);

        scene.overlay().showText(90)
            .attachKeyFrame()
            .colored(PonderPalette.GREEN)
            .text(PonderText.get("ponder.cc_aeroworks.peripheral_search.text_7"))
            .pointAt(util.vector().topOf(computer))
            .placeNearTarget();
        scene.idle(100);
        scene.markAsFinished();
    }

    public static void diagnostics(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("desk_diagnostics", PonderText.get("ponder.cc_aeroworks.diagnostics.header"));
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos computer = util.grid().at(1, 1, 2);
        BlockPos middle = util.grid().at(2, 1, 2);
        BlockPos right = util.grid().at(3, 1, 2);
        scene.world().showSection(util.select().fromTo(computer, right), Direction.DOWN);
        scene.idle(20);

        BlockState duplicate = CCBlocks.COMPUTER_CONTROL_DESK.get()
            .defaultBlockState()
            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        scene.world().setBlock(middle, duplicate, false);
        scene.effects().indicateRedstone(middle);
        scene.overlay().showText(75)
            .attachKeyFrame()
            .colored(PonderPalette.RED)
            .text(PonderText.get("ponder.cc_aeroworks.diagnostics.text_1"))
            .pointAt(util.vector().topOf(middle))
            .placeNearTarget();
        scene.idle(85);

        scene.world().setBlock(middle, normalDesk(), false);
        scene.effects().indicateSuccess(middle);
        scene.overlay().showText(75)
            .attachKeyFrame()
            .colored(PonderPalette.GREEN)
            .text(PonderText.get("ponder.cc_aeroworks.diagnostics.text_2"))
            .pointAt(util.vector().topOf(middle))
            .placeNearTarget();
        scene.idle(85);

        scene.overlay().showText(85)
            .attachKeyFrame()
            .colored(PonderPalette.RED)
            .text(PonderText.get("ponder.cc_aeroworks.diagnostics.text_3"))
            .pointAt(util.vector().centerOf(right))
            .placeNearTarget();
        scene.idle(95);

        scene.overlay().showText(90)
            .attachKeyFrame()
            .text(PonderText.get("ponder.cc_aeroworks.diagnostics.text_4"))
            .pointAt(util.vector().topOf(computer))
            .placeNearTarget();
        scene.idle(100);

        scene.overlay().showText(95)
            .attachKeyFrame()
            .colored(PonderPalette.GREEN)
            .text(PonderText.get("ponder.cc_aeroworks.diagnostics.text_5"))
            .pointAt(util.vector().centerOf(middle))
            .placeNearTarget();
        scene.idle(105);
        scene.markAsFinished();
    }

    private static BlockState normalDesk() {
        return AeroworksTypes.INSTANCE.vanillaControlDeskBlock()
            .defaultBlockState()
            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
    }

    private static ItemStack itemStack(ResourceLocation id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(id));
    }
}
