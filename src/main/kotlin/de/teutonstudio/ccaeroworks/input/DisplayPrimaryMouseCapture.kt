package de.teutonstudio.ccaeroworks.input

import com.mred231.aeroworks.content.controls.ConsoleControlClient
import de.teutonstudio.ccaeroworks.debug.TouchInputDiagnostics
import de.teutonstudio.ccaeroworks.network.DisplayDrawPayload
import de.teutonstudio.ccaeroworks.network.DisplayDrawSamplePayload
import de.teutonstudio.ccaeroworks.network.DisplayPointerAction
import de.teutonstudio.ccaeroworks.network.DisplayPointerActionPayload
import net.minecraft.client.Minecraft
import net.neoforged.neoforge.network.PacketDistributor
import org.lwjgl.glfw.GLFW

/**
 * Shared primary-button state for an active Combined display session.
 *
 * Button ownership remains protected by raw/event/poll capture, but draw motion itself is sampled at
 * the higher-frequency player-turn callback. Those points are buffered locally and sent as one
 * bounded batch per client tick instead of discarding the path between 20 Hz packets.
 */
object DisplayPrimaryMouseCapture {
    private var sessionTarget: DisplayCombinedTarget? = null
    private var leftDown: Boolean = false
    private var rightDown: Boolean = false
    private var leftOwnedByDisplay: Boolean = false
    private var rightOwnedByDisplay: Boolean = false
    private var nextGestureId: Long = 1L

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

    /** Record one high-frequency virtual-finger point for the next bounded network batch. */
    @JvmStatic
    fun observePointer(active: DisplayCombinedTarget) {
        if (!active.drawActive) return
        if (active.drawPath.isEmpty() && active.u == active.drawLastSentU && active.v == active.drawLastSentV) return

        active.drawPath.record(currentPathSample(active))
        active.drawDirty = !active.drawPath.isEmpty()
    }

    /** Send at most one packet per client tick, containing up to 16 path-preserving sub-samples. */
    @JvmStatic
    fun flushDrawSample(active: DisplayCombinedTarget) {
        if (!active.drawActive || !active.drawDirty || active.drawPath.isEmpty()) return
        active.drawSequence += 1
        sendDraw(active, isEnd = false, stage = "sample")
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
        active.drawLastSentU = active.u
        active.drawLastSentV = active.v
        active.drawPath.clear()
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
        active.drawLastSentU = 0.0
        active.drawLastSentV = 0.0
        active.drawPath.clear()
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
        val buffered = active.drawPath.drain()
        val pathSamples = if (buffered.isEmpty()) listOf(currentPathSample(active)) else buffered
        val samples = pathSamples.map { sample ->
            DisplayDrawSamplePayload(
                u = sample.u,
                v = sample.v,
                directionU = sample.directionU,
                directionV = sample.directionV,
                speed = sample.speed
            )
        }
        val latest = samples.last()

        TouchInputDiagnostics.info(
            "client",
            "send draw stage=$stage desk=${active.pos.toShortString()} socket=${active.socket} gesture=${active.drawGestureId} seq=${active.drawSequence} u=${latest.u} v=${latest.v} previousU=${active.drawLastSentU} previousV=${active.drawLastSentV} direction=${latest.directionU},${latest.directionV} speed=${latest.speed} samples=${samples.size} end=$isEnd held=${active.heldBindings}"
        )
        PacketDistributor.sendToServer(
            DisplayDrawPayload(
                pos = active.pos,
                socket = active.socket,
                gestureId = active.drawGestureId,
                sequence = active.drawSequence,
                u = latest.u,
                v = latest.v,
                directionU = latest.directionU,
                directionV = latest.directionV,
                speed = latest.speed,
                samples = samples,
                isEnd = isEnd
            )
        )
        active.drawLastSentU = latest.u
        active.drawLastSentV = latest.v
        active.drawDirty = false
    }

    private fun currentPathSample(active: DisplayCombinedTarget): DisplayPointerPathSample =
        DisplayPointerPathSample(
            u = active.u,
            v = active.v,
            directionU = active.pointerMotion.directionU,
            directionV = active.pointerMotion.directionV,
            speed = active.pointerMotion.speed
        )
}
