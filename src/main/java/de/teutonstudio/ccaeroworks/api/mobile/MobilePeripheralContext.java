package de.teutonstudio.ccaeroworks.api.mobile;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Live, server-side view of one explicitly adapted block in a Create contraption. */
public interface MobilePeripheralContext {
    AbstractContraptionEntity entity();
    ServerLevel level();
    BlockPos localPosition();
    BlockPos worldPosition();
    BlockState blockState();
    CompoundTag blockEntityData();
    void setBlockEntityData(CompoundTag data);
}
