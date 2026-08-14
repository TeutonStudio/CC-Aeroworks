package de.teutonstudio.ccaeroworks.compat.sable

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import java.util.IdentityHashMap

/**
 * Associates Aeroworks' float mount frame with the precise Vec3 center from the same
 * MountSpot. This is render metadata only; neither the MountSpot list nor selection and
 * mounting behavior is modified.
 *
 * Aeroworks 1.3.0 passes MountSpot.frame() directly into OrientedBoxOutline and that
 * outline stores the exact same Matrix4f reference. Object identity is therefore the
 * strongest and least ambiguous key. No reflection or component-wise matrix matching is
 * needed, and a failed association can no longer accidentally bind another socket whose
 * large float translation collapsed to the same values.
 */
object SableControlOutlineBridge {
    data class Frame(
        val anchor: BlockEntity,
        val center: Vec3
    )

    private val pendingFrames = ThreadLocal.withInitial {
        IdentityHashMap<Matrix4f, Frame>()
    }

    @JvmStatic
    fun capture(desk: ConsoleBlockEntity, spots: List<ConsoleBlockEntity.MountSpot>) {
        val pending = pendingFrames.get()

        // mountSpots() is also used by hit testing. Replace older unconsumed frames for the
        // same desk so ordinary client ticks cannot accumulate stale matrices indefinitely.
        val iterator = pending.entries.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().value.anchor === desk) {
                iterator.remove()
            }
        }
        if (pending.size > 64) {
            pending.clear()
        }

        for (spot in spots) {
            pending[spot.frame()] = Frame(desk, spot.center())
        }
    }

    @JvmStatic
    fun bind(frame: Matrix4f, anchor: BlockEntity): Frame? {
        val candidate = pendingFrames.get().remove(frame) ?: return null
        return candidate.takeIf { it.anchor === anchor }
    }
}
