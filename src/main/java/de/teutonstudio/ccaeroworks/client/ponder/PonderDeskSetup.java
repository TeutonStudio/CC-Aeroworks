package de.teutonstudio.ccaeroworks.client.ponder;

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksDeskAccess;
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksTypes;
import de.teutonstudio.ccaeroworks.display.DeskDisplayPixels;
import de.teutonstudio.ccaeroworks.display.DeskDisplayState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayList;
import java.util.List;

public final class PonderDeskSetup {
    private PonderDeskSetup() {
    }

    public static BlockState normalDesk() {
        return AeroworksTypes.INSTANCE.vanillaControlDeskBlock()
            .defaultBlockState()
            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
    }

    public static void mount(CreateSceneBuilder scene, BlockPos position, int socket, ItemStack stack) {
        scene.world().modifyBlockEntity(position, ConsoleBlockEntity.class,
            desk -> desk.mount(socket, stack.copy()));
    }

    public static void setDisplayText(CreateSceneBuilder scene, BlockPos position, int socket, String text) {
        scene.world().modifyBlockEntity(position, ConsoleBlockEntity.class,
            desk -> AeroworksDeskAccess.setDisplayText(desk, socket, text));
    }

    public static void clearDisplay(CreateSceneBuilder scene, BlockPos position, int socket) {
        setDisplayText(scene, position, socket, "");
    }

    public static void setDisplayPattern(CreateSceneBuilder scene, BlockPos position, int socket) {
        scene.world().modifyBlockEntity(position, ConsoleBlockEntity.class, desk -> {
            DeskDisplayState display = AeroworksDeskAccess.display(desk, socket);
            if (display == null) {
                return;
            }

            int width = DeskDisplayPixels.pixelWidth(display.getType());
            int height = DeskDisplayPixels.pixelHeight(display.getType());
            List<String> rows = new ArrayList<>(height);
            for (int y = 0; y < height; y++) {
                StringBuilder row = new StringBuilder(width);
                for (int x = 0; x < width; x++) {
                    boolean border = x == 0 || y == 0 || x == width - 1 || y == height - 1;
                    boolean diagonal = x * Math.max(1, height - 1) == y * Math.max(1, width - 1)
                        || (width - 1 - x) * Math.max(1, height - 1) == y * Math.max(1, width - 1);
                    row.append(border || diagonal ? '1' : '0');
                }
                rows.add(row.toString());
            }

            AeroworksDeskAccess.setDisplayPixels(
                desk,
                socket,
                DeskDisplayPixels.fromRows(display.getType(), rows)
            );
        });
    }
}
