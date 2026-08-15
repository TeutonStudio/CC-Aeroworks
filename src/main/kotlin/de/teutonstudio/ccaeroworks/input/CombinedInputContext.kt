package de.teutonstudio.ccaeroworks.input

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.compat.sable.SableClientSpatial
import de.teutonstudio.ccaeroworks.compat.sable.SableSpatial
import de.teutonstudio.ccaeroworks.mixin.ConsoleBlockEntityInvoker
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockSnapshot
import java.util.function.Predicate
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult

/**
 * Keeps Combined input scoped to one ControlDesk network without making every mouse frame
 * rediscover that network. Looking at any member refreshes the context; once established, the
 * context remains usable while the player stays within interaction range of any member.
 *
 * Sable deliberately returns plot-local BlockHitResult positions for SubLevel blocks. Those hit
 * positions remain plot-local here so BlockEntity and multiblock lookup stay correct. Only custom
 * view rays and distances cross the world/plot boundary through the Sable spatial helpers.
 */
object CombinedInputContext {
    data class Candidate(
        val pos: BlockPos,
        val socket: Int,
        val moduleId: String,
        val display: Boolean,
        val channels: List<String>
    )

    private data class CachedContext(
        val dimension: ResourceKey<Level>,
        val anchor: BlockPos
    )

    private data class Selection(val pos: BlockPos, val socket: Int)

    private var cachedContext: CachedContext? = null
    private val lastSelections = hashMapOf<String, Selection>()

    fun candidates(minecraft: Minecraft, binding: String): List<Candidate> {
        if (binding.isBlank()) return emptyList()
        val snapshot = resolveNetwork(minecraft) ?: return emptyList()
        val result = mutableListOf<Candidate>()
        snapshot.members.forEach { member ->
            for (socket in 0 until member.desk.socketCount()) {
                val module = member.desk.module(socket) ?: continue
                val channels = CombinedInputSource.channels(module).filter { channel ->
                    CombinedInputSource.isCombined(module, channel) &&
                        CombinedInputSource.activationBinding(module, channel) == binding
                }
                if (channels.isEmpty()) continue
                result += Candidate(
                    pos = member.pos,
                    socket = socket,
                    moduleId = CombinedInputSource.moduleId(module),
                    display = CombinedInputSource.isDisplayPointerModule(module),
                    channels = channels
                )
            }
        }
        return result
    }

    fun directCandidate(minecraft: Minecraft, candidates: List<Candidate>): Candidate? {
        if (candidates.isEmpty()) return null
        val player = minecraft.player ?: return null
        val level = minecraft.level ?: return null
        val hit = minecraft.hitResult as? BlockHitResult ?: return null
        if (hit.type != HitResult.Type.BLOCK) return null

        // Sable's vanilla clip returns the SubLevel/plot BlockPos. Do not project this to world
        // space: the desk BlockEntity and its multiblock neighbours are stored in that plot space.
        val desk = level.getBlockEntity(hit.blockPos) as? ConsoleBlockEntity ?: return null
        val localCandidates = candidates.filter { it.pos == desk.blockPos }
        if (localCandidates.isEmpty()) return null

        val from = player.eyePosition
        val to = from.add(player.getViewVector(1.0f).scale(player.blockInteractionRange()))
        val mount = (desk as ConsoleBlockEntityInvoker).ccaeroworks_nearestMount(from, to, Predicate { spot ->
            if (!spot.occupied()) return@Predicate false
            val target = spot.target()
            target.subPath() == null && localCandidates.any { it.socket == target.socket() }
        }) ?: return null
        return localCandidates.firstOrNull { it.socket == mount.socket() }
    }

    fun choose(
        minecraft: Minecraft,
        binding: String,
        candidates: List<Candidate>,
        display: Boolean
    ): Candidate? {
        directCandidate(minecraft, candidates)?.takeIf { it.display == display }?.let { return it }

        val remembered = lastSelections[binding]
        if (remembered != null) {
            candidates.firstOrNull {
                it.display == display && it.pos == remembered.pos && it.socket == remembered.socket
            }?.let { return it }
        }

        return candidates.singleOrNull()?.takeIf { it.display == display }
    }

    fun rememberSelection(binding: String, candidate: Candidate) {
        lastSelections[binding] = Selection(candidate.pos.immutable(), candidate.socket)
    }

    fun resolveNetwork(minecraft: Minecraft): ConsoleMultiblockSnapshot? {
        val player = minecraft.player ?: return null
        val level = minecraft.level ?: return null

        val hit = minecraft.hitResult as? BlockHitResult
        if (hit?.type == HitResult.Type.BLOCK &&
            level.getBlockEntity(hit.blockPos) is ConsoleBlockEntity
        ) {
            val snapshot = ConsoleMultiblockManager.resolve(level, hit.blockPos)
            remember(level.dimension(), snapshot)
            return snapshot
        }

        val cached = cachedContext
        if (cached != null) {
            if (cached.dimension == level.dimension() && level.isLoaded(cached.anchor)) {
                val snapshot = ConsoleMultiblockManager.resolve(level, cached.anchor)
                val maximumDistance = player.blockInteractionRange() + 2.0
                if (snapshot.members.any {
                        SableSpatial.distanceSquared(level, player.position(), it.pos.center) <=
                            maximumDistance * maximumDistance
                    }
                ) {
                    return snapshot
                }
            }
            cachedContext = null
        }

        val nearbyDesk = findDeskNearViewRay(minecraft) ?: return null
        val snapshot = ConsoleMultiblockManager.resolve(level, nearbyDesk)
        remember(level.dimension(), snapshot)
        return snapshot
    }

    fun reset() {
        cachedContext = null
        lastSelections.clear()
    }

    /**
     * Initial acquisition for visual modules which do not own Vanilla's collision hit. The world
     * ray is scanned in the main level and, for every Sable ship intersecting it, in the inverse-
     * projected plot space. ClientSubLevel#renderPose is used so a moving/rotating ship is tested
     * where it is currently rendered, matching Sable's vanilla GameRenderer picking semantics.
     */
    private fun findDeskNearViewRay(minecraft: Minecraft): BlockPos? {
        val player = minecraft.player ?: return null
        val level = minecraft.level ?: return null
        val reach = player.blockInteractionRange()
        val from = player.eyePosition
        val to = from.add(player.getViewVector(1.0f).scale(reach))
        val steps = kotlin.math.ceil(reach * 2.0).toInt().coerceAtLeast(1)
        val visited = hashSetOf<Long>()

        for (raySpace in SableClientSpatial.raySpaces(level, from, to)) {
            visited.clear()
            val segment = raySpace.to.subtract(raySpace.from)
            for (step in 0..steps) {
                val fraction = step.toDouble() / steps.toDouble()
                val point = raySpace.from.add(segment.scale(fraction))
                val center = BlockPos.containing(point)
                for (dx in -1..1) {
                    for (dy in -1..1) {
                        for (dz in -1..1) {
                            val pos = center.offset(dx, dy, dz)
                            if (!visited.add(pos.asLong()) || !level.isLoaded(pos)) continue
                            if (!SableClientSpatial.belongsTo(level, pos, raySpace.subLevel)) continue
                            if (level.getBlockEntity(pos) is ConsoleBlockEntity) return pos.immutable()
                        }
                    }
                }
            }
        }
        return null
    }

    private fun remember(dimension: ResourceKey<Level>, snapshot: ConsoleMultiblockSnapshot) {
        if (snapshot.members.isNotEmpty()) {
            cachedContext = CachedContext(dimension, snapshot.anchor.immutable())
        }
    }
}
