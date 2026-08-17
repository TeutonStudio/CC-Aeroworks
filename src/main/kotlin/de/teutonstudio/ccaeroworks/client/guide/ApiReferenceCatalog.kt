package de.teutonstudio.ccaeroworks.client.guide

data class ApiSourceContract(
    val path: String,
    val className: String,
    val internalMethods: Set<String> = emptySet()
)

data class ApiReference(
    val id: String,
    val name: String,
    val availability: String,
    val moduleName: String? = null,
    val preferred: Boolean = false,
    val requiredMod: String? = null,
    val methods: List<String>,
    val source: ApiSourceContract? = null
)

/**
 * Canonical inventory of the public Lua surfaces shown by the in-game manual.
 *
 * Narrative guide text intentionally stays separate. Method names, availability and module names
 * live here so CI can compare the manual against the real @LuaFunction surfaces instead of asking
 * several hand-written documents to remain synchronized by optimism alone.
 */
object ApiReferenceCatalog {
    val references: List<ApiReference> = listOf(
        ApiReference(
            id = "control_desk",
            name = "ControlDesk",
            availability = "LOCAL DESK · EXTERNAL CC:TWEAKED",
            methods = listOf(
                "getInfo()",
                "getSocketCount()",
                "getSockets()",
                "getModules()",
                "getModule(socket)",
                "getInput(socket)",
                "getInputs()",
                "getDisplays()",
                "getDisplay(socket)",
                "setDisplayText(socket, text)",
                "setDisplayNumber(socket, value, zeroPad?)",
                "clearDisplay(socket)",
                "clearDisplays()",
                "getDisplaySize(socket)",
                "getDisplayPixel(socket, x, y)",
                "setDisplayPixel(socket, x, y, enabled)",
                "setDisplayPixels(socket, rows)",
                "clearDisplayPixels(socket)",
                "getDisplayBinding(socket)",
                "setDisplayTouchScript(socket, path)",
                "clearDisplayBinding(socket)"
            ),
            source = ApiSourceContract(
                path = "src/main/kotlin/de/teutonstudio/ccaeroworks/compat/computercraft/ControlDeskPeripheral.kt",
                className = "ControlDeskPeripheral",
                internalMethods = setOf("debugDisplayTouchLog")
            )
        ),
        ApiReference(
            id = "desk_handle",
            name = "Desk handle",
            availability = "EMBEDDED · peripherals.find(\"ControlDesk\")",
            methods = listOf(
                "getInfo()",
                "getSocketCount()",
                "getSockets()",
                "getModules()",
                "getModule(socket)",
                "getInput(socket)",
                "getInputs()",
                "getDisplays()",
                "getDisplay(socket)",
                "setDisplayText(socket, text)",
                "setDisplayNumber(socket, value, zeroPad?)",
                "clearDisplay(socket)",
                "clearDisplays()",
                "getDisplaySize(socket)",
                "getDisplayPixel(socket, x, y)",
                "setDisplayPixel(socket, x, y, enabled)",
                "setDisplayPixels(socket, rows)",
                "clearDisplayPixels(socket)",
                "getPeripherals()",
                "find(type)",
                "findAll(type)",
                "wrap(side)"
            ),
            source = ApiSourceContract(
                path = "src/main/kotlin/de/teutonstudio/ccaeroworks/computer/PeripheralNetwork.kt",
                className = "DeskLuaHandle"
            )
        ),
        ApiReference(
            id = "peripherals",
            name = "peripherals",
            availability = "EMBEDDED · GLOBAL + MODULE",
            moduleName = "cc_aeroworks.peripherals",
            methods = listOf(
                "find(type)",
                "findAll(type)",
                "wrap(x, y, z, type?)",
                "getDesks()",
                "getTree()",
                "getTypes()",
                "getNetwork()",
                "refresh()"
            ),
            source = ApiSourceContract(
                path = "src/main/kotlin/de/teutonstudio/ccaeroworks/computer/ComputerConsoleLuaApi.kt",
                className = "ComputerConsoleLuaApi"
            )
        ),
        ApiReference(
            id = "channels",
            name = "channels",
            availability = "EMBEDDED · GLOBAL + MODULE · HIGH LEVEL",
            moduleName = "cc_aeroworks.channels",
            preferred = true,
            methods = listOf(
                "ls(path?)",
                "stat(pathOrId)",
                "read(pathOrId)",
                "setWire(pathOrId, value)",
                "pulseWire(pathOrId, ticks?, value?)",
                "resetWire(pathOrId)",
                "override(pathOrId, value)",
                "overrideBatch(commands)",
                "release(pathOrId)",
                "releaseAll()"
            ),
            source = ApiSourceContract(
                path = "src/main/kotlin/de/teutonstudio/ccaeroworks/computer/ComputerChannelLuaApi.kt",
                className = "ComputerChannelLuaApi"
            )
        ),
        ApiReference(
            id = "controls",
            name = "controls",
            availability = "EMBEDDED · GLOBAL + MODULE · NATIVE -15..15",
            moduleName = "cc_aeroworks.controls",
            methods = listOf(
                "getChannels()",
                "getState(deskId, socket, channel)",
                "override(deskId, socket, channel, value)",
                "overrideBatch(commands)",
                "release(deskId, socket, channel)",
                "releaseAll()"
            ),
            source = ApiSourceContract(
                path = "src/main/kotlin/de/teutonstudio/ccaeroworks/computer/ComputerControlLuaApi.kt",
                className = "ComputerControlLuaApi"
            )
        ),
        ApiReference(
            id = "wires",
            name = "wires",
            availability = "EMBEDDED · GLOBAL + MODULE · LOW LEVEL 0..15",
            moduleName = "cc_aeroworks.wires",
            methods = listOf(
                "list()",
                "exists(name)",
                "get(name)",
                "set(name, value)",
                "pulse(name, ticks?, value?)",
                "reset(name)",
                "resetAll()",
                "getInfo(name)",
                "getBackend()",
                "isEnabled()"
            ),
            source = ApiSourceContract(
                path = "src/main/kotlin/de/teutonstudio/ccaeroworks/computer/ComputerWireLuaApi.kt",
                className = "ComputerWireLuaApi"
            )
        ),
        ApiReference(
            id = "telemetry",
            name = "telemetry",
            availability = "EMBEDDED · GLOBAL + MODULE",
            moduleName = "cc_aeroworks.telemetry",
            methods = listOf(
                "list()",
                "get(nameOrId)",
                "find(type)",
                "rename(nameOrId, alias)",
                "clearName(nameOrId)",
                "getStatus()",
                "getDocks()",
                "getDock(nameOrId)",
                "renameDock(nameOrId, alias)",
                "clearDockName(nameOrId)"
            ),
            source = ApiSourceContract(
                path = "src/main/kotlin/de/teutonstudio/ccaeroworks/telemetry/TelemetryLuaApi.kt",
                className = "TelemetryLuaApi"
            )
        ),
        ApiReference(
            id = "dock_handle",
            name = "Dock handle",
            availability = "EMBEDDED · telemetry.getDock(...) · CREATE: SIMULATED",
            requiredMod = "simulated",
            methods = listOf(
                "getInfo()",
                "listTelemetry()",
                "getTelemetry(nameOrId)",
                "renameTelemetry(nameOrId, alias)",
                "clearTelemetryName(nameOrId)",
                "getTransferBuffers()"
            ),
            source = ApiSourceContract(
                path = "src/main/kotlin/de/teutonstudio/ccaeroworks/telemetry/TelemetryLuaApi.kt",
                className = "DockLuaHandle"
            )
        ),
        ApiReference(
            id = "display",
            name = "display",
            availability = "DISPLAY SCRIPT · MODULE",
            moduleName = "display",
            methods = listOf(
                "getContext()",
                "getDesk()",
                "getSize()",
                "getPixel(x, y)",
                "setPixel(x, y, enabled)",
                "setPixels(rows)",
                "clearPixels()",
                "setText(text)",
                "setNumber(value, zeroPad?)",
                "clear()"
            )
        ),
        ApiReference(
            id = "touchdisplay",
            name = "touchdisplay",
            availability = "DISPLAY SCRIPT · MODULE · LARGE DISPLAY",
            moduleName = "touchdisplay",
            methods = listOf(
                "isTap(event)",
                "isDraw(event)",
                "position(event)",
                "normalizedPosition(event)",
                "drawStart(event)",
                "drawDelta(event)",
                "drawIdentity(event)",
                "drawEnded(event)"
            )
        ),
        ApiReference(
            id = "radar_control_desk",
            name = "ControlDesk + Create: Radars",
            availability = "LOCAL DESK · OPTIONAL",
            requiredMod = "create_radar",
            methods = listOf(
                "getRadarSources()",
                "setRadarSource(socket, sourceId)"
            ),
            source = ApiSourceContract(
                path = "src/main/kotlin/de/teutonstudio/ccaeroworks/radarcompat/compat/computercraft/RadarControlDeskPeripheral.kt",
                className = "RadarControlDeskPeripheral"
            )
        )
    )

    fun find(id: String): ApiReference = references.first { it.id == id }

    fun visible(isModLoaded: (String) -> Boolean): List<ApiReference> = references.filter { reference ->
        reference.requiredMod?.let(isModLoaded) != false
    }
}
