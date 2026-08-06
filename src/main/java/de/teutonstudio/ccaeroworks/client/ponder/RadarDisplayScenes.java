package de.teutonstudio.ccaeroworks.client.ponder;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import de.teutonstudio.ccaeroworks.registry.CCItems;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class RadarDisplayScenes {
    private static final ResourceLocation DATA_LINK_ID =
        ResourceLocation.fromNamespaceAndPath("create_radar", "data_link");
    private static final ResourceLocation MONITOR_ID =
        ResourceLocation.fromNamespaceAndPath("create_radar", "monitor");

    public static void automaticRouting(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("radar_routing", PonderText.get("ponder.cc_aeroworks.radar_routing.header"));
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos computer = util.grid().at(1, 1, 2);
        BlockPos sourceDesk = util.grid().at(2, 1, 2);
        BlockPos displayDesk = util.grid().at(3, 1, 2);
        scene.world().showSection(util.select().fromTo(computer, displayDesk), Direction.DOWN);
        scene.idle(20);

        scene.overlay().showControls(util.vector().topOf(displayDesk), Pointing.DOWN, 65)
            .withItem(new ItemStack(CCItems.LARGE_RADAR_DISPLAY.get()));
        scene.overlay().showText(75)
            .attachKeyFrame()
            .text(PonderText.get("ponder.cc_aeroworks.radar_routing.text_1"))
            .pointAt(util.vector().topOf(displayDesk))
            .placeNearTarget();
        scene.idle(85);

        scene.overlay().showControls(util.vector().blockSurface(sourceDesk, Direction.SOUTH), Pointing.UP, 65)
            .withItem(dataLinkStack());
        scene.overlay().showText(80)
            .attachKeyFrame()
            .text(PonderText.get("ponder.cc_aeroworks.radar_routing.text_2"))
            .pointAt(util.vector().blockSurface(sourceDesk, Direction.SOUTH))
            .placeNearTarget();
        scene.idle(90);

        scene.effects().indicateSuccess(computer);
        scene.overlay().showText(85)
            .attachKeyFrame()
            .colored(PonderPalette.GREEN)
            .text(PonderText.get("ponder.cc_aeroworks.radar_routing.text_3"))
            .pointAt(util.vector().topOf(computer))
            .placeNearTarget();
        scene.idle(95);

        scene.effects().indicateSuccess(displayDesk);
        scene.overlay().showText(90)
            .attachKeyFrame()
            .colored(PonderPalette.GREEN)
            .text(PonderText.get("ponder.cc_aeroworks.radar_routing.text_4"))
            .pointAt(util.vector().topOf(displayDesk))
            .placeNearTarget();
        scene.idle(100);

        scene.overlay().showText(90)
            .attachKeyFrame()
            .colored(PonderPalette.RED)
            .text(PonderText.get("ponder.cc_aeroworks.radar_routing.text_5"))
            .pointAt(util.vector().centerOf(sourceDesk))
            .placeNearTarget();
        scene.idle(100);

        scene.overlay().showText(95)
            .attachKeyFrame()
            .text(PonderText.get("ponder.cc_aeroworks.radar_routing.text_6"))
            .pointAt(util.vector().centerOf(displayDesk))
            .placeNearTarget();
        scene.idle(105);
        scene.markAsFinished();
    }

    public static void dataLinkCompatibility(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("radar_data_link", PonderText.get("ponder.cc_aeroworks.radar_data_link.header"));
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos computer = util.grid().at(1, 1, 2);
        BlockPos sourceDesk = util.grid().at(2, 1, 2);
        BlockPos displayDesk = util.grid().at(3, 1, 2);
        scene.world().showSection(util.select().fromTo(computer, displayDesk), Direction.DOWN);
        scene.idle(20);

        scene.world().createItemEntity(
            util.vector().topOf(displayDesk),
            util.vector().of(0.05, 0.12, 0),
            monitorStack()
        );
        scene.overlay().showControls(util.vector().topOf(displayDesk), Pointing.DOWN, 65)
            .withItem(dataLinkStack());
        scene.overlay().showText(80)
            .attachKeyFrame()
            .text(PonderText.get("ponder.cc_aeroworks.radar_data_link.text_1"))
            .pointAt(util.vector().topOf(displayDesk))
            .placeNearTarget();
        scene.idle(90);

        scene.overlay().showControls(util.vector().blockSurface(sourceDesk, Direction.SOUTH), Pointing.UP, 65)
            .withItem(dataLinkStack());
        scene.overlay().showText(85)
            .attachKeyFrame()
            .text(PonderText.get("ponder.cc_aeroworks.radar_data_link.text_2"))
            .pointAt(util.vector().blockSurface(sourceDesk, Direction.SOUTH))
            .placeNearTarget();
        scene.idle(95);

        scene.effects().indicateSuccess(sourceDesk);
        scene.overlay().showText(85)
            .attachKeyFrame()
            .colored(PonderPalette.GREEN)
            .text(PonderText.get("ponder.cc_aeroworks.radar_data_link.text_3"))
            .pointAt(util.vector().centerOf(sourceDesk))
            .placeNearTarget();
        scene.idle(95);

        scene.effects().indicateSuccess(displayDesk);
        scene.overlay().showText(85)
            .attachKeyFrame()
            .colored(PonderPalette.GREEN)
            .text(PonderText.get("ponder.cc_aeroworks.radar_data_link.text_4"))
            .pointAt(util.vector().topOf(displayDesk))
            .placeNearTarget();
        scene.idle(95);

        scene.effects().indicateRedstone(displayDesk);
        scene.overlay().showText(85)
            .attachKeyFrame()
            .colored(PonderPalette.RED)
            .text(PonderText.get("ponder.cc_aeroworks.radar_data_link.text_5"))
            .pointAt(util.vector().topOf(displayDesk))
            .placeNearTarget();
        scene.idle(95);
        scene.markAsFinished();
    }

    private static ItemStack dataLinkStack() {
        return new ItemStack(BuiltInRegistries.ITEM.get(DATA_LINK_ID));
    }

    private static ItemStack monitorStack() {
        return new ItemStack(BuiltInRegistries.ITEM.get(MONITOR_ID));
    }
}
