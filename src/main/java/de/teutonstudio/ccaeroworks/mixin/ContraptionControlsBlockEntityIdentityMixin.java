package de.teutonstudio.ccaeroworks.mixin;

import com.simibubi.create.content.contraptions.actors.contraptionControls.ContraptionControlsBlockEntity;
import com.mred231.aeroworks.content.controls.console.DeskSkin;
import de.teutonstudio.ccaeroworks.contraption.ContraptionControlIdentityAccess;
import de.teutonstudio.ccaeroworks.multiblock.ConsoleAssemblyBlockEntity;
import de.teutonstudio.ccaeroworks.multiblock.ConsoleAssemblyStateAccess;
import de.teutonstudio.ccaeroworks.multiblock.ConsoleAssemblyStyleAccess;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds a stable ID without replacing any of Create's filter/power serialization. */
@Mixin(ContraptionControlsBlockEntity.class)
public abstract class ContraptionControlsBlockEntityIdentityMixin implements ContraptionControlIdentityAccess, ConsoleAssemblyStateAccess, ConsoleAssemblyStyleAccess {
    @Unique private static final String CCAEROWORKS_ID_TAG = "CCAeroworksContraptionControlId";
    @Unique private UUID ccaeroworks$contraptionControlId;
    @Unique private UUID ccaeroworks$assemblyId;
    @Unique private boolean ccaeroworks$legacyAssembly;
    @Unique private final DeskSkin ccaeroworks$assemblySkin = new DeskSkin();
    @Unique private long ccaeroworks$styleRevision;

    @Override public UUID ccaeroworks_getAssemblyId() { return ccaeroworks$assemblyId; }
    @Override public void ccaeroworks_setAssemblyId(UUID id) { ccaeroworks$assemblyId = id; }
    @Override public boolean ccaeroworks_isLegacyAssembly() { return ccaeroworks$legacyAssembly; }
    @Override public void ccaeroworks_setLegacyAssembly(boolean legacy) { ccaeroworks$legacyAssembly = legacy; }
    @Override public DeskSkin ccaeroworks_getAssemblySkin() { return ccaeroworks$assemblySkin; }
    @Override public long ccaeroworks_getStyleRevision() { return ccaeroworks$styleRevision; }
    @Override public void ccaeroworks_setStyleRevision(long revision) { ccaeroworks$styleRevision = revision; }

    @Override
    public UUID ccaeroworks_getContraptionControlId() {
        if (ccaeroworks$contraptionControlId == null) {
            ccaeroworks$contraptionControlId = UUID.randomUUID();
        }
        return ccaeroworks$contraptionControlId;
    }

    @Inject(method = "read", at = @At("TAIL"))
    private void ccaeroworks$readIdentity(
        CompoundTag tag,
        HolderLookup.Provider registries,
        boolean clientPacket,
        CallbackInfo ci
    ) {
        if (tag.hasUUID(CCAEROWORKS_ID_TAG)) {
            ccaeroworks$contraptionControlId = tag.getUUID(CCAEROWORKS_ID_TAG);
        }
        ccaeroworks$assemblyId = tag.hasUUID(ConsoleAssemblyBlockEntity.NBT_ASSEMBLY_ID)
            ? tag.getUUID(ConsoleAssemblyBlockEntity.NBT_ASSEMBLY_ID) : null;
        ccaeroworks$legacyAssembly = tag.getBoolean(ConsoleAssemblyBlockEntity.NBT_LEGACY);
        ccaeroworks$styleRevision = tag.getLong(ConsoleAssemblyBlockEntity.NBT_STYLE_REVISION);
        ccaeroworks$assemblySkin.read(tag, registries.lookupOrThrow(Registries.BLOCK), registries);
    }

    @Inject(method = "write", at = @At("TAIL"))
    private void ccaeroworks$writeIdentity(
        CompoundTag tag,
        HolderLookup.Provider registries,
        boolean clientPacket,
        CallbackInfo ci
    ) {
        tag.putUUID(CCAEROWORKS_ID_TAG, ccaeroworks_getContraptionControlId());
        if (ccaeroworks$assemblyId != null) {
            tag.putUUID(ConsoleAssemblyBlockEntity.NBT_ASSEMBLY_ID, ccaeroworks$assemblyId);
        }
        tag.putBoolean(ConsoleAssemblyBlockEntity.NBT_LEGACY, ccaeroworks$legacyAssembly);
        tag.putLong(ConsoleAssemblyBlockEntity.NBT_STYLE_REVISION, ccaeroworks$styleRevision);
        BlockEntity self = (BlockEntity)(Object)this;
        tag.putString(ConsoleAssemblyBlockEntity.NBT_PROFILE, BuiltInRegistries.BLOCK.getKey(self.getBlockState().getBlock()).toString());
        ccaeroworks$assemblySkin.write(tag, registries);
    }

    public ModelData getModelData() {
        if (ccaeroworks$assemblySkin.isEmpty()) return ModelData.EMPTY;
        ModelData.Builder builder = ModelData.builder();
        for (DeskSkin.Zone zone : DeskSkin.Zone.values()) {
            var material = ccaeroworks$assemblySkin.material(zone);
            if (material != null) builder.with(zone.property(), material);
        }
        return builder.build();
    }
}
