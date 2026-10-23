package de.teutonstudio.ccaeroworks.api.mobile;

import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.nbt.CompoundTag;

/** Runtime object returned by a MobilePeripheralAdapter. */
public interface MobilePeripheralInstance extends AutoCloseable {
    IPeripheral peripheral();

    default void tick(MobilePeripheralContext context) {}
    default void save(MobilePeripheralContext context) {}
    default CompoundTag clientSync(MobilePeripheralContext context) { return new CompoundTag(); }
    default void setActorOutput(String port, int value) {
        throw new UnsupportedOperationException("Actor port is unavailable");
    }
    default void releaseActorOutputs() {}
    @Override default void close() {}
}
