package de.teutonstudio.ccaeroworks.input

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.config.CCClientConfig
import de.teutonstudio.ccaeroworks.mixin.ConsoleBlockEntityInvoker
import de.teutonstudio.ccaeroworks.mixin.client.MouseHandlerAccessor
import de.teutonstudio.ccaeroworks.network.SetCombinedLeverValuePayload
import java.util.function.Predicate
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.network.PacketDistributor

object CombinedLeverController {
    private var target: CombinedLeverTarget? = null
    private var suppressedBinding: String? = null

    @JvmStatic
    fun isActive(): Boolean = target != null || DisplayCombinedInputController.isActive()

    @SubscribeEvent(priority = EventPriority.LOW)
    fun onClientTick(event: ClientTickEvent.Post) {
        val minecraft = Minecraft.getInstance()
        refreshSuppression(minecraft)
        if (handleShiftOverride(minecraft) || handleDisplayOverride(minecraft)) return
        acquireTargetIfPossible(minecraft)
        val active = target ?: return
        val activationDown = CombinedActivationKey.isDown(active.activationBinding, minecraft)
        if (!activationDown || !targetStillValid(minecraft)) {
            if (activationDown) suppressedBinding = active.activationBinding
            stop()
            return
        }
        sendPending()
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    fun onCalculateTurn(event: CalculatePlayerTurnEvent) {
        val minecraft = Minecraft.getInstance()
        refreshSuppression(minecraft)
        if (handleShiftOverride(minecraft) || handleDisplayOverride(minecraft)) return
        acquireTargetIfPossible(minecraft)
        val active = target ?: return

        if (!CombinedActivationKey.isDown(active.activationBinding, minecraft)) {
            stop()
            return
        }
        if (!targetStillValid(minecraft)) {
            suppressedBinding = active.activationBinding
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
        consumeMouseDelta(mouse.ccaeroworks_getAccumulatedDX(), mouse.ccaeroworks_getAccumulatedDY())
    }

    @SubscribeEvent
    fun onLogout(event: ClientPlayerNetworkEvent.LoggingOut) = reset()

    @SubscribeEvent
    fun onClone(event: ClientPlayerNetworkEvent.Clone) = reset()

    private fun handleShiftOverride(minecraft: Minecraft): Boolean {
        if (!CombinedInputCoordinator.isShiftCameraOnly(minecraft)) return false
        val active = target
        if (active != null && CombinedActivationKey.isDown(active.activationBinding, minecraft)) {
            suppressedBinding = active.activationBinding
        } else if (active == null && suppressedBinding == null) {
            acquireTarget(minecraft)?.let { suppressedBinding = it.activationBinding }
        }
        stop()
        return true
    }

    private fun handleDisplayOverride(minecraft: Minecraft): Boolean {
        if (!DisplayCombinedInputController.isActive()) return false
        val active = target
        if (active != null && CombinedActivationKey.isDown(active.activationBinding, minecraft)) {
            suppressedBinding = active.activationBinding
        }
        stop()
        return true
    }

    private fun refreshSuppression(minecraft: Minecraft) {
        suppressedBinding?.let {
            if (!CombinedActivationKey.isDown(it, minecraft)) suppressedBinding = null
        }
    }

    private fun acquireTargetIfPossible(minecraft: Minecraft) {
        if (target == null && suppressedBinding == null) target = acquireTarget(minecraft)
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

    private fun acquireTarget(minecraft: Minecraft): CombinedLeverTarget? {
        val player = minecraft.player ?: return null
        val level = minecraft.level ?: return null
        val hit = minecraft.hitResult as? BlockHitResult ?: return null
        if (hit.type != HitResult.Type.BLOCK) return null
        val desk = level.getBlockEntity(hit.blockPos) as? ConsoleBlockEntity ?: return null
        val from = player.eyePosition
        val to = from.add(player.getViewVector(1.0f).scale(player.blockInteractionRange()))
        val mount = (desk as ConsoleBlockEntityInvoker).ccaeroworks_nearestMount(from, to, Predicate { spot ->
            val candidate = spot.target()
            if (!spot.occupied() || candidate.subPath() != null) return@Predicate false
            val candidateModule = desk.module(candidate.socket()) ?: return@Predicate false
            CombinedInputSource.channels(candidateModule).any { channel ->
                CombinedInputSource.isCombined(candidateModule, channel) &&
                    CombinedActivationKey.isDown(CombinedInputSource.activationBinding(candidateModule, channel), minecraft)
            }
        }) ?: return null
        val module = desk.module(mount.socket()) ?: return null
        val firstChannel = CombinedInputSource.channels(module).firstOrNull {
            CombinedInputSource.isCombined(module, it) &&
                CombinedActivationKey.isDown(CombinedInputSource.activationBinding(module, it), minecraft)
        } ?: return null
        val binding = CombinedInputSource.activationBinding(module, firstChannel)
        val axes = CombinedInputSource.channels(module)
            .filter { CombinedInputSource.isCombined(module, it) && CombinedInputSource.activationBinding(module, it) == binding }
            .map { channel ->
                val value = module.value(channel).coerceIn(-15, 15)
                CombinedAxisTarget(channel, LeverAccumulator(value), value)
            }
        return CombinedLeverTarget(level.dimension(), desk.blockPos.immutable(), mount.socket(), binding, axes)
    }

    private fun targetStillValid(minecraft: Minecraft): Boolean {
        val active = target ?: return false
        val player = minecraft.player ?: return false
        val level = minecraft.level ?: return false
        if (minecraft.screen != null || !minecraft.isWindowActive || !player.isAlive || level.dimension() != active.dimension) return false
        if (!level.isLoaded(active.pos)) return false
        val desk = level.getBlockEntity(active.pos) as? ConsoleBlockEntity ?: return false
        if (active.socket !in 0 until desk.socketCount()) return false
        val module = desk.module(active.socket) ?: return false
        return active.axes.isNotEmpty() && active.axes.all { axis ->
            CombinedInputSource.isCombined(module, axis.channel) &&
                CombinedInputSource.activationBinding(module, axis.channel) == active.activationBinding
        }
    }

    private fun sendPending() {
        val active = target ?: return
        val interval = 1_000_000_000L / CCClientConfig.combinedLeverPacketRate.get().coerceIn(1, 20)
        val now = System.nanoTime()
        active.axes.forEach { axis ->
            val pending = axis.pendingValue ?: return@forEach
            if (now - axis.lastPacketNanos < interval) return@forEach
            PacketDistributor.sendToServer(SetCombinedLeverValuePayload(active.pos, active.socket, axis.channel, pending))
            axis.sentValue = pending
            axis.pendingValue = null
            axis.lastPacketNanos = now
        }
    }

    private fun stop() {
        target = null
    }

    private fun reset() {
        target = null
        suppressedBinding = null
    }
}
