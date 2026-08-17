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
import net.minecraft.world.level.block.state.BlockState;

public class DisplayModuleScenes {
    private static final ResourceLocation MECHANICAL_PRESS =
        ResourceLocation.fromNamespaceAndPath("create", "mechanical_press");
    private static final ResourceLocation DEPOT =
        ResourceLocation.fromNamespaceAndPath("create", "depot");
    private static final ResourceLocation NORMAL_MONITOR =
        ResourceLocation.fromNamespaceAndPath("computercraft", "monitor_normal");
    private static final ResourceLocation ADVANCED_MONITOR =
        ResourceLocation.fromNamespaceAndPath("computercraft", "monitor_advanced");

    public static void crafting(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("display_crafting", PonderText.get("display_crafting", "header"));
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos smallResultDesk = util.grid().at(0, 1, 2);
        BlockPos depot = util.grid().at(2, 1, 2);
        BlockPos press = util.grid().at(2, 2, 2);
        BlockPos largeResultDesk = util.grid().at(4, 1, 2);

        scene.world().setBlock(smallResultDesk, PonderDeskSetup.normalDesk(), false);
        scene.world().setBlock(largeResultDesk, PonderDeskSetup.normalDesk(), false);
        scene.world().setBlock(depot, blockState(DEPOT), false);
        scene.world().setBlock(press, blockState(MECHANICAL_PRESS), false);
        scene.world().showSection(util.select().fromTo(depot, press), Direction.DOWN);
        scene.idle(20);

        scene.overlay().showControls(util.vector().topOf(depot), Pointing.DOWN, 55)
            .withItem(itemStack(NORMAL_MONITOR));
        scene.overlay().showText(75)
            .attachKeyFrame()
            .text(PonderText.get("display_crafting", "text_1"))
            .pointAt(util.vector().centerOf(depot))
            .placeNearTarget();
        scene.idle(85);
        scene.effects().indicateSuccess(depot);

        PonderDeskSetup.mount(scene, smallResultDesk, 2, new ItemStack(CCItems.TWO_DIGIT_DISPLAY.get()));
        PonderDeskSetup.setDisplayText(scene, smallResultDesk, 2, "42");
        scene.world().showSection(util.select().position(smallResultDesk), Direction.DOWN);
        scene.effects().indicateSuccess(smallResultDesk);
        scene.idle(55);

        scene.overlay().showControls(util.vector().topOf(depot), Pointing.DOWN, 55)
            .withItem(itemStack(ADVANCED_MONITOR));
        scene.overlay().showText(75)
            .attachKeyFrame()
            .text(PonderText.get("display_crafting", "text_2"))
            .pointAt(util.vector().centerOf(depot))
            .placeNearTarget();
        scene.idle(85);
        scene.effects().indicateSuccess(depot);

        PonderDeskSetup.mount(scene, largeResultDesk, 2, new ItemStack(CCItems.THREE_DIGIT_DISPLAY.get()));
        PonderDeskSetup.setDisplayText(scene, largeResultDesk, 2, "123");
        scene.world().showSection(util.select().position(largeResultDesk), Direction.DOWN);
        scene.effects().indicateSuccess(largeResultDesk);
        scene.idle(55);

        scene.overlay().showText(80)
            .attachKeyFrame()
            .colored(PonderPalette.GREEN)
            .text(PonderText.get("display_crafting", "text_3"))
            .pointAt(util.vector().topOf(smallResultDesk))
            .placeNearTarget();
        scene.idle(90);

        scene.overlay().showText(85)
            .attachKeyFrame()
            .text(PonderText.get("display_crafting", "text_4"))
            .pointAt(util.vector().centerOf(press))
            .placeNearTarget();
        scene.idle(95);
        scene.markAsFinished();
    }

