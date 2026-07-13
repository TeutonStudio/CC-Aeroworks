package de.teutonstudio.aeroworkscockpitbridge.compat.aeroworks;

import de.teutonstudio.aeroworkscockpitbridge.display.DeskDisplayData;

/**
 * Bridge-owned view of display data associated with one Aeroworks desk.
 *
 * <p>This interface intentionally does not expose Aeroworks classes. The verified public
 * Aeroworks module API can be connected in the implementation phase without making persisted
 * bridge data depend on private foreign-mod fields.</p>
 */
public interface AeroworksDeskAccess {
    DeskDisplayData aeroworksCockpitBridge$getDisplayData();
}
