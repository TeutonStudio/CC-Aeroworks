package de.teutonstudio.ccaeroworks.computer.io

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksDeskAccess
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksModuleAccess
import de.teutonstudio.ccaeroworks.compat.aeroworks.DeskSockets
import de.teutonstudio.ccaeroworks.compat.createradar.RadarNetworkTopology
import de.teutonstudio.ccaeroworks.computer.ComputerControlDeskBlockEntity
import de.teutonstudio.ccaeroworks.computer.PeripheralNetworkBuilder
import de.teutonstudio.ccaeroworks.computer.channel.ComputerChannelRegistry
import de.teutonstudio.ccaeroworks.computer.channel.channelGroups
import de.teutonstudio.ccaeroworks.display.DisplayBindings
import de.teutonstudio.ccaeroworks.display.RadarSourceRegistry
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMember
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager
import de.teutonstudio.ccaeroworks.multiblock.ConsoleNetworkState
import de.teutonstudio.ccaeroworks.registry.CCModuleTypes
import de.teutonstudio.ccaeroworks.telemetry.TelemetryRuntime
import net.minecraft.core.BlockPos
import java.util.Locale

enum class DeskIoCategory(val serializedName: String) {
    CONTROL("control"),
    DISPLAY("display"),
    INFORMATION("information"),
    OUTPUT("output")
}

/**
 * Server-authoritative inventory backing the ControlDesk I/O overview.
 *
 * The inventory deliberately references the existing owners of each subsystem instead of copying
 * their state into another persistent model: Aeroworks owns modules, DisplayBindings owns routing,
 * TelemetryRuntime owns Display Link inputs, PeripheralNetwork owns storage connections and
 * WireChannelBank owns virtual redstone outputs. User channel groups only store stable references.
 */
object DeskIoInventory {
    fun list(owner: ComputerControlDeskBlockEntity): List<Map<String, Any>> {
        val level = owner.level ?: return emptyList()
        val network = ConsoleMultiblockManager.resolve(level, owner.blockPos)
        val objects = mutableListOf<Map<String, Any>>()

        network.members.forEach { member ->
            objects += memberObjects(member)
        }

        if (!level.isClientSide) {
            objects += channelGroupObjects(owner)
            objects += telemetryObjects(owner)
            objects += storageObjects(owner)
            objects += radarInformationObjects(owner)
            objects += wireObjects(owner)
        }

        return objects
    }

    fun snapshot(owner: ComputerControlDeskBlockEntity): Map<String, Any> {
        val level = owner.level
        if (level == null) {
            return linkedMapOf(
                "state" to ConsoleNetworkState.NONE.name.lowercase(),
                "active" to false,
                "revision" to 0L,
                "objects" to emptyList<Map<String, Any>>(),
                "counts" to emptyMap<String, Int>()
            )
        }

        val network = ConsoleMultiblockManager.resolve(level, owner.blockPos)
        val objects = list(owner)
        return linkedMapOf(
            "state" to network.state.name.lowercase(),
            "active" to (network.state == ConsoleNetworkState.ACTIVE && network.owner === owner),
            "revision" to network.revision,
            "objects" to objects,
            "counts" to counts(objects),
            "channelTree" to channelTree(owner)
        )
    }

    /** Compact projection for the client GUI; large telemetry/inventory contents remain in their APIs. */
    fun overview(owner: ComputerControlDeskBlockEntity, origin: BlockPos): Map<String, Any> {
        val level = owner.level
        if (level == null) return snapshot(owner)
        val network = ConsoleMultiblockManager.resolve(level, owner.blockPos)
        val objects = list(owner).map(::compactObject)
        return linkedMapOf(
            "state" to network.state.name.lowercase(),
            "active" to (network.state == ConsoleNetworkState.ACTIVE && network.owner === owner),
            "revision" to network.revision,
            "originX" to origin.x,
            "originY" to origin.y,
            "originZ" to origin.z,
            "objects" to objects,
            "counts" to counts(objects),
            "channelTree" to channelTree(owner)
        )
    }

