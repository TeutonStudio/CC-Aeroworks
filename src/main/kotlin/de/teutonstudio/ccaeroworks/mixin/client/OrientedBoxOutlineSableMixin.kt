package de.teutonstudio.ccaeroworks.mixin.client

import com.mojang.blaze3d.vertex.PoseStack
import de.teutonstudio.ccaeroworks.compat.sable.SableClientRenderPose
import de.teutonstudio.ccaeroworks.compat.sable.SableControlOutlineBridge
import dev.ryanhcode.sable.Sable
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.phys.Vec3
import org.spongepowered.asm.mixin.Final
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.Redirect
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/**
 * Gives Aeroworks placement outlines a precise Sable render anchor while retaining
 * Aeroworks' own outline geometry, color and socket orientation.
 *
 * Only the translation call inside OrientedBoxOutline is redirected. The outline's
 * native Matrix4f is never rewritten and ConsoleBlockEntity selection/mounting remains
 * untouched. On Sable, a captured MountSpot.center Vec3 is projected with the current
 * renderPose in double precision before the camera is subtracted. The SubLevel linear
 * transform (orientation and scale) is then applied exactly once to the local outline.
 */
@Mixin(
    targets = ["com.mred231.aeroworks.content.controls.OrientedBoxOutline"],
    remap = false
)
abstract class OrientedBoxOutlineSableMixin {
    @Shadow
    @Final
    private lateinit var anchor: BlockEntity

    @Unique
    private var ccaeroworks_preciseSableFrame: SableControlOutlineBridge.Frame? = null

    @Inject(
        method = ["<init>"],
        at = [At("RETURN")],
        remap = false
    )
    private fun ccaeroworks_bindPreciseSableFrame(callback: CallbackInfo) {
        // Delegating constructors can reach RETURN more than once. Do not discard a frame
        // that was already matched by the inner constructor.
        if (ccaeroworks_preciseSableFrame == null) {
            ccaeroworks_preciseSableFrame = SableControlOutlineBridge.bind(this, anchor)
        }
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
        val frame = ccaeroworks_preciseSableFrame
        if (
            frame != null &&
            frame.anchor === blockEntity &&
            Sable.HELPER.getContainingClient(blockEntity) != null
        ) {
            SableClientRenderPose.applyOutline(
                poseStack,
                blockEntity,
                frame.center,
                camera,
                partialTicks
            )
            return
        }

        // Preserve the already-established CC-Aeroworks SubLevelPoseClient semantics for
        // non-Sable outlines and for defensive fallback when no unique precise frame could
        // be associated with this outline instance.
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
