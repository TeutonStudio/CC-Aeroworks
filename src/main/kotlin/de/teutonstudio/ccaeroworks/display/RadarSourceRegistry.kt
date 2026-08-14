package de.teutonstudio.ccaeroworks.display

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.compat.createradar.RadarDeskStateAccess
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager
import de.teutonstudio.ccaeroworks.registry.CCModuleTypes
import net.minecraft.core.BlockPos

data class RadarSourceDescriptor(
    val key: RadarSourceKey,
    val memberIndex: Int,
    val memberId: String,
    val ingressPos: BlockPos,
    val radarPos: BlockPos?,
    val status: RadarLinkStatus
) {
    val id: String
        get() = key.id

    fun toLua(): Map<String, Any> = linkedMapOf<String, Any>(
        "id" to id,
        "memberIndex" to memberIndex,
        "memberId" to memberId,
        "dimension" to key.dimension.toString(),
        "x" to ingressPos.x,
        "y" to ingressPos.y,
        "z" to ingressPos.z,
        "status" to status.name.lowercase()
    ).apply {
        radarPos?.let { radar ->
            put("radarX", radar.x)
            put("radarY", radar.y)
            put("radarZ", radar.z)
        }
    }
}

/**
 * Treats every radar-linked desk in a desk multiblock as a reusable radar ingress.
 *
 * The ingress desk remains the only native Create: Radars monitor endpoint and therefore
 * synchronizes its native snapshot exactly once. Other radar displays only reference that
 * already synchronized ingress instead of duplicating up to 256 tracks per display.
 */
object RadarSourceRegistry {
    fun sources(desk: ConsoleBlockEntity): List<RadarSourceDescriptor> {
        val level = desk.level ?: return emptyList()
        val network = ConsoleMultiblockManager.resolve(level, desk.blockPos)
        return network.members
            .asSequence()
            .filter { hasRadarDisplay(it.desk) }
            .map { member ->
                val snapshot = (member.desk as? RadarDeskStateAccess)?.ccaeroworks_getRadarSnapshot()
                RadarSourceDescriptor(
                    key = RadarSourceKey(level.dimension().location(), member.pos.immutable()),
                    memberIndex = member.index,
                    memberId = member.id,
                    ingressPos = member.pos.immutable(),
                    radarPos = snapshot?.radarPos,
                    status = snapshot?.status ?: RadarLinkStatus.NOT_LINKED
                )
            }
            .toList()
    }

    fun resolveSnapshot(desk: ConsoleBlockEntity, source: RadarSourceKey): RadarDisplaySnapshot? {
        val level = desk.level ?: return null
        if (level.dimension().location() != source.dimension) return null

        val network = ConsoleMultiblockManager.resolve(level, desk.blockPos)
        val member = network.memberAt(source.ingressPos) ?: return null
        if (!hasRadarDisplay(member.desk)) return null
        return (member.desk as? RadarDeskStateAccess)?.ccaeroworks_getRadarSnapshot()
    }

    fun find(desk: ConsoleBlockEntity, sourceId: String): RadarSourceDescriptor? =
        sources(desk).firstOrNull { it.id == sourceId }

    private fun hasRadarDisplay(desk: ConsoleBlockEntity): Boolean =
        (0 until desk.socketCount()).any { socket ->
            desk.module(socket)?.let { CCModuleTypes.radarDisplayType(it.type()) } != null
        }
}
