package de.teutonstudio.ccaeroworks.radarcompat.computer.source

import de.teutonstudio.ccaeroworks.computer.ConsoleComputer
import de.teutonstudio.ccaeroworks.computer.source.InformationSourceKind
import de.teutonstudio.ccaeroworks.computer.source.InformationSourceView
import de.teutonstudio.ccaeroworks.radarcompat.createradar.RadarNetworkControllerLookup
import de.teutonstudio.ccaeroworks.radarcompat.display.RadarSourceRegistry

object RadarInformationSources {
    private val RADAR_DATA_LINK = InformationSourceKind("radar_data_link", "RADAR DATA LINKS", 100)
    private val RADAR_NETWORK_CONTROLLER = InformationSourceKind("radar_network_controller", "NETWORK CONTROLLERS", 110)

    fun sources(owner: ConsoleComputer): List<InformationSourceView> = buildList {
        RadarSourceRegistry.sources(owner.host).forEach { source ->
            val radar = source.radarPos
            add(InformationSourceView(
                id = "radar_data_link:${source.id}", kind = RADAR_DATA_LINK,
                label = "Radar ingress ${source.memberIndex}", status = source.status.name.lowercase(),
                x = source.ingressPos.x, y = source.ingressPos.y, z = source.ingressPos.z, side = "",
                details = radar?.let { "radar ${it.x},${it.y},${it.z}" }.orEmpty()
            ))
        }
        RadarNetworkControllerLookup.controllersFor(owner.host).forEach { controller ->
            add(InformationSourceView(
                id = "radar_controller:${controller.asLong()}", kind = RADAR_NETWORK_CONTROLLER,
                label = "Radar network controller", status = "available",
                x = controller.x, y = controller.y, z = controller.z, side = "", details = "computer-routed"
            ))
        }
    }
}
