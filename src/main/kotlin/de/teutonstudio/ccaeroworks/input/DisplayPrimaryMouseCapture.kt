package de.teutonstudio.ccaeroworks.input

import com.mred231.aeroworks.content.controls.ConsoleControlClient
import de.teutonstudio.ccaeroworks.debug.TouchInputDiagnostics
import de.teutonstudio.ccaeroworks.network.DisplayDrawPayload
import de.teutonstudio.ccaeroworks.network.DisplayPointerAction
import de.teutonstudio.ccaeroworks.network.DisplayPointerActionPayload
import net.minecraft.client.Minecraft
import net.neoforged.neoforge.network.PacketDistributor
import org.lwjgl.glfw.GLFW

/**
 * Shared primary-button state for an active Combined display session.
 *
 * The raw MouseHandler mixin remains the earliest interception point and blocks vanilla actions,
 * but touch detection no longer depends on that callback alone. The active display controller also
 * polls the physical GLFW state. Raw callbacks, NeoForge fallback edges and polling all feed the
 * same edge state, so a press can be observed through several paths without producing duplicates.
 *
 * Display semantics intentionally follow the configured interaction model:
 * RIGHT -> draw gesture, LEFT -> tap.
 */
object DisplayPrimaryMouseCapture {
    private var sessionTarget: DisplayCombinedTarget? = null
    private var leftDown: Boolean = false
    private var rightDown: Boolean = false
    private var leftOwnedByDisplay: Boolean = false
    private var rightOwnedByDisplay: Boolean = false
    private var nextGestureId: Long = 1L

    /**
     * Synchronise the edge detector when DISPLAY acquires Combined ownership.
     * Buttons already held before acquisition are remembered as down but not owned by the display,
     * preventing both phantom touch actions and swallowed gameplay releases.
     */
    @JvmStatic
    fun beginSession(active: DisplayCombinedTarget, minecraft: Minecraft = Minecraft.getInstance()) {
        sessionTarget = active
        val window = minecraft.window.window
        leftDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS
        rightDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS
        leftOwnedByDisplay = false
        rightOwnedByDisplay = false
        resetDraw(active)
        TouchInputDiagnostics.info(
            "button-sample",
            "baseline desk=${active.pos.toShortString()} socket=${active.socket} leftDown=$leftDown rightDown=$rightDown"
        )
    }

    @JvmStatic
    fun endSession(active: DisplayCombinedTarget?, sendDrawEnd: Boolean = true) {
        if (active != null && sessionTarget === active) {
            if (sendDrawEnd && active.drawActive) finishDraw(active, "session-end") else resetDraw(active)
            TouchInputDiagnostics.info(
                "button-sample",
                "end button session desk=${active.pos.toShortString()} socket=${active.socket} leftDown=$leftDown rightDown=$rightDown leftOwned=$leftOwnedByDisplay rightOwned=$rightOwnedByDisplay"
            )
        }
        sessionTarget = null
        leftDown = false
        rightDown = false
        leftOwnedByDisplay = false
        rightOwnedByDisplay = false
    }

    /**
     * Earliest raw callback. Returns true when Minecraft must not process the primary button.
     */
    @JvmStatic
    fun capture(windowPointer: Long, button: Int, action: Int): Boolean {
        if (!isPrimary(button)) return false

        val minecraft = Minecraft.getInstance()
        val active = DisplayCombinedInputController.activeTarget() ?: return false
        if (windowPointer != minecraft.window.window ||
            !CombinedInputCoordinator.ownsDisplay() ||
            CombinedInputCoordinator.isShiftCameraOnly(minecraft)
        ) return false

        ensureSession(active, minecraft)
        if (!runtimeValid(minecraft, active)) {
            TouchInputDiagnostics.warn(
                "mouse-gate",
                "raw primary edge blocked while display session is transiently invalid desk=${active.pos.toShortString()} socket=${active.socket} button=$button action=$action"
            )
            return true
        }

        return applyEdge(active, button, action, "raw")
    }

