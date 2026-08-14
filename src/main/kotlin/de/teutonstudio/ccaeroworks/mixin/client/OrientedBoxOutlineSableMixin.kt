package de.teutonstudio.ccaeroworks.mixin.client

import com.mojang.blaze3d.vertex.PoseStack
import de.teutonstudio.ccaeroworks.compat.sable.SableClientRenderPose
import de.teutonstudio.ccaeroworks.compat.sable.SableControlOutlineBridge
import dev.ryanhcode.sable.Sable
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.Redirect
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/**
 * Repairs the precision loss in Aeroworks 1.3.0 placement/removal outlines on Sable.
 *
 * Aeroworks builds each MountSpot with an exact Vec3 center and a Matrix4f whose
 * translation is the same center cast to float. OrientedBoxOutline stores that exact
 * Matrix4f object, extracts the lossy float translation in render(), and only then calls
 * SubLevelPoseClient.translateTo(). We bind the constructor's frame identity back to its
 * precise MountSpot.center and replace only that translation. Aeroworks keeps ownership
 * of the local bounds, socket/desk rotation, color and line rendering.
 */
@Mixin(
    targets = ["com.mred231.aeroworks.content.controls.OrientedBoxOutline"],
    remap = false
)
abstract class OrientedBoxOutlineSableMixin {
    @Unique
    private var ccaeroworks_preciseSableFrame: SableControlOutlineBridge.Frame? = null

    @Inject(
        method = ["<init>(Lnet/minecraft/world/phys/AABB;Lorg/joml/Matrix4f;Lnet/minecraft/world/level/block/entity/BlockEntity;)V"],
        at = [At("RETURN")],
        remap = false
    )
    private fun ccaeroworks_bindPreciseSableFrame(
        localBounds: AABB,
        frame: Matrix4f,
        anchor: BlockEntity,
        callback: CallbackInfo
    ) {
        ccaeroworks_preciseSableFrame = SableControlOutlineBridge.bind(frame, anchor)
    }

    @Redirect(
        method = ["render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/createmod/catnip/render/SuperRenderTypeBuffer;Lnet/minecraft/world/phys/Vec3;F)V"],
        at = At(
            value = "INVOKE",
            target = "Lcom/mred231/aeroworks/content/controls/SubLevelPoseClient;translateTo(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/level/block/entity/BlockEntity;DDDLnet/minecraft/world/phys/Vec3;F)V",
            remap = false
        ),
        remap = false
    )
    private fun ccaeroworks_translatePreciseSableOutline(
        poseStack: PoseStack,
        blockEntity: BlockEntity,
        x: Double,
        y: Double,
        z: Double,
        camera: Vec3,
        partialTicks: Float
    ) {
        val preciseFrame = ccaeroworks_preciseSableFrame
        if (
            preciseFrame != null &&
            preciseFrame.anchor === blockEntity &&
            Sable.HELPER.getContainingClient(blockEntity) != null
        ) {
            SableClientRenderPose.applyOutline(
                poseStack,
                blockEntity,
                preciseFrame.center,
                camera,
                partialTicks
            )
            return
        }

        // Non-Sable outlines and third-party OrientedBoxOutline instances that were not
        // created from an Aeroworks MountSpot keep their native coordinates.
        SableClientRenderPose.apply(
            poseStack,
            blockEntity,
            x,
            y,
            z,
            camera,
            partialTicks
        )
    }
}
