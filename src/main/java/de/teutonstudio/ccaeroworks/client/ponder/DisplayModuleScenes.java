package de.teutonstudio.ccaeroworks.client.ponder;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksTypes;
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

    public static void overview(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("display_modules", "Obtaining and arranging desk displays");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos leftDesk = util.grid().at(1, 1, 2);
        BlockPos middleDesk = util.grid().at(2, 1, 2);
        BlockPos rightDesk = util.grid().at(3, 1, 2);
        BlockPos press = util.grid().at(2, 2, 2);
        Selection machine = util.select().fromTo(middleDesk, press);

        scene.world().setBlock(middleDesk, blockState(DEPOT), false);
        scene.world().setBlock(press, blockState(MECHANICAL_PRESS), false);
        scene.world().showSection(machine, Direction.DOWN);
        scene.idle(15);

        scene.overlay().showControls(util.vector().topOf(middleDesk), Pointing.DOWN, 55)
            .withItem(itemStack(NORMAL_MONITOR));
        scene.overlay().showText(70)
            .attachKeyFrame()
            .text("A Normal Monitor becomes a two-digit display under a Mechanical Press")
            .pointAt(util.vector().centerOf(middleDesk))
            .placeNearTarget();
        scene.idle(75);
        scene.effects().indicateSuccess(middleDesk);
        scene.overlay().showControls(util.vector().topOf(middleDesk), Pointing.DOWN, 45)
            .withItem(new ItemStack(CCItems.TWO_DIGIT_DISPLAY.get()));
        scene.idle(55);

        scene.overlay().showControls(util.vector().topOf(middleDesk), Pointing.DOWN, 55)
            .withItem(itemStack(ADVANCED_MONITOR));
        scene.overlay().showText(70)
            .attachKeyFrame()
            .text("An Advanced Monitor becomes the three-digit display in the same press")
            .pointAt(util.vector().centerOf(middleDesk))
            .placeNearTarget();
        scene.idle(75);
        scene.effects().indicateSuccess(middleDesk);
        scene.overlay().showControls(util.vector().topOf(middleDesk), Pointing.DOWN, 45)
            .withItem(new ItemStack(CCItems.THREE_DIGIT_DISPLAY.get()));
        scene.idle(55);

        BlockState normalDesk = AeroworksTypes.INSTANCE.vanillaControlDeskBlock()
            .defaultBlockState()
            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        scene.world().setBlock(press, Blocks.AIR.defaultBlockState(), false);
        scene.world().setBlock(leftDesk, normalDesk, false);
        scene.world().setBlock(middleDesk, normalDesk, false);
        scene.world().setBlock(rightDesk, normalDesk, false);
        Selection desks = util.select().fromTo(leftDesk, rightDesk);
        scene.world().showSection(desks, Direction.DOWN);
        scene.idle(20);

        scene.overlay().showControls(util.vector().of(1.25, 1.9, 2.5), Pointing.DOWN, 90)
            .withItem(new ItemStack(CCItems.TWO_DIGIT_DISPLAY.get()));
        scene.overlay().showControls(util.vector().of(1.75, 1.9, 2.5), Pointing.DOWN, 90)
            .withItem(new ItemStack(CCItems.TWO_DIGIT_DISPLAY.get()));
        scene.overlay().showText(75)
            .attachKeyFrame()
            .colored(PonderPalette.GREEN)
            .text("Two small displays can occupy the left and right sockets together")
            .pointAt(util.vector().topOf(leftDesk))
            .placeNearTarget();
        scene.idle(85);

        scene.overlay().showControls(util.vector().topOf(middleDesk), Pointing.DOWN, 85)
            .withItem(new ItemStack(CCItems.TWO_DIGIT_DISPLAY.get()));
        scene.overlay().showText(75)
            .attachKeyFrame()
            .text("A small display also fits the big socket, so it can be arranged in any desk socket")
            .pointAt(util.vector().topOf(middleDesk))
            .placeNearTarget();
        scene.idle(85);

        scene.overlay().showControls(util.vector().topOf(rightDesk), Pointing.DOWN, 95)
            .withItem(new ItemStack(CCItems.THREE_DIGIT_DISPLAY.get()));
        scene.overlay().showText(80)
            .attachKeyFrame()
            .text("The large display fits only the big socket and provides one three-character surface")
            .pointAt(util.vector().topOf(rightDesk))
            .placeNearTarget();
        scene.idle(90);

        scene.overlay().showText(90)
            .attachKeyFrame()
            .colored(PonderPalette.GREEN)
            .text("Two small displays provide 70 pixels in total; one large display provides 55 pixels")
            .pointAt(util.vector().centerOf(leftDesk))
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
