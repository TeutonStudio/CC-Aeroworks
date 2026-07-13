package de.teutonstudio.aeroworkscockpitbridge.display;

import java.util.List;

/**
 * Immutable bridge-owned desk state. Persistence will be added after an implementation-level
 * compatibility test against Aeroworks 1.3.0.
 */
public record DeskDisplayData(List<DeskDisplaySlot> slots) {
    public static final DeskDisplayData EMPTY = new DeskDisplayData(List.of());

    public DeskDisplayData {
        slots = List.copyOf(slots);
    }
}
