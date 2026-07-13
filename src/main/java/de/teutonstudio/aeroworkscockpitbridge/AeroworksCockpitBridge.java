package de.teutonstudio.aeroworkscockpitbridge;

import de.teutonstudio.aeroworkscockpitbridge.registry.BridgeBlockEntities;
import de.teutonstudio.aeroworkscockpitbridge.registry.BridgeBlocks;
import de.teutonstudio.aeroworkscockpitbridge.registry.BridgeDisplayTargets;
import de.teutonstudio.aeroworkscockpitbridge.registry.BridgeItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(AeroworksCockpitBridge.MOD_ID)
public final class AeroworksCockpitBridge {
    public static final String MOD_ID = "aeroworks_cockpit_bridge";

    public AeroworksCockpitBridge(IEventBus modEventBus, ModContainer modContainer) {
        BridgeBlocks.register(modEventBus);
        BridgeItems.register(modEventBus);
        BridgeBlockEntities.register(modEventBus);
        BridgeDisplayTargets.register(modEventBus);
    }
}
