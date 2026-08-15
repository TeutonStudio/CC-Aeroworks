package de.teutonstudio.ccaeroworks.computer.source

import de.teutonstudio.ccaeroworks.compat.createradar.RadarNetworkControllerLookup
import de.teutonstudio.ccaeroworks.computer.ComputerControlDeskBlockEntity
import de.teutonstudio.ccaeroworks.computer.PeripheralNetworkBuilder
import de.teutonstudio.ccaeroworks.display.RadarSourceRegistry
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager
import de.teutonstudio.ccaeroworks.telemetry.TelemetryRuntime
import net.minecraft.core.BlockPos
import java.util.Locale
import kotlin.math.roundToInt

/** Server-only projection of existing authoritative I/O owners into compact GUI metadata. */
object InformationSourceSnapshotBuilder {
    fun build(owner: ComputerControlDeskBlockEntity): InformationSourceSnapshot {
        val sources = arrayListOf<InformationSourceView>()
        addDisplayLinks(owner, sources)
        addStorage(owner, sources)
        addRadarIngress(owner, sources)
        addRadarControllers(owner, sources)
        addGps(owner, sources)
        return InformationSourceSnapshot(
            sources.sortedWith(compareBy<InformationSourceView>({ it.kind.ordinal }, { it.label }, { it.id }))
        )
    }

    private fun addDisplayLinks(owner: ComputerControlDeskBlockEntity, result: MutableList<InformationSourceView>) {
        runCatching { TelemetryRuntime.describeSources(owner) }.getOrDefault(emptyMap()).forEach { (id, raw) ->
            val info = raw as? Map<*, *> ?: return@forEach
            val pos = position(info["sourcePosition"]) ?: return@forEach
            val linkPos = position(info["linkPosition"])
            val label = sequenceOf(info["alias"], info["createLabel"], info["sourceType"], id)
                .firstOrNull { it != null }
                .toString()
            val status = when {
                info["stale"] == true -> "stale"
                info["supported"] == false -> "unsupported"
                else -> "ready"
            }
            val kind = info["kind"]?.toString().orEmpty()
            val link = linkPos?.let { " · link ${it.x},${it.y},${it.z}" }.orEmpty()
            result += InformationSourceView(
                id = "display_link:$id",
                kind = InformationSourceKind.DISPLAY_LINK,
                label = label,
                status = status,
                x = pos.x,
                y = pos.y,
                z = pos.z,
                side = "",
                details = "$kind$link".trim()
            )
        }
    }

    private fun addStorage(owner: ComputerControlDeskBlockEntity, result: MutableList<InformationSourceView>) {
        val graph = runCatching { PeripheralNetworkBuilder.build(owner) }.getOrNull() ?: return
        graph.peripherals
            .asSequence()
            .filter { it.matches("inventory") || it.matches("fluid_storage") }
            .forEach { node ->
                result += InformationSourceView(
                    id = "storage:${node.address}",
                    kind = InformationSourceKind.STORAGE,
                    label = node.primaryType,
                    status = "connected",
                    x = node.pos.x,
                    y = node.pos.y,
                    z = node.pos.z,
                    side = node.side.name.lowercase(),
                    details = node.types.sorted().joinToString(", ").take(120)
                )
            }
    }

    private fun addRadarIngress(owner: ComputerControlDeskBlockEntity, result: MutableList<InformationSourceView>) {
        RadarSourceRegistry.sources(owner).forEach { source ->
            val radar = source.radarPos
            val details = radar?.let { "radar ${it.x},${it.y},${it.z}" }.orEmpty()
            result += InformationSourceView(
                id = "radar_data_link:${source.id}",
                kind = InformationSourceKind.RADAR_DATA_LINK,
                label = "Radar ingress ${source.memberIndex}",
                status = source.status.name.lowercase(),
                x = source.ingressPos.x,
                y = source.ingressPos.y,
                z = source.ingressPos.z,
                side = "",
                details = details
            )
        }
    }

    private fun addRadarControllers(owner: ComputerControlDeskBlockEntity, result: MutableList<InformationSourceView>) {
        val level = owner.level ?: return
        val network = ConsoleMultiblockManager.resolve(level, owner.blockPos)
        val seen = hashSetOf<BlockPos>()
        network.members.forEach { member ->
            val controller = RadarNetworkControllerLookup.controllerFor(member.desk) ?: return@forEach
            if (!seen.add(controller)) return@forEach
            result += InformationSourceView(
                id = "radar_controller:${controller.asLong()}",
                kind = InformationSourceKind.RADAR_NETWORK_CONTROLLER,
                label = "Radar network controller",
                status = "linked",
                x = controller.x,
                y = controller.y,
                z = controller.z,
                side = "",
                details = "via desk ${member.index}"
            )
        }
    }

    private fun addGps(owner: ComputerControlDeskBlockEntity, result: MutableList<InformationSourceView>) {
        GpsSourceTracker.request(owner)
        val source = GpsSourceTracker.current(owner) ?: return
        val fix = source.fix
        val ageSeconds = source.ageTicks / 20L
        result += InformationSourceView(
            id = "gps:${owner.deskId}",
            kind = InformationSourceKind.GPS,
            label = "GPS fix",
            status = source.status,
            x = fix.x.roundToInt(),
            y = fix.y.roundToInt(),
            z = fix.z.roundToInt(),
            side = "",
            details = String.format(
                Locale.ROOT,
                "%.2f, %.2f, %.2f · %d hosts · age %ds",
                fix.x,
                fix.y,
                fix.z,
                fix.hostCount,
                ageSeconds
            )
        )
    }

    private fun position(value: Any?): BlockPos? {
        val map = value as? Map<*, *> ?: return null
        val x = (map["x"] as? Number)?.toInt() ?: return null
        val y = (map["y"] as? Number)?.toInt() ?: return null
        val z = (map["z"] as? Number)?.toInt() ?: return null
        return BlockPos(x, y, z)
    }
}
