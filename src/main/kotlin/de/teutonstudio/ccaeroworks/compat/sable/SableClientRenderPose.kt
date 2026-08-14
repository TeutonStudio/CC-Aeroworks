package de.teutonstudio.ccaeroworks.compat.sable

import com.mojang.blaze3d.vertex.PoseStack
import dev.ryanhcode.sable.Sable
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf

/**
 * Projects Aeroworks render anchors through Sable without first storing their plot-grid
 * position in a float matrix.
 *
 * Sable's render pose is an affine transform. The anchor itself is transformed in double
 * precision, the camera is subtracted while it is still a Vec3, and the remaining linear
 * transform is then applied to geometry rendered relative to that anchor. Rotation and
 * scale therefore match Sable's native render matrix while avoiding its large-coordinate
 * float translation loss.
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
            val relative = cameraRelative(localPosition, camera)
            poseStack.translate(relative.x, relative.y, relative.z)
            return Result(localPosition, false)
        }

        val renderPose = subLevel.renderPose(partialTicks)
        val worldPosition = renderPose.transformPosition(localPosition)
        val relative = cameraRelative(worldPosition, camera)
        poseStack.translate(relative.x, relative.y, relative.z)
        poseStack.mulPose(Quaternionf(renderPose.orientation()))

        val scale = renderPose.scale()
        poseStack.scale(
            scale.x().toFloat(),
            scale.y().toFloat(),
            scale.z().toFloat()
        )

        return Result(worldPosition, true)
    }

    /**
     * Kept as an explicit name for placement-outline callers. It intentionally has the
     * same transform semantics as every other Aeroworks Sable anchor now.
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
