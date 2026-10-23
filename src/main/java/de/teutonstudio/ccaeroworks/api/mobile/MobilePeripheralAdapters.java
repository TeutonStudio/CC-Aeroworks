package de.teutonstudio.ccaeroworks.api.mobile;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/** Thread-safe public registry. Registration is expected during mod setup. */
public final class MobilePeripheralAdapters {
    private static final CopyOnWriteArrayList<MobilePeripheralAdapter> ADAPTERS = new CopyOnWriteArrayList<>();

    private MobilePeripheralAdapters() {}

    public static void register(MobilePeripheralAdapter adapter) {
        Objects.requireNonNull(adapter, "adapter");
        if (ADAPTERS.stream().anyMatch(existing -> existing.id().equals(adapter.id()))) {
            throw new IllegalArgumentException("Duplicate mobile peripheral adapter " + adapter.id());
        }
        ADAPTERS.add(adapter);
    }

    public static List<MobilePeripheralAdapter> all() {
        return List.copyOf(ADAPTERS);
    }
}
