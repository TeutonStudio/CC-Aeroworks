package de.teutonstudio.ccaeroworks.input

import com.mojang.blaze3d.platform.InputConstants
import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.compat.sable.SableSpatial
import de.teutonstudio.ccaeroworks.config.CCClientConfig
import de.teutonstudio.ccaeroworks.mixin.client.MouseHandlerAccessor
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager
import de.teutonstudio.ccaeroworks.network.CombinedChannelValue
import de.teutonstudio.ccaeroworks.network.CombinedControlSamplePayload
import net.minecraft.client.Minecraft
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.InputEvent
import net.neoforged.neoforge.network.PacketDistributor
import org.lwjgl.glfw.GLFW

object CombinedLeverController {
    private const val WATCHDOG_INTERVAL_TICKS = 5

    private var target: CombinedLeverTarget? = null
    private var suppressedBinding: String? = null

    @JvmStatic
    fun isActive(): Boolean = target != null || DisplayCombinedInputController.isActive()

    @SubscribeEvent(priority = EventPriority.LOW)
    fun onKey(event: InputEvent.Key) {
        if (event.action == GLFW.GLFW_REPEAT) return
        val binding = InputConstants.Type.KEYSYM.getOrCreate(event.key).name
        if (event.action == GLFW.GLFW_PRESS && CombinedInputCoordinator.isShiftCameraOnly(Minecraft.getInstance())) {
            val active = target
            if (active != null) {
                if (CombinedActivationKey.isDown(active.activationBinding, Minecraft.getInstance())) {
                    suppressedBinding = active.activationBinding
                }
                stop(flushFinal = true)
            }
            return
        }
        when (event.action) {
            GLFW.GLFW_PRESS -> onBindingPressed(binding, Minecraft.getInstance())
            GLFW.GLFW_RELEASE -> onBindingReleased(binding)
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    fun onMouseButton(event: InputEvent.MouseButton.Pre) {
        val binding = InputConstants.Type.MOUSE.getOrCreate(event.button).name
        val consumed = when (event.action) {
            GLFW.GLFW_PRESS -> onBindingPressed(binding, Minecraft.getInstance())
            GLFW.GLFW_RELEASE -> onBindingReleased(binding)
            else -> false
        }
        if (consumed) event.isCanceled = true
    }

    /**
     * Client ticks are a watchdog only. Target acquisition is edge-driven and never happens here.
     * The hot control path therefore does not raycast, resolve the multiblock or inspect modules.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    fun onClientTick(event: ClientTickEvent.Post) {
        val minecraft = Minecraft.getInstance()
        refreshSuppression(minecraft)
        val active = target ?: return

        if (CombinedInputCoordinator.isShiftCameraOnly(minecraft)) {
            if (CombinedActivationKey.isDown(active.activationBinding, minecraft)) {
                suppressedBinding = active.activationBinding
            }
            stop(flushFinal = true)
            return
        }

        if (!basicSessionValid(minecraft) || !CombinedActivationKey.isDown(active.activationBinding, minecraft)) {
            stop(flushFinal = true)
            return
        }

        active.watchdogTicks++
        if (active.watchdogTicks >= WATCHDOG_INTERVAL_TICKS) {
            active.watchdogTicks = 0
            if (!targetStillValid(minecraft, active)) {
                suppressedBinding = active.activationBinding
                stop(flushFinal = true)
                return
            }
        }

        sendPending()
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    fun onCalculateTurn(event: CalculatePlayerTurnEvent) {
        val active = target ?: return
        val minecraft = Minecraft.getInstance()

        if (CombinedInputCoordinator.isShiftCameraOnly(minecraft)) {
            if (CombinedActivationKey.isDown(active.activationBinding, minecraft)) {
                suppressedBinding = active.activationBinding
            }
            stop(flushFinal = true)
            return
        }

        if (!CombinedActivationKey.isDown(active.activationBinding, minecraft)) {
            stop(flushFinal = true)
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

        if (deltaX != 0.0 || deltaY != 0.0) consumeMouseDelta(deltaX, deltaY)
    }

    @SubscribeEvent
    fun onLogout(event: ClientPlayerNetworkEvent.LoggingOut) = reset()

    @SubscribeEvent
    fun onClone(event: ClientPlayerNetworkEvent.Clone) = reset()

    private fun onBindingPressed(binding: String, minecraft: Minecraft): Boolean {
        if (binding.isBlank() || suppressedBinding == binding || CombinedInputCoordinator.ownsDisplay()) return false
        target?.let { return it.activationBinding == binding }

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
        var consumed = false
        if (suppressedBinding == binding) {
            suppressedBinding = null
            consumed = true
        }
        val active = target
        if (active != null && active.activationBinding == binding) {
            stop(flushFinal = true)
            consumed = true
        }
        return consumed
    }

    private fun refreshSuppression(minecraft: Minecraft) {
        suppressedBinding?.let {
            if (!CombinedActivationKey.isDown(it, minecraft)) suppressedBinding = null
        }
    }

    private fun consumeMouseDelta(deltaX: Double, deltaY: Double) {
        val active = target ?: return
        active.axes.forEach { axis ->
            val mouseAxis = CombinedInputSource.mouseAxis(axis.channel)
            val delta = when (mouseAxis) {
                CombinedInputSource.MouseAxis.X -> deltaX
                CombinedInputSource.MouseAxis.Y -> deltaY
            }
            val discrete = axis.accumulator.apply(
                delta,
                CCClientConfig.combinedLeverSensitivity.get(),
                mouseAxis == CombinedInputSource.MouseAxis.Y && CCClientConfig.combinedLeverInvertY.get()
            )
            if (discrete != axis.sentValue) axis.pendingValue = discrete
        }
        sendPending()
    }

    private fun acquireTarget(minecraft: Minecraft, binding: String): CombinedLeverTarget? {
        val level = minecraft.level ?: return null
        val candidates = CombinedInputContext.candidates(minecraft, binding)
        val candidate = CombinedInputContext.choose(minecraft, binding, candidates, display = false) ?: return null
        val desk = level.getBlockEntity(candidate.pos) as? ConsoleBlockEntity ?: return null
        val module = desk.module(candidate.socket) ?: return null
        val axes = candidate.channels.map { channel ->
            val value = module.value(channel).coerceIn(-15, 15)
            CombinedAxisTarget(channel, LeverAccumulator(value), value)
        }
        if (axes.isEmpty()) return null

        val mouse = minecraft.mouseHandler as MouseHandlerAccessor
        CombinedInputContext.rememberSelection(binding, candidate)
        return CombinedLeverTarget(
            dimension = level.dimension(),
            pos = candidate.pos.immutable(),
            socket = candidate.socket,
            activationBinding = binding,
            axes = axes,
            baselineDX = mouse.ccaeroworks_getAccumulatedDX(),
            baselineDY = mouse.ccaeroworks_getAccumulatedDY()
        )
    }

    private fun basicSessionValid(minecraft: Minecraft): Boolean {
        val player = minecraft.player ?: return false
        val level = minecraft.level ?: return false
        val active = target ?: return false
        return CombinedInputCoordinator.ownsControl() &&
            minecraft.screen == null &&
            minecraft.isWindowActive &&
            player.isAlive &&
            level.dimension() == active.dimension
    }

    private fun targetStillValid(minecraft: Minecraft, active: CombinedLeverTarget): Boolean {
        val player = minecraft.player ?: return false
        val level = minecraft.level ?: return false
        if (level.dimension() != active.dimension || !level.isLoaded(active.pos)) return false
        val desk = level.getBlockEntity(active.pos) as? ConsoleBlockEntity ?: return false
        if (active.socket !in 0 until desk.socketCount()) return false
        val module = desk.module(active.socket) ?: return false
        if (active.axes.isEmpty() || active.axes.any { axis ->
                !CombinedInputSource.isCombined(module, axis.channel) ||
                    CombinedInputSource.activationBinding(module, axis.channel) != active.activationBinding
            }
        ) return false

        val network = ConsoleMultiblockManager.resolve(level, active.pos)
        val maximumDistance = player.blockInteractionRange() + 1.0
        return network.members.any {
            SableSpatial.distanceSquared(level, player.position(), it.pos.center) <=
                maximumDistance * maximumDistance
        }
    }

    private fun sendPending(force: Boolean = false, finalSample: Boolean = false) {
        val active = target ?: return
        val hasPending = active.axes.any { it.pendingValue != null }
        if (!hasPending && !finalSample) return

        val now = System.nanoTime()
        val interval = 1_000_000_000L / CCClientConfig.combinedLeverPacketRate.get().coerceIn(1, 20)
        if (!force && now - active.lastPacketNanos < interval) return

        val values = active.axes.map { axis ->
            CombinedChannelValue(axis.channel, axis.pendingValue ?: axis.sentValue)
        }
        active.sequence++
        PacketDistributor.sendToServer(
            CombinedControlSamplePayload(
                pos = active.pos,
                socket = active.socket,
                sequence = active.sequence,
                finalSample = finalSample,
                values = values
            )
        )
        active.axes.forEach { axis ->
            val current = axis.pendingValue ?: axis.sentValue
            axis.sentValue = current
            axis.pendingValue = null
        }
        active.lastPacketNanos = now
    }

    private fun stop(flushFinal: Boolean) {
        if (flushFinal) sendPending(force = true, finalSample = true)
        target = null
        CombinedInputCoordinator.releaseControl()
    }

    private fun reset() {
        target = null
        suppressedBinding = null
        CombinedInputCoordinator.releaseControl()
        CombinedInputContext.reset()
    }
}
