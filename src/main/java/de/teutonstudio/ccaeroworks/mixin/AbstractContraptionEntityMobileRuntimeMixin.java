package de.teutonstudio.ccaeroworks.mixin;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import de.teutonstudio.ccaeroworks.contraption.MobileContraptionRuntime;
import de.teutonstudio.ccaeroworks.contraption.MobileContraptionRuntimeAccess;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContraptionEntity.class)
public abstract class AbstractContraptionEntityMobileRuntimeMixin implements MobileContraptionRuntimeAccess {
    @Unique private MobileContraptionRuntime ccaeroworks$mobileRuntime;

    @Override public MobileContraptionRuntime ccaeroworks_mobileRuntime() { return ccaeroworks$mobileRuntime; }
    @Override public void ccaeroworks_setMobileRuntime(MobileContraptionRuntime runtime) {
        if (ccaeroworks$mobileRuntime != runtime && ccaeroworks$mobileRuntime != null) ccaeroworks$mobileRuntime.close();
        ccaeroworks$mobileRuntime = runtime;
    }

    @Inject(method = "contraptionInitialize", at = @At("TAIL"))
    private void ccaeroworks$initializeMobileRuntime(CallbackInfo ci) {
        ccaeroworks_setMobileRuntime(MobileContraptionRuntime.create((AbstractContraptionEntity) (Object) this));
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void ccaeroworks$tickMobileRuntime(CallbackInfo ci) {
        if (ccaeroworks$mobileRuntime != null) ccaeroworks$mobileRuntime.tick();
    }

    @Inject(method = "handlePlayerInteraction", at = @At("HEAD"), cancellable = true)
    private void ccaeroworks$openMovingTerminal(
        Player player,
        BlockPos localPos,
        Direction side,
        InteractionHand hand,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (!player.isShiftKeyDown() || ccaeroworks$mobileRuntime == null) return;
        if (ccaeroworks$mobileRuntime.openTerminal(localPos, player)) cir.setReturnValue(true);
    }

    @Inject(method = "writeAdditional", at = @At("HEAD"))
    private void ccaeroworks$saveMobileRuntime(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        if (ccaeroworks$mobileRuntime != null) ccaeroworks$mobileRuntime.save();
    }

    @Inject(method = "disassemble", at = @At("HEAD"))
    private void ccaeroworks$closeBeforeDisassembly(CallbackInfo ci) {
        if (ccaeroworks$mobileRuntime != null) {
            ccaeroworks$mobileRuntime.save();
            ccaeroworks_setMobileRuntime(null);
        }
    }

    @Inject(method = "remove", at = @At("HEAD"))
    private void ccaeroworks$closeOnRemove(RemovalReason reason, CallbackInfo ci) {
        ccaeroworks_setMobileRuntime(null);
    }

    @Inject(method = "onRemovedFromLevel", at = @At("HEAD"))
    private void ccaeroworks$closeOnUnload(CallbackInfo ci) {
        ccaeroworks_setMobileRuntime(null);
    }
}
