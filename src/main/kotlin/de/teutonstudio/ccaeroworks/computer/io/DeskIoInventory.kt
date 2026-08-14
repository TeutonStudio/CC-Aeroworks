package de.teutonstudio.ccaeroworks.computer.io

import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksDeskAccess
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksModuleAccess
import de.teutonstudio.ccaeroworks.compat.aeroworks.DeskSockets
import de.teutonstudio.ccaeroworks.computer.ComputerControlDeskBlockEntity
import de.teutonstudio.ccaeroworks.display.DisplayBindings
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMember
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager
import de.teutonstudio.ccaeroworks.multiblock.ConsoleNetworkState
import de.teutonstudio.ccaeroworks.registry.CCModuleTypes
import de.teutonstudio.ccaeroworks.telemetry.TelemetryRuntime

enum class DeskIoCategory(val serializedName: String) {
    CONTROL("control"),
    DISPLAY("display"),
    INFORMATION("information"),
    OUTPUT("output")
}

/**
 * Server-authoritative inventory backing the future ControlDesk I/O overview.
 *
 * The inventory deliberately references the existing owners of each subsystem instead of copying
 * their state into another persistent model: Aeroworks owns modules, DisplayBindings owns routing,
 * TelemetryRuntime owns Display Link inputs and WireChannelBank owns virtual redstone outputs.
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
            objects += telemetryObjects(owner)
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
        val counts = DeskIoCategory.entries.associateTo(linkedMapOf()) { category ->
            category.serializedName to objects.count { it["category"] == category.serializedName }
        }
        return linkedMapOf(
            "state" to network.state.name.lowercase(),
            "active" to (network.state == ConsoleNetworkState.ACTIVE && network.owner === owner),
            "revision" to network.revision,
            "objects" to objects,
            "counts" to counts
        )
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
                    radarType?.let { put("radarType", it.name.lowercase()) }
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
                "socket" to socket,
                "socketName" to socketName,
                "moduleId" to moduleId,
                "values" to values.toMap()
            ))
        }
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
}
