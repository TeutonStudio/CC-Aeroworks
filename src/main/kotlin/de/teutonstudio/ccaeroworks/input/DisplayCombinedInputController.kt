package de.teutonstudio.ccaeroworks.input

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
    private var suppressedUntilRelease = false

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

        if (!DisplayInteractionKey.isDown(minecraft) || !targetStillValid(minecraft, active)) {
            if (DisplayInteractionKey.isPhysicallyDown(minecraft)) suppressedUntilRelease = true
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
        // Screen V grows downwards, while raw mouse Y grows in the opposite visual direction here.
        // Subtracting the delta makes moving the mouse up move the pseudo finger up on the display.
        active.v = (active.v - mouse.ccaeroworks_getAccumulatedDY() * sensitivity).coerceIn(0.0, 1.0)
    }

    @SubscribeEvent
    fun onMouseButton(event: InputEvent.MouseButton.Pre) {
        val minecraft = Minecraft.getInstance()
        refreshSuppression(minecraft)
        if (handleShiftOverride(minecraft)) return
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

    private fun handleShiftOverride(minecraft: Minecraft): Boolean {
        if (!CombinedInputCoordinator.isShiftCameraOnly(minecraft)) return false
        if (DisplayInteractionKey.isPhysicallyDown(minecraft)) suppressedUntilRelease = true
        stop()
        return true
    }

    private fun refreshSuppression(minecraft: Minecraft) {
        if (suppressedUntilRelease && !DisplayInteractionKey.isPhysicallyDown(minecraft)) {
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
                if (pointer != null) {
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
