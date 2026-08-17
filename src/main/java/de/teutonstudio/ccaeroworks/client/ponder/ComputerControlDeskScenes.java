package de.teutonstudio.ccaeroworks.client.ponder;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import de.teutonstudio.ccaeroworks.registry.CCBlocks;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class ComputerControlDeskScenes {
    private static final ResourceLocation ADVANCED_MODEM =
        ResourceLocation.fromNamespaceAndPath("computercraft", "wireless_modem_advanced");
    private static final ResourceLocation SPEAKER =
        ResourceLocation.fromNamespaceAndPath("computercraft", "speaker");

    public static void network(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("desk_network", PonderText.get("desk_network", "header"));
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos computer = util.grid().at(1, 1, 2);
        BlockPos middle = util.grid().at(2, 1, 2);
        BlockPos right = util.grid().at(3, 1, 2);

        // Real mounted modules make the scene explain the desk itself instead of substituting
        // floating inventory icons for its state.
        PonderDeskSetup.mount(scene, middle, 0, new ItemStack(CCItems.TWO_DIGIT_DISPLAY.get()));
        PonderDeskSetup.mount(scene, right, 2, new ItemStack(CCItems.THREE_DIGIT_DISPLAY.get()));
        PonderDeskSetup.setDisplayText(scene, middle, 0, "42");
        PonderDeskSetup.setDisplayText(scene, right, 2, "123");

        scene.world().showSection(util.select().position(computer), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(65)
            .attachKeyFrame()
            .colored(PonderPalette.GREEN)
            .text(PonderText.get("desk_network", "text_1"))
            .pointAt(util.vector().topOf(computer))
            .placeNearTarget();
        scene.idle(75);

        scene.world().showSection(util.select().position(middle), Direction.WEST);
        scene.idle(12);
        scene.world().showSection(util.select().position(right), Direction.WEST);
        scene.overlay().showText(75)
            .attachKeyFrame()
            .text(PonderText.get("desk_network", "text_2"))
            .pointAt(util.vector().centerOf(middle))
            .placeNearTarget();
        scene.idle(85);

        scene.effects().indicateSuccess(middle);
        scene.effects().indicateSuccess(right);
        scene.overlay().showText(75)
            .attachKeyFrame()
            .text(PonderText.get("desk_network", "text_3"))
            .pointAt(util.vector().topOf(right))
            .placeNearTarget();
        scene.idle(85);

        scene.overlay().showText(80)
            .attachKeyFrame()
            .colored(PonderPalette.GREEN)
            .text(PonderText.get("desk_network", "text_4"))
            .pointAt(util.vector().centerOf(middle))
            .placeNearTarget();
        scene.idle(90);

        scene.overlay().showControls(util.vector().topOf(right), Pointing.DOWN, 55)
            .rightClick()
            .whileSneaking();
        scene.overlay().showText(70)
            .attachKeyFrame()
            .text(PonderText.get("desk_network", "text_5"))
            .pointAt(util.vector().topOf(right))
            .placeNearTarget();
        scene.idle(80);

        scene.overlay().showText(75)
            .attachKeyFrame()
            .colored(PonderPalette.GREEN)
            .text(PonderText.get("desk_network", "text_6"))
            .pointAt(util.vector().topOf(computer))
            .placeNearTarget();
        scene.idle(85);
        scene.markAsFinished();
    }

    public static void peripheralSearch(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("peripheral_search", PonderText.get("peripheral_search", "header"));
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos computer = util.grid().at(1, 1, 2);
        BlockPos middle = util.grid().at(2, 1, 2);
        BlockPos right = util.grid().at(3, 1, 2);
        BlockPos modem = middle.south();
        BlockPos speaker = right.east();

        scene.world().setBlock(modem, blockState(ADVANCED_MODEM), false);
        scene.world().setBlock(speaker, blockState(SPEAKER), false);
        scene.world().showSection(util.select().fromTo(computer, right), Direction.DOWN);
        scene.idle(20);

        scene.world().showSection(util.select().position(modem), Direction.NORTH);
        scene.effects().indicateSuccess(modem);
        scene.overlay().showText(70)
            .attachKeyFrame()
            .text(PonderText.get("peripheral_search", "text_1"))
            .pointAt(util.vector().centerOf(modem))
            .placeNearTarget();
        scene.idle(80);

        scene.world().showSection(util.select().position(speaker), Direction.WEST);
        scene.effects().indicateSuccess(speaker);
        scene.overlay().showText(75)
            .attachKeyFrame()
            .text(PonderText.get("peripheral_search", "text_2"))
            .pointAt(util.vector().centerOf(speaker))
            .placeNearTarget();
        scene.idle(85);

        scene.effects().indicateSuccess(computer);
        scene.overlay().showText(85)
            .attachKeyFrame()
            .colored(PonderPalette.GREEN)
            .text(PonderText.get("peripheral_search", "text_3"))
            .pointAt(util.vector().topOf(computer))
            .placeNearTarget();
        scene.idle(95);

        scene.overlay().showText(95)
            .attachKeyFrame()
            .text(PonderText.get("peripheral_search", "text_4"))
            .pointAt(util.vector().centerOf(middle))
            .placeNearTarget();
        scene.idle(105);

        scene.overlay().showText(95)
            .attachKeyFrame()
            .text(PonderText.get("peripheral_search", "text_5"))
            .pointAt(util.vector().centerOf(right))
            .placeNearTarget();
        scene.idle(105);

        scene.overlay().showText(90)
            .attachKeyFrame()
            .text(PonderText.get("peripheral_search", "text_6"))
            .pointAt(util.vector().centerOf(modem))
            .placeNearTarget();
        scene.idle(100);

        scene.overlay().showText(90)
            .attachKeyFrame()
            .colored(PonderPalette.GREEN)
            .text(PonderText.get("peripheral_search", "text_7"))
            .pointAt(util.vector().topOf(computer))
            .placeNearTarget();
        scene.idle(100);
        scene.markAsFinished();
    }

    public static void diagnostics(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("desk_diagnostics", PonderText.get("desk_diagnostics", "header"));
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
            .text(PonderText.get("desk_diagnostics", "text_1"))
            .pointAt(util.vector().topOf(middle))
            .placeNearTarget();
        scene.idle(85);

        scene.world().setBlock(middle, PonderDeskSetup.normalDesk(), false);
        scene.effects().indicateSuccess(middle);
        scene.overlay().showText(75)
            .attachKeyFrame()
            .colored(PonderPalette.GREEN)
            .text(PonderText.get("desk_diagnostics", "text_2"))
            .pointAt(util.vector().topOf(middle))
            .placeNearTarget();
        scene.idle(85);

        scene.world().hideSection(util.select().position(right), Direction.UP);
        scene.effects().indicateRedstone(right);
        scene.overlay().showText(85)
            .attachKeyFrame()
            .colored(PonderPalette.RED)
            .text(PonderText.get("desk_diagnostics", "text_3"))
            .pointAt(util.vector().centerOf(right))
            .placeNearTarget();
        scene.idle(95);

        scene.world().showSection(util.select().position(right), Direction.DOWN);
        scene.effects().indicateSuccess(right);
        scene.overlay().showText(90)
            .attachKeyFrame()
            .text(PonderText.get("desk_diagnostics", "text_4"))
            .pointAt(util.vector().topOf(computer))
            .placeNearTarget();
        scene.idle(100);

        scene.overlay().showText(95)
            .attachKeyFrame()
            .colored(PonderPalette.GREEN)
            .text(PonderText.get("desk_diagnostics", "text_5"))
            .pointAt(util.vector().centerOf(middle))
            .placeNearTarget();
        scene.idle(105);
        scene.markAsFinished();
    }

    private static BlockState blockState(ResourceLocation id) {
        return BuiltInRegistries.BLOCK.get(id).defaultBlockState();
    }
}
