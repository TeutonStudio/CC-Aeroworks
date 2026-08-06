package de.teutonstudio.ccaeroworks.client.ponder;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksTypes;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

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
        scene.title("display_crafting", PonderText.get("ponder.cc_aeroworks.display_crafting.header"));
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos depot = util.grid().at(2, 1, 2);
        BlockPos press = util.grid().at(2, 2, 2);
        scene.world().setBlock(depot, blockState(DEPOT), false);
        scene.world().setBlock(press, blockState(MECHANICAL_PRESS), false);
        scene.world().showSection(util.select().fromTo(depot, press), Direction.DOWN);
        scene.idle(20);

        scene.overlay().showControls(util.vector().topOf(depot), Pointing.DOWN, 55)
            .withItem(itemStack(NORMAL_MONITOR));
        scene.overlay().showText(75)
            .attachKeyFrame()
            .text(PonderText.get("ponder.cc_aeroworks.display_crafting.text_1"))
            .pointAt(util.vector().centerOf(depot))
            .placeNearTarget();
        scene.idle(85);
        scene.effects().indicateSuccess(depot);
        scene.overlay().showControls(util.vector().topOf(depot), Pointing.DOWN, 45)
            .withItem(new ItemStack(CCItems.TWO_DIGIT_DISPLAY.get()));
        scene.idle(55);

        scene.overlay().showControls(util.vector().topOf(depot), Pointing.DOWN, 55)
            .withItem(itemStack(ADVANCED_MONITOR));
        scene.overlay().showText(75)
            .attachKeyFrame()
            .text(PonderText.get("ponder.cc_aeroworks.display_crafting.text_2"))
            .pointAt(util.vector().centerOf(depot))
            .placeNearTarget();
        scene.idle(85);
        scene.effects().indicateSuccess(depot);
        scene.overlay().showControls(util.vector().topOf(depot), Pointing.DOWN, 45)
            .withItem(new ItemStack(CCItems.THREE_DIGIT_DISPLAY.get()));
        scene.idle(55);

        scene.overlay().showText(80)
            .attachKeyFrame()
            .colored(PonderPalette.GREEN)
            .text(PonderText.get("ponder.cc_aeroworks.display_crafting.text_3"))
            .pointAt(util.vector().topOf(depot))
            .placeNearTarget();
        scene.idle(90);

        scene.overlay().showText(85)
            .attachKeyFrame()
            .text(PonderText.get("ponder.cc_aeroworks.display_crafting.text_4"))
            .pointAt(util.vector().centerOf(press))
            .placeNearTarget();
        scene.idle(95);
        scene.markAsFinished();
    }

    public static void mounting(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("display_mounting", PonderText.get("ponder.cc_aeroworks.display_mounting.header"));
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos left = util.grid().at(1, 1, 2);
        BlockPos middle = util.grid().at(2, 1, 2);
        BlockPos right = util.grid().at(3, 1, 2);
        scene.world().setBlock(left, normalDesk(), false);
        scene.world().setBlock(middle, normalDesk(), false);
        scene.world().setBlock(right, normalDesk(), false);
        scene.world().showSection(util.select().fromTo(left, right), Direction.DOWN);
        scene.idle(20);

        scene.overlay().showControls(util.vector().of(1.25, 1.9, 2.5), Pointing.DOWN, 80)
            .withItem(new ItemStack(CCItems.TWO_DIGIT_DISPLAY.get()));
        scene.overlay().showControls(util.vector().of(1.75, 1.9, 2.5), Pointing.DOWN, 80)
            .withItem(new ItemStack(CCItems.TWO_DIGIT_DISPLAY.get()));
        scene.overlay().showText(75)
            .attachKeyFrame()
            .colored(PonderPalette.GREEN)
            .text(PonderText.get("ponder.cc_aeroworks.display_mounting.text_1"))
            .pointAt(util.vector().topOf(left))
            .placeNearTarget();
        scene.idle(85);

        scene.overlay().showControls(util.vector().topOf(middle), Pointing.DOWN, 75)
            .withItem(new ItemStack(CCItems.TWO_DIGIT_DISPLAY.get()));
        scene.overlay().showText(75)
            .attachKeyFrame()
            .text(PonderText.get("ponder.cc_aeroworks.display_mounting.text_2"))
            .pointAt(util.vector().topOf(middle))
            .placeNearTarget();
        scene.idle(85);

        scene.overlay().showControls(util.vector().topOf(right), Pointing.DOWN, 80)
            .withItem(new ItemStack(CCItems.THREE_DIGIT_DISPLAY.get()));
        scene.overlay().showText(75)
            .attachKeyFrame()
            .text(PonderText.get("ponder.cc_aeroworks.display_mounting.text_3"))
            .pointAt(util.vector().topOf(right))
            .placeNearTarget();
        scene.idle(85);

        scene.effects().indicateSuccess(middle);
        scene.overlay().showText(85)
            .attachKeyFrame()
            .colored(PonderPalette.GREEN)
            .text(PonderText.get("ponder.cc_aeroworks.display_mounting.text_4"))
            .pointAt(util.vector().centerOf(middle))
            .placeNearTarget();
        scene.idle(95);
        scene.markAsFinished();
    }

    public static void programming(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("display_programming", PonderText.get("ponder.cc_aeroworks.display_programming.header"));
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos computer = util.grid().at(1, 1, 2);
        BlockPos middle = util.grid().at(2, 1, 2);
        BlockPos right = util.grid().at(3, 1, 2);
        scene.world().showSection(util.select().fromTo(computer, right), Direction.DOWN);
        scene.idle(20);

        scene.overlay().showControls(util.vector().topOf(right), Pointing.DOWN, 70)
            .withItem(new ItemStack(CCItems.THREE_DIGIT_DISPLAY.get()));
        scene.overlay().showText(75)
            .attachKeyFrame()
            .text(PonderText.get("ponder.cc_aeroworks.display_programming.text_1"))
            .pointAt(util.vector().topOf(right))
            .placeNearTarget();
        scene.idle(85);

        scene.overlay().showText(95)
            .attachKeyFrame()
            .text(PonderText.get("ponder.cc_aeroworks.display_programming.text_2"))
            .pointAt(util.vector().topOf(computer))
            .placeNearTarget();
        scene.idle(105);

        scene.overlay().showText(95)
            .attachKeyFrame()
            .text(PonderText.get("ponder.cc_aeroworks.display_programming.text_3"))
            .pointAt(util.vector().centerOf(right))
            .placeNearTarget();
        scene.idle(105);

        scene.overlay().showText(90)
            .attachKeyFrame()
            .text(PonderText.get("ponder.cc_aeroworks.display_programming.text_4"))
            .pointAt(util.vector().centerOf(middle))
            .placeNearTarget();
        scene.idle(100);

        scene.effects().indicateSuccess(right);
        scene.overlay().showText(90)
            .attachKeyFrame()
            .colored(PonderPalette.GREEN)
            .text(PonderText.get("ponder.cc_aeroworks.display_programming.text_5"))
            .pointAt(util.vector().topOf(right))
            .placeNearTarget();
        scene.idle(100);
        scene.markAsFinished();
    }

    private static BlockState normalDesk() {
        return AeroworksTypes.INSTANCE.vanillaControlDeskBlock()
            .defaultBlockState()
            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
    }

    private static BlockState blockState(ResourceLocation id) {
        return BuiltInRegistries.BLOCK.get(id).defaultBlockState();
    }

    private static ItemStack itemStack(ResourceLocation id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(id));
    }
}
