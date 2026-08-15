package de.teutonstudio.ccaeroworks.compat.sable

import com.mojang.blaze3d.vertex.PoseStack
import dev.ryanhcode.sable.Sable
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf

/**
 * Applies the Sable render-anchor semantics used by the previously working radar/display overlays.
 *
 * A plot-local anchor is transformed into the interpolated world render pose in double precision,
 * camera subtraction is performed in world space, and only the SubLevel orientation is left on the
 * PoseStack for geometry that follows. Keeping this shared path stable is important: radar surfaces,
 * display pointers and Aeroworks placement rendering all depend on the same anchor convention.
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

        val renderPose = subLevel.renderPose(partialTicks)
        val worldPosition = renderPose.transformPosition(localPosition)
        poseStack.translate(
            worldPosition.x - camera.x,
            worldPosition.y - camera.y,
            worldPosition.z - camera.z
        )
        poseStack.mulPose(Quaternionf(renderPose.orientation()))
        return Result(worldPosition, true)
    }

    /**
     * Placement outlines use the same Sable anchor convention as every other CC-Aeroworks overlay.
     * Outline-specific corrections must happen before this method, never by changing the shared pose.
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
