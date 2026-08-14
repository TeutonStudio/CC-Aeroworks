package de.teutonstudio.ccaeroworks.compat.sable

import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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
    fun matchesCopiedMountFramesButRejectsDifferentFrames() {
        val original = Matrix4f()
            .translate(12.25f, 7.5f, -3.75f)
            .rotateY(0.7f)
        val copy = Matrix4f(original)
        val different = Matrix4f(original).translate(0.25f, 0.0f, 0.0f)

        assertTrue(SableControlOutlineBridge.matricesEqual(original, copy))
        assertFalse(SableControlOutlineBridge.matricesEqual(original, different))
    }
}
