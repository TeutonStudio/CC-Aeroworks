package de.teutonstudio.ccaeroworks.input

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import com.mred231.aeroworks.content.controls.ModuleTypes
import de.teutonstudio.ccaeroworks.client.CCKeyMappings
import de.teutonstudio.ccaeroworks.config.CCClientConfig
import de.teutonstudio.ccaeroworks.mixin.client.MouseHandlerAccessor
import de.teutonstudio.ccaeroworks.network.SetCombinedLeverValuePayload
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.network.PacketDistributor

object CombinedLeverController {
    private const val LEVER_CHANNEL = "lever"
    private var target: CombinedLeverTarget? = null
    private var keyWasDown = false

    @JvmStatic
    fun isActive(): Boolean = target != null

    @SubscribeEvent
    fun onClientTick(event: ClientTickEvent.Post) {
        val minecraft = Minecraft.getInstance()
        val keyDown = CCKeyMappings.COMBINED_LEVER.isDown
        if (keyDown && !keyWasDown) target = acquireTarget(minecraft)
        keyWasDown = keyDown

        if (!keyDown || !targetStillValid(minecraft)) {
            stop()
            return
        }
        sendPending()
    }

    @SubscribeEvent
    fun onCalculateTurn(event: CalculatePlayerTurnEvent) {
        if (!isActive()) return
        val minecraft = Minecraft.getInstance()
        if (!targetStillValid(minecraft)) {
            stop()
            return
        }
        val deltaY = (minecraft.mouseHandler as MouseHandlerAccessor).ccaeroworks_getAccumulatedDY()
        consumeMouseDelta(deltaY)
        // Vanilla computes (sensitivity * 0.6 + 0.2)^3. -1/3 therefore produces exactly zero rotation.
        event.mouseSensitivity = -1.0 / 3.0
        event.cinematicCameraEnabled = false
    }

    @JvmStatic
    fun consumeMouseDelta(deltaY: Double) {
        val active = target ?: return
        val discrete = active.accumulator.apply(
            deltaY,
            CCClientConfig.combinedLeverSensitivity.get(),
            CCClientConfig.combinedLeverInvertY.get()
        )
        if (discrete != active.sentValue) active.pendingValue = discrete
        sendPending()
    }

    @SubscribeEvent
    fun onLogout(event: ClientPlayerNetworkEvent.LoggingOut) = stop()

    @SubscribeEvent
    fun onClone(event: ClientPlayerNetworkEvent.Clone) = stop()

    private fun acquireTarget(minecraft: Minecraft): CombinedLeverTarget? {
        val player = minecraft.player ?: return null
        val level = minecraft.level ?: return null
        val hit = minecraft.hitResult as? BlockHitResult ?: return null
        if (hit.type != HitResult.Type.BLOCK) return null
        val desk = level.getBlockEntity(hit.blockPos) as? ConsoleBlockEntity ?: return null
        val from = player.eyePosition
        val to = from.add(player.getViewVector(1.0f).scale(player.blockInteractionRange()))
        val mount = desk.nearestOccupiedMount(from, to) ?: return null
        if (mount.subPath() != null) return null
        val module = desk.module(mount.socket()) ?: return null
        if (ModuleTypes.idOf(module.type()).toString() != "aeroworks:lever") return null
        val value = module.value(LEVER_CHANNEL).coerceIn(-15, 15)
        return CombinedLeverTarget(level.dimension(), desk.blockPos.immutable(), mount.socket(), LeverAccumulator(value), value)
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
        return ModuleTypes.idOf(module.type()).toString() == "aeroworks:lever"
    }

    private fun sendPending() {
        val active = target ?: return
        val pending = active.pendingValue ?: return
        val interval = 1_000_000_000L / CCClientConfig.combinedLeverPacketRate.get().coerceIn(1, 20)
        val now = System.nanoTime()
        if (now - active.lastPacketNanos < interval) return
        PacketDistributor.sendToServer(SetCombinedLeverValuePayload(active.pos, active.socket, pending))
        active.sentValue = pending
        active.pendingValue = null
        active.lastPacketNanos = now
    }

    private fun stop() {
        target = null
    }
}
