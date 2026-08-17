package de.teutonstudio.ccaeroworks.radarcompat.computer.source

import de.teutonstudio.ccaeroworks.computer.ComputerControlDeskBlockEntity
import de.teutonstudio.ccaeroworks.computer.source.InformationSourceKind
import de.teutonstudio.ccaeroworks.computer.source.InformationSourceView
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager
import de.teutonstudio.ccaeroworks.radarcompat.createradar.RadarNetworkControllerLookup
import de.teutonstudio.ccaeroworks.radarcompat.display.RadarSourceRegistry
import net.minecraft.core.BlockPos

object RadarInformationSources {
    private val RADAR_DATA_LINK = InformationSourceKind("radar_data_link", "RADAR DATA LINKS", 100)
    private val RADAR_NETWORK_CONTROLLER = InformationSourceKind("radar_network_controller", "NETWORK CONTROLLERS", 110)

    fun sources(owner: ComputerControlDeskBlockEntity): List<InformationSourceView> = buildList {
        RadarSourceRegistry.sources(owner).forEach { source ->
            val radar = source.radarPos
            add(InformationSourceView(
                id = "radar_data_link:${source.id}", kind = RADAR_DATA_LINK,
                label = "Radar ingress ${source.memberIndex}", status = source.status.name.lowercase(),
                x = source.ingressPos.x, y = source.ingressPos.y, z = source.ingressPos.z, side = "",
                details = radar?.let { "radar ${it.x},${it.y},${it.z}" }.orEmpty()
            ))
        }
        val level = owner.level ?: return@buildList
        val network = ConsoleMultiblockManager.resolve(level, owner.blockPos)
        val seen = hashSetOf<BlockPos>()
        network.members.forEach { member ->
            val controller = RadarNetworkControllerLookup.controllerFor(member.desk) ?: return@forEach
            if (!seen.add(controller)) return@forEach
            add(InformationSourceView(
                id = "radar_controller:${controller.asLong()}", kind = RADAR_NETWORK_CONTROLLER,
                label = "Radar network controller", status = "linked",
                x = controller.x, y = controller.y, z = controller.z, side = "", details = "via desk ${member.index}"
            ))
        }
    }
}
