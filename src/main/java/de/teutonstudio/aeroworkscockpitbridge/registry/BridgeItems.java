package de.teutonstudio.aeroworkscockpitbridge.registry;

import de.teutonstudio.aeroworkscockpitbridge.AeroworksCockpitBridge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class BridgeItems {
    private static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(AeroworksCockpitBridge.MOD_ID);

    private BridgeItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
