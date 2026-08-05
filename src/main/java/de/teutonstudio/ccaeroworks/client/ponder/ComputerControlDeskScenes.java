package de.teutonstudio.ccaeroworks.client.ponder;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import dan200.computercraft.shared.ModRegistry;
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksTypes;
import de.teutonstudio.ccaeroworks.registry.CCBlocks;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class ComputerControlDeskScenes {
    private static final ResourceLocation CREATE_WRENCH_ID =
        ResourceLocation.fromNamespaceAndPath("create", "wrench");

    public static void overview(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("computer_control_desk", "Using Computer Control Desks");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos computerDesk = util.grid().at(1, 1, 2);
        BlockPos middleDesk = util.grid().at(2, 1, 2);
        BlockPos rightDesk = util.grid().at(3, 1, 2);
        Selection desks = util.select().fromTo(computerDesk, rightDesk);

        scene.world().showSection(desks, Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(70)
            .attachKeyFrame()
            .text("Same-facing desks connect left and right into one multiblock")
            .pointAt(util.vector().centerOf(middleDesk))
            .placeNearTarget();
        scene.idle(80);

        scene.overlay().showText(70)
            .attachKeyFrame()
            .colored(PonderPalette.GREEN)
            .text("One Computer Control Desk manages the entire multiblock")
            .pointAt(util.vector().topOf(computerDesk))
            .placeNearTarget();
        scene.idle(80);

        scene.overlay().showControls(util.vector().topOf(rightDesk), Pointing.DOWN, 55)
            .rightClick()
            .whileSneaking();
        scene.overlay().showText(65)
            .attachKeyFrame()
            .text("Sneak and right-click any desk with an empty main hand to open the terminal")
            .pointAt(util.vector().topOf(rightDesk))
            .placeNearTarget();
        scene.idle(75);

        scene.overlay().showControls(util.vector().topOf(middleDesk), Pointing.DOWN, 45)
            .rightClick();
        scene.overlay().showText(60)
            .attachKeyFrame()
            .text("Right-click a mounted control normally to operate it")
            .pointAt(util.vector().topOf(middleDesk))
            .placeNearTarget();
        scene.idle(70);

        scene.overlay().showControls(util.vector().blockSurface(middleDesk, Direction.SOUTH), Pointing.UP, 55)
            .rightClick()
            .withItem(createWrenchStack());
        scene.overlay().showText(65)
            .attachKeyFrame()
            .text("Right-click a horizontal desk face with a Wrench to open control settings")
            .pointAt(util.vector().blockSurface(middleDesk, Direction.SOUTH))
            .placeNearTarget();
        scene.idle(75);

        scene.overlay().showControls(util.vector().topOf(rightDesk), Pointing.DOWN, 55)
            .withItem(new ItemStack(ModRegistry.Items.COMPUTER_NORMAL.get()));
        scene.overlay().showText(70)
            .attachKeyFrame()
            .text("Alternatively, connect one external computer to any desk; no embedded computer is required")
            .pointAt(util.vector().topOf(rightDesk))
            .placeNearTarget();
        scene.idle(80);

        BlockState duplicate = CCBlocks.COMPUTER_CONTROL_DESK.get()
            .defaultBlockState()
            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        scene.world().setBlock(middleDesk, duplicate, false);
        scene.effects().indicateRedstone(middleDesk);
        scene.idle(20);
        scene.overlay().showText(65)
            .attachKeyFrame()
            .colored(PonderPalette.RED)
            .text("A multiblock can contain only one embedded computer")
            .pointAt(util.vector().topOf(middleDesk))
            .placeNearTarget();
        scene.idle(75);

        BlockState normalDesk = AeroworksTypes.INSTANCE.vanillaControlDeskBlock()
            .defaultBlockState()
            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        scene.world().setBlock(middleDesk, normalDesk, false);
        scene.world().createItemEntity(
            util.vector().topOf(middleDesk),
            util.vector().of(0, 0.12, 0),
            new ItemStack(ModRegistry.Items.COMPUTER_NORMAL.get())
        );
        scene.effects().indicateSuccess(middleDesk);
        scene.overlay().showText(70)
            .attachKeyFrame()
            .colored(PonderPalette.GREEN)
            .text("If placed accidentally, the new desk becomes normal and its computer is ejected")
            .pointAt(util.vector().topOf(middleDesk))
            .placeNearTarget();
        scene.idle(80);
        scene.markAsFinished();
    }

    private static ItemStack createWrenchStack() {
        return new ItemStack(BuiltInRegistries.ITEM.get(CREATE_WRENCH_ID));
    }
}
