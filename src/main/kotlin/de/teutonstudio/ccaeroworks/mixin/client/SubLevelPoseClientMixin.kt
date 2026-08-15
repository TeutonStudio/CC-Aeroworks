package de.teutonstudio.ccaeroworks.mixin.client

import com.mojang.blaze3d.vertex.PoseStack
import de.teutonstudio.ccaeroworks.compat.sable.SableClientRenderPose
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.phys.Vec3
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/**
 * Backports the corrected Sable render-space transform used by the working CC-Aeroworks overlays
 * to Aeroworks 1.3.0's shared client helper.
 */
@Mixin(
    targets = ["com.mred231.aeroworks.content.controls.SubLevelPoseClient"],
    remap = false
)
abstract class SubLevelPoseClientMixin {
    private companion object {
        @JvmStatic
        @Inject(
            method = ["translateTo(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/level/block/entity/BlockEntity;DDDLnet/minecraft/world/phys/Vec3;F)V"],
            at = [At("HEAD")],
            cancellable = true
        )
        private fun ccaeroworks_fixSableRenderPose(
            poseStack: PoseStack,
            blockEntity: BlockEntity,
            x: Double,
            y: Double,
            z: Double,
            camera: Vec3,
            partialTicks: Float,
            callback: CallbackInfo
        ) {
            SableClientRenderPose.apply(
                poseStack,
                blockEntity,
                x,
                y,
                z,
                camera,
                partialTicks
            )
            callback.cancel()
        }
    }
}
