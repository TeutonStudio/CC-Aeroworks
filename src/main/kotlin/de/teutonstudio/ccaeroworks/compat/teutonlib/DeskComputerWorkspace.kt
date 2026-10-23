package de.teutonstudio.ccaeroworks.compat.teutonlib

import dan200.computercraft.shared.computer.core.ServerComputer
import de.TeutonStudio.TeutonLib.api.computer.ComputerExtensions
import de.TeutonStudio.TeutonLib.api.computer.ComputerWorkspace
import de.TeutonStudio.TeutonLib.api.computer.ComputerWorkspace.Action
import de.TeutonStudio.TeutonLib.api.computer.ComputerWorkspace.Entry
import de.TeutonStudio.TeutonLib.api.computer.ComputerWorkspace.Snapshot
import de.TeutonStudio.TeutonLib.api.computer.SourceView
import de.TeutonStudio.TeutonLib.api.computer.SourceApi
import de.TeutonStudio.TeutonLib.api.computer.SourceMethod
import de.TeutonStudio.TeutonLib.api.computer.PeripheralSource
import de.TeutonStudio.TeutonLib.api.computer.peripheral.PeripheralProviders
import de.TeutonStudio.TeutonLib.api.computer.peripheral.PeripheralTargetProvider
import com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity
import dan200.computercraft.shared.peripheral.modem.wired.WiredModemPeripheral
import dan200.computercraft.shared.peripheral.modem.wired.WiredModemElement
import de.teutonstudio.ccaeroworks.client.guide.ApiReferenceCatalog
import de.TeutonStudio.TeutonLib.api.computer.ChannelView
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.computer.*
import de.teutonstudio.ccaeroworks.computer.channel.ChannelRegistry
import de.teutonstudio.ccaeroworks.computer.channel.channelGroups
import de.teutonstudio.ccaeroworks.computer.source.*
import de.teutonstudio.ccaeroworks.computer.wire.WireConnectionView
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager
import de.teutonstudio.ccaeroworks.multiblock.ConsoleNetworkState
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import java.util.UUID

/** Projection only: persistence, logical channels and override semantics stay with their existing owners. */
object DeskComputerWorkspace : ComputerWorkspace {
    @JvmField val ID = CCAeroworks.id("desk")

    fun register(bus: IEventBus) {
        bus.addListener<FMLCommonSetupEvent> { event -> event.enqueueWork {
            ComputerExtensions.registerWorkspace(ID, this)
            PeripheralProviders.registerTargets(object : PeripheralTargetProvider {
                override fun supports(anchor: net.minecraft.world.level.block.entity.BlockEntity): Boolean =
                    anchor is ConsoleBlockEntity

                override fun owner(anchor: net.minecraft.world.level.block.entity.BlockEntity): net.minecraft.world.level.block.entity.BlockEntity? {
                    if (anchor !is ConsoleBlockEntity) return null
                    val level = anchor.level ?: return null
                    val snapshot = ConsoleMultiblockManager.resolve(level, anchor.blockPos)
                    return if (snapshot.state != ConsoleNetworkState.ACTIVE) null else snapshot.owner?.host
                }
            })
            ComputerExtensions.registerPeripheralSources { context ->
                val owner = ConsoleComputers.get(context.host()) ?: return@registerPeripheralSources emptyMap()
                buildMap {
                    PeripheralNetworkBuilder.build(owner).peripherals.forEach { node ->
                        // The owner's direct sides already come from CC; retain the graph addresses of other desks.
                        if (node.desk.pos != owner.blockPos && !node.address.startsWith("cc_aeroworks:link_"))
                            put(node.address, PeripheralSource(node.target, "cable_network", node.pos.toShortString()))
                        val element = (node.target as? WiredModemPeripheral)?.node?.element as? WiredModemElement
                        element?.remotePeripherals?.forEach { (name, peripheral) ->
                            val pos = (peripheral.target as? net.minecraft.world.level.block.entity.BlockEntity)?.blockPos
                            putIfAbsent(name, PeripheralSource(peripheral, "wired", pos?.toShortString()))
                        }
                    }
                }
            }
            ComputerExtensions.registerSourceDocumentation { _, source ->
                val ids = when {
                    source.types.any { it.equals("ControlDesk", ignoreCase = true) } -> listOf("control_desk", "radar_control_desk")
                    else -> emptyList()
                }
                documentedMethods(ids)
            }
            ComputerExtensions.registerHosts { system ->
                system.getComponent(CCComputerComponents.CONSOLE)?.owner()?.takeIf {
                    it.getServerComputer()?.mainThreadMonitor === system.mainThreadMonitor
                }?.host
            }
            ComputerExtensions.registerSources { context ->
                val owner = ConsoleComputers.get(context.host()) ?: return@registerSources emptyList()
                InformationSourceSnapshotBuilder.build(owner).sources.map { source ->
                    SourceView("cc_aeroworks:${source.id}", listOf(source.kind.id), "local", "${source.x}, ${source.y}, ${source.z}", "")
                }
            }
            ComputerExtensions.registerChannelAuthority { context -> ConsoleComputers.get(context.host()) != null }
            ComputerExtensions.registerChannels { context ->
                val owner = ConsoleComputers.get(context.host()) ?: return@registerChannels emptyList()
                ChannelRegistry.snapshot(owner).channels.map { channel ->
                    ChannelView("cc_aeroworks:${channel.id}", "output", "", true, channel.value, channel.connections.size, "cc_aeroworks", false)
                }
            }
        } }
    }

