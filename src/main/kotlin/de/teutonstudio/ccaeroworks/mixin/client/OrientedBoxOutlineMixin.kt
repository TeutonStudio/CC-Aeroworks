package de.teutonstudio.ccaeroworks.mixin.client

import dev.ryanhcode.sable.Sable
import net.minecraft.world.level.block.entity.BlockEntity
import org.spongepowered.asm.mixin.Final
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.ModifyArg

/**
 * Completes the Aeroworks 1.4.x outline backport for Sable.
 *
 * ConsoleBlockEntitySableMountFrameMixin converts Sable MountSpot frames to
 * block-local coordinates so Matrix4f never stores the large plot-grid origin.
 * OrientedBoxOutline must therefore add the BlockPos back in double precision
 * immediately before SubLevelPoseClient projects the point into render space.
 */
@Mixin(
    targets = ["com.mred231.aeroworks.content.controls.OrientedBoxOutline"],
    remap = false
)
abstract class OrientedBoxOutlineMixin {
    @Shadow
    @Final
    private lateinit var anchor: BlockEntity

    @ModifyArg(
        method = ["render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/createmod/catnip/render/SuperRenderTypeBuffer;Lnet/minecraft/world/phys/Vec3;F)V"],
        at = At(
            value = "INVOKE",
            target = "Lcom/mred231/aeroworks/content/controls/SubLevelPoseClient;translateTo(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/level/block/entity/BlockEntity;DDDLnet/minecraft/world/phys/Vec3;F)V",
            remap = false
        ),
        index = 2,
        remap = false
    )
    private fun ccaeroworks_restoreSableOutlineX(x: Double): Double =
        if (Sable.HELPER.getContainingClient(anchor) != null) x + anchor.blockPos.x else x

    @ModifyArg(
        method = ["render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/createmod/catnip/render/SuperRenderTypeBuffer;Lnet/minecraft/world/phys/Vec3;F)V"],
        at = At(
            value = "INVOKE",
            target = "Lcom/mred231/aeroworks/content/controls/SubLevelPoseClient;translateTo(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/level/block/entity/BlockEntity;DDDLnet/minecraft/world/phys/Vec3;F)V",
            remap = false
        ),
        index = 3,
        remap = false
    )
    private fun ccaeroworks_restoreSableOutlineY(y: Double): Double =
        if (Sable.HELPER.getContainingClient(anchor) != null) y + anchor.blockPos.y else y

    @ModifyArg(
        method = ["render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/createmod/catnip/render/SuperRenderTypeBuffer;Lnet/minecraft/world/phys/Vec3;F)V"],
        at = At(
            value = "INVOKE",
            target = "Lcom/mred231/aeroworks/content/controls/SubLevelPoseClient;translateTo(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/level/block/entity/BlockEntity;DDDLnet/minecraft/world/phys/Vec3;F)V",
            remap = false
        ),
        index = 4,
        remap = false
    )
    private fun ccaeroworks_restoreSableOutlineZ(z: Double): Double =
        if (Sable.HELPER.getContainingClient(anchor) != null) z + anchor.blockPos.z else z
}