    private fun channelTree(owner: ComputerControlDeskBlockEntity): Map<String, Any> = linkedMapOf(
        "modules" to ComputerChannelRegistry.ls(owner, "/modules").map { module ->
            val path = module["path"]?.toString().orEmpty()
            linkedMapOf<String, Any>(
                "name" to module["name"].toString(),
                "label" to module["label"].toString(),
                "path" to path,
                "channels" to ComputerChannelRegistry.ls(owner, path)
            )
        },
        "wires" to ComputerChannelRegistry.ls(owner, "/wires"),
        "groups" to ComputerChannelRegistry.ls(owner, "/groups").map { group ->
            val path = group["path"]?.toString().orEmpty()
            linkedMapOf<String, Any>(
                "id" to group["id"].toString(),
                "name" to group["name"].toString(),
                "path" to path,
                "channels" to ComputerChannelRegistry.ls(owner, path)
            )
        }
    )

    private fun counts(objects: List<Map<String, Any>>): Map<String, Int> =
        DeskIoCategory.entries.associateTo(linkedMapOf()) { category ->
            category.serializedName to objects.count { it["category"] == category.serializedName }
        }

    private fun memberObjects(member: ConsoleMember): List<Map<String, Any>> = buildList {
        val desk = member.desk
        for (socket in 0 until desk.socketCount()) {
            val module = desk.module(socket) ?: continue
            val moduleId = AeroworksModuleAccess.id(module).toString()
            val displayType = CCModuleTypes.displayType(module.type())
            val radarType = CCModuleTypes.radarDisplayType(module.type())
            val socketName = DeskSockets.name(socket)
            val customLabel = module.customName()?.string?.trim().orEmpty()

            if (displayType != null || radarType != null) {
                val display = AeroworksDeskAccess.display(desk, socket)
                add(linkedMapOf<String, Any>(
                    "id" to "module:${member.id}:$socket",
                    "category" to DeskIoCategory.DISPLAY.serializedName,
                    "kind" to if (radarType != null) "radar_display" else "display",
                    "label" to customLabel.ifEmpty {
                        if (radarType != null) "Radar Display" else "Display"
                    },
                    "memberId" to member.id,
                    "memberIndex" to member.index,
                    "memberX" to member.pos.x,
                    "memberY" to member.pos.y,
                    "memberZ" to member.pos.z,
                    "socket" to socket,
                    "socketName" to socketName,
                    "moduleId" to moduleId,
                    "binding" to DisplayBindings.describe(DisplayBindings.get(desk, socket))
                ).apply {
                    display?.let {
                        put("mode", if (it.pixels == null) "text" else "pixels")
                        put("pixelWidth", it.type.pixelWidth)
                        put("pixelHeight", it.type.pixelHeight)
                    }
                    radarType?.let {
                        put("radarType", it.name.lowercase())
                        put("radarSources", RadarSourceRegistry.sources(desk).map { source ->
                            linkedMapOf<String, Any>(
                                "id" to source.id,
                                "memberIndex" to source.memberIndex,
                                "memberId" to source.memberId,
                                "x" to source.ingressPos.x,
                                "y" to source.ingressPos.y,
                                "z" to source.ingressPos.z,
                                "status" to source.status.name.lowercase()
                            )
                        })
                    }
                })
                continue
            }

            val values = AeroworksModuleAccess.values(module)
            if (values.isEmpty()) continue
            val kind = AeroworksModuleAccess.kind(module)
            add(linkedMapOf(
                "id" to "module:${member.id}:$socket",
                "category" to DeskIoCategory.CONTROL.serializedName,
                "kind" to kind,
                "label" to customLabel.ifEmpty { kind.replace('_', ' ') },
                "memberId" to member.id,
                "memberIndex" to member.index,
                "memberX" to member.pos.x,
                "memberY" to member.pos.y,
                "memberZ" to member.pos.z,
                "socket" to socket,
                "socketName" to socketName,
                "moduleId" to moduleId,
                "values" to values.toMap(),
                "channels" to values.map { (channel, value) ->
                    linkedMapOf<String, Any>(
                        "id" to "control:${member.id}:$socket:$moduleId:$channel",
                        "name" to channel,
                        "value" to value,
                        "mutable" to false
                    )
                }
            ))
        }
    }

    private fun channelGroupObjects(owner: ComputerControlDeskBlockEntity): List<Map<String, Any>> =
        owner.channelGroups.definitions().map { group ->
            val path = "/groups/${group.name}"
            val members = ComputerChannelRegistry.ls(owner, path)
            linkedMapOf(
                "id" to "channel-group:${group.id}",
                "category" to DeskIoCategory.CONTROL.serializedName,
                "kind" to "channel_group",
                "label" to group.name,
                "groupId" to group.id.toString(),
                "path" to path,
                "mutable" to true,
                "memberCount" to group.bindings.size,
                "availableCount" to members.count { it["available"] != false },
                "members" to members
            )
        }

