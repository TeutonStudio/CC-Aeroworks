package de.teutonstudio.ccaeroworks.client.guide

data class ApiSourceContract(
    val path: String,
    val className: String,
    val internalMethods: Set<String> = emptySet()
)

enum class ApiAccent(val color: Int) {
    DESK(0xFFFFB86C.toInt()),
    PERIPHERALS(0xFF68D4EB.toInt()),
    CHANNELS(0xFF80E27E.toInt()),
    CONTROLS(0xFFFF7A90.toInt()),
    WIRES(0xFFFFD166.toInt()),
    TELEMETRY(0xFF7DB6FF.toInt()),
    DISPLAY(0xFFC792EA.toInt()),
    TOUCH(0xFFFF79C6.toInt()),
    RADAR(0xFFFF8A65.toInt())
}

data class ApiScope(
    val name: String,
    val meaning: String,
    val color: Int
)

data class ApiReference(
    val id: String,
    val name: String,
    val availability: String,
    val accent: ApiAccent,
    val moduleName: String? = null,
    val preferred: Boolean = false,
    val requiredMod: String? = null,
    val methods: List<String>,
    val source: ApiSourceContract? = null
)

/** Canonical public Lua inventory used by the in-game manual and checked by CI. */
object ApiReferenceCatalog {
    val scopes: List<ApiScope> = listOf(
        ApiScope("LOCAL DESK", "direct CC:Tweaked peripheral", 0xFFFFB86C.toInt()),
        ApiScope("EMBEDDED", "ComputerControlDesk only", 0xFFC792EA.toInt()),
        ApiScope("GLOBAL", "injected CraftOS API", 0xFF68D4EB.toInt()),
        ApiScope("MODULE", "available through require(...) too", 0xFF80E27E.toInt()),
        ApiScope("OPTIONAL", "requires an integration mod", 0xFFFF7A90.toInt())
    )

    val typeLegend: List<Pair<String, String>> = listOf(
        "integer" to "whole Lua number",
        "number" to "Lua number; decimals allowed",
        "table[]" to "array-like Lua table",
        "table<K,V>" to "keyed Lua table",
        "handle" to "callable peripheral/API object",
        "T|nil" to "value may be absent",
        "void" to "returns no Lua values"
    )

    private val displayMethods = listOf(
        "resolve(event: table) -> (desk: handle|nil, socket: string|integer)",
        "getSize(event: table) -> table",
        "clear(event: table) -> void",
        "getPixel(event: table, x: integer, y: integer) -> boolean",
        "setPixel(event: table, x: integer, y: integer, enabled: boolean) -> boolean",
        "setPixelBatch(event: table, points: table[], enabled?: boolean) -> integer",
        "setPixels(event: table, rows: string[]) -> string[]",
        "setText(event: table, text: string) -> string",
        "setNumber(event: table, value: number, zeroPad?: boolean) -> string"
    )

