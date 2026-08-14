package de.teutonstudio.ccaeroworks.input

import com.mojang.blaze3d.platform.InputConstants
import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.config.CCClientConfig
import de.teutonstudio.ccaeroworks.mixin.ConsoleBlockEntityInvoker
import de.teutonstudio.ccaeroworks.mixin.client.MouseHandlerAccessor
import de.teutonstudio.ccaeroworks.network.SetCombinedLeverValuePayload
import java.util.function.Predicate
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.InputEvent
import net.neoforged.neoforge.client.event.ScreenEvent
import net.neoforged.neoforge.network.PacketDistributor
import org.lwjgl.glfw.GLFW

/**
 * High-priority Combined controller for continuous Aeroworks controls.
 *
 * Acquisition is edge-driven. Client ticks only validate an existing session and flush coalesced
 * network state; they never search for a new target. The camera turn path therefore only routes an
 * already-resolved mouse sample to precomputed axis targets.
 */
object CombinedLeverController {
    private var target: CombinedLeverTarget? = null
    private var suppressedBinding: String? = null

    @JvmStatic
    fun isActive(): Boolean = target != null || DisplayCombinedInputController.isActive()

    @JvmStatic
    fun preemptForDisplay() {
        if (target != null) stop(flush = true)
    }

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
    fun onMouseButton(event: InputEvent.MouseButton.Pre) {
        val minecraft = Minecraft.getInstance()
        val binding = InputConstants.Type.MOUSE.getOrCreate(event.button).name
        val handled = when (event.action) {
            GLFW.GLFW_PRESS -> onBindingPressed(binding, minecraft)
            GLFW.GLFW_RELEASE -> onBindingReleased(binding)
            else -> false
        }
        if (handled) event.isCanceled = true
    }

