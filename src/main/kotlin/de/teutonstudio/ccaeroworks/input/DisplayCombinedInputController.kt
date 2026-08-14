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
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.InputEvent
import net.neoforged.neoforge.network.PacketDistributor
import org.lwjgl.glfw.GLFW
import kotlin.math.ceil

/**
 * Edge-driven Combined input for interactive desk displays.
 *
 * X/Y bindings are resolved once when the display is acquired. The hot mouse path only checks held
 * binding state and updates normalized pointer coordinates. Shift temporarily routes mouse motion to
 * the camera without destroying the display session.
 */
object DisplayCombinedInputController {
    private var target: DisplayCombinedTarget? = null
    private val heldBindings: MutableSet<String> = linkedSetOf()
    private val suppressedBindings: MutableSet<String> = linkedSetOf()

    @JvmStatic
    fun isActive(): Boolean = target != null

    @JvmStatic
    fun activeTarget(): DisplayCombinedTarget? = target

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onKey(event: InputEvent.Key) {
        if (event.action == GLFW.GLFW_REPEAT) return
        val minecraft = Minecraft.getInstance()

        if (event.key == GLFW.GLFW_KEY_LEFT_SHIFT || event.key == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            if (event.action == GLFW.GLFW_RELEASE && !CombinedInputCoordinator.isShiftCameraOnly(minecraft)) {
                rebaseMouseBoundary(minecraft)
            }
            return
        }

        val binding = InputConstants.Type.KEYSYM.getOrCreate(event.key).name
        when (event.action) {
            GLFW.GLFW_PRESS -> onBindingPressed(binding, minecraft)
            GLFW.GLFW_RELEASE -> onBindingReleased(binding)
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onClientTick(event: ClientTickEvent.Post) {
        val minecraft = Minecraft.getInstance()
        refreshHeldBindings(minecraft)
        refreshSuppression(minecraft)

        val active = target ?: return
        if (!CombinedInputCoordinator.ownsDisplay()) {
            stop()
            return
        }

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

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onCalculateTurn(event: CalculatePlayerTurnEvent) {
        val minecraft = Minecraft.getInstance()
        if (CombinedInputCoordinator.isShiftCameraOnly(minecraft)) return

        val active = target ?: return
        if (!CombinedInputCoordinator.ownsDisplay()) return
        val axes = activeAxes(active) ?: run {
            stop()
            return
        }

        event.mouseSensitivity = -1.0 / 3.0
        event.cinematicCameraEnabled = false

        val mouse = minecraft.mouseHandler as MouseHandlerAccessor
        var deltaX = mouse.ccaeroworks_getAccumulatedDX()
        var deltaY = mouse.ccaeroworks_getAccumulatedDY()
        if (active.subtractMouseBaseline) {
            deltaX -= active.mouseBaselineX
            deltaY -= active.mouseBaselineY
            active.subtractMouseBaseline = false
        }

        val sensitivity = CCClientConfig.displayPointerSensitivity.get()
        if (axes.x) {
            active.u = (active.u + deltaX * sensitivity).coerceIn(0.0, 1.0)
        }
        if (axes.y) {
            active.v = (active.v - deltaY * sensitivity).coerceIn(0.0, 1.0)
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onMouseButton(event: InputEvent.MouseButton.Pre) {
        val minecraft = Minecraft.getInstance()
        val binding = InputConstants.Type.MOUSE.getOrCreate(event.button).name
        val activationEdge = when (event.action) {
            GLFW.GLFW_PRESS -> onBindingPressed(binding, minecraft)
            GLFW.GLFW_RELEASE -> onBindingReleased(binding)
            else -> false
        }

        if (activationEdge) {
            event.isCanceled = true
            return
        }

        if (CombinedInputCoordinator.isShiftCameraOnly(minecraft)) return
        val active = target ?: return
        val axes = activeAxes(active) ?: run {
            stop()
            return
        }
        if (event.button != GLFW.GLFW_MOUSE_BUTTON_LEFT && event.button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return

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
        val candidate = target ?: acquireTarget(minecraft) ?: return false
        if (!bindingActivates(candidate, binding)) return false

        heldBindings += binding

        if (CombinedInputCoordinator.isShiftCameraOnly(minecraft)) {
            suppressedBindings += binding
            return false
        }
        if (binding in suppressedBindings) return true

        if (target == null) {
            CombinedLeverController.preemptForDisplay()
            if (!CombinedInputCoordinator.claimDisplay(minecraft)) {
                heldBindings -= binding
                return false
            }
            target = candidate
        }
        return true
    }

    private fun onBindingReleased(binding: String): Boolean {
        val wasDisplayBinding = binding in heldBindings || binding in suppressedBindings
        heldBindings -= binding
        suppressedBindings -= binding

        val active = target
        if (active != null && activeAxes(active) == null) stop()
        return wasDisplayBinding
    }

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

    private fun rebaseMouseBoundary(minecraft: Minecraft) {
        val active = target ?: return
        val mouse = minecraft.mouseHandler as MouseHandlerAccessor
        active.mouseBaselineX = mouse.ccaeroworks_getAccumulatedDX()
        active.mouseBaselineY = mouse.ccaeroworks_getAccumulatedDY()
        active.subtractMouseBaseline = true
    }

    private fun activeAxes(active: DisplayCombinedTarget): ActiveAxes? {
        val x = active.xBinding?.let { it in heldBindings && it !in suppressedBindings } == true
        val y = active.yBinding?.let { it in heldBindings && it !in suppressedBindings } == true
        if (!x && !y) return null

        val bindings = linkedSetOf<String>()
        if (x) active.xBinding?.let(bindings::add)
        if (y) active.yBinding?.let(bindings::add)
        return ActiveAxes(x, y, bindings)
    }

    private fun bindingActivates(active: DisplayCombinedTarget, binding: String): Boolean =
        active.xBinding == binding || active.yBinding == binding

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

        val vanillaHit = minecraft.hitResult as? BlockHitResult
        if (vanillaHit?.type == HitResult.Type.BLOCK) {
            val desk = level.getBlockEntity(vanillaHit.blockPos) as? ConsoleBlockEntity
            if (desk != null) {
                val pointer = DeskDisplayGeometry.resolveRay(desk, from, to)
                    ?: DeskDisplayGeometry.resolveHit(desk, vanillaHit.location)
                if (pointer != null) {
                    buildTarget(minecraft, desk, pointer.socket, pointer.u, pointer.v)?.let { return it }
                }
            }
        }

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
                    val candidate = buildTarget(minecraft, desk, pointer.socket, pointer.u, pointer.v) ?: continue
                    val distanceSquared = from.distanceToSqr(desk.blockPos.center)
                    if (distanceSquared >= bestDistanceSquared) continue

                    bestDistanceSquared = distanceSquared
                    bestTarget = candidate
                }
            }
        }

        return bestTarget
    }

    private fun buildTarget(
        minecraft: Minecraft,
        desk: ConsoleBlockEntity,
        socket: Int,
        u: Double,
        v: Double
    ): DisplayCombinedTarget? {
        if (!DeskDisplayGeometry.isInteractiveDisplay(desk, socket)) return null
        val module = if (socket in 0 until desk.socketCount()) desk.module(socket) else null
        if (module == null || !CombinedInputSource.isDisplayPointerModule(module)) return null

        fun bindingFor(channel: String): String? {
            if (!CombinedInputSource.isCombined(module, channel)) return null
            return CombinedInputSource.activationBinding(module, channel).takeIf { it.isNotBlank() }
        }

        val xBinding = bindingFor(CombinedInputSource.X_CHANNEL)
        val yBinding = bindingFor(CombinedInputSource.Y_CHANNEL)
        if (xBinding == null && yBinding == null) return null

        val level = minecraft.level ?: return null
        val mouse = minecraft.mouseHandler as MouseHandlerAccessor
        return DisplayCombinedTarget(
            dimension = level.dimension(),
            pos = desk.blockPos.immutable(),
            socket = socket,
            xBinding = xBinding,
            yBinding = yBinding,
            u = u,
            v = v,
            mouseBaselineX = mouse.ccaeroworks_getAccumulatedDX(),
            mouseBaselineY = mouse.ccaeroworks_getAccumulatedDY()
        )
    }

    private fun targetStillValid(minecraft: Minecraft, active: DisplayCombinedTarget): Boolean {
        val player = minecraft.player ?: return false
        val level = minecraft.level ?: return false
        if (minecraft.screen != null || !minecraft.isWindowActive || !player.isAlive || level.dimension() != active.dimension) {
            return false
        }
        if (!level.isLoaded(active.pos)) return false
        val desk = level.getBlockEntity(active.pos) as? ConsoleBlockEntity ?: return false
        if (!DeskDisplayGeometry.isInteractiveDisplay(desk, active.socket)) return false
        val module = moduleForTarget(minecraft, active) ?: return false
        if (!CombinedInputSource.isDisplayPointerModule(module)) return false

        fun currentBinding(channel: String): String? {
            if (!CombinedInputSource.isCombined(module, channel)) return null
            return CombinedInputSource.activationBinding(module, channel).takeIf { it.isNotBlank() }
        }

        if (currentBinding(CombinedInputSource.X_CHANNEL) != active.xBinding) return false
        if (currentBinding(CombinedInputSource.Y_CHANNEL) != active.yBinding) return false

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
