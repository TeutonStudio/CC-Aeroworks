package de.teutonstudio.ccaeroworks.input

import com.mred231.aeroworks.content.controls.ConsoleControlClient
import de.teutonstudio.ccaeroworks.debug.TouchInputDiagnostics
import de.teutonstudio.ccaeroworks.network.DisplayPointerAction
import de.teutonstudio.ccaeroworks.network.DisplayPointerActionPayload
import net.minecraft.client.Minecraft
import net.neoforged.neoforge.network.PacketDistributor
import org.lwjgl.glfw.GLFW

/**
 * Raw primary-button capture for an active Combined display session.
 *
 * This deliberately lives below NeoForge's InputEvent.MouseButton routing. A MouseHandler mixin
 * calls it at the native GLFW callback boundary and cancels Minecraft's normal processing only
 * when a display owns Combined input. This prevents Aeroworks, vanilla or another event listener
 * from consuming the click before the pseudo pointer can classify it.
 */
object DisplayPrimaryMouseCapture {
    private var sessionTarget: DisplayCombinedTarget? = null
    private var leftDown: Boolean = false
    private var rightDown: Boolean = false

    /**
     * Returns true when the primary button belongs to the active display and Minecraft must not
     * process this callback any further.
     */
    @JvmStatic
    fun capture(windowPointer: Long, button: Int, action: Int): Boolean {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return false

        val minecraft = Minecraft.getInstance()
        val active = DisplayCombinedInputController.activeTarget() ?: return false
        if (windowPointer != minecraft.window.window ||
            !CombinedInputCoordinator.ownsDisplay() ||
            CombinedInputCoordinator.isShiftCameraOnly(minecraft) ||
            !ConsoleControlClient.isActive() ||
            minecraft.screen != null ||
            !minecraft.isWindowActive ||
            minecraft.player?.isAlive != true ||
            minecraft.level?.dimension() != active.dimension
        ) return false

        if (sessionTarget !== active) {
            // A button which was already held before this display session must not create a
            // synthetic press. Its first observed release is passed back to Minecraft because the
            // matching press belonged to gameplay before DISPLAY acquired Combined ownership.
            sessionTarget = active
            leftDown = false
            rightDown = false
            TouchInputDiagnostics.info(
                "button-sample",
                "new raw button session desk=${active.pos.toShortString()} socket=${active.socket}"
            )
        }

        val buttonName = if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) "RIGHT" else "LEFT"
        val actionName = when (action) {
            GLFW.GLFW_PRESS -> "PRESS"
            GLFW.GLFW_RELEASE -> "RELEASE"
            GLFW.GLFW_REPEAT -> "REPEAT"
            else -> action.toString()
        }
        var blockMinecraft = true

        when (button) {
            GLFW.GLFW_MOUSE_BUTTON_RIGHT -> when (action) {
                GLFW.GLFW_PRESS -> if (!rightDown) {
                    rightDown = true
                    TouchInputDiagnostics.info(
                        "button-sample",
                        "right false->true tapEdge=true desk=${active.pos.toShortString()} socket=${active.socket} u=${active.u} v=${active.v}"
                    )
                    sendPointerAction(active, DisplayPointerAction.TAP)
                }

                GLFW.GLFW_RELEASE -> {
                    if (!rightDown) {
                        blockMinecraft = false
                    } else {
                        rightDown = false
                        TouchInputDiagnostics.info(
                            "button-sample",
                            "right true->false desk=${active.pos.toShortString()} socket=${active.socket}"
                        )
                    }
                }
            }

            GLFW.GLFW_MOUSE_BUTTON_LEFT -> when (action) {
                GLFW.GLFW_PRESS -> if (!leftDown) {
                    leftDown = true
                    active.holdActive = true
                    TouchInputDiagnostics.info(
                        "button-sample",
                        "left false->true holdEdge=true desk=${active.pos.toShortString()} socket=${active.socket} u=${active.u} v=${active.v}"
                    )
                    sendPointerAction(active, DisplayPointerAction.HOLD)
                }

                GLFW.GLFW_RELEASE -> {
                    if (!leftDown) {
                        blockMinecraft = false
                    } else {
                        leftDown = false
                        active.holdActive = false
                        TouchInputDiagnostics.info(
                            "button-sample",
                            "left true->false holdReleased=true desk=${active.pos.toShortString()} socket=${active.socket} u=${active.u} v=${active.v}"
                        )
                    }
                }
            }
        }

        if (blockMinecraft) {
            TouchInputDiagnostics.info(
                "mouse-gate",
                "blocked vanilla button=$buttonName action=$actionName owner=DISPLAY desk=${active.pos.toShortString()} socket=${active.socket}"
            )
        } else {
            TouchInputDiagnostics.info(
                "mouse-gate",
                "passed through pre-session release button=$buttonName owner=DISPLAY desk=${active.pos.toShortString()} socket=${active.socket}"
            )
        }
        return blockMinecraft
    }

    private fun sendPointerAction(active: DisplayCombinedTarget, action: DisplayPointerAction) {
        TouchInputDiagnostics.info(
            "client",
            "send raw action=${action.eventName} desk=${active.pos.toShortString()} socket=${active.socket} u=${active.u} v=${active.v} held=${active.heldBindings}"
        )
        PacketDistributor.sendToServer(
            DisplayPointerActionPayload(active.pos, active.socket, active.u, active.v, action)
        )
    }
}
