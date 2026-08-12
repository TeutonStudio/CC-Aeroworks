package de.teutonstudio.ccaeroworks.input

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.config.CCClientConfig
import de.teutonstudio.ccaeroworks.display.DeskDisplayGeometry
import de.teutonstudio.ccaeroworks.mixin.client.MouseHandlerAccessor
import de.teutonstudio.ccaeroworks.network.DisplayPointerAction
import de.teutonstudio.ccaeroworks.network.DisplayPointerActionPayload
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.InputEvent
import net.neoforged.neoforge.network.PacketDistributor
import org.lwjgl.glfw.GLFW

object DisplayCombinedInputController {
    private var target: DisplayCombinedTarget? = null
    private var suppressedUntilRelease = false

    @JvmStatic
    fun isActive(): Boolean = target != null

    @JvmStatic
    fun activeTarget(): DisplayCombinedTarget? = target

    @SubscribeEvent
    fun onClientTick(event: ClientTickEvent.Post) {
        val minecraft = Minecraft.getInstance()
        refreshSuppression(minecraft)
        acquireTargetIfPossible(minecraft)
        val active = target ?: return

        if (!DisplayInteractionKey.isDown(minecraft) || !targetStillValid(minecraft, active)) {
            if (DisplayInteractionKey.isDown(minecraft)) suppressedUntilRelease = true
            stop()
        }
    }

    @SubscribeEvent
    fun onCalculateTurn(event: CalculatePlayerTurnEvent) {
        val minecraft = Minecraft.getInstance()
        refreshSuppression(minecraft)
        acquireTargetIfPossible(minecraft)
        val active = target ?: return

        if (!DisplayInteractionKey.isDown(minecraft)) {
            stop()
            return
        }
        if (!targetStillValid(minecraft, active)) {
            suppressedUntilRelease = true
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
        active.u = (active.u + mouse.ccaeroworks_getAccumulatedDX() * sensitivity).coerceIn(0.0, 1.0)
        active.v = (active.v + mouse.ccaeroworks_getAccumulatedDY() * sensitivity).coerceIn(0.0, 1.0)
    }

    @SubscribeEvent
    fun onMouseButton(event: InputEvent.MouseButton.Pre) {
        val minecraft = Minecraft.getInstance()
        refreshSuppression(minecraft)
        acquireTargetIfPossible(minecraft)
        val active = target ?: return
        if (event.button != GLFW.GLFW_MOUSE_BUTTON_LEFT && event.button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return

        // Consume press and release so vanilla attack/use state cannot leak through the pointer session.
        event.isCanceled = true
        if (event.action != GLFW.GLFW_PRESS || !DisplayInteractionKey.isDown(minecraft)) return
        if (!targetStillValid(minecraft, active)) {
            suppressedUntilRelease = true
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

    private fun refreshSuppression(minecraft: Minecraft) {
        if (suppressedUntilRelease && !DisplayInteractionKey.isDown(minecraft)) {
            suppressedUntilRelease = false
        }
    }

    private fun acquireTargetIfPossible(minecraft: Minecraft) {
        if (target != null || suppressedUntilRelease || !DisplayInteractionKey.isDown(minecraft)) return
        target = acquireTarget(minecraft)
    }

    private fun acquireTarget(minecraft: Minecraft): DisplayCombinedTarget? {
        val player = minecraft.player ?: return null
        val level = minecraft.level ?: return null
        if (minecraft.screen != null || !minecraft.isWindowActive || !player.isAlive) return null

        val hit = minecraft.hitResult as? BlockHitResult ?: return null
        if (hit.type != HitResult.Type.BLOCK) return null
        val desk = level.getBlockEntity(hit.blockPos) as? ConsoleBlockEntity ?: return null

        val from = player.eyePosition
        val to = from.add(player.getViewVector(1.0f).scale(player.blockInteractionRange()))
        val pointer = DeskDisplayGeometry.resolveRay(desk, from, to)
            ?: DeskDisplayGeometry.resolveHit(desk, hit.location)
            ?: return null

        return DisplayCombinedTarget(
            dimension = level.dimension(),
            pos = desk.blockPos.immutable(),
            socket = pointer.socket,
            u = pointer.u,
            v = pointer.v
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

        val maximumDistance = player.blockInteractionRange() + 1.0
        return player.distanceToSqr(active.pos.center) <= maximumDistance * maximumDistance
    }

    private fun stop() {
        target = null
    }

    private fun reset() {
        target = null
        suppressedUntilRelease = false
    }
}
