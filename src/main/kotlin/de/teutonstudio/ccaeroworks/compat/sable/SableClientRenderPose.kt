package de.teutonstudio.ccaeroworks.compat.sable

import com.mojang.blaze3d.vertex.PoseStack
import dev.ryanhcode.sable.Sable
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf

/**
 * Applies Aeroworks' corrected client render anchor semantics to arbitrary desk
 * render passes. On normal levels this is the usual block-position minus camera
 * translation. On Sable SubLevels the local anchor is first transformed into the
 * current world render pose and only the SubLevel orientation is then applied.
 *
 * Keeping this in one place prevents overlay renderers and Aeroworks' shared
 * SubLevel pose helper from drifting into different coordinate spaces again.
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
    ): Result = applyPosition(
        poseStack,
        blockEntity,
        Vec3(x, y, z),
        camera,
        partialTicks,
        includeScale = false
    )

    /**
     * Applies the complete linear Sable render transform around an already-precise
     * Aeroworks mount center. Placement outlines use this path so neither their
     * fractional socket translation nor SubLevel scale is reconstructed from the
     * float Matrix4f translation.
     */
    @JvmStatic
    fun applyOutline(
        poseStack: PoseStack,
        blockEntity: BlockEntity,
        localPosition: Vec3,
        camera: Vec3,
        partialTicks: Float
    ): Result = applyPosition(
        poseStack,
        blockEntity,
        localPosition,
        camera,
        partialTicks,
        includeScale = true
    )

    private fun applyPosition(
        poseStack: PoseStack,
        blockEntity: BlockEntity,
        localPosition: Vec3,
        camera: Vec3,
        partialTicks: Float,
        includeScale: Boolean
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

        if (includeScale) {
            val scale = renderPose.scale()
            poseStack.scale(
                scale.x().toFloat(),
                scale.y().toFloat(),
                scale.z().toFloat()
            )
        }

        return Result(worldPosition, true)
    }

    internal fun cameraRelative(position: Vec3, camera: Vec3): Vec3 = Vec3(
        position.x - camera.x,
        position.y - camera.y,
        position.z - camera.z
    )
}
