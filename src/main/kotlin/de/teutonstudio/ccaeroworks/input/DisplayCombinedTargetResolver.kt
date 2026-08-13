package de.teutonstudio.ccaeroworks.input

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.display.DeskDisplayGeometry
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import kotlin.math.ceil

object DisplayCombinedTargetResolver {
    fun acquire(minecraft: Minecraft): DisplayCombinedTarget? {
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
                    return DisplayCombinedTarget(
                        level.dimension(), desk.blockPos.immutable(), pointer.socket, pointer.u, pointer.v
                    )
                }
            }
        }

        val center = BlockPos.containing(from.x, from.y, from.z)
        val radius = ceil(reach + 1.0).toInt()
        var best: DisplayCombinedTarget? = null
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
                    best = DisplayCombinedTarget(
                        level.dimension(), desk.blockPos.immutable(), pointer.socket, pointer.u, pointer.v
                    )
                }
            }
        }
        return best
    }

    fun isValid(minecraft: Minecraft, active: DisplayCombinedTarget): Boolean {
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
}
