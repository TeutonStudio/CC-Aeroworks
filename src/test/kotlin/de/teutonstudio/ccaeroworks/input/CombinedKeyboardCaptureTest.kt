package de.teutonstudio.ccaeroworks.input

import org.lwjgl.glfw.GLFW
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CombinedKeyboardCaptureTest {
    @Test
    fun `ordinary bindings are reserved while control focus waits for a trigger`() {
        for (key in listOf(GLFW.GLFW_KEY_E, GLFW.GLFW_KEY_I)) {
            assertTrue(
                shouldConsumeControlModeKey(
                    controlFocusBefore = true,
                    routedConsumption = false,
                    key = key,
                    freeCameraKey = false
                ),
                "key $key"
            )
        }
    }

    @Test
    fun `escape and the configured free camera key remain available`() {
        assertFalse(
            shouldConsumeControlModeKey(
                controlFocusBefore = true,
                routedConsumption = false,
                key = GLFW.GLFW_KEY_ESCAPE,
                freeCameraKey = false
            )
        )
        assertFalse(
            shouldConsumeControlModeKey(
                controlFocusBefore = true,
                routedConsumption = false,
                key = GLFW.GLFW_KEY_LEFT_SHIFT,
                freeCameraKey = true
            )
        )
    }

    @Test
    fun `routed activation edges and inactive ordinary keys keep their existing behavior`() {
        assertTrue(
            shouldConsumeControlModeKey(
                controlFocusBefore = false,
                routedConsumption = true,
                key = GLFW.GLFW_KEY_K,
                freeCameraKey = false
            )
        )
        assertFalse(
            shouldConsumeControlModeKey(
                controlFocusBefore = false,
                routedConsumption = false,
                key = GLFW.GLFW_KEY_E,
                freeCameraKey = false
            )
        )
    }
}