    /**
     * NeoForge fallback when the raw mixin did not consume the callback. It deliberately shares the
     * raw/polled edge state, therefore it cannot duplicate an action already observed elsewhere.
     */
    @JvmStatic
    fun captureFallback(button: Int, action: Int): Boolean {
        if (!isPrimary(button)) return false
        val minecraft = Minecraft.getInstance()
        val active = DisplayCombinedInputController.activeTarget() ?: return false
        if (!CombinedInputCoordinator.ownsDisplay() || CombinedInputCoordinator.isShiftCameraOnly(minecraft)) return false

        ensureSession(active, minecraft)
        if (!runtimeValid(minecraft, active)) return true
        return applyEdge(active, button, action, "event")
    }

    /**
     * Independent physical-state fallback. This is the decisive safety net for environments where
     * another integration prevents the expected MouseHandler/NeoForge callback from reaching us.
     */
    @JvmStatic
    fun poll(minecraft: Minecraft, active: DisplayCombinedTarget) {
        if (sessionTarget !== active) ensureSession(active, minecraft)
        if (!CombinedInputCoordinator.ownsDisplay() ||
            CombinedInputCoordinator.isShiftCameraOnly(minecraft) ||
            !runtimeValid(minecraft, active)
        ) return

        val window = minecraft.window.window
        val physicalLeft = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS
        val physicalRight = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS

        if (physicalRight != rightDown) {
            applyEdge(
                active,
                GLFW.GLFW_MOUSE_BUTTON_RIGHT,
                if (physicalRight) GLFW.GLFW_PRESS else GLFW.GLFW_RELEASE,
                "poll"
            )
        }
        if (physicalLeft != leftDown) {
            applyEdge(
                active,
                GLFW.GLFW_MOUSE_BUTTON_LEFT,
                if (physicalLeft) GLFW.GLFW_PRESS else GLFW.GLFW_RELEASE,
                "poll"
            )
        }
    }

    /** Mark a moved pointer for the next 20 Hz draw sample without sending render-frame packets. */
    @JvmStatic
    fun observePointer(active: DisplayCombinedTarget) {
        if (!active.drawActive) return
        if (active.u != active.drawLastSentU || active.v != active.drawLastSentV) active.drawDirty = true
    }

    /** Send at most one moved draw sample per client tick. */
    @JvmStatic
    fun flushDrawSample(active: DisplayCombinedTarget) {
        if (!active.drawActive || !active.drawDirty) return
        if (active.u == active.drawLastSentU && active.v == active.drawLastSentV) {
            active.drawDirty = false
            return
        }
        active.drawSequence += 1
        sendDraw(active, isEnd = false, stage = "sample")
        active.drawLastSentU = active.u
        active.drawLastSentV = active.v
        active.drawDirty = false
    }

    private fun ensureSession(active: DisplayCombinedTarget, minecraft: Minecraft) {
        if (sessionTarget !== active) beginSession(active, minecraft)
    }

    private fun applyEdge(active: DisplayCombinedTarget, button: Int, action: Int, source: String): Boolean {
        val buttonName = if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) "RIGHT" else "LEFT"
        val actionName = when (action) {
            GLFW.GLFW_PRESS -> "PRESS"
            GLFW.GLFW_RELEASE -> "RELEASE"
            GLFW.GLFW_REPEAT -> "REPEAT"
            else -> action.toString()
        }

