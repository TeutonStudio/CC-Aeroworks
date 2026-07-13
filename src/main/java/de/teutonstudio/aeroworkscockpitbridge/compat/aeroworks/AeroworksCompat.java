package de.teutonstudio.aeroworkscockpitbridge.compat.aeroworks;

import net.neoforged.fml.ModList;

public final class AeroworksCompat {
    public static final String MOD_ID = "aeroworks";

    private AeroworksCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }
}
