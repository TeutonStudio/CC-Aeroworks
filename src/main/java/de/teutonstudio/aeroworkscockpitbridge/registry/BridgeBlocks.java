package de.teutonstudio.aeroworkscockpitbridge.registry;

import de.teutonstudio.aeroworkscockpitbridge.AeroworksCockpitBridge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class BridgeBlocks {
    private static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(AeroworksCockpitBridge.MOD_ID);

    private BridgeBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