    private fun owner(computer: ServerComputer): ConsoleComputer? {
        val pos = computer.position
        val host = computer.level.chunkSource.getChunkNow(pos.x shr 4, pos.z shr 4)?.blockEntities?.get(pos) ?: return null
        return ConsoleComputers.get(host)?.takeIf { it.getServerComputer() === computer }
    }

    override fun owns(computer: ServerComputer): Boolean = owner(computer) != null

    private fun validatedOwner(player: ServerPlayer, computer: ServerComputer): ConsoleComputer {
        val owner = owner(computer) ?: error("Computer is no longer available")
        check(!player.isSpectator && computer.checkUsable(player)) { "Computer is not usable" }
        val menu = player.containerMenu as? dan200.computercraft.shared.computer.inventory.AbstractComputerMenu
            ?: error("Open the computer again")
        check(menu.computer === computer && menu.stillValid(player)) { "Computer menu is no longer valid" }
        val network = de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager.resolve(computer.level, owner.blockPos)
        check(network.members.none { it.desk.hasController() && !it.desk.checkUser(player.uuid) }) { "Console is in use" }
        return owner
    }

    override fun snapshot(player: ServerPlayer, computer: ServerComputer, page: Int): Snapshot {
        val owner = validatedOwner(player, computer)
        return if (page == 1) sources(owner, computer) else channels(owner)
    }

    override fun execute(player: ServerPlayer, computer: ServerComputer, command: ComputerWorkspace.Command) {
        val owner = validatedOwner(player, computer)
        require(command.page == 2) { "Sources are read-only" }
        val current = channels(owner)
        fun find(entries: List<Entry>): Entry? {
            for (entry in entries) {
                if (entry.id == command.selected) return entry
                find(entry.children)?.let { return it }
            }
            return null
        }
        val selected = find(current.entries)
        val available = current.actions + selected?.actions.orEmpty()
        require(available.any { it.id == command.action && !it.clientSide }) { "Action is no longer available" }
        val name = command.text.trim()
        val bank = owner.channelGroups()
        when (command.action) {
            "add_wire" -> owner.wireBank.addChannel(name)
            "add_group" -> bank.addGroup(name)
            "rename_wire" -> owner.wireBank.renameChannel(UUID.fromString(command.selected.removePrefix("wire:")), name)
            "remove_wire" -> owner.wireBank.removeChannel(UUID.fromString(command.selected.removePrefix("wire:")))
            "rename_group" -> bank.renameGroup(UUID.fromString(selected!!.group), name)
            "remove_group" -> bank.removeGroup(UUID.fromString(selected!!.group))
            "rename_binding" -> bank.renameBinding(UUID.fromString(selected!!.group), selected.metadata.getString("alias"), name)
            "unbind" -> bank.unbind(UUID.fromString(selected!!.group), selected.metadata.getString("alias"))
            "bind" -> {
                val group = UUID.fromString(command.group)
                require(current.entries.flatMap { it.children }.any { it.id == "group:$group" }) { "Select a user group first" }
                val target = selected!!.target
                require(target.isNotEmpty() && target == command.target && ChannelRegistry.findById(owner, target) != null) { "Select an available channel" }
                bank.bind(group, name, target)
            }
            else -> error("Unsupported workspace action")
        }
    }