    /**
     * Inventory screens are not allowed to steal an active Combined session. This also covers the
     * case where the activation binding itself is E: the key edge claims Combined first, then the
     * subsequent vanilla inventory opening is rejected.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onInteractionKeyMapping(event: InputEvent.InteractionKeyMappingTriggered) {
        val minecraft = Minecraft.getInstance()
        if (!CombinedInputCoordinator.hasOwner() || !CombinedInputCoordinator.isShiftCameraOnly(minecraft)) return
        event.isCanceled = true
        event.setSwingHand(false)
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onScreenOpening(event: ScreenEvent.Opening) {
        if (!CombinedInputCoordinator.hasOwner()) return
        when (event.newScreen) {
            is InventoryScreen,
            is CreativeModeInventoryScreen -> event.isCanceled = true
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onClientTick(event: ClientTickEvent.Post) {
        val minecraft = Minecraft.getInstance()
        refreshSuppression(minecraft)

        val active = target ?: return
        if (!CombinedInputCoordinator.ownsControl()) {
            stop(flush = false)
            return
        }

        val activationDown = CombinedActivationKey.isDown(active.activationBinding, minecraft)
        if (!activationDown) {
            stop(flush = true)
            return
        }

        if (!targetStillValid(minecraft, active)) {
            suppressedBinding = active.activationBinding
            stop(flush = false)
            return
        }

        sendPending(active, force = false)
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onCalculateTurn(event: CalculatePlayerTurnEvent) {
        val minecraft = Minecraft.getInstance()
        val active = target ?: return
        if (!CombinedInputCoordinator.ownsControl()) return
        if (CombinedInputCoordinator.isShiftCameraOnly(minecraft)) return

        if (!CombinedActivationKey.isDown(active.activationBinding, minecraft)) {
            stop(flush = true)
            return
        }

        // Vanilla computes (sensitivity * 0.6 + 0.2)^3. -1/3 therefore produces zero rotation.
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

        consumeMouseDelta(active, deltaX, deltaY)
    }

    @SubscribeEvent
    fun onLogout(event: ClientPlayerNetworkEvent.LoggingOut) = reset()

    @SubscribeEvent
    fun onClone(event: ClientPlayerNetworkEvent.Clone) = reset()

    private fun onBindingPressed(binding: String, minecraft: Minecraft): Boolean {
        val active = target
        if (active != null) return active.activationBinding == binding
        if (suppressedBinding == binding) return true

        val candidate = acquireTarget(minecraft, binding) ?: return false
        if (CombinedInputCoordinator.isShiftCameraOnly(minecraft)) {
            suppressedBinding = binding
            return false
        }
        if (!CombinedInputCoordinator.claimControl(minecraft)) return false

        target = candidate
        return true
    }

    private fun onBindingReleased(binding: String): Boolean {
        var handled = false
        if (suppressedBinding == binding) {
            suppressedBinding = null
            handled = true
        }

        val active = target
        if (active != null && active.activationBinding == binding) {
            stop(flush = true)
            handled = true
        }
        return handled
    }

    private fun refreshSuppression(minecraft: Minecraft) {
        suppressedBinding?.let {
            if (!CombinedActivationKey.isDown(it, minecraft)) suppressedBinding = null
        }
    }

    private fun rebaseMouseBoundary(minecraft: Minecraft) {
        val active = target ?: return
        val mouse = minecraft.mouseHandler as MouseHandlerAccessor
        active.mouseBaselineX = mouse.ccaeroworks_getAccumulatedDX()
        active.mouseBaselineY = mouse.ccaeroworks_getAccumulatedDY()
        active.subtractMouseBaseline = true
    }

    private fun consumeMouseDelta(active: CombinedLeverTarget, deltaX: Double, deltaY: Double) {
        active.axes.forEach { axis ->
            val delta = when (axis.mouseAxis) {
                CombinedInputSource.MouseAxis.X -> deltaX
                CombinedInputSource.MouseAxis.Y -> deltaY
            }
            val discrete = axis.accumulator.apply(
                delta,
                CCClientConfig.combinedLeverSensitivity.get(),
                axis.mouseAxis == CombinedInputSource.MouseAxis.Y && CCClientConfig.combinedLeverInvertY.get()
            )
            axis.pendingValue = if (discrete != axis.sentValue) discrete else null
        }
        sendPending(active, force = false)
    }

    private fun acquireTarget(minecraft: Minecraft, binding: String): CombinedLeverTarget? {
        val player = minecraft.player ?: return null
        val level = minecraft.level ?: return null
        if (minecraft.screen != null || !minecraft.isWindowActive || !player.isAlive) return null

        val hit = minecraft.hitResult as? BlockHitResult ?: return null
        if (hit.type != HitResult.Type.BLOCK) return null
        val desk = level.getBlockEntity(hit.blockPos) as? ConsoleBlockEntity ?: return null
        val from = player.eyePosition
        val to = from.add(player.getViewVector(1.0f).scale(player.blockInteractionRange()))

        val mount = (desk as ConsoleBlockEntityInvoker).ccaeroworks_nearestMount(from, to, Predicate { spot ->
            val candidate = spot.target()
            if (!spot.occupied() || candidate.subPath() != null) return@Predicate false
            val candidateModule = desk.module(candidate.socket()) ?: return@Predicate false
            if (CombinedInputSource.isDisplayPointerModule(candidateModule)) return@Predicate false
            CombinedInputSource.channels(candidateModule).any { channel ->
                CombinedInputSource.isCombined(candidateModule, channel) &&
                    CombinedInputSource.activationBinding(candidateModule, channel) == binding
            }
        }) ?: return null

        val module = desk.module(mount.socket()) ?: return null
        if (CombinedInputSource.isDisplayPointerModule(module)) return null

        val axes = CombinedInputSource.channels(module)
            .filter { channel ->
                CombinedInputSource.isCombined(module, channel) &&
                    CombinedInputSource.activationBinding(module, channel) == binding
            }
            .map { channel ->
                val value = module.value(channel).coerceIn(-15, 15)
                CombinedAxisTarget(
                    channel = channel,
                    mouseAxis = CombinedInputSource.mouseAxis(module, channel),
                    accumulator = LeverAccumulator(value),
                    sentValue = value
                )
            }
        if (axes.isEmpty()) return null

        val mouse = minecraft.mouseHandler as MouseHandlerAccessor
        return CombinedLeverTarget(
            dimension = level.dimension(),
            pos = desk.blockPos.immutable(),
            socket = mount.socket(),
            activationBinding = binding,
            axes = axes,
            mouseBaselineX = mouse.ccaeroworks_getAccumulatedDX(),
            mouseBaselineY = mouse.ccaeroworks_getAccumulatedDY()
        )
    }

    private fun targetStillValid(minecraft: Minecraft, active: CombinedLeverTarget): Boolean {
        val player = minecraft.player ?: return false
        val level = minecraft.level ?: return false
        if (minecraft.screen != null || !minecraft.isWindowActive || !player.isAlive || level.dimension() != active.dimension) {
            return false
        }
        if (!level.isLoaded(active.pos)) return false
        val desk = level.getBlockEntity(active.pos) as? ConsoleBlockEntity ?: return false
        if (active.socket !in 0 until desk.socketCount()) return false
        val module = desk.module(active.socket) ?: return false
        if (CombinedInputSource.isDisplayPointerModule(module)) return false

        return active.axes.isNotEmpty() && active.axes.all { axis ->
            CombinedInputSource.isCombined(module, axis.channel) &&
                CombinedInputSource.activationBinding(module, axis.channel) == active.activationBinding &&
                CombinedInputSource.mouseAxis(module, axis.channel) == axis.mouseAxis
        }
    }

    private fun sendPending(active: CombinedLeverTarget, force: Boolean) {
        val interval = 1_000_000_000L / CCClientConfig.combinedLeverPacketRate.get().coerceIn(1, 20)
        val now = System.nanoTime()
        active.axes.forEach { axis ->
            val pending = axis.pendingValue ?: return@forEach
            if (!force && now - axis.lastPacketNanos < interval) return@forEach
            PacketDistributor.sendToServer(
                SetCombinedLeverValuePayload(
                    active.pos,
                    active.socket,
                    axis.channel,
                    pending,
                    finalValue = force
                )
            )
            axis.sentValue = pending
            axis.pendingValue = null
            axis.lastPacketNanos = now
        }
    }

    private fun stop(flush: Boolean) {
        val active = target
        if (flush && active != null) sendPending(active, force = true)
        target = null
        CombinedInputCoordinator.releaseControl()
    }

    private fun reset() {
        stop(flush = false)
        suppressedBinding = null
    }
}