    public static void mounting(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("display_mounting", PonderText.get("display_mounting", "header"));
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos left = util.grid().at(1, 1, 2);
        BlockPos middle = util.grid().at(2, 1, 2);
        BlockPos right = util.grid().at(3, 1, 2);
        scene.world().setBlock(left, PonderDeskSetup.normalDesk(), false);
        scene.world().setBlock(middle, PonderDeskSetup.normalDesk(), false);
        scene.world().setBlock(right, PonderDeskSetup.normalDesk(), false);
        scene.world().showSection(util.select().fromTo(left, right), Direction.DOWN);
        scene.idle(20);

        scene.overlay().showControls(util.vector().topOf(left), Pointing.DOWN, 45)
            .withItem(CCItems.TWO_DIGIT_DISPLAY.get().getDefaultInstance())
            .rightClick();
        scene.idle(35);
        PonderDeskSetup.mount(scene, left, 0, new ItemStack(CCItems.TWO_DIGIT_DISPLAY.get()));
        PonderDeskSetup.mount(scene, left, 1, new ItemStack(CCItems.TWO_DIGIT_DISPLAY.get()));
        PonderDeskSetup.setDisplayText(scene, left, 0, "12");
        PonderDeskSetup.setDisplayText(scene, left, 1, "34");
        scene.effects().indicateSuccess(left);
        scene.overlay().showText(75)
            .attachKeyFrame()
            .colored(PonderPalette.GREEN)
            .text(PonderText.get("display_mounting", "text_1"))
            .pointAt(util.vector().topOf(left))
            .placeNearTarget();
        scene.idle(85);

        scene.overlay().showControls(util.vector().topOf(middle), Pointing.DOWN, 45)
            .withItem(CCItems.TWO_DIGIT_DISPLAY.get().getDefaultInstance())
            .rightClick();
        scene.idle(30);
        PonderDeskSetup.mount(scene, middle, 2, new ItemStack(CCItems.TWO_DIGIT_DISPLAY.get()));
        PonderDeskSetup.setDisplayText(scene, middle, 2, "56");
        scene.effects().indicateSuccess(middle);
        scene.overlay().showText(75)
            .attachKeyFrame()
            .text(PonderText.get("display_mounting", "text_2"))
            .pointAt(util.vector().topOf(middle))
            .placeNearTarget();
        scene.idle(85);

        scene.overlay().showControls(util.vector().topOf(left), Pointing.DOWN, 45)
            .withItem(CCItems.THREE_DIGIT_DISPLAY.get().getDefaultInstance())
            .rightClick();
        scene.effects().indicateRedstone(left);
        scene.overlay().showText(70)
            .attachKeyFrame()
            .colored(PonderPalette.RED)
            .text(PonderText.get("display_mounting", "text_3"))
            .pointAt(util.vector().topOf(left))
            .placeNearTarget();
        scene.idle(80);

        scene.overlay().showControls(util.vector().topOf(right), Pointing.DOWN, 45)
            .withItem(CCItems.THREE_DIGIT_DISPLAY.get().getDefaultInstance())
            .rightClick();
        scene.idle(30);
        PonderDeskSetup.mount(scene, right, 2, new ItemStack(CCItems.THREE_DIGIT_DISPLAY.get()));
        PonderDeskSetup.setDisplayText(scene, right, 2, "789");
        scene.effects().indicateSuccess(right);
        scene.overlay().showText(75)
            .attachKeyFrame()
            .colored(PonderPalette.GREEN)
            .text(PonderText.get("display_mounting", "text_4"))
            .pointAt(util.vector().topOf(right))
            .placeNearTarget();
        scene.idle(85);
        scene.markAsFinished();
    }

    public static void programming(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("display_programming", PonderText.get("display_programming", "header"));
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos computer = util.grid().at(1, 1, 2);
        BlockPos middle = util.grid().at(2, 1, 2);
        BlockPos right = util.grid().at(3, 1, 2);
        scene.world().setBlock(middle, PonderDeskSetup.normalDesk(), false);
        scene.world().setBlock(right, PonderDeskSetup.normalDesk(), false);
        PonderDeskSetup.mount(scene, right, 2, new ItemStack(CCItems.THREE_DIGIT_DISPLAY.get()));
        PonderDeskSetup.clearDisplay(scene, right, 2);
        scene.world().showSection(util.select().fromTo(computer, right), Direction.DOWN);
        scene.idle(20);

        scene.overlay().showText(70)
            .attachKeyFrame()
            .text(PonderText.get("display_programming", "text_1"))
            .pointAt(util.vector().topOf(right))
            .placeNearTarget();
        scene.idle(80);

        scene.overlay().showText(80)
            .attachKeyFrame()
            .text(PonderText.get("display_programming", "text_2"))
            .pointAt(util.vector().topOf(computer))
            .placeNearTarget();
        scene.idle(90);

        PonderDeskSetup.setDisplayText(scene, right, 2, "123");
        scene.effects().indicateSuccess(right);
        scene.overlay().showText(85)
            .attachKeyFrame()
            .colored(PonderPalette.GREEN)
            .text(PonderText.get("display_programming", "text_3"))
            .pointAt(util.vector().topOf(right))
            .placeNearTarget();
        scene.idle(95);

        PonderDeskSetup.setDisplayPattern(scene, right, 2);
        scene.effects().indicateSuccess(right);
        scene.overlay().showText(90)
            .attachKeyFrame()
            .text(PonderText.get("display_programming", "text_4"))
            .pointAt(util.vector().centerOf(right))
            .placeNearTarget();
        scene.idle(100);

        PonderDeskSetup.clearDisplay(scene, right, 2);
        scene.effects().indicateSuccess(right);
        scene.overlay().showText(90)
            .attachKeyFrame()
            .colored(PonderPalette.GREEN)
            .text(PonderText.get("display_programming", "text_5"))
            .pointAt(util.vector().topOf(right))
            .placeNearTarget();
        scene.idle(100);
        scene.markAsFinished();
    }

    private static BlockState blockState(ResourceLocation id) {
        return BuiltInRegistries.BLOCK.get(id).defaultBlockState();
    }

    private static ItemStack itemStack(ResourceLocation id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(id));
    }
}
