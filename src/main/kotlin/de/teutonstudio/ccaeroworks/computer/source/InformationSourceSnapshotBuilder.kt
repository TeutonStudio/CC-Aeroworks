package de.teutonstudio.ccaeroworks.computer.source

import de.teutonstudio.ccaeroworks.compat.aeroworks.DeskSockets
import de.teutonstudio.ccaeroworks.compat.createradar.RadarNetworkControllerLookup
import de.teutonstudio.ccaeroworks.computer.ComputerControlDeskBlockEntity
import de.teutonstudio.ccaeroworks.computer.DisplayScriptDiagnosticsRegistry
import de.teutonstudio.ccaeroworks.computer.DisplayScriptRuntimeObservation
import de.teutonstudio.ccaeroworks.computer.PeripheralNetworkBuilder
import de.teutonstudio.ccaeroworks.display.DisplayBinding
import de.teutonstudio.ccaeroworks.display.DisplayBindings
import de.teutonstudio.ccaeroworks.display.DisplayScriptCatalog
import de.teutonstudio.ccaeroworks.display.RadarSourceRegistry
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager
import de.teutonstudio.ccaeroworks.multiblock.ConsoleNetworkState
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
            sources = sources.sortedWith(compareBy<InformationSourceView>({ it.kind.ordinal }, { it.label }, { it.id })),
            displayScripts = buildDisplayScripts(owner)
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

    private data class ScriptBindingRef(
        val path: String,
        val deskId: String,
        val deskIndex: Int,
        val socket: Int,
        val socketName: String
    )

    private fun buildDisplayScripts(owner: ComputerControlDeskBlockEntity): List<DisplayScriptInformationView> {
        val catalog = runCatching { DisplayScriptCatalog.scan(owner) }.getOrDefault(emptyList())
        val descriptors = catalog.associateBy { it.path }
        val bindings = displayScriptBindings(owner)
        val runtime = DisplayScriptDiagnosticsRegistry.snapshot(owner)
        val telemetry = runCatching { TelemetryRuntime.describeSources(owner) }.getOrDefault(emptyMap())
        val paths = linkedSetOf<String>()
        paths += descriptors.keys
        paths += bindings.map(ScriptBindingRef::path)

        return paths.take(DisplayScriptCatalog.MAX_SCRIPTS).map { path ->
            val descriptor = descriptors[path]
            val scriptBindings = bindings.filter { it.path == path }
            val observations = runtime.filter { it.path == path }
            val roles = linkedSetOf<String>()
            if (descriptor?.display == true) roles += "DISPLAY"
            if (descriptor?.touchDisplay == true) roles += "TOUCH"
            if (descriptor?.imports?.contains("cc_aeroworks.ui") == true) roles += "REACTIVE_UI"
            if (scriptBindings.isNotEmpty()) roles += "LEGACY_HANDLER"
            observations.flatMapTo(roles) { it.roles.map(String::uppercase) }

            val status = when {
                descriptor == null && scriptBindings.isNotEmpty() -> "missing"
                observations.isNotEmpty() -> "observed"
                scriptBindings.isNotEmpty() -> "bound"
                else -> "detected"
            }

            DisplayScriptInformationView(
                path = path,
                name = descriptor?.name ?: path.substringAfterLast('/').substringBeforeLast('.'),
                status = status,
                roles = roles.toList(),
                imports = descriptor?.imports.orEmpty(),
                declaredTouchEvents = descriptor?.declaredTouchEvents.orEmpty(),
                instances = scriptBindings.take(MAX_SCRIPT_INSTANCES).map { binding ->
                    val observation = observations.firstOrNull {
                        it.deskId == binding.deskId && it.socket == binding.socket
                    }
                    DisplayScriptInstanceView(
                        deskId = binding.deskId,
                        deskIndex = binding.deskIndex,
                        socket = binding.socket,
                        socketName = binding.socketName,
                        status = if (observation != null) "observed" else "bound",
                        dependencies = observation?.dependencies.orEmpty().take(MAX_SCRIPT_DEPENDENCIES).map { dependency ->
                            DisplayScriptDependencyView(
                                key = dependency.key,
                                label = dependencyLabel(dependency.key, telemetry),
                                kind = dependency.kind,
                                phases = dependency.phases.take(MAX_DEPENDENCY_PHASES)
                            )
                        },
                        touchEvents = observation?.touchEvents.orEmpty().take(MAX_TOUCH_EVENTS)
                    )
                }
            )
        }.sortedBy(DisplayScriptInformationView::path)
    }

    private fun displayScriptBindings(owner: ComputerControlDeskBlockEntity): List<ScriptBindingRef> {
        val level = owner.level ?: return emptyList()
        val network = ConsoleMultiblockManager.resolve(level, owner.blockPos)
        if (network.state != ConsoleNetworkState.ACTIVE || network.owner !== owner) return emptyList()
        return buildList {
            network.members.forEach { member ->
                repeat(member.desk.socketCount()) { socket ->
                    val binding = DisplayBindings.get(member.desk, socket) as? DisplayBinding.LuaHandler ?: return@repeat
                    val path = DisplayScriptCatalog.normalizePath(binding.path) ?: return@repeat
                    add(
                        ScriptBindingRef(
                            path = path,
                            deskId = member.id,
                            deskIndex = member.index,
                            socket = socket,
                            socketName = DeskSockets.name(socket)
                        )
                    )
                }
            }
        }
    }

    private fun dependencyLabel(key: String, telemetry: Map<String, Any>): String {
        if (key == "telemetry:*") return "All telemetry sources"
        if (!key.startsWith("telemetry:")) return key
        val sourceId = key.removePrefix("telemetry:")
        val entry = telemetry.entries.firstOrNull { (mapKey, raw) ->
            mapKey == sourceId || (raw as? Map<*, *>)?.get("id")?.toString() == sourceId
        }
        val info = entry?.value as? Map<*, *> ?: return sourceId
        return sequenceOf(info["alias"], info["createLabel"], info["sourceType"], sourceId)
            .firstOrNull { it != null }
            .toString()
    }

    private fun position(value: Any?): BlockPos? {
        val map = value as? Map<*, *> ?: return null
        val x = (map["x"] as? Number)?.toInt() ?: return null
        val y = (map["y"] as? Number)?.toInt() ?: return null
        val z = (map["z"] as? Number)?.toInt() ?: return null
        return BlockPos(x, y, z)
    }

    private const val MAX_SCRIPT_INSTANCES = 32
    private const val MAX_SCRIPT_DEPENDENCIES = 64
    private const val MAX_DEPENDENCY_PHASES = 8
    private const val MAX_TOUCH_EVENTS = 8
}
