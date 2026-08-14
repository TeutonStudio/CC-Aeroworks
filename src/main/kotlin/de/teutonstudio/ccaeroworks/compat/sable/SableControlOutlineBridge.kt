package de.teutonstudio.ccaeroworks.compat.sable

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4fc
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap

/**
 * Carries precise Aeroworks mount centers from ConsoleBlockEntity.mountSpots() to the
 * client-only OrientedBoxOutline instances that render those mount frames.
 *
 * Aeroworks 1.3.0 stores the mount-frame translation in Matrix4f. On Sable plot grids
 * that translation can be large enough to lose the socket's fractional offset before
 * the outline reaches the render pass. We deliberately do not replace or mutate the
 * MountSpot list: gameplay selection and mounting continue to use Aeroworks unchanged.
 *
 * The normal fast path associates the exact Matrix4f instance with its precise Vec3
 * center. A component-wise fallback handles an outline constructor that copied the
 * matrix. If a copy is ambiguous, no metadata is attached and native semantics win.
 */
object SableControlOutlineBridge {
    data class Frame(
        val anchor: BlockEntity,
        val center: Vec3
    )

    private val pendingFrames = ThreadLocal.withInitial {
        IdentityHashMap<Any, Frame>()
    }

    private val matrixFields = ConcurrentHashMap<Class<*>, List<Field>>()

    @JvmStatic
    fun capture(desk: ConsoleBlockEntity, spots: List<ConsoleBlockEntity.MountSpot>) {
        val pending = pendingFrames.get()

        // mountSpots() may be queried repeatedly while the held module changes. Keep only
        // the newest frames for this desk and cap unrelated stale entries defensively.
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
    fun bind(outline: Any, anchor: BlockEntity): Frame? {
        val pending = pendingFrames.get()
        if (pending.isEmpty()) return null

        val matrices = matrixFields(outline.javaClass).mapNotNull { field ->
            runCatching { field.get(outline) as? Matrix4fc }.getOrNull()
        }

        // Normal case: OrientedBoxOutline retains the Matrix4f supplied by MountSpot.
        for (matrix in matrices) {
            val direct = pending[matrix]
            if (direct != null && direct.anchor === anchor) {
                pending.remove(matrix)
                return direct
            }
        }

        // Defensive compatibility path for an Aeroworks constructor that copies the frame.
        // Only accept a unique matrix match so two collapsed large-coordinate frames can
        // never be silently associated with the wrong socket.
        for (matrix in matrices) {
            val matches = pending.entries.filter { (candidate, frame) ->
                frame.anchor === anchor &&
                    candidate is Matrix4fc &&
                    matricesEqual(candidate, matrix)
            }
            if (matches.size == 1) {
                val match = matches.single()
                pending.remove(match.key)
                return match.value
            }
        }

        return null
    }

    internal fun matricesEqual(first: Matrix4fc, second: Matrix4fc): Boolean =
        first.m00().toRawBits() == second.m00().toRawBits() &&
            first.m01().toRawBits() == second.m01().toRawBits() &&
            first.m02().toRawBits() == second.m02().toRawBits() &&
            first.m03().toRawBits() == second.m03().toRawBits() &&
            first.m10().toRawBits() == second.m10().toRawBits() &&
            first.m11().toRawBits() == second.m11().toRawBits() &&
            first.m12().toRawBits() == second.m12().toRawBits() &&
            first.m13().toRawBits() == second.m13().toRawBits() &&
            first.m20().toRawBits() == second.m20().toRawBits() &&
            first.m21().toRawBits() == second.m21().toRawBits() &&
            first.m22().toRawBits() == second.m22().toRawBits() &&
            first.m23().toRawBits() == second.m23().toRawBits() &&
            first.m30().toRawBits() == second.m30().toRawBits() &&
            first.m31().toRawBits() == second.m31().toRawBits() &&
            first.m32().toRawBits() == second.m32().toRawBits() &&
            first.m33().toRawBits() == second.m33().toRawBits()

    private fun matrixFields(type: Class<*>): List<Field> =
        matrixFields.computeIfAbsent(type) { outlineType ->
            buildList {
                var current: Class<*>? = outlineType
                while (current != null && current != Any::class.java) {
                    for (field in current.declaredFields) {
                        if (Modifier.isStatic(field.modifiers)) continue
                        if (!Matrix4fc::class.java.isAssignableFrom(field.type)) continue
                        if (runCatching { field.trySetAccessible() }.getOrDefault(false)) {
                            add(field)
                        }
                    }
                    current = current.superclass
                }
            }
        }
}
