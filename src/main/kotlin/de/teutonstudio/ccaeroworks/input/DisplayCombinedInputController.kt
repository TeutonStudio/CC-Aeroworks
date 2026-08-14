package de.teutonstudio.ccaeroworks.input

import com.mojang.blaze3d.platform.InputConstants
import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import com.mred231.aeroworks.content.controls.MountedModule
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
    private val heldBindings: MutableSet<String> = linkedSetOf()
    private val suppressedBindings: MutableSet<String> = linkedSetOf()

    @JvmStatic
    fun isActive(): Boolean = target != null

    @JvmStatic
    fun activeTarget(): DisplayCombinedTarget? = target

    /**
     * Keyboard activation is edge-driven. Creating the target on GLFW_PRESS makes the pointer
     * render immediately, even when the mouse has not produced a movement sample yet.
     */
    @SubscribeEvent
    fun onKey(event: InputEvent.Key) {
        if (event.action == GLFW.GLFW_REPEAT) return
        val binding = InputConstants.Type.KEYSYM.getOrCreate(event.key).name
        when (event.action) {
            GLFW.GLFW_PRESS -> onBindingPressed(binding, Minecraft.getInstance())
            GLFW.GLFW_RELEASE -> onBindingReleased(binding, Minecraft.getInstance())
        }
    }

    @SubscribeEvent
    fun onClientTick(event: ClientTickEvent.Post) {
        val minecraft = Minecraft.getInstance()
        refreshHeldBindings(minecraft)
        refreshSuppression(minecraft)
        if (handleShiftOverride(minecraft)) return

        val active = target ?: return
        val axes = activeAxes(active)
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
        if (handleShiftOverride(minecraft)) return
        val active = target ?: return
        val axes = activeAxes(active)

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
        val binding = InputConstants.Type.MOUSE.getOrCreate(event.button).name
        val activationEdge = when (event.action) {
            GLFW.GLFW_PRESS -> onBindingPressed(binding, minecraft)
            GLFW.GLFW_RELEASE -> onBindingReleased(binding, minecraft)
            else -> false
        }

        // A mouse button used as an activation binding belongs to the Combined transition itself.
        // Never let the same edge leak through as attack/use/pick or as a display tap.
        if (activationEdge) {
            event.isCanceled = true
            return
        }

        if (handleShiftOverride(minecraft)) return
        val active = target ?: return
        val axes = activeAxes(active) ?: run {
            stop()
            return
        }
        if (event.button != GLFW.GLFW_MOUSE_BUTTON_LEFT && event.button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return

        // An active display Combined session owns both action buttons so attack/use cannot leak to
        // Minecraft. Right click is a tap; left click is the explicit double-tap gesture.
        event.isCanceled = true
        if (event.action != GLFW.GLFW_PRESS) return
        if (!targetStillValid(minecraft, active)) {
            suppress(axes.bindings)
            stop()
            return
        }

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

    private fun onBindingPressed(binding: String, minecraft: Minecraft): Boolean {
        heldBindings += binding
        if (binding in suppressedBindings || CombinedInputCoordinator.isShiftCameraOnly(minecraft)) return false

        val active = target
        if (active != null) {
            // A second independently configured axis may join the already selected display, but a
            // binding for some other module never steals the target mid-session.
            return bindingActivates(active, binding)
        }

        val candidate = acquireTarget(minecraft) ?: return false
        if (!bindingActivates(candidate, binding)) return false
        if (!CombinedInputCoordinator.claimDisplay(minecraft)) return false

        target = candidate
        return true
    }

    private fun onBindingReleased(binding: String, minecraft: Minecraft): Boolean {
        val wasDisplayBinding = binding in heldBindings || binding in suppressedBindings
        heldBindings -= binding
        suppressedBindings -= binding

        val active = target
        if (active != null && activeAxes(active) == null) stop()
        return wasDisplayBinding
    }

    private fun handleShiftOverride(minecraft: Minecraft): Boolean {
        if (!CombinedInputCoordinator.isShiftCameraOnly(minecraft)) return false

        val active = target
        if (active != null) {
            activeAxes(active)?.let { suppress(it.bindings) }
            stop()
        }
        return true
    }

    /**
     * PRESS/RELEASE events are authoritative, but focus changes can make a release edge disappear.
     * The tick path is therefore only a watchdog: it may retire stale held state, never acquire a
     * new display target.
     */
    private fun refreshHeldBindings(minecraft: Minecraft) {
        if (heldBindings.isEmpty()) return
        val released = heldBindings.filterNot { CombinedActivationKey.isDown(it, minecraft) }
        if (released.isEmpty()) return
        heldBindings.removeAll(released.toSet())
        suppressedBindings.removeAll(released.toSet())
    }

    private fun refreshSuppression(minecraft: Minecraft) {
        if (suppressedBindings.isEmpty()) return
        suppressedBindings.removeAll { !CombinedActivationKey.isDown(it, minecraft) }
    }

    private fun suppress(bindings: Set<String>) {
        suppressedBindings += bindings.filter { it in heldBindings }
    }

    private fun activeAxes(active: DisplayCombinedTarget): ActiveAxes? {
        val minecraft = Minecraft.getInstance()
        val module = moduleForTarget(minecraft, active) ?: return null
        if (!CombinedInputSource.isDisplayPointerModule(module)) return null

        var x = false
        var y = false
        val bindings = linkedSetOf<String>()
        for (channel in CombinedInputSource.channels(module)) {
            if (!CombinedInputSource.isCombined(module, channel)) continue
            val binding = CombinedInputSource.activationBinding(module, channel)
            if (binding.isBlank() || binding !in heldBindings || binding in suppressedBindings) continue
            bindings += binding
            when (CombinedInputSource.mouseAxis(channel)) {
                CombinedInputSource.MouseAxis.X -> x = true
                CombinedInputSource.MouseAxis.Y -> y = true
            }
        }
        return if (x || y) ActiveAxes(x, y, bindings) else null
    }

    private fun bindingActivates(active: DisplayCombinedTarget, binding: String): Boolean {
        val module = moduleForTarget(Minecraft.getInstance(), active) ?: return false
        if (!CombinedInputSource.isDisplayPointerModule(module)) return false
        return CombinedInputSource.channels(module).any { channel ->
            CombinedInputSource.isCombined(module, channel) &&
                CombinedInputSource.activationBinding(module, channel) == binding
        }
    }

    private fun moduleForTarget(minecraft: Minecraft, active: DisplayCombinedTarget): MountedModule? {
        val level = minecraft.level ?: return null
        if (!level.isLoaded(active.pos)) return null
        val desk = level.getBlockEntity(active.pos) as? ConsoleBlockEntity ?: return null
        if (active.socket !in 0 until desk.socketCount()) return null
        return desk.module(active.socket)
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
        return module != null && CombinedInputSource.isDisplayPointerModule(module)
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
        CombinedInputCoordinator.releaseDisplay()
    }

    private fun reset() {
        target = null
        heldBindings.clear()
        suppressedBindings.clear()
        CombinedInputCoordinator.releaseDisplay()
    }

    private data class ActiveAxes(
        val x: Boolean,
        val y: Boolean,
        val bindings: Set<String>
    )
}
