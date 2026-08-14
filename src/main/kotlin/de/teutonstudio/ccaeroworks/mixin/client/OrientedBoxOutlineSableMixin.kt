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
 * Repairs only Aeroworks' lossy mount-anchor translation on Sable.
 *
 * Aeroworks 1.3.0 stores MountSpot.center as a Vec3 but also writes that center into the
 * MountSpot Matrix4f as floats. OrientedBoxOutline later reads the float translation back
 * out before applying the Sable render pose. On large plot coordinates that loses the
 * fractional socket offset.
 *
 * The global SubLevelPoseClient override used by earlier CC-Aeroworks fixes is deliberately
 * gone. This mixin substitutes only the precise Vec3 center and then uses a narrow helper
 * that mirrors Aeroworks 1.3.0's native baked-matrix composition exactly. Aeroworks keeps
 * ownership of the local bounds, socket/desk rotation, color and line rendering.
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