    private fun action(id: String, label: String, text: Boolean = false, confirm: Boolean = false, client: Boolean = false) =
        Action(id, "ui.cc_aeroworks.workspace.$label", text, confirm, client)

    private fun entry(id: String, label: String, detail: String = "", group: String = "", target: String = "",
                      selectable: Boolean = false, collapsed: Boolean = false, children: List<Entry> = emptyList(),
                      actions: List<Action> = emptyList(), metadata: CompoundTag = CompoundTag()) =
        Entry(id, label, detail, group, target, selectable, collapsed, children, actions, metadata)

    private fun connections(id: String, values: List<WireConnectionView>): List<Entry> = values.mapIndexed { index, value ->
        entry("$id/connection:$index", "→ ${value.x}, ${value.y}, ${value.z}", value.side)
    }

    private fun connectionMetadata(values: List<WireConnectionView>) = CompoundTag().also { tag ->
        tag.put("connections", ListTag().also { list -> values.forEach { value -> list.add(CompoundTag().also {
            it.putInt("x", value.x); it.putInt("y", value.y); it.putInt("z", value.z); it.putString("side", value.side)
        }) } })
    }

    private fun channels(owner: ConsoleComputer): Snapshot {
        val registry = ChannelRegistry.snapshot(owner)
        fun channelActions() = buildList {
            add(action("bind", "bind", text = true))
            if (registry.wireBackend == "drivebywire") add(action("targets", "targets", client = true))
        }
        val modules = registry.modules.map { module ->
            entry(module.id, module.label, "Desk ${module.deskIndex} · ${module.socketName}", children = module.channels.map { channel ->
                entry(channel.id, channel.name, "${channel.value}/15${if (channel.overridden) " · OVR" else ""}",
                    target = channel.id, selectable = true, children = connections(channel.id, channel.connections),
                    actions = channelActions(), metadata = connectionMetadata(channel.connections))
            })
        }
        val wires = registry.wires.map { channel ->
            entry(channel.id, channel.name, "${channel.value}/15 · ${channel.connections.size} connections", target = channel.id,
                selectable = true, children = connections(channel.id, channel.connections),
                actions = listOf(action("rename_wire", "rename", text = true), action("remove_wire", "delete", confirm = true)) + channelActions(),
                metadata = connectionMetadata(channel.connections))
        }
        val groups = registry.groups.map { group ->
            entry("group:${group.id}", group.name, group = group.id.toString(), selectable = true,
                actions = listOf(action("rename_group", "rename", text = true), action("remove_group", "delete", confirm = true)),
                children = group.bindings.map { binding ->
                    entry("binding:${group.id}:${binding.alias}", "${binding.alias} → ${binding.targetLabel}",
                        if (binding.available) "${binding.value ?: 0}/15" else "MISSING", group = group.id.toString(), target = binding.targetId,
                        selectable = true,
                        actions = listOf(action("rename_binding", "rename", text = true), action("unbind", "unbind", confirm = true)) +
                            if (binding.available) channelActions() else emptyList(),
                        metadata = connectionMetadata(ChannelRegistry.findById(owner, binding.targetId)?.connections.orEmpty()).also { it.putString("alias", binding.alias) })
                })
        }
        return Snapshot("teutonlib.computer.channels", "${registry.wireBackend} · ${if (registry.wireEnabled) "enabled" else "disabled"}",
            listOf(entry("section:controls", "ui.cc_aeroworks.workspace.controls", children = modules),
                entry("section:wires", "ui.cc_aeroworks.workspace.wires", children = wires),
                entry("section:groups", "ui.cc_aeroworks.workspace.groups", children = groups)),
            listOf(action("add_wire", "add_wire", text = true), action("add_group", "add_group", text = true)))
    }

    private fun documentedMethods(ids: List<String>): List<SourceMethod> =
        ApiReferenceCatalog.references.filter { it.id in ids }.flatMap { reference ->
            reference.methods.map { signature ->
                val name = signature.substringBefore('(')
                val qualified = if (reference.moduleName == null) signature else "${reference.moduleName}.$signature"
                SourceMethod(name, qualified, reference.availability)
            }
        }.distinctBy { it.name }

    private fun apiMetadata(name: String, types: List<String>, ids: List<String>, gps: Boolean = false, position: String? = null): CompoundTag {
        val methods = if (gps) listOf(SourceMethod("gps.locate")) else documentedMethods(ids)
        return CompoundTag().also { it.put("api", SourceApi(name, types, "local", methods, devicePosition = position).encode()) }
    }

