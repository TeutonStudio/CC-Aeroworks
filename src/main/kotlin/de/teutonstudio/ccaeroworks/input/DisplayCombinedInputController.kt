package de.teutonstudio.ccaeroworks.input

import com.mojang.blaze3d.platform.InputConstants
import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.config.CCClientConfig
import de.teutonstudio.ccaeroworks.display.DeskDisplayGeometry
import de.teutonstudio.ccaeroworks.mixin.client.MouseHandlerAccessor
import de.teutonstudio.ccaeroworks.network.DisplayPointerAction
import de.teutonstudio.ccaeroworks.network.DisplayPointerActionPayload
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.InputEvent
import net.neoforged.neoforge.network.PacketDistributor
import org.lwjgl.glfw.GLFW
import kotlin.math.ceil

object DisplayCombinedInputController {
    private var target: DisplayCombinedTarget? = null
    private var suppressedBindings: Set<String> = emptySet()

    @JvmStatic
    fun isActive(): Boolean = target != null

    @JvmStatic
    fun activeTarget(): DisplayCombinedTarget? = target

    @SubscribeEvent
    fun onClientTick(event: ClientTickEvent.Post) {
        val minecraft = Minecraft.getInstance()
        refreshSuppression(minecraft)
        if (handleShiftOverride(minecraft)) return
        acquireTargetIfPossible(minecraft)
        val active = target ?: return
        val axes = activeAxes(minecraft, active)

        if (axes == null) {
            stop()
            return
        }
        if (!targetStillValid(minecraft, active)) {
            suppress(axes.bindings)
            stop()
        }
    }

    @SubscribeEvent
    fun onCalculateTurn(event: CalculatePlayerTurnEvent) {
        val minecraft = Minecraft.getInstance()
        refreshSuppression(minecraft)
        if (handleShiftOverride(minecraft)) return
        acquireTargetIfPossible(minecraft)
        val active = target ?: return
        val axes = activeAxes(minecraft, active)

        if (axes == null) {
            stop()
            return
        }
        if (!targetStillValid(minecraft, active)) {
            suppress(axes.bindings)
            stop()
            return
        }

        event.mouseSensitivity = -1.0 / 3.0
        event.cinematicCameraEnabled = false
        if (active.discardNextMouseSample) {
            active.discardNextMouseSample = false
            return
        }

        val mouse = minecraft.mouseHandler as MouseHandlerAccessor
        val sensitivity = CCClientConfig.displayPointerSensitivity.get()
        if (axes.x) {
            active.u = (active.u + mouse.ccaeroworks_getAccumulatedDX() * sensitivity).coerceIn(0.0, 1.0)
        }
        if (axes.y) {
            // Screen V grows downwards; subtract raw mouse Y so moving the mouse upward also moves
            // the pseudo finger upward on the display.
            active.v = (active.v - mouse.ccaeroworks_getAccumulatedDY() * sensitivity).coerceIn(0.0, 1.0)
        }
    }

