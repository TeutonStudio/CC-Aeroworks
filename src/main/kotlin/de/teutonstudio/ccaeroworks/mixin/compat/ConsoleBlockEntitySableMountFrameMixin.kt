package de.teutonstudio.ccaeroworks.mixin.compat

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.mixin.ConsoleBlockEntityInvoker
import dev.ryanhcode.sable.Sable
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
import java.util.function.Predicate

/**
 * Backports Aeroworks 1.4.x's block-local mount coordinate model only while a
 * Control Desk is inside a Sable SubLevel.
 *
 * Aeroworks 1.3.0 stores absolute plot-grid positions in Matrix4f mount frames.
 * Sable plot coordinates can be large enough that the float translation loses
 * the socket's fractional offset. Placement raycasts still use Vec3 doubles, so
 * the selected socket and the final placement remain correct while Catnip's
 * outline is rendered at a stable but incorrect position.
 *
 * Newer Aeroworks keeps MountSpot centers/frames block-local and subtracts the
 * desk BlockPos from the ray before comparing it with those centers. Restricting
 * the backport to Sable preserves the original 1.3.0 behavior in normal levels.
 */
@Mixin(value = [ConsoleBlockEntity::class], remap = false)
abstract class ConsoleBlockEntitySableMountFrameMixin {
    @Inject(
        method = ["mountSpots()Ljava/util/List;"],
        at = [At("RETURN")],
        cancellable = true
    )
    private fun ccaeroworks_localizeSableMountSpots(
        callback: CallbackInfoReturnable<List<ConsoleBlockEntity.MountSpot>>
    ) {
        val blockEntity = this as BlockEntity
        if (Sable.HELPER.getContaining(blockEntity) == null) return

        val origin = Vec3.atLowerCornerOf(blockEntity.blockPos)
        val localized = callback.returnValue.map { spot ->
            val localCenter = spot.center().subtract(origin)
            val localFrame = Matrix4f(spot.frame()).setTranslation(
                localCenter.x.toFloat(),
                localCenter.y.toFloat(),
                localCenter.z.toFloat()
            )
            ConsoleBlockEntity.MountSpot(
                spot.target(),
                localCenter,
                spot.slotType(),
                spot.occupied(),
                localFrame
            )
        }
        callback.returnValue = localized
    }

    @Inject(
        method = ["nearestMount(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Ljava/util/function/Predicate;)Lcom/mred231/aeroworks/content/controls/ConsoleBlockEntity\$MountTarget;"],
        at = [At("HEAD")],
        cancellable = true
    )
    private fun ccaeroworks_useLocalSableRaycast(
        from: Vec3,
        to: Vec3,
        filter: Predicate<ConsoleBlockEntity.MountSpot>,
        callback: CallbackInfoReturnable<ConsoleBlockEntity.MountTarget?>
    ) {
        val blockEntity = this as BlockEntity
        val subLevel = Sable.HELPER.getContaining(blockEntity) ?: return
        val logicalPose = subLevel.logicalPose()
        val origin = Vec3.atLowerCornerOf(blockEntity.blockPos)
        val localFrom = logicalPose.transformPositionInverse(from).subtract(origin)
        val localTo = logicalPose.transformPositionInverse(to).subtract(origin)
        val ray = localTo.subtract(localFrom)
        val rayLengthSquared = ray.lengthSqr()

        var bestTarget: ConsoleBlockEntity.MountTarget? = null
        var bestDistanceSquared = Double.MAX_VALUE
        val mountSpots = (this as ConsoleBlockEntityInvoker).ccaeroworks_mountSpots()

        for (spot in mountSpots) {
            if (!filter.test(spot)) continue

            val center = spot.center()
            val t = if (rayLengthSquared < 1.0e-6) {
                0.0
            } else {
                center.subtract(localFrom).dot(ray).div(rayLengthSquared).coerceIn(0.0, 1.0)
            }
            val distanceSquared = center.distanceToSqr(localFrom.add(ray.scale(t)))
            val radius = if (spot.occupied()) {
                0.5
            } else {
                spot.slotType().halfExtent().toDouble() + 0.15
            }

            if (distanceSquared < bestDistanceSquared && distanceSquared <= radius * radius) {
                bestDistanceSquared = distanceSquared
                bestTarget = spot.target()
            }
        }

        callback.returnValue = bestTarget
    }
}
