package de.teutonstudio.ccaeroworks.compat.sable

import com.mojang.blaze3d.vertex.PoseStack
import dev.ryanhcode.sable.Sable
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4d
import org.joml.Matrix4f

/**
 * Mirrors Aeroworks 1.3.0's native SubLevelPoseClient transform for CC-Aeroworks
 * overlays without globally replacing that helper.
 *
 * The native helper is package-private, so callers outside Aeroworks cannot invoke it
 * directly. Its matrix composition is intentionally reproduced in the same order and
 * precision: a non-Sable anchor is translated camera-relative in one operation; a Sable
 * anchor translates by -camera, multiplies the render pose baked through Matrix4d and
 * converted to Matrix4f, then translates by the plot-local anchor. This preserves the
 * native rotation-point semantics instead of rebuilding the affine transform piecemeal.
 */
object SableClientRenderPose {
    data class Result(
        val worldPosition: Vec3,
        val usedSubLevel: Boolean
    )

    @JvmStatic
    fun apply(
        poseStack: PoseStack,
        blockEntity: BlockEntity,
        x: Double,
        y: Double,
        z: Double,
        camera: Vec3,
        partialTicks: Float
    ): Result = apply(
        poseStack,
        blockEntity,
        Vec3(x, y, z),
        camera,
        partialTicks
    )

    @JvmStatic
    fun apply(
        poseStack: PoseStack,
        blockEntity: BlockEntity,
        localPosition: Vec3,
        camera: Vec3,
        partialTicks: Float
    ): Result {
        val subLevel = Sable.HELPER.getContainingClient(blockEntity)
        if (subLevel == null) {
            poseStack.translate(
                localPosition.x - camera.x,
                localPosition.y - camera.y,
                localPosition.z - camera.z
            )
            return Result(localPosition, false)
        }

        poseStack.translate(-camera.x, -camera.y, -camera.z)

        val renderPose = subLevel.renderPose(partialTicks)
        val transform = Matrix4f(renderPose.bakeIntoMatrix(Matrix4d()))
        poseStack.mulPose(transform)
        poseStack.translate(localPosition.x, localPosition.y, localPosition.z)

        return Result(
            renderPose.transformPosition(localPosition),
            true
        )
    }

    /**
     * Semantic alias for placement-outline callers. It deliberately has exactly the
     * same matrix composition as Aeroworks' native helper.
     */
    @JvmStatic
    fun applyOutline(
        poseStack: PoseStack,
        blockEntity: BlockEntity,
        localPosition: Vec3,
        camera: Vec3,
        partialTicks: Float
    ): Result = apply(
        poseStack,
        blockEntity,
        localPosition,
        camera,
        partialTicks
    )

    internal fun cameraRelative(position: Vec3, camera: Vec3): Vec3 = Vec3(
        position.x - camera.x,
        position.y - camera.y,
        position.z - camera.z
    )
}