    val references: List<ApiReference> = listOf(
        ApiReference(
            "control_desk", "ControlDesk", "LOCAL DESK · EXTERNAL CC:TWEAKED", ApiAccent.DESK,
            methods = listOf(
                "getInfo() -> table",
                "getSocketCount() -> integer",
                "getSockets() -> table[]",
                "getModules() -> table[]",
                "getModule(socket: string|integer) -> table|nil",
                "getInput(socket: string|integer) -> integer|table<string,integer>",
                "getInputs() -> table<integer,integer|table<string,integer>>",
                "getDisplays() -> table[]",
                "getDisplay(socket: string|integer) -> table",
                "setDisplayText(socket: string|integer, text: string) -> string",
                "setDisplayNumber(socket: string|integer, value: number, zeroPad?: boolean) -> string",
                "clearDisplay(socket: string|integer) -> void",
                "clearDisplays() -> integer",
                "getDisplaySize(socket: string|integer) -> table",
                "getDisplayPixel(socket: string|integer, x: integer, y: integer) -> boolean",
                "setDisplayPixel(socket: string|integer, x: integer, y: integer, enabled: boolean) -> boolean",
                "setDisplayPixelBatch(socket: string|integer, points: table[], enabled?: boolean) -> integer",
                "setDisplayPixels(socket: string|integer, rows: string[]) -> string[]",
                "clearDisplayPixels(socket: string|integer) -> void",
                "getDisplayBinding(socket: string|integer) -> table",
                "setDisplayTouchScript(socket: string|integer, path: string) -> table",
                "clearDisplayBinding(socket: string|integer) -> table"
            ),
            source = ApiSourceContract(
                "src/main/kotlin/de/teutonstudio/ccaeroworks/compat/computercraft/ControlDeskPeripheral.kt",
                "ControlDeskPeripheral", setOf("debugDisplayTouchLog")
            )
        ),
        ApiReference(
            "desk_handle", "Desk handle", "EMBEDDED · peripherals.find(\"ControlDesk\")", ApiAccent.PERIPHERALS,
            methods = listOf(
                "getInfo() -> table",
                "getSocketCount() -> integer",
                "getSockets() -> table[]",
                "getModules() -> table[]",
                "getModule(socket: string|integer) -> table|nil",
                "getInput(socket: string|integer) -> integer|table<string,integer>",
                "getInputs() -> table<integer,integer|table<string,integer>>",
                "getDisplays() -> table[]",
                "getDisplay(socket: string|integer) -> table",
                "setDisplayText(socket: string|integer, text: string) -> string",
                "setDisplayNumber(socket: string|integer, value: number, zeroPad?: boolean) -> string",
                "clearDisplay(socket: string|integer) -> void",
                "clearDisplays() -> integer",
                "getDisplaySize(socket: string|integer) -> table",
                "getDisplayPixel(socket: string|integer, x: integer, y: integer) -> boolean",
                "setDisplayPixel(socket: string|integer, x: integer, y: integer, enabled: boolean) -> boolean",
                "setDisplayPixelBatch(socket: string|integer, points: table[], enabled?: boolean) -> integer",
                "setDisplayPixels(socket: string|integer, rows: string[]) -> string[]",
                "clearDisplayPixels(socket: string|integer) -> void",
                "getPeripherals() -> table<string,handle>",
                "find(type: string) -> handle|table<string,handle>|nil",
                "findAll(type: string) -> table<string,handle>",
                "wrap(side: string) -> handle|nil"
            ),
            source = ApiSourceContract(
                "src/main/kotlin/de/teutonstudio/ccaeroworks/computer/PeripheralLuaHandles.kt", "DeskLuaHandle"
            )
        ),
        ApiReference(
            "peripherals", "peripherals", "EMBEDDED · GLOBAL + MODULE", ApiAccent.PERIPHERALS,
            moduleName = "cc_aeroworks.peripherals",
            methods = listOf(
                "find(type: string) -> handle|table<string,handle>|nil",
                "findAll(type: string) -> table<string,handle>",
                "wrap(x: integer, y: integer, z: integer, type?: string) -> handle|nil",
                "wrap(position: table, type?: string) -> handle|nil",
                "getDesks() -> table<string,handle>",
                "getTree() -> table",
                "getTypes() -> table<string,integer>",
                "getNetwork() -> table",
                "refresh() -> table"
            ),
            source = ApiSourceContract(
                "src/main/kotlin/de/teutonstudio/ccaeroworks/computer/ComputerConsoleLuaApi.kt", "ComputerConsoleLuaApi"
            )
        ),
        ApiReference(
            "channels", "channels", "EMBEDDED · GLOBAL + MODULE · HIGH LEVEL", ApiAccent.CHANNELS,
            moduleName = "cc_aeroworks.channels", preferred = true,
            methods = listOf(
                "ls(path?: string) -> table[]",
                "stat(pathOrId: string) -> table",
                "read(pathOrId: string) -> integer",
                "setWire(pathOrId: string, value: integer[0..15]) -> void",
                "pulseWire(pathOrId: string, ticks?: integer, value?: integer[0..15]) -> void",
                "resetWire(pathOrId: string) -> void",
                "override(pathOrId: string, value: integer) -> table",
                "overrideBatch(commands: table[]) -> integer",
                "release(pathOrId: string) -> boolean",
                "releaseAll() -> integer"
            ),
            source = ApiSourceContract(
                "src/main/kotlin/de/teutonstudio/ccaeroworks/computer/ComputerChannelLuaApi.kt", "ComputerChannelLuaApi"
            )
        ),
        ApiReference(
            "controls", "controls", "EMBEDDED · GLOBAL + MODULE · NATIVE -15..15", ApiAccent.CONTROLS,
            moduleName = "cc_aeroworks.controls",
            methods = listOf(
                "getChannels() -> table[]",
                "getState(deskId: string, socket: string|integer, channel: string) -> table",
                "override(deskId: string, socket: string|integer, channel: string, value: integer[-15..15]) -> table",
                "overrideBatch(commands: table[]) -> integer",
                "release(deskId: string, socket: string|integer, channel: string) -> boolean",
                "releaseAll() -> integer"
            ),
            source = ApiSourceContract(
                "src/main/kotlin/de/teutonstudio/ccaeroworks/computer/ComputerControlLuaApi.kt", "ComputerControlLuaApi"
            )
        ),
        ApiReference(
            "wires", "wires", "EMBEDDED · GLOBAL + MODULE · DRIVE BY WIRE CABLE · MOD REQUIRED FOR OUTPUT", ApiAccent.WIRES,
            moduleName = "cc_aeroworks.wires",
            methods = listOf(
                "list() -> table<string,table>",
                "exists(name: string) -> boolean",
                "get(name: string) -> integer[0..15]",
                "set(name: string, value: integer[0..15]) -> void",
                "pulse(name: string, ticks?: integer, value?: integer[0..15]) -> void",
                "reset(name: string) -> void",
                "resetAll() -> void",
                "getInfo(name: string) -> table",
                "getBackend() -> string",
                "isEnabled() -> boolean"
            ),
            source = ApiSourceContract(
                "src/main/kotlin/de/teutonstudio/ccaeroworks/computer/ComputerWireLuaApi.kt", "ComputerWireLuaApi"
            )
        ),
        ApiReference(
            "telemetry", "telemetry", "EMBEDDED · GLOBAL + MODULE", ApiAccent.TELEMETRY,
            moduleName = "cc_aeroworks.telemetry",
            methods = listOf(
                "list() -> table<string,table>",
                "get(nameOrId: string) -> table|nil",
                "find(type: string) -> table<string,table>",
                "rename(nameOrId: string, alias: string) -> table",
                "clearName(nameOrId: string) -> table",
                "getStatus() -> table",
                "getDocks() -> table<string,DockHandle>",
                "getDock(nameOrId: string) -> DockHandle|nil",
                "renameDock(nameOrId: string, alias: string) -> table",
                "clearDockName(nameOrId: string) -> table"
            ),
            source = ApiSourceContract(
                "src/main/kotlin/de/teutonstudio/ccaeroworks/telemetry/TelemetryLuaApi.kt", "TelemetryLuaApi"
            )
        ),
        ApiReference(
            "dock_handle", "Dock handle", "EMBEDDED · telemetry.getDock(...) · CREATE: SIMULATED", ApiAccent.TELEMETRY,
            requiredMod = "simulated",
            methods = listOf(
                "getInfo() -> table",
                "listTelemetry() -> table<string,table>",
                "getTelemetry(nameOrId: string) -> table|nil",
                "renameTelemetry(nameOrId: string, alias: string) -> table",
                "clearTelemetryName(nameOrId: string) -> table",
                "getTransferBuffers() -> table"
            ),
            source = ApiSourceContract(
                "src/main/kotlin/de/teutonstudio/ccaeroworks/telemetry/TelemetryLuaApi.kt", "DockLuaHandle"
            )
        ),
        ApiReference(
            "display", "display", "DISPLAY SCRIPT · MODULE", ApiAccent.DISPLAY,
            moduleName = "display", methods = displayMethods
        ),
        ApiReference(
            "touchdisplay", "touchdisplay", "DISPLAY SCRIPT · MODULE · LARGE DISPLAY", ApiAccent.TOUCH,
            moduleName = "touchdisplay",
            methods = displayMethods + listOf(
                "isTap(event: table) -> boolean",
                "isDraw(event: table) -> boolean",
                "isDoubleTap(event: table) -> boolean [legacy]",
                "isHold(event: table) -> boolean [legacy]",
                "position(event: table) -> (x: integer, y: integer, width: integer, height: integer)",
                "drawStart(event: table) -> (x: integer, y: integer)",
                "drawDelta(event: table) -> (dx: integer, dy: integer)",
                "drawDirection(event: table) -> (directionU: number, directionV: number)",
                "drawSpeed(event: table) -> number",
                "drawSamples(event: table) -> table[]",
                "drawStroke(event: table) -> integer",
                "drawEnded(event: table) -> boolean",
                "drawIdentity(event: table) -> (gestureId: any, sequence: integer)",
                "normalizedPosition(event: table) -> (u: number|nil, v: number|nil)"
            )
        ),
        ApiReference(
            "radar_control_desk", "ControlDesk + Create: Radars", "LOCAL DESK · OPTIONAL", ApiAccent.RADAR,
            requiredMod = "create_radar",
            methods = listOf(
                "getRadarSources() -> table[]",
                "setRadarSource(socket: string|integer, sourceId: string) -> table"
            ),
            source = ApiSourceContract(
                "src/main/kotlin/de/teutonstudio/ccaeroworks/radarcompat/compat/computercraft/RadarControlDeskPeripheral.kt",
                "RadarControlDeskPeripheral"
            )
        )
    )

    fun find(id: String): ApiReference = references.first { it.id == id }

    fun visible(isModLoaded: (String) -> Boolean): List<ApiReference> = references.filter { reference ->
        reference.requiredMod?.let(isModLoaded) != false
    }
}
