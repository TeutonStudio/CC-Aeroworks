package de.teutonstudio.ccaeroworks.client.ponder;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import de.teutonstudio.ccaeroworks.registry.CCItems;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class RadarDisplayScenes {
    private static final ResourceLocation DATA_LINK_ID =
        ResourceLocation.fromNamespaceAndPath("create_radar", "data_link");

    public static void dataLink(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("radar_display", "Using Radar Displays");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos computerDesk = util.grid().at(1, 1, 2);
        BlockPos rightDesk = util.grid().at(3, 1, 2);
        Selection desks = util.select().fromTo(computerDesk, rightDesk);
        scene.world().showSection(desks, Direction.DOWN);
        scene.idle(15);

        scene.overlay().showControls(util.vector().topOf(computerDesk), Pointing.DOWN, 55)
            .withItem(new ItemStack(CCItems.SMALL_RADAR_DISPLAY.get()));
        scene.overlay().showText(65)
            .attachKeyFrame()
            .text("Mount a small or large Radar Display in a compatible control desk socket")
            .pointAt(util.vector().topOf(computerDesk))
            .placeNearTarget();
        scene.idle(75);

        scene.world().createItemEntity(
            util.vector().topOf(computerDesk),
            util.vector().of(0.05, 0.12, 0),
            new ItemStack(CCItems.LARGE_RADAR_DISPLAY.get())
        );
        scene.overlay().showControls(util.vector().blockSurface(computerDesk, Direction.WEST), Pointing.RIGHT, 55)
            .withItem(dataLinkStack());
        scene.overlay().showText(70)
            .attachKeyFrame()
            .colored(PonderPalette.RED)
            .text("A Create: Radars Data Link must use the control desk as its source")
            .pointAt(util.vector().blockSurface(computerDesk, Direction.WEST))
            .placeNearTarget();
        scene.idle(80);

        scene.overlay().showControls(util.vector().topOf(rightDesk), Pointing.DOWN, 55)
            .withItem(dataLinkStack());
        scene.overlay().showText(75)
            .attachKeyFrame()
            .text("Connect the other end to a Create: Radars monitor that belongs to a working radar network")
            .pointAt(util.vector().topOf(rightDesk))
            .placeNearTarget();
        scene.idle(85);

        scene.effects().indicateSuccess(computerDesk);
        scene.overlay().showText(70)
            .attachKeyFrame()
            .colored(PonderPalette.GREEN)
            .text("The display receives nearby tracks automatically and marks the selected contact")
            .pointAt(util.vector().topOf(computerDesk))
            .placeNearTarget();
        scene.idle(80);

        scene.effects().indicateRedstone(computerDesk);
        scene.overlay().showText(70)
            .attachKeyFrame()
            .colored(PonderPalette.RED)
            .text("An X means that no fresh Data Link radar signal is available")
            .pointAt(util.vector().topOf(computerDesk))
            .placeNearTarget();
        scene.idle(80);
        scene.markAsFinished();
    }

    private static ItemStack dataLinkStack() {
        return new ItemStack(BuiltInRegistries.ITEM.get(DATA_LINK_ID));
    }
}
