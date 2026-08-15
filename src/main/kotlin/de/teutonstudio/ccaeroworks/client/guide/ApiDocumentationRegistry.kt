package de.teutonstudio.ccaeroworks.client.guide

enum class ApiAvailability {
    EMBEDDED,
    CONTROL_DESK,
    BOTH
}

data class ApiMethodDocumentation(
    val name: String,
    val signature: String,
    val result: String = "",
    val reactive: Boolean = false
)

data class ApiEventDocumentation(
    val name: String,
    val arguments: String
)

data class ApiModuleDocumentation(
    val id: String,
    val displayName: String,
    val moduleName: String?,
    val availability: ApiAvailability,
    val summaryKey: String,
    val methods: List<ApiMethodDocumentation>,
    val events: List<ApiEventDocumentation> = emptyList()
)

/**
 * Single structured reference used by the in-game handbook and repository validators.
 * Tutorials remain hand-written, but public method/event names should be added here when the
 * corresponding implementation is added so the book cannot silently keep teaching retired APIs.
 */
object ApiDocumentationRegistry {
    val modules: List<ApiModuleDocumentation> = listOf(
        ApiModuleDocumentation(
            id = "control_desk",
            displayName = "ControlDesk",
            moduleName = null,
            availability = ApiAvailability.CONTROL_DESK,
            summaryKey = "guide.cc_aeroworks.api.control_desk.summary",
            methods = listOf(
                ApiMethodDocumentation("getInfo", "getInfo()", "table"),
                ApiMethodDocumentation("getSockets", "getSockets()", "table"),
                ApiMethodDocumentation("getModules", "getModules()", "table"),
                ApiMethodDocumentation("getModule", "getModule(socket)", "table | nil"),
                ApiMethodDocumentation("getInput", "getInput(socket)", "number | table"),
                ApiMethodDocumentation("getInputs", "getInputs()", "table"),
                ApiMethodDocumentation("getDisplays", "getDisplays()", "table"),
                ApiMethodDocumentation("getDisplay", "getDisplay(socket)", "table"),
                ApiMethodDocumentation("getDisplaySize", "getDisplaySize(socket)", "table"),
                ApiMethodDocumentation("setDisplayText", "setDisplayText(socket, text)", "string"),
                ApiMethodDocumentation("setDisplayNumber", "setDisplayNumber(socket, value, zeroPad?)", "string"),
                ApiMethodDocumentation("setDisplayPixel", "setDisplayPixel(socket, x, y, enabled)", "boolean"),
                ApiMethodDocumentation("setDisplayPixels", "setDisplayPixels(socket, rows)", "table"),
                ApiMethodDocumentation("clearDisplay", "clearDisplay(socket)"),
                ApiMethodDocumentation("clearDisplayPixels", "clearDisplayPixels(socket)")
            ),
            events = listOf(
                ApiEventDocumentation(
                    "cc_aeroworks_desk_input",
                    "peripheralName, socket, moduleId, value, channel, socketName"
                )
            )
        ),
        ApiModuleDocumentation(
            id = "peripherals",
            displayName = "peripherals",
            moduleName = "cc_aeroworks.peripherals",
            availability = ApiAvailability.EMBEDDED,
            summaryKey = "guide.cc_aeroworks.api.peripherals.summary",
            methods = listOf(
                ApiMethodDocumentation("find", "find(type)", "handle | table | nil"),
                ApiMethodDocumentation("findAll", "findAll(type)", "table"),
                ApiMethodDocumentation("wrap", "wrap(position, type?)", "handle | nil"),
                ApiMethodDocumentation("getDesks", "getDesks()", "table"),
                ApiMethodDocumentation("getTree", "getTree()", "table"),
                ApiMethodDocumentation("getTypes", "getTypes()", "table"),
                ApiMethodDocumentation("getNetwork", "getNetwork()", "table"),
                ApiMethodDocumentation("refresh", "refresh()", "table")
            ),
            events = listOf(
                ApiEventDocumentation("cc_aeroworks_peripheral_attached", "address, primaryType"),
                ApiEventDocumentation("cc_aeroworks_peripheral_detached", "address, primaryType")
            )
        ),
        ApiModuleDocumentation(
            id = "controls",
            displayName = "controls",
            moduleName = "cc_aeroworks.controls",
            availability = ApiAvailability.EMBEDDED,
            summaryKey = "guide.cc_aeroworks.api.controls.summary",
            methods = listOf(
                ApiMethodDocumentation("getChannels", "getChannels()", "table"),
                ApiMethodDocumentation("getState", "getState(deskId, socket, channel)", "table"),
                ApiMethodDocumentation("override", "override(deskId, socket, channel, value)", "table"),
                ApiMethodDocumentation("overrideBatch", "overrideBatch(commands)", "number"),
                ApiMethodDocumentation("release", "release(deskId, socket, channel)", "boolean"),
                ApiMethodDocumentation("releaseAll", "releaseAll()", "number")
            ),
            events = listOf(
                ApiEventDocumentation(
                    "cc_aeroworks_control_override",
                    "action, deskId, deskIndex, socket, socketName, channel, value, mode"
                ),
                ApiEventDocumentation(
                    "cc_aeroworks_control_release",
                    "deskId, socket, socketName, channel, reason"
                )
            )
        ),
        ApiModuleDocumentation(
            id = "telemetry",
            displayName = "telemetry",
            moduleName = "cc_aeroworks.telemetry",
            availability = ApiAvailability.EMBEDDED,
            summaryKey = "guide.cc_aeroworks.api.telemetry.summary",
            methods = listOf(
                ApiMethodDocumentation("list", "list()", "table", reactive = true),
                ApiMethodDocumentation("get", "get(nameOrId)", "table | nil", reactive = true),
                ApiMethodDocumentation("find", "find(type)", "table", reactive = true),
                ApiMethodDocumentation("rename", "rename(nameOrId, alias)", "table"),
                ApiMethodDocumentation("clearName", "clearName(nameOrId)", "table"),
                ApiMethodDocumentation("getStatus", "getStatus()", "table"),
                ApiMethodDocumentation("getDocks", "getDocks()", "table"),
                ApiMethodDocumentation("getDock", "getDock(nameOrId)", "handle | nil")
            ),
            events = listOf(
                ApiEventDocumentation("cc_aeroworks_telemetry_added", "sourceId, revision"),
                ApiEventDocumentation("cc_aeroworks_telemetry_changed", "sourceId, revision"),
                ApiEventDocumentation("cc_aeroworks_telemetry_removed", "sourceId"),
                ApiEventDocumentation("cc_aeroworks_dock_changed", "dockId, state, locked, remoteSubLevelId")
            )
        ),
        ApiModuleDocumentation(
            id = "ui",
            displayName = "ui",
            moduleName = "cc_aeroworks.ui",
            availability = ApiAvailability.EMBEDDED,
            summaryKey = "guide.cc_aeroworks.api.ui.summary",
            methods = listOf(
                ApiMethodDocumentation("app", "ui.app(root, options?)", "app"),
                ApiMethodDocumentation("component", "ui.component(name, content)", "component"),
                ApiMethodDocumentation("state", "ui.state(key, initial)", "state", reactive = true),
                ApiMethodDocumentation("derived", "ui.derived(key, calculation, equals?)", "derived state", reactive = true),
                ApiMethodDocumentation("source", "ui.source(key, getter)", "state", reactive = true),
                ApiMethodDocumentation("Column", "ui.Column(props, content)"),
                ApiMethodDocumentation("Row", "ui.Row(props, content)"),
                ApiMethodDocumentation("Box", "ui.Box(props, content)"),
                ApiMethodDocumentation("Text", "ui.Text(text | props)"),
                ApiMethodDocumentation("ProgressBar", "ui.ProgressBar(props)"),
                ApiMethodDocumentation("Button", "ui.Button(props)"),
                ApiMethodDocumentation("Canvas", "ui.Canvas(props)"),
                ApiMethodDocumentation("LazyColumn", "ui.LazyColumn(props, itemContent)"),
                ApiMethodDocumentation("navigator", "ui.navigator(key, initialRoute)", "navigator", reactive = true),
                ApiMethodDocumentation("Route", "ui.Route(navigator, routes)"),
                ApiMethodDocumentation("listDisplays", "ui.listDisplays()", "table"),
                ApiMethodDocumentation("supervise", "ui.supervise()")
            ),
            events = listOf(
                ApiEventDocumentation("cc_aeroworks_ui_invalidated", "")
            )
        )
    )

    val events: List<ApiEventDocumentation> = modules.flatMap(ApiModuleDocumentation::events).distinctBy { it.name }

    fun module(id: String): ApiModuleDocumentation? = modules.firstOrNull { it.id == id }
}
