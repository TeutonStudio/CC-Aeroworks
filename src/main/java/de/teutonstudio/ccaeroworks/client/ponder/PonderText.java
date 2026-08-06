package de.teutonstudio.ccaeroworks.client.ponder;

import net.minecraft.client.resources.language.I18n;

final class PonderText {
    private PonderText() {
    }

    static String get(String key, Object... arguments) {
        return I18n.get(key, arguments);
    }
}
