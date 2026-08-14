package de.teutonstudio.ccaeroworks.compat.sable

import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class SableControlOutlinePrecisionTest {
    @Test
    fun keepsFractionalCameraRelativeOffsetAtLargeCoordinates() {
        val relative = SableClientRenderPose.cameraRelative(
            Vec3(30_000_000.375, 512.625, -30_000_000.8125),
            Vec3(30_000_000.0, 512.0, -30_000_001.0)
        )

        assertEquals(0.375, relative.x, 1.0e-12)
        assertEquals(0.625, relative.y, 1.0e-12)
        assertEquals(0.1875, relative.z, 1.0e-12)
    }

    @Test
    fun aeroworksFloatFrameLosesLargeCoordinateSocketFractions() {
        val center = Vec3(30_000_000.375, 512.625, -30_000_000.8125)
        val camera = Vec3(30_000_000.0, 512.0, -30_000_001.0)

        // Aeroworks 1.3.0 constructs MountSpot.frame() with these float casts.
        val frame = Matrix4f().translate(
            center.x.toFloat(),
            center.y.toFloat(),
            center.z.toFloat()
        )
        val frameTranslation = frame.getTranslation(Vector3f())
        val lossyCenter = Vec3(
            frameTranslation.x.toDouble(),
            frameTranslation.y.toDouble(),
            frameTranslation.z.toDouble()
        )

        assertTrue(abs(center.x - lossyCenter.x) > 1.0e-6)
        assertTrue(abs(center.z - lossyCenter.z) > 1.0e-6)

        val exactRelative = SableClientRenderPose.cameraRelative(center, camera)
        val lossyRelative = SableClientRenderPose.cameraRelative(lossyCenter, camera)
        assertEquals(0.375, exactRelative.x, 1.0e-12)
        assertEquals(0.1875, exactRelative.z, 1.0e-12)
        assertTrue(abs(exactRelative.x - lossyRelative.x) > 1.0e-6)
        assertTrue(abs(exactRelative.z - lossyRelative.z) > 1.0e-6)
    }
}
