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
    ): Result {
        val localPosition = Vec3(x, y, z)
        val subLevel = Sable.HELPER.getContainingClient(blockEntity)
        if (subLevel == null) {
            poseStack.translate(
                x - camera.x,
                y - camera.y,
                z - camera.z
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
}
