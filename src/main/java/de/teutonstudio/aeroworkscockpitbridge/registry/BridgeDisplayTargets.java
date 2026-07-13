package de.teutonstudio.aeroworkscockpitbridge.registry;

import net.neoforged.bus.api.IEventBus;

/**
 * Registration boundary for Create display targets.
 *
 * <p>The verified Create 6.0.10 API is documented in the JAR research report. Registration is
 * deliberately deferred until the exact local Create and Aeroworks development dependencies are
 * wired into Gradle, so this class contains no unverified foreign-mod imports.</p>
 */
public final class BridgeDisplayTargets {
    private BridgeDisplayTargets() {
    }

    public static void register(IEventBus modEventBus) {
        // Intentionally empty during the research/bootstrap phase.
    }
}
