package de.teutonstudio.ccaeroworks.input

import com.mojang.blaze3d.platform.InputConstants
import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import com.mred231.aeroworks.content.controls.ConsoleControlClient
import de.teutonstudio.ccaeroworks.compat.sable.SableInteractionGeometry
import de.teutonstudio.ccaeroworks.config.CCClientConfig
import de.teutonstudio.ccaeroworks.display.DeskDisplayGeometry
import de.teutonstudio.ccaeroworks.mixin.client.MouseHandlerAccessor
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager
import de.teutonstudio.ccaeroworks.network.DisplayPointerAction
import de.teutonstudio.ccaeroworks.network.DisplayPointerActionPayload
import net.minecraft.client.Minecraft
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.InputEvent
import net.neoforged.neoforge.network.PacketDistributor
import org.lwjgl.glfw.GLFW

object DisplayCombinedInputController {
    private const val WATCHDOG_INTERVAL_TICKS = 5

    private var target: DisplayCombinedTarget? = null
    private val suppressedBindings: MutableSet<String> = linkedSetOf()

    @JvmStatic
    fun isActive(): Boolean = target != null

    @JvmStatic
    fun activeTarget(): DisplayCombinedTarget? = target

    /** Keep held pointer bindings suppressed after Aeroworks ends the ControlDesk session. */
    @JvmStatic
    fun abortControlMode() {
        val active = target ?: return
        suppressedBindings += active.heldBindings
        stop()
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    fun onKey(event: InputEvent.Key) {
        if (event.action == GLFW.GLFW_REPEAT) return
        val minecraft = Minecraft.getInstance()
        val binding = InputConstants.Type.KEYSYM.getOrCreate(event.key).name
        if (event.action == GLFW.GLFW_PRESS && CombinedInputCoordinator.isShiftCameraOnly(minecraft)) {
            val active = target
            if (active != null) {
                suppressedBindings += active.heldBindings
                stop()
            }
            return
        }
        when (event.action) {
            GLFW.GLFW_PRESS -> onBindingPressed(binding, minecraft)
            GLFW.GLFW_RELEASE -> onBindingReleased(binding)
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
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
        if (event.button != GLFW.GLFW_MOUSE_BUTTON_LEFT && event.button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return

        event.isCanceled = true
        if (event.action != GLFW.GLFW_PRESS || !basicSessionValid(minecraft)) return

        val action = if (event.button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            DisplayPointerAction.TAP
        } else {
            DisplayPointerAction.DOUBLE_TAP
        }
        PacketDistributor.sendToServer(
            DisplayPointerActionPayload(active.pos, active.socket, active.u, active.v, action)
        )
    }

    /**
     * The tick path only retires lost releases and runs a low-frequency world watchdog.
     * It never performs target acquisition.
     */
    @SubscribeEvent
    fun onClientTick(event: ClientTickEvent.Post) {
        val minecraft = Minecraft.getInstance()
        refreshSuppression(minecraft)
        val active = target ?: return

        if (CombinedInputCoordinator.isShiftCameraOnly(minecraft)) {
            suppressedBindings += active.heldBindings
            stop()
            return
        }

        active.heldBindings.removeIf { !CombinedActivationKey.isDown(it, minecraft) }
        if (active.heldBindings.isEmpty() || !basicSessionValid(minecraft)) {
            stop()
            return
        }

        active.watchdogTicks++
        if (active.watchdogTicks >= WATCHDOG_INTERVAL_TICKS) {
            active.watchdogTicks = 0
            if (!targetStillValid(minecraft, active)) {
                suppressedBindings += active.heldBindings
                stop()
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    fun onCalculateTurn(event: CalculatePlayerTurnEvent) {
        val minecraft = Minecraft.getInstance()
        val active = target ?: return

        if (CombinedInputCoordinator.isShiftCameraOnly(minecraft)) {
            suppressedBindings += active.heldBindings
            stop()
            return
        }

        active.heldBindings.removeIf { !CombinedActivationKey.isDown(it, minecraft) }
        if (active.heldBindings.isEmpty()) {
            stop()
            return
        }

        if (!ConsoleControlClient.isActive()) {
            suppressedBindings += active.heldBindings
            stop()
            return
        }

        event.mouseSensitivity = -1.0 / 3.0
        event.cinematicCameraEnabled = false

        val mouse = minecraft.mouseHandler as MouseHandlerAccessor
        var deltaX = mouse.ccaeroworks_getAccumulatedDX()
        var deltaY = mouse.ccaeroworks_getAccumulatedDY()
        if (active.baselinePending) {
            deltaX -= active.baselineDX
            deltaY -= active.baselineDY
            active.baselinePending = false
        }

        val sensitivity = CCClientConfig.displayPointerSensitivity.get()
        if (active.xActive() && deltaX != 0.0) {
            active.u = (active.u + deltaX * sensitivity).coerceIn(0.0, 1.0)
        }
        if (active.yActive() && deltaY != 0.0) {
            active.v = (active.v - deltaY * sensitivity).coerceIn(0.0, 1.0)
        }
    }

    @SubscribeEvent
    fun onLogout(event: ClientPlayerNetworkEvent.LoggingOut) = reset()

    @SubscribeEvent
    fun onClone(event: ClientPlayerNetworkEvent.Clone) = reset()

    private fun onBindingPressed(binding: String, minecraft: Minecraft): Boolean {
        if (binding.isBlank() ||
            binding in suppressedBindings ||
            CombinedInputCoordinator.ownsControl() ||
            !ConsoleControlClient.isActive()
        ) return false

        target?.let { active ->
            if (binding != active.xBinding && binding != active.yBinding) return false
            if (CombinedInputCoordinator.isShiftCameraOnly(minecraft)) {
                suppressedBindings += binding
                return false
            }
            active.heldBindings += binding
            return true
        }

        val candidate = acquireTarget(minecraft, binding) ?: return false
        if (CombinedInputCoordinator.isShiftCameraOnly(minecraft)) {
            suppressedBindings += binding
            return false
        }
        if (!CombinedInputCoordinator.claimDisplay(minecraft)) return false
        target = candidate
        return true
    }

    private fun onBindingReleased(binding: String): Boolean {
        var consumed = false
        if (suppressedBindings.remove(binding)) consumed = true

        val active = target
        if (active != null && active.heldBindings.remove(binding)) {
            consumed = true
            if (active.heldBindings.isEmpty()) stop()
        }
        return consumed
    }

    private fun refreshSuppression(minecraft: Minecraft) {
        suppressedBindings.removeIf { !CombinedActivationKey.isDown(it, minecraft) }
    }

    private fun acquireTarget(minecraft: Minecraft, binding: String): DisplayCombinedTarget? {
        val player = minecraft.player ?: return null
        val level = minecraft.level ?: return null
        if (minecraft.screen != null || !minecraft.isWindowActive || !player.isAlive) return null

        val candidates = CombinedInputContext.candidates(minecraft, binding)
        val candidate = CombinedInputContext.choose(minecraft, binding, candidates, display = true) ?: return null
        val desk = level.getBlockEntity(candidate.pos) as? ConsoleBlockEntity ?: return null
        val module = desk.module(candidate.socket) ?: return null
        if (!CombinedInputSource.isDisplayPointerModule(module)) return null

        var xBinding: String? = null
        var yBinding: String? = null
        CombinedInputSource.channels(module).forEach { channel ->
            if (!CombinedInputSource.isCombined(module, channel)) return@forEach
            val configured = CombinedInputSource.activationBinding(module, channel).takeIf(String::isNotBlank)
            when (CombinedInputSource.mouseAxis(channel)) {
                CombinedInputSource.MouseAxis.X -> xBinding = configured
                CombinedInputSource.MouseAxis.Y -> yBinding = configured
            }
        }
        if (binding != xBinding && binding != yBinding) return null

        val from = player.eyePosition
        val to = from.add(player.getViewVector(1.0f).scale(player.blockInteractionRange()))
        val pointer = DeskDisplayGeometry.resolveRay(desk, from, to)?.takeIf { it.socket == candidate.socket }
        val mouse = minecraft.mouseHandler as MouseHandlerAccessor
        CombinedInputContext.rememberSelection(binding, candidate)

        return DisplayCombinedTarget(
            dimension = level.dimension(),
            pos = candidate.pos.immutable(),
            socket = candidate.socket,
            xBinding = xBinding,
            yBinding = yBinding,
            heldBindings = linkedSetOf(binding),
            u = pointer?.u ?: 0.5,
            v = pointer?.v ?: 0.5,
            baselineDX = mouse.ccaeroworks_getAccumulatedDX(),
            baselineDY = mouse.ccaeroworks_getAccumulatedDY()
        )
    }

    private fun basicSessionValid(minecraft: Minecraft): Boolean {
        val player = minecraft.player ?: return false
        val level = minecraft.level ?: return false
        val active = target ?: return false
        return ConsoleControlClient.isActive() &&
            CombinedInputCoordinator.ownsDisplay() &&
            minecraft.screen == null &&
            minecraft.isWindowActive &&
            player.isAlive &&
            level.dimension() == active.dimension
    }

    private fun targetStillValid(minecraft: Minecraft, active: DisplayCombinedTarget): Boolean {
        val player = minecraft.player ?: return false
        val level = minecraft.level ?: return false
        if (level.dimension() != active.dimension || !level.isLoaded(active.pos)) return false
        val desk = level.getBlockEntity(active.pos) as? ConsoleBlockEntity ?: return false
        if (!DeskDisplayGeometry.isInteractiveDisplay(desk, active.socket)) return false
        val module = (if (active.socket in 0 until desk.socketCount()) desk.module(active.socket) else null)
            ?: return false
        if (!CombinedInputSource.isDisplayPointerModule(module)) return false

        val validBindings = CombinedInputSource.channels(module)
            .filter { CombinedInputSource.isCombined(module, it) }
            .mapTo(hashSetOf()) { CombinedInputSource.activationBinding(module, it) }
        if (active.heldBindings.none { it in validBindings }) return false

        val network = ConsoleMultiblockManager.resolve(level, active.pos)
        return network.members.any {
            SableInteractionGeometry.withinReach(player, level, it.pos)
        }
    }

    private fun stop() {
        target = null
        CombinedInputCoordinator.releaseDisplay()
    }

    private fun reset() {
        target = null
        suppressedBindings.clear()
        CombinedInputCoordinator.releaseDisplay()
        CombinedInputContext.reset()
    }
}
