package de.teutonstudio.ccaeroworks.client.ponder;

import net.minecraft.client.resources.language.I18n;

public final class PonderText {
    private static final String PREFIX = "cc_aeroworks.ponder.";
    private static final String LEGACY_PREFIX = "ponder.cc_aeroworks.";

    private PonderText() {
    }

    public static String get(String scene, String entry, Object... arguments) {
        return I18n.get(PREFIX + scene + "." + entry, arguments);
    }

    /**
     * Compatibility bridge for radar-compat scenes and older third-party calls. The old key order
     * never matched the actual resource namespace, so normalize it before asking Minecraft.
     */
    public static String get(String key, Object... arguments) {
        String normalized = key.startsWith(LEGACY_PREFIX)
            ? PREFIX + key.substring(LEGACY_PREFIX.length())
            : key;
        return I18n.get(normalized, arguments);
    }
}
