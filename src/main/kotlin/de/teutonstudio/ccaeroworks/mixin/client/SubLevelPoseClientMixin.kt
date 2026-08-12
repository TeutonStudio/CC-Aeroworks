package de.teutonstudio.ccaeroworks.mixin.client

import com.mojang.blaze3d.vertex.PoseStack
import dev.ryanhcode.sable.Sable
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/**
 * Backports the corrected Sable render-space transform from Aeroworks 1.4.x to the
 * supported Aeroworks 1.3.0 runtime.
 *
 * Aeroworks 1.3.0 multiplies the complete SubLevel render-pose matrix after the
 * camera translation. That also transforms the already camera-relative origin and
 * offsets placement ghosts/outlines on translated or rotated Sable SubLevels.
 *
 * Newer Aeroworks transforms the local anchor position into world space first,
 * subtracts the camera there, and applies only the SubLevel orientation afterwards.
 * Keep this mixin scoped to Aeroworks' shared helper so normal Control Desks and
 * CC-Aeroworks Computer Control Desks receive the same correction.
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
            val subLevel = Sable.HELPER.getContainingClient(blockEntity)
            if (subLevel == null) {
                poseStack.translate(
                    x - camera.x,
                    y - camera.y,
                    z - camera.z
                )
                callback.cancel()
                return
            }

            val renderPose = subLevel.renderPose(partialTicks)
            val worldPosition = renderPose.transformPosition(Vec3(x, y, z))
            poseStack.translate(
                worldPosition.x - camera.x,
                worldPosition.y - camera.y,
                worldPosition.z - camera.z
            )
            poseStack.mulPose(Quaternionf(renderPose.orientation()))
            callback.cancel()
        }
    }
}
