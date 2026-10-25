package de.teutonstudio.ccaeroworks.ctfcompat.mixin;

import createtrainfunctionality.DashboardControllerBlockEntity;
import de.teutonstudio.ccaeroworks.ctfcompat.DashboardControllerIdentityAccess;
import de.teutonstudio.ccaeroworks.ctfcompat.computer.DashboardControllerPeripheralRegistry;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DashboardControllerBlockEntity.class, remap = false)
public abstract class DashboardControllerBlockEntityMixin implements DashboardControllerIdentityAccess {
    @Unique private static final String CCAEROWORKS_ID_TAG = "CCAeroworksDashboardControllerId";
    @Unique private UUID ccaeroworks$dashboardControllerId;

    @Override
    public UUID ccaeroworks_getDashboardControllerId() {
        if (ccaeroworks$dashboardControllerId == null) ccaeroworks$dashboardControllerId = UUID.randomUUID();
        return ccaeroworks$dashboardControllerId;
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void ccaeroworks$readIdentity(
        CompoundTag tag,
        HolderLookup.Provider registries,
        CallbackInfo callback
    ) {
        if (tag.hasUUID(CCAEROWORKS_ID_TAG)) ccaeroworks$dashboardControllerId = tag.getUUID(CCAEROWORKS_ID_TAG);
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void ccaeroworks$writeIdentity(
        CompoundTag tag,
        HolderLookup.Provider registries,
        CallbackInfo callback
    ) {
        tag.putUUID(CCAEROWORKS_ID_TAG, ccaeroworks_getDashboardControllerId());
    }

    @Inject(method = {"setRemoved", "onChunkUnloaded"}, at = @At("HEAD"))
    private void ccaeroworks$resetRuntimeState(CallbackInfo callback) {
        DashboardControllerPeripheralRegistry.reset((DashboardControllerBlockEntity)(Object)this);
    }
}
