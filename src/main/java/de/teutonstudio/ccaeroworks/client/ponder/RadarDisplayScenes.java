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
    private static final ResourceLocation NETWORK_CONTROLLER_ID =
        ResourceLocation.fromNamespaceAndPath("create_radar", "network_filterer");

    public static void controllerConnection(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("radar_controller", PonderText.get("ponder.cc_aeroworks.radar_controller.header"));
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos sourceDesk = util.grid().at(2, 1, 2);
        BlockPos displayDesk = util.grid().at(3, 1, 2);
        scene.world().showSection(util.select().fromTo(sourceDesk, displayDesk), Direction.DOWN);
        scene.idle(20);

        scene.overlay().showControls(util.vector().topOf(displayDesk), Pointing.DOWN, 60)
            .withItem(new ItemStack(CCItems.LARGE_RADAR_DISPLAY.get()));
        scene.overlay().showText(70)
            .attachKeyFrame()
            .text(PonderText.get("ponder.cc_aeroworks.radar_controller.text_1"))
            .pointAt(util.vector().topOf(displayDesk))
            .placeNearTarget();
        scene.idle(80);

        scene.world().createItemEntity(
            util.vector().topOf(sourceDesk),
            util.vector().of(0.05, 0.12, 0),
            networkControllerStack()
        );
        scene.overlay().showText(80)
            .attachKeyFrame()
            .text(PonderText.get("ponder.cc_aeroworks.radar_controller.text_2"))
            .pointAt(util.vector().topOf(sourceDesk))
            .placeNearTarget();
        scene.idle(90);

        scene.overlay().showControls(util.vector().topOf(sourceDesk), Pointing.DOWN, 60)
            .withItem(dataLinkStack());
        scene.overlay().showText(80)
            .attachKeyFrame()
            .text(PonderText.get("ponder.cc_aeroworks.radar_controller.text_3"))
            .pointAt(util.vector().topOf(sourceDesk))
            .placeNearTarget();
        scene.idle(90);

        scene.effects().indicateSuccess(sourceDesk);
        scene.overlay().showText(80)
            .attachKeyFrame()
            .text(PonderText.get("ponder.cc_aeroworks.radar_controller.text_4"))
            .pointAt(util.vector().centerOf(sourceDesk))
            .placeNearTarget();
        scene.idle(90);

        scene.effects().indicateSuccess(displayDesk);
        scene.overlay().showText(90)
            .attachKeyFrame()
            .colored(PonderPalette.GREEN)
            .text(PonderText.get("ponder.cc_aeroworks.radar_controller.text_5"))
            .pointAt(util.vector().topOf(displayDesk))
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

        BlockPos leftDesk = util.grid().at(1, 1, 2);
        BlockPos sourceDesk = util.grid().at(2, 1, 2);
        BlockPos rightDesk = util.grid().at(3, 1, 2);
        scene.world().showSection(util.select().fromTo(leftDesk, rightDesk), Direction.DOWN);
        scene.idle(20);

        scene.effects().indicateSuccess(sourceDesk);
        scene.overlay().showText(80)
            .attachKeyFrame()
            .text(PonderText.get("ponder.cc_aeroworks.radar_direct.text_1"))
            .pointAt(util.vector().centerOf(sourceDesk))
            .placeNearTarget();
        scene.idle(90);

        scene.effects().indicateSuccess(rightDesk);
        scene.overlay().showText(80)
            .attachKeyFrame()
            .colored(PonderPalette.GREEN)
            .text(PonderText.get("ponder.cc_aeroworks.radar_direct.text_2"))
            .pointAt(util.vector().topOf(rightDesk))
            .placeNearTarget();
        scene.idle(90);

        scene.overlay().showText(80)
            .attachKeyFrame()
            .text(PonderText.get("ponder.cc_aeroworks.radar_direct.text_3"))
            .pointAt(util.vector().centerOf(leftDesk))
            .placeNearTarget();
        scene.idle(90);

        scene.overlay().showControls(util.vector().topOf(leftDesk), Pointing.DOWN, 60)
            .withItem(new ItemStack(CCItems.SMALL_RADAR_DISPLAY.get()));
        scene.overlay().showText(85)
            .attachKeyFrame()
            .text(PonderText.get("ponder.cc_aeroworks.radar_direct.text_4"))
            .pointAt(util.vector().topOf(leftDesk))
            .placeNearTarget();
        scene.idle(95);

        scene.effects().indicateRedstone(rightDesk);
        scene.overlay().showText(85)
            .attachKeyFrame()
            .colored(PonderPalette.RED)
            .text(PonderText.get("ponder.cc_aeroworks.radar_direct.text_5"))
            .pointAt(util.vector().topOf(rightDesk))
            .placeNearTarget();
        scene.idle(95);
        scene.markAsFinished();
    }

    private static ItemStack dataLinkStack() {
        return new ItemStack(BuiltInRegistries.ITEM.get(DATA_LINK_ID));
    }

    private static ItemStack networkControllerStack() {
        return new ItemStack(BuiltInRegistries.ITEM.get(NETWORK_CONTROLLER_ID));
    }
}