    private fun telemetryObjects(owner: ComputerControlDeskBlockEntity): List<Map<String, Any>> =
        TelemetryRuntime.describeSources(owner).mapNotNull { (sourceId, raw) ->
            val source = raw as? Map<*, *> ?: return@mapNotNull null
            val kind = source["kind"]?.toString().orEmpty().ifEmpty { "display_link" }
            val label = source["alias"]?.toString()
                ?: source["createLabel"]?.toString()
                ?: kind.replace('_', ' ')
            linkedMapOf<String, Any>(
                "id" to "telemetry:$sourceId",
                "category" to DeskIoCategory.INFORMATION.serializedName,
                "kind" to "display_link",
                "label" to label,
                "sourceId" to sourceId,
                "informationKind" to kind
            ).apply {
                source.forEach { (key, value) ->
                    if (key is String && value != null) put(key, value)
                }
            }
        }

    private fun storageObjects(owner: ComputerControlDeskBlockEntity): List<Map<String, Any>> {
        val graph = runCatching { PeripheralNetworkBuilder.build(owner) }.getOrNull() ?: return emptyList()
        return graph.peripherals.mapNotNull { node ->
            val inventory = node.matches("inventory")
            val fluids = node.matches("fluid_storage") || node.matches("fluidstorage")
            if (!inventory && !fluids) return@mapNotNull null
            val side = node.side.name.lowercase(Locale.ROOT)
            linkedMapOf<String, Any>(
                "id" to "storage:${node.desk.id}:$side",
                "category" to DeskIoCategory.INFORMATION.serializedName,
                "kind" to "storage_connection",
                "label" to node.primaryType,
                "sourceId" to "storage:${node.desk.id}:$side",
                "informationKind" to "storage",
                "memberId" to node.desk.id,
                "memberIndex" to node.desk.member.index,
                "side" to side,
                "x" to node.pos.x,
                "y" to node.pos.y,
                "z" to node.pos.z,
                "types" to node.types.toList(),
                "inventory" to inventory,
                "fluids" to fluids,
                "available" to true
            )
        }
    }

    private fun radarInformationObjects(owner: ComputerControlDeskBlockEntity): List<Map<String, Any>> {
        val level = owner.level ?: return emptyList()
        val result = mutableListOf<Map<String, Any>>()
        val seenControllers = hashSetOf<BlockPos>()

        RadarSourceRegistry.sources(owner).forEach { source ->
            val ingressDesk = level.getBlockEntity(source.ingressPos) as? ConsoleBlockEntity
            val controller = ingressDesk?.let(RadarNetworkTopology::filtererForEndpoint)
            val status = source.status.name.lowercase()

            result += linkedMapOf<String, Any>(
                "id" to "radar-data-link:${source.id}",
                "category" to DeskIoCategory.INFORMATION.serializedName,
                "kind" to "radar_data_link",
                "label" to "Radar Data Link · Desk ${source.memberIndex}",
                "sourceId" to source.id,
                "informationKind" to "radar_data_link",
                "memberId" to source.memberId,
                "memberIndex" to source.memberIndex,
                "x" to source.ingressPos.x,
                "y" to source.ingressPos.y,
                "z" to source.ingressPos.z,
                "status" to status
            ).apply {
                source.radarPos?.let { radar ->
                    put("radarX", radar.x)
                    put("radarY", radar.y)
                    put("radarZ", radar.z)
                }
                controller?.let { pos ->
                    put("networkControllerX", pos.x)
                    put("networkControllerY", pos.y)
                    put("networkControllerZ", pos.z)
                }
            }

            if (controller != null && seenControllers.add(controller.immutable())) {
                result += linkedMapOf<String, Any>(
                    "id" to "radar-network-controller:${level.dimension().location()}:${controller.x},${controller.y},${controller.z}",
                    "category" to DeskIoCategory.INFORMATION.serializedName,
                    "kind" to "radar_network_controller",
                    "label" to "Radar Network Controller",
                    "sourceId" to "${level.dimension().location()}:${controller.x},${controller.y},${controller.z}",
                    "informationKind" to "radar_network_controller",
                    "x" to controller.x,
                    "y" to controller.y,
                    "z" to controller.z,
                    "status" to status,
                    "dataLinkX" to source.ingressPos.x,
                    "dataLinkY" to source.ingressPos.y,
                    "dataLinkZ" to source.ingressPos.z
                ).apply {
                    source.radarPos?.let { radar ->
                        put("radarX", radar.x)
                        put("radarY", radar.y)
                        put("radarZ", radar.z)
                    }
                }
            }
        }
        return result
    }