    private fun sources(owner: ConsoleComputer, computer: ServerComputer): Snapshot {
        val snapshot = InformationSourceSnapshotBuilder.build(owner)
        // TeutonLib is authoritative for peripheral addresses and connection metadata. Other source owners stay in Aeroworks.
        val peripherals = ComputerExtensions.sourceViews(computer)
            .filterNot { it.name.startsWith("cc_aeroworks:") && !it.name.startsWith("cc_aeroworks:link_") }
        val categorizedPeripherals = peripherals.map(DeskWorkspaceProjection::categorize)
        val peripheralEntries = categorizedPeripherals.map { source ->
            ComputerExtensions.sourceEntry(computer, source)
        }
        val peripheralSections = DeskWorkspaceProjection.connectionGroups(categorizedPeripherals).map { connection ->
            entry("peripheral_connection:$connection", "teutonlib.computer.$connection", children =
                categorizedPeripherals.zip(peripheralEntries).filter { (source, _) -> source.connection == connection }.map { it.second })
        }
        val kinds = (InformationSourceKinds.CORE + snapshot.sources.map { it.kind }).distinctBy { it.id }.sortedBy { it.order }
        val sections = kinds.filter { it != InformationSourceKinds.DISPLAY_SCRIPT && (it != InformationSourceKinds.STORAGE || peripherals.isEmpty()) }.map { kind ->
            entry("source_kind:${kind.id}", kind.title, children = snapshot.sources.filter { it.kind == kind }.map { source ->
                entry("source:${source.id}", source.label, DeskWorkspaceProjection.sourceDetail(source), children = listOf(
                    entry("source:${source.id}/position", "${source.x}, ${source.y}, ${source.z} ${source.side}"),
                    entry("source:${source.id}/detail", source.details)),
                    metadata = apiMetadata(source.label, listOf(source.kind.id), when (source.kind) {
                        InformationSourceKinds.DISPLAY_LINK -> listOf("telemetry")
                        InformationSourceKinds.GPS -> emptyList()
                        else -> emptyList()
                    }, gps = source.kind == InformationSourceKinds.GPS,
                        position = "${source.x}, ${source.y}, ${source.z}"))
            })
        }
        val scripts = snapshot.displayScripts.map { script ->
            val id = "script:${script.path}"
            val details = buildList {
                add(entry("$id/path", script.path))
                add(entry("$id/roles", "ROLES", script.roles.joinToString(" · ")))
                add(entry("$id/imports", "IMPORTS", children = script.imports.mapIndexed { i, value -> entry("$id/import:$i", value) }))
                add(entry("$id/touch", "TOUCH · DECLARED", children = script.declaredTouchEvents.mapIndexed { i, value -> entry("$id/touch:$i", value) }))
                script.instances.forEachIndexed { index, instance ->
                    val instanceId = "$id/instance:$index"
                    val reactive = instance.dependencies.filter { dependency -> dependency.phases.any { it in setOf("composition", "layout", "draw") } }
                    val observed = instance.dependencies.filterNot { it in reactive }
                    fun dependencyEntries(values: List<DisplayScriptDependencyView>, section: String) = values.mapIndexed { i, dependency ->
                        entry("$instanceId/$section:$i", dependency.label, "${dependency.key} · ${dependency.kind} · ${dependency.phases.joinToString("/")}")
                    }
                    add(entry(instanceId, "DISPLAY ${instance.deskIndex} · ${instance.socketName}", "${instance.status} · ${instance.deskId}",
                        children = listOf(entry("$instanceId/reactive", "REACTIVE", children = dependencyEntries(reactive, "reactive")),
                            entry("$instanceId/observed", "OBSERVED", children = dependencyEntries(observed, "observed")),
                            entry("$instanceId/touch", "TOUCH · ACTIVE", instance.touchEvents.joinToString(" · ")))))
                }
            }
            entry(id, script.name, script.status, collapsed = true, children = details,
                metadata = apiMetadata(script.name, listOf("display_script"),
                    listOf("display") + if ("TOUCH" in script.roles) listOf("touchdisplay") else emptyList()))
        }
        return Snapshot("teutonlib.computer.sources", "${snapshot.sources.size} sources · ${scripts.size} scripts",
            sections + entry("section:peripherals", "ui.cc_aeroworks.workspace.peripherals", children = peripheralSections) +
                entry("source_kind:display_script", "DISPLAY SCRIPTS", children = scripts), emptyList())
    }
}
