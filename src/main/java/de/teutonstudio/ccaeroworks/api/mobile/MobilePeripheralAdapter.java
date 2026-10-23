package de.teutonstudio.ccaeroworks.api.mobile;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Public Java-compatible opt-in boundary for block entities which are safe to execute while
 * mounted. Unknown block entities are intentionally ignored.
 */
public interface MobilePeripheralAdapter {
    ResourceLocation id();
    boolean matches(BlockState state, CompoundTag blockEntityData);
    MobilePeripheralInstance create(MobilePeripheralContext context) throws Exception;
}
