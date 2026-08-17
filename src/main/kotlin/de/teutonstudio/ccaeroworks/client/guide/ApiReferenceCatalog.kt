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

/** Canonical public Lua inventory used by the in-game manual and checked by CI. */
object ApiReferenceCatalog {
    private val displayMethods = listOf(
        "resolve(event)",
        "getSize(event)",
        "clear(event)",
        "getPixel(event, x, y)",
        "setPixel(event, x, y, enabled)",
        "setPixels(event, rows)",
        "setText(event, text)",
        "setNumber(event, value, zeroPad?)"
    )

    val references: List<ApiReference> = listOf(
        ApiReference(
            "control_desk", "ControlDesk", "LOCAL DESK · EXTERNAL CC:TWEAKED",
            methods = listOf(
                "getInfo()", "getSocketCount()", "getSockets()", "getModules()", "getModule(socket)",
                "getInput(socket)", "getInputs()", "getDisplays()", "getDisplay(socket)",
                "setDisplayText(socket, text)", "setDisplayNumber(socket, value, zeroPad?)", "clearDisplay(socket)",
                "clearDisplays()", "getDisplaySize(socket)", "getDisplayPixel(socket, x, y)",
                "setDisplayPixel(socket, x, y, enabled)", "setDisplayPixels(socket, rows)", "clearDisplayPixels(socket)",
                "getDisplayBinding(socket)", "setDisplayTouchScript(socket, path)", "clearDisplayBinding(socket)"
            ),
            source = ApiSourceContract(
                "src/main/kotlin/de/teutonstudio/ccaeroworks/compat/computercraft/ControlDeskPeripheral.kt",
                "ControlDeskPeripheral", setOf("debugDisplayTouchLog")
            )
        ),
        ApiReference(
            "desk_handle", "Desk handle", "EMBEDDED · peripherals.find(\"ControlDesk\")",
            methods = listOf(
                "getInfo()", "getSocketCount()", "getSockets()", "getModules()", "getModule(socket)",
                "getInput(socket)", "getInputs()", "getDisplays()", "getDisplay(socket)",
                "setDisplayText(socket, text)", "setDisplayNumber(socket, value, zeroPad?)", "clearDisplay(socket)",
                "clearDisplays()", "getDisplaySize(socket)", "getDisplayPixel(socket, x, y)",
                "setDisplayPixel(socket, x, y, enabled)", "setDisplayPixels(socket, rows)", "clearDisplayPixels(socket)",
                "getPeripherals()", "find(type)", "findAll(type)", "wrap(side)"
            ),
            source = ApiSourceContract(
                "src/main/kotlin/de/teutonstudio/ccaeroworks/computer/PeripheralNetwork.kt", "DeskLuaHandle"
            )
        ),
        ApiReference(
            "peripherals", "peripherals", "EMBEDDED · GLOBAL + MODULE",
            moduleName = "cc_aeroworks.peripherals",
            methods = listOf(
                "find(type)", "findAll(type)", "wrap(x, y, z, type?)", "getDesks()", "getTree()",
                "getTypes()", "getNetwork()", "refresh()"
            ),
            source = ApiSourceContract(
                "src/main/kotlin/de/teutonstudio/ccaeroworks/computer/ComputerConsoleLuaApi.kt", "ComputerConsoleLuaApi"
            )
        ),
        ApiReference(
            "channels", "channels", "EMBEDDED · GLOBAL + MODULE · HIGH LEVEL",
            moduleName = "cc_aeroworks.channels", preferred = true,
            methods = listOf(
                "ls(path?)", "stat(pathOrId)", "read(pathOrId)", "setWire(pathOrId, value)",
                "pulseWire(pathOrId, ticks?, value?)", "resetWire(pathOrId)", "override(pathOrId, value)",
                "overrideBatch(commands)", "release(pathOrId)", "releaseAll()"
            ),
            source = ApiSourceContract(
                "src/main/kotlin/de/teutonstudio/ccaeroworks/computer/ComputerChannelLuaApi.kt", "ComputerChannelLuaApi"
            )
        ),
        ApiReference(
            "controls", "controls", "EMBEDDED · GLOBAL + MODULE · NATIVE -15..15",
            moduleName = "cc_aeroworks.controls",
            methods = listOf(
                "getChannels()", "getState(deskId, socket, channel)", "override(deskId, socket, channel, value)",
                "overrideBatch(commands)", "release(deskId, socket, channel)", "releaseAll()"
            ),
            source = ApiSourceContract(
                "src/main/kotlin/de/teutonstudio/ccaeroworks/computer/ComputerControlLuaApi.kt", "ComputerControlLuaApi"
            )
        ),
        ApiReference(
            "wires", "wires", "EMBEDDED · GLOBAL + MODULE · LOW LEVEL 0..15",
            moduleName = "cc_aeroworks.wires",
            methods = listOf(
                "list()", "exists(name)", "get(name)", "set(name, value)", "pulse(name, ticks?, value?)",
                "reset(name)", "resetAll()", "getInfo(name)", "getBackend()", "isEnabled()"
            ),
            source = ApiSourceContract(
                "src/main/kotlin/de/teutonstudio/ccaeroworks/computer/ComputerWireLuaApi.kt", "ComputerWireLuaApi"
            )
        ),
        ApiReference(
            "telemetry", "telemetry", "EMBEDDED · GLOBAL + MODULE",
            moduleName = "cc_aeroworks.telemetry",
            methods = listOf(
                "list()", "get(nameOrId)", "find(type)", "rename(nameOrId, alias)", "clearName(nameOrId)",
                "getStatus()", "getDocks()", "getDock(nameOrId)", "renameDock(nameOrId, alias)", "clearDockName(nameOrId)"
            ),
            source = ApiSourceContract(
                "src/main/kotlin/de/teutonstudio/ccaeroworks/telemetry/TelemetryLuaApi.kt", "TelemetryLuaApi"
            )
        ),
        ApiReference(
            "dock_handle", "Dock handle", "EMBEDDED · telemetry.getDock(...) · CREATE: SIMULATED",
            requiredMod = "simulated",
            methods = listOf(
                "getInfo()", "listTelemetry()", "getTelemetry(nameOrId)", "renameTelemetry(nameOrId, alias)",
                "clearTelemetryName(nameOrId)", "getTransferBuffers()"
            ),
            source = ApiSourceContract(
                "src/main/kotlin/de/teutonstudio/ccaeroworks/telemetry/TelemetryLuaApi.kt", "DockLuaHandle"
            )
        ),
        ApiReference(
            "display", "display", "DISPLAY SCRIPT · MODULE", moduleName = "display", methods = displayMethods
        ),
        ApiReference(
            "touchdisplay", "touchdisplay", "DISPLAY SCRIPT · MODULE · LARGE DISPLAY", moduleName = "touchdisplay",
            methods = displayMethods + listOf(
                "isTap(event)", "isDraw(event)", "isDoubleTap(event) [legacy]", "isHold(event) [legacy]",
                "position(event)", "drawStart(event)", "drawDelta(event)", "drawEnded(event)",
                "drawIdentity(event)", "normalizedPosition(event)"
            )
        ),
        ApiReference(
            "radar_control_desk", "ControlDesk + Create: Radars", "LOCAL DESK · OPTIONAL",
            requiredMod = "create_radar", methods = listOf("getRadarSources()", "setRadarSource(socket, sourceId)"),
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
