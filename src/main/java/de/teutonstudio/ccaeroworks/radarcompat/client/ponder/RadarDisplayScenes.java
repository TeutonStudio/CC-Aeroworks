package de.teutonstudio.ccaeroworks.radarcompat.client.ponder;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import de.teutonstudio.ccaeroworks.client.ponder.PonderDeskSetup;
import de.teutonstudio.ccaeroworks.client.ponder.PonderText;
import de.teutonstudio.ccaeroworks.radarcompat.registry.RadarItems;
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

public class RadarDisplayScenes {
    private static final ResourceLocation DATA_LINK_ID =
        ResourceLocation.fromNamespaceAndPath("create_radar", "data_link");
    private static final ResourceLocation NETWORK_FILTERER_ID =
        ResourceLocation.fromNamespaceAndPath("create_radar", "network_filterer");

    public static void controllerConnection(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("radar_controller", PonderText.get("ponder.cc_aeroworks.radar_controller.header"));
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos filterer = util.grid().at(1, 1, 2);
        BlockPos desk = util.grid().at(3, 1, 2);
        BlockPos physicalLink = desk.above();
        scene.world().setBlock(filterer, blockState(NETWORK_FILTERER_ID), false);
        scene.world().setBlock(desk, PonderDeskSetup.normalDesk(), false);
        PonderDeskSetup.mount(scene, desk, 2, new ItemStack(RadarItems.LARGE_RADAR_DISPLAY.get()));
        scene.world().showSection(util.select().position(filterer), Direction.DOWN);
        scene.world().showSection(util.select().position(desk), Direction.DOWN);
        scene.idle(20);

        scene.overlay().showText(75)
            .attachKeyFrame()
            .text(PonderText.get("ponder.cc_aeroworks.radar_controller.text_1"))
            .pointAt(util.vector().topOf(desk))
            .placeNearTarget();
        scene.idle(85);

        scene.overlay().showControls(util.vector().topOf(filterer), Pointing.DOWN, 65)
            .withItem(dataLinkStack());
        scene.overlay().showText(85)
            .attachKeyFrame()
            .text(PonderText.get("ponder.cc_aeroworks.radar_controller.text_2"))
            .pointAt(util.vector().centerOf(filterer))
            .placeNearTarget();
        scene.idle(95);
        scene.effects().indicateSuccess(filterer);

        scene.overlay().showControls(util.vector().topOf(desk), Pointing.DOWN, 65)
            .withItem(dataLinkStack());
        scene.overlay().showText(85)
            .attachKeyFrame()
            .text(PonderText.get("ponder.cc_aeroworks.radar_controller.text_3"))
            .pointAt(util.vector().centerOf(desk))
            .placeNearTarget();
        scene.idle(95);

        scene.world().setBlock(physicalLink, blockState(DATA_LINK_ID), false);
        scene.world().showSection(util.select().position(physicalLink), Direction.DOWN);
        scene.effects().indicateSuccess(physicalLink);
        scene.overlay().showText(90)
            .attachKeyFrame()
            .colored(PonderPalette.GREEN)
            .text(PonderText.get("ponder.cc_aeroworks.radar_controller.text_4"))
            .pointAt(util.vector().centerOf(physicalLink))
            .placeNearTarget();
        scene.idle(100);

        scene.effects().indicateSuccess(desk);
        scene.overlay().showText(90)
            .attachKeyFrame()
            .colored(PonderPalette.GREEN)
            .text(PonderText.get("ponder.cc_aeroworks.radar_controller.text_5"))
            .pointAt(util.vector().topOf(desk))
            .placeNearTarget();
        scene.idle(100);
        scene.markAsFinished();
    }

    public static void directRadarDisplay(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("radar_direct", PonderText.get("ponder.cc_aeroworks.radar_direct.header"));
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos linkedDesk = util.grid().at(2, 1, 2);
        BlockPos unlinkedDesk = util.grid().at(3, 1, 2);
        BlockPos physicalLink = linkedDesk.above();
        scene.world().setBlock(linkedDesk, PonderDeskSetup.normalDesk(), false);
        scene.world().setBlock(unlinkedDesk, PonderDeskSetup.normalDesk(), false);
        PonderDeskSetup.mount(scene, linkedDesk, 2, new ItemStack(RadarItems.LARGE_RADAR_DISPLAY.get()));
        PonderDeskSetup.mount(scene, unlinkedDesk, 2, new ItemStack(RadarItems.SMALL_RADAR_DISPLAY.get()));
        scene.world().setBlock(physicalLink, blockState(DATA_LINK_ID), false);
        scene.world().showSection(util.select().fromTo(linkedDesk, unlinkedDesk), Direction.DOWN);
        scene.world().showSection(util.select().position(physicalLink), Direction.DOWN);
        scene.idle(20);

        scene.effects().indicateSuccess(linkedDesk);
        scene.overlay().showText(80)
            .attachKeyFrame()
            .text(PonderText.get("ponder.cc_aeroworks.radar_direct.text_1"))
            .pointAt(util.vector().centerOf(linkedDesk))
            .placeNearTarget();
        scene.idle(90);

        scene.overlay().showText(90)
            .attachKeyFrame()
            .text(PonderText.get("ponder.cc_aeroworks.radar_direct.text_2"))
            .pointAt(util.vector().topOf(linkedDesk))
            .placeNearTarget();
        scene.idle(100);

        scene.effects().indicateRedstone(linkedDesk);
        scene.overlay().showText(90)
            .attachKeyFrame()
            .colored(PonderPalette.GREEN)
            .text(PonderText.get("ponder.cc_aeroworks.radar_direct.text_3"))
            .pointAt(util.vector().topOf(linkedDesk))
            .placeNearTarget();
        scene.idle(100);

        scene.overlay().showText(90)
            .attachKeyFrame()
            .text(PonderText.get("ponder.cc_aeroworks.radar_direct.text_4"))
            .pointAt(util.vector().topOf(unlinkedDesk))
            .placeNearTarget();
        scene.idle(100);

        scene.world().hideSection(util.select().position(physicalLink), Direction.UP);
        scene.effects().indicateRedstone(linkedDesk);
        scene.overlay().showText(95)
            .attachKeyFrame()
            .colored(PonderPalette.RED)
            .text(PonderText.get("ponder.cc_aeroworks.radar_direct.text_5"))
            .pointAt(util.vector().topOf(linkedDesk))
            .placeNearTarget();
        scene.idle(105);
        scene.markAsFinished();
    }

    private static BlockState blockState(ResourceLocation id) {
        return BuiltInRegistries.BLOCK.get(id).defaultBlockState();
    }

    private static ItemStack dataLinkStack() {
        return new ItemStack(BuiltInRegistries.ITEM.get(DATA_LINK_ID));
    }
}