    private fun wireObjects(owner: ComputerControlDeskBlockEntity): List<Map<String, Any>> =
        owner.wireBank.describeChannels().mapNotNull { (name, raw) ->
            val channel = raw as? Map<*, *> ?: return@mapNotNull null
            val channelId = channel["id"]?.toString() ?: return@mapNotNull null
            linkedMapOf<String, Any>(
                "id" to "wire:$channelId",
                "category" to DeskIoCategory.OUTPUT.serializedName,
                "kind" to "wire_channel",
                "label" to name,
                "channelId" to channelId,
                "name" to name
            ).apply {
                channel.forEach { (key, value) ->
                    if (key is String && value != null) put(key, value)
                }
            }
        }

    private fun compactObject(source: Map<String, Any>): Map<String, Any> {
        return when (source["category"]?.toString()) {
            DeskIoCategory.CONTROL.serializedName -> pick(
                source,
                "id", "category", "kind", "label", "memberId", "memberIndex",
                "memberX", "memberY", "memberZ", "socket", "socketName", "moduleId", "values",
                "channels", "groupId", "path", "mutable", "memberCount", "availableCount", "members"
            )
            DeskIoCategory.DISPLAY.serializedName -> pick(
                source,
                "id", "category", "kind", "label", "memberId", "memberIndex",
                "memberX", "memberY", "memberZ", "socket", "socketName", "moduleId",
                "binding", "mode", "pixelWidth", "pixelHeight", "radarType", "radarSources"
            )
            DeskIoCategory.INFORMATION.serializedName -> pick(
                source,
                "id", "category", "kind", "label", "sourceId", "informationKind",
                "sourceType", "supported", "available", "stale", "ageTicks", "revision",
                "memberId", "memberIndex", "side", "x", "y", "z", "types", "inventory", "fluids",
                "status", "dataLinkX", "dataLinkY", "dataLinkZ", "radarX", "radarY", "radarZ",
                "networkControllerX", "networkControllerY", "networkControllerZ"
            ).toMutableMap().apply {
                put("summary", informationSummary(source))
            }
            DeskIoCategory.OUTPUT.serializedName -> pick(
                source,
                "id", "category", "kind", "label", "channelId", "name",
                "value", "backend", "connected", "connections", "enabled"
            )
            else -> pick(source, "id", "category", "kind", "label")
        }
    }

    private fun pick(source: Map<String, Any>, vararg keys: String): Map<String, Any> =
        linkedMapOf<String, Any>().apply {
            keys.forEach { key -> source[key]?.let { put(key, it) } }
        }

    private fun informationSummary(source: Map<String, Any>): String {
        when (source["kind"]?.toString()) {
            "storage_connection" -> {
                val inventory = source["inventory"] == true
                val fluids = source["fluids"] == true
                return when {
                    inventory && fluids -> "items + fluids"
                    inventory -> "inventory"
                    fluids -> "fluids"
                    else -> "storage"
                }
            }
            "radar_data_link", "radar_network_controller" -> return source["status"]?.toString().orEmpty()
        }
        val displayText = source["displayText"] as? List<*>
        displayText?.firstOrNull()?.toString()?.takeIf(String::isNotBlank)?.let { return it }
        val value = source["value"] as? Map<*, *> ?: return source["informationKind"]?.toString().orEmpty()
        value["percent"]?.let { return "%.1f%%".format((it as Number).toDouble()) }
        value["count"]?.let { return it.toString() }
        value["totalCount"]?.let { return "${it} items" }
        value["buckets"]?.let { return "%.2f B".format((it as Number).toDouble()) }
        value["totalAmount"]?.let { return "${it} mB" }
        return source["informationKind"]?.toString().orEmpty()
    }
}
