package de.teutonstudio.ccaeroworks.client.ponder;

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

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
}