    @SubscribeEvent
    fun onMouseButton(event: InputEvent.MouseButton.Pre) {
        val minecraft = Minecraft.getInstance()
        refreshSuppression(minecraft)
        if (handleShiftOverride(minecraft)) return
        acquireTargetIfPossible(minecraft)
        val active = target ?: return
        val axes = activeAxes(minecraft, active) ?: run {
            stop()
            return
        }
        if (event.button != GLFW.GLFW_MOUSE_BUTTON_LEFT && event.button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return

        // An active display Combined session owns both action buttons so attack/use cannot leak to
        // Minecraft. If a mouse button itself is used as an axis activation binding, its press is
        // only the activation edge and must not simultaneously become a tap on the display.
        event.isCanceled = true
        if (event.action != GLFW.GLFW_PRESS) return
        if (!targetStillValid(minecraft, active)) {
            suppress(axes.bindings)
            stop()
            return
        }
        val pressedBinding = InputConstants.Type.MOUSE.getOrCreate(event.button).name
        if (pressedBinding in axes.bindings) return

        val action = if (event.button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            DisplayPointerAction.TAP
        } else {
            DisplayPointerAction.DOUBLE_TAP
        }
        PacketDistributor.sendToServer(
            DisplayPointerActionPayload(active.pos, active.socket, active.u, active.v, action)
        )
    }

    @SubscribeEvent
    fun onLogout(event: ClientPlayerNetworkEvent.LoggingOut) = reset()

    @SubscribeEvent
    fun onClone(event: ClientPlayerNetworkEvent.Clone) = reset()

    private fun handleShiftOverride(minecraft: Minecraft): Boolean {
        if (!CombinedInputCoordinator.isShiftCameraOnly(minecraft)) return false

        val active = target
        if (active != null) {
            activeAxes(minecraft, active)?.let { suppress(it.bindings) }
        } else if (suppressedBindings.isEmpty()) {
            // Shift has absolute camera priority. If an axis activation key is already held while
            // Shift is down, remember that edge so releasing Shift cannot immediately re-enter
            // Combined without a real release + press cycle.
            acquireTarget(minecraft)?.let { candidate ->
                activeAxes(minecraft, candidate)?.let { suppress(it.bindings) }
            }
        }
        stop()
        return true
    }

    private fun refreshSuppression(minecraft: Minecraft) {
        if (suppressedBindings.isEmpty()) return
        suppressedBindings = suppressedBindings.filterTo(linkedSetOf()) {
            CombinedActivationKey.isDown(it, minecraft)
        }
    }

    private fun suppress(bindings: Set<String>) {
        if (bindings.isNotEmpty()) suppressedBindings = suppressedBindings + bindings
    }

    private fun acquireTargetIfPossible(minecraft: Minecraft) {
        if (target != null || suppressedBindings.isNotEmpty() || CombinedInputCoordinator.isShiftCameraOnly(minecraft)) return
        val candidate = acquireTarget(minecraft) ?: return
        if (activeAxes(minecraft, candidate) != null) target = candidate
    }

    private fun activeAxes(minecraft: Minecraft, active: DisplayCombinedTarget): ActiveAxes? {
        val level = minecraft.level ?: return null
        if (!level.isLoaded(active.pos)) return null
        val desk = level.getBlockEntity(active.pos) as? ConsoleBlockEntity ?: return null
        if (active.socket !in 0 until desk.socketCount()) return null
        val module = desk.module(active.socket) ?: return null
        if (!CombinedInputSource.isCombinedOnly(module)) return null

        var x = false
        var y = false
        val bindings = linkedSetOf<String>()
        for (channel in CombinedInputSource.channels(module)) {
            if (!CombinedInputSource.isCombined(module, channel)) continue
            val binding = CombinedInputSource.activationBinding(module, channel)
            if (binding.isBlank() || !CombinedActivationKey.isDown(binding, minecraft)) continue
            bindings += binding
            when (CombinedInputSource.mouseAxis(channel)) {
                CombinedInputSource.MouseAxis.X -> x = true
                CombinedInputSource.MouseAxis.Y -> y = true
            }
        }
        return if (x || y) ActiveAxes(x, y, bindings) else null
    }

    private fun acquireTarget(minecraft: Minecraft): DisplayCombinedTarget? {
        val player = minecraft.player ?: return null
        val level = minecraft.level ?: return null
        if (minecraft.screen != null || !minecraft.isWindowActive || !player.isAlive) return null

        val from = player.eyePosition
        val reach = player.blockInteractionRange()
        val to = from.add(player.getViewVector(1.0f).scale(reach))

        // Fast path for the ordinary case where vanilla already resolves the desk block itself.
        val vanillaHit = minecraft.hitResult as? BlockHitResult
        if (vanillaHit?.type == HitResult.Type.BLOCK) {
            val desk = level.getBlockEntity(vanillaHit.blockPos) as? ConsoleBlockEntity
            if (desk != null) {
                val pointer = DeskDisplayGeometry.resolveRay(desk, from, to)
                    ?: DeskDisplayGeometry.resolveHit(desk, vanillaHit.location)
                if (pointer != null && isCombinedDisplay(desk, pointer.socket)) {
                    return DisplayCombinedTarget(
                        dimension = level.dimension(),
                        pos = desk.blockPos.immutable(),
                        socket = pointer.socket,
                        u = pointer.u,
                        v = pointer.v
                    )
                }
            }
        }

        // Large desk displays are visual modules and do not necessarily own the collision surface
        // selected by Minecraft's normal crosshair hit result. Search nearby desk block entities and
        // let the actual transformed display plane decide whether the view ray hits the screen.
        val center = BlockPos.containing(from.x, from.y, from.z)
        val radius = ceil(reach + 1.0).toInt()
        var bestTarget: DisplayCombinedTarget? = null
        var bestDistanceSquared = Double.POSITIVE_INFINITY

        for (x in center.x - radius..center.x + radius) {
            for (y in center.y - radius..center.y + radius) {
                for (z in center.z - radius..center.z + radius) {
                    val pos = BlockPos(x, y, z)
                    if (!level.isLoaded(pos)) continue
                    val desk = level.getBlockEntity(pos) as? ConsoleBlockEntity ?: continue
                    val pointer = DeskDisplayGeometry.resolveRay(desk, from, to) ?: continue
                    if (!isCombinedDisplay(desk, pointer.socket)) continue
                    val distanceSquared = from.distanceToSqr(desk.blockPos.center)
                    if (distanceSquared >= bestDistanceSquared) continue

                    bestDistanceSquared = distanceSquared
                    bestTarget = DisplayCombinedTarget(
                        dimension = level.dimension(),
                        pos = desk.blockPos.immutable(),
                        socket = pointer.socket,
                        u = pointer.u,
                        v = pointer.v
                    )
                }
            }
        }

        return bestTarget
    }

    private fun isCombinedDisplay(desk: ConsoleBlockEntity, socket: Int): Boolean {
        if (!DeskDisplayGeometry.isInteractiveDisplay(desk, socket)) return false
        val module = if (socket in 0 until desk.socketCount()) desk.module(socket) else null
        return module != null && CombinedInputSource.isCombinedOnly(module)
    }

    private fun targetStillValid(minecraft: Minecraft, active: DisplayCombinedTarget): Boolean {
        val player = minecraft.player ?: return false
        val level = minecraft.level ?: return false
        if (minecraft.screen != null || !minecraft.isWindowActive || !player.isAlive || level.dimension() != active.dimension) {
            return false
        }
        if (!level.isLoaded(active.pos)) return false
        val desk = level.getBlockEntity(active.pos) as? ConsoleBlockEntity ?: return false
        if (!isCombinedDisplay(desk, active.socket)) return false

        val maximumDistance = player.blockInteractionRange() + 1.0
        return player.distanceToSqr(active.pos.center) <= maximumDistance * maximumDistance
    }

    private fun stop() {
        target = null
    }

    private fun reset() {
        target = null
        suppressedBindings = emptySet()
    }

    private data class ActiveAxes(
        val x: Boolean,
        val y: Boolean,
        val bindings: Set<String>
    )
}
