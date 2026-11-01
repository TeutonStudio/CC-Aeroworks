package de.teutonstudio.ccaeroworks.mixin.client;

import com.mred231.aeroworks.content.controls.console.ConsoleControlClient;
import java.util.List;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ConsoleControlClient.class, remap = false)
public interface ConsoleControlClientAccessor {
    @Accessor("activeGroup")
    static List<BlockPos> ccaeroworks$getActiveGroup() {
        throw new AssertionError("Mixin accessor");
    }

    @Accessor("groupSocketCounts")
    static int[] ccaeroworks$getGroupSocketCounts() {
        throw new AssertionError("Mixin accessor");
    }
}
