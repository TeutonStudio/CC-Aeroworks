package de.teutonstudio.aeroworkscockpitbridge.display;

import java.util.Objects;
import java.util.Optional;

/**
 * Runtime representation only. A final NBT/Codec format is intentionally not selected yet.
 */
public record DeskDisplaySlot(int aeroworksSlotIndex, DeskDisplayType type, String text,
                              Optional<String> displayLinkChannel) {
    public DeskDisplaySlot {
        if (aeroworksSlotIndex < 0) {
            throw new IllegalArgumentException("Aeroworks slot index must not be negative");
        }
        Objects.requireNonNull(type, "type");
        text = Objects.requireNonNull(text, "text");
        displayLinkChannel = Objects.requireNonNull(displayLinkChannel, "displayLinkChannel");
    }

    public DeskDisplaySlot(int aeroworksSlotIndex, DeskDisplayType type, String text) {
        this(aeroworksSlotIndex, type, text, Optional.empty());
    }
}