        var blockMinecraft = false
        when (button) {
            GLFW.GLFW_MOUSE_BUTTON_RIGHT -> when (action) {
                GLFW.GLFW_PRESS -> {
                    if (!rightDown) {
                        rightDown = true
                        rightOwnedByDisplay = true
                        TouchInputDiagnostics.info(
                            "button-sample",
                            "source=$source right false->true drawEdge=true desk=${active.pos.toShortString()} socket=${active.socket} u=${active.u} v=${active.v}"
                        )
                        beginDraw(active)
                    }
                    blockMinecraft = rightOwnedByDisplay
                }

                GLFW.GLFW_RELEASE -> {
                    val wasOwned = rightOwnedByDisplay
                    val wasDown = rightDown
                    rightDown = false
                    rightOwnedByDisplay = false
                    blockMinecraft = wasOwned
                    if (wasDown) {
                        if (wasOwned && active.drawActive) finishDraw(active, source)
                        TouchInputDiagnostics.info(
                            "button-sample",
                            "source=$source right true->false drawReleased=$wasOwned desk=${active.pos.toShortString()} socket=${active.socket} u=${active.u} v=${active.v}"
                        )
                    }
                }

                else -> blockMinecraft = rightOwnedByDisplay
            }

            GLFW.GLFW_MOUSE_BUTTON_LEFT -> when (action) {
                GLFW.GLFW_PRESS -> {
                    if (!leftDown) {
                        leftDown = true
                        leftOwnedByDisplay = true
                        TouchInputDiagnostics.info(
                            "button-sample",
                            "source=$source left false->true tapEdge=true desk=${active.pos.toShortString()} socket=${active.socket} u=${active.u} v=${active.v}"
                        )
                        sendPointerAction(active, DisplayPointerAction.TAP)
                    }
                    blockMinecraft = leftOwnedByDisplay
                }

                GLFW.GLFW_RELEASE -> {
                    val wasOwned = leftOwnedByDisplay
                    val wasDown = leftDown
                    leftDown = false
                    leftOwnedByDisplay = false
                    blockMinecraft = wasOwned
                    if (wasDown) {
                        TouchInputDiagnostics.info(
                            "button-sample",
                            "source=$source left true->false ownedRelease=$wasOwned desk=${active.pos.toShortString()} socket=${active.socket}"
                        )
                    }
                }

                else -> blockMinecraft = leftOwnedByDisplay
            }
        }

        if (source != "poll") {
            TouchInputDiagnostics.info(
                "mouse-gate",
                "${if (blockMinecraft) "blocked" else "passed"} vanilla source=$source button=$buttonName action=$actionName owner=DISPLAY desk=${active.pos.toShortString()} socket=${active.socket}"
            )
        }
        return blockMinecraft
    }

    private fun beginDraw(active: DisplayCombinedTarget) {
        active.drawActive = true
        active.drawGestureId = nextGestureId++
        if (nextGestureId <= 0L) nextGestureId = 1L
        active.drawSequence = 0
        active.drawStartU = active.u
        active.drawStartV = active.v
        active.drawLastSentU = active.u
        active.drawLastSentV = active.v
        active.drawDirty = false
        sendDraw(active, isEnd = false, stage = "start")
    }

    private fun finishDraw(active: DisplayCombinedTarget, source: String) {
        active.drawSequence += 1
        sendDraw(active, isEnd = true, stage = "end:$source")
        resetDraw(active)
    }

    private fun resetDraw(active: DisplayCombinedTarget) {
        active.drawActive = false
        active.drawGestureId = 0L
        active.drawSequence = 0
        active.drawStartU = 0.0
        active.drawStartV = 0.0
        active.drawLastSentU = 0.0
        active.drawLastSentV = 0.0
        active.drawDirty = false
    }

    private fun runtimeValid(minecraft: Minecraft, active: DisplayCombinedTarget): Boolean =
        ConsoleControlClient.isActive() &&
            minecraft.screen == null &&
            minecraft.isWindowActive &&
            minecraft.player?.isAlive == true &&
            minecraft.level?.dimension() == active.dimension

    private fun isPrimary(button: Int): Boolean =
        button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT

    private fun sendPointerAction(active: DisplayCombinedTarget, action: DisplayPointerAction) {
        TouchInputDiagnostics.info(
            "client",
            "send physical action=${action.eventName} desk=${active.pos.toShortString()} socket=${active.socket} u=${active.u} v=${active.v} held=${active.heldBindings}"
        )
        PacketDistributor.sendToServer(
            DisplayPointerActionPayload(active.pos, active.socket, active.u, active.v, action)
        )
    }

    private fun sendDraw(active: DisplayCombinedTarget, isEnd: Boolean, stage: String) {
        TouchInputDiagnostics.info(
            "client",
            "send draw stage=$stage desk=${active.pos.toShortString()} socket=${active.socket} gesture=${active.drawGestureId} seq=${active.drawSequence} u=${active.u} v=${active.v} previousU=${active.drawLastSentU} previousV=${active.drawLastSentV} end=$isEnd held=${active.heldBindings}"
        )
        PacketDistributor.sendToServer(
            DisplayDrawPayload(
                pos = active.pos,
                socket = active.socket,
                gestureId = active.drawGestureId,
                sequence = active.drawSequence,
                u = active.u,
                v = active.v,
                isEnd = isEnd
            )
        )
    }
}
