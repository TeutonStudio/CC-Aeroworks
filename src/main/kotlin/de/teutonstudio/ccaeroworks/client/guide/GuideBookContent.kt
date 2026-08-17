package de.teutonstudio.ccaeroworks.client.guide

import net.minecraft.network.chat.Component
import net.neoforged.fml.ModList

sealed interface GuideEntry {
    data class Text(val key: String) : GuideEntry
    data class Note(val key: String) : GuideEntry
    data class Warning(val key: String) : GuideEntry
    data class InputHint(val key: String) : GuideEntry
    data class Heading(val text: String) : GuideEntry
    data class Api(val referenceId: String) : GuideEntry
    data class Code(val lines: List<String>) : GuideEntry
    data object PixelEditor : GuideEntry
}

enum class GuideSectionId {
    START, COMPUTERS, NETWORK_API, MODULES, CONTROLS, TELEMETRY, DISPLAYS,
    TOUCH, EVENTS, PIXEL_EDITOR, ERRORS, DOCKING, RADAR
}

data class GuideSection(
    val id: GuideSectionId,
    val label: Component,
    val title: Component,
    val entries: List<GuideEntry>,
    val requiredMod: String? = null
)

object GuideBookContent {
    private fun tr(key: String): Component = Component.translatable(key)
    private fun tech(value: String): Component = Component.literal(value)

    private val allSections: List<GuideSection> = listOf(
        GuideSection(GuideSectionId.START, tr("guide.cc_aeroworks.tab.start"), tr("guide.cc_aeroworks.start.title"), listOf(
            GuideEntry.Text("guide.cc_aeroworks.start.text"),
            GuideEntry.InputHint("guide.cc_aeroworks.start.controls"),
            GuideEntry.Note("guide.cc_aeroworks.start.note"),
            GuideEntry.Heading("API scopes"),
            GuideEntry.Code(listOf(
                "LOCAL DESK  peripheral.find(\"ControlDesk\")",
                "EMBEDDED    ComputerControlDesk only",
                "GLOBAL      injected CraftOS API",
                "MODULE      require(\"cc_aeroworks....\")",
                "OPTIONAL    requires integration mod"
            ))
        )),
        GuideSection(GuideSectionId.COMPUTERS, tr("guide.cc_aeroworks.tab.computers"), tr("guide.cc_aeroworks.computers.title"), listOf(
            GuideEntry.Text("guide.cc_aeroworks.computers.text"),
            GuideEntry.InputHint("guide.cc_aeroworks.computers.controls"),
            GuideEntry.Warning("guide.cc_aeroworks.computers.warning"),
            GuideEntry.Note("guide.cc_aeroworks.computers.note"),
            GuideEntry.Heading("External ControlDesk"), GuideEntry.Api("control_desk")
        )),
        GuideSection(GuideSectionId.NETWORK_API, tr("guide.cc_aeroworks.tab.network"), tr("guide.cc_aeroworks.network.title"), listOf(
            GuideEntry.Text("guide.cc_aeroworks.network.text"), GuideEntry.Api("peripherals"),
            GuideEntry.Heading("Embedded desk handle"), GuideEntry.Api("desk_handle"),
            GuideEntry.Code(listOf(
                "local net = peripherals.getNetwork()", "local desks = peripherals.find(\"ControlDesk\")",
                "local modem = peripherals.find(\"endermodem\")", "local all = peripherals.findAll(\"endermodem\")",
                "local tree = peripherals.getTree()"
            )), GuideEntry.Note("guide.cc_aeroworks.network.note")
        )),
        GuideSection(GuideSectionId.MODULES, tr("guide.cc_aeroworks.tab.modules"), tr("guide.cc_aeroworks.modules.title"), listOf(
            GuideEntry.Text("guide.cc_aeroworks.modules.text"),
            GuideEntry.Code(listOf(
                "desk.getSockets()       -- left, right, big", "desk.getModules()", "desk.getModule(\"big\")",
                "desk.getInputs()", "desk.getDisplays()", "desk.getPeripherals()   -- embedded handle only"
            )), GuideEntry.Note("guide.cc_aeroworks.modules.note")
        )),
        GuideSection(GuideSectionId.CONTROLS, tr("guide.cc_aeroworks.tab.controls"), tr("guide.cc_aeroworks.controls.title"), listOf(
            GuideEntry.Text("guide.cc_aeroworks.controls.text"), GuideEntry.InputHint("guide.cc_aeroworks.controls.input"),
            GuideEntry.Heading("Preferred high-level API"), GuideEntry.Api("channels"),
            GuideEntry.Code(listOf(
                "local v = channels.read(\"/groups/flight/roll_right\")",
                "channels.override(\"/groups/flight/roll_right\", 7)",
                "channels.setWire(\"/groups/flight/gear\", 15)", "channels.releaseAll()"
            )),
            GuideEntry.Heading("Native control authority"), GuideEntry.Api("controls"),
            GuideEntry.Heading("Low-level wire outputs"), GuideEntry.Api("wires"),
            GuideEntry.Note("guide.cc_aeroworks.controls.note")
        )),
        GuideSection(GuideSectionId.TELEMETRY, tech("telemetry"), tech("Telemetry & information sources"), listOf(
            GuideEntry.Api("telemetry"),
            GuideEntry.Code(listOf(
                "local fuel = telemetry.get(\"fuel\")", "if fuel then print(fuel.value.percent) end",
                "for id, source in pairs(telemetry.list()) do", "  print(id, source.kind, source.stale)", "end"
            )),
            GuideEntry.Heading("Lifecycle events"),
            GuideEntry.Code(listOf("cc_aeroworks_telemetry_added", "cc_aeroworks_telemetry_changed", "cc_aeroworks_telemetry_removed"))
        )),
        GuideSection(GuideSectionId.DISPLAYS, tr("guide.cc_aeroworks.tab.displays"), tr("guide.cc_aeroworks.displays.title"), listOf(
            GuideEntry.Text("guide.cc_aeroworks.displays.text"),
            GuideEntry.Code(listOf(
                "desk.setDisplayText(\"big\", \"42\")", "local size = desk.getDisplaySize(\"big\")",
                "desk.setDisplayPixel(\"big\", 1, 1, true)", "desk.setDisplayPixels(\"big\", rows)"
            )), GuideEntry.Heading("Display-script module"), GuideEntry.Api("display"),
            GuideEntry.Note("guide.cc_aeroworks.displays.note")
        )),
        GuideSection(GuideSectionId.TOUCH, tech("touch / draw"), tech("Interactive display scripts"), listOf(
            GuideEntry.Api("touchdisplay"),
            GuideEntry.Code(listOf(
                "local touch = require(\"touchdisplay\")", "local handler = {}", "function handler.onTap(event)",
                "  local x, y = touch.position(event)", "end", "function handler.onDraw(event)",
                "  local dx, dy = touch.drawDelta(event)", "end", "return handler"
            )),
            GuideEntry.Heading("Raw embedded event"),
            GuideEntry.Code(listOf(
                "cc_aeroworks_console_display_input", "action = tap | draw",
                "draw: gestureId, sequence, startX/startY,", "      deltaX/deltaY, isEnd"
            ))
        )),
        GuideSection(GuideSectionId.EVENTS, tech("events"), tech("CC-Aeroworks event reference"), listOf(
            GuideEntry.Heading("Desk / console"),
            GuideEntry.Code(listOf(
                "cc_aeroworks_desk_input", "cc_aeroworks_desk_touch", "cc_aeroworks_desk_display_input",
                "cc_aeroworks_console_input", "cc_aeroworks_console_touch", "cc_aeroworks_console_display_input",
                "cc_aeroworks_console_changed"
            )),
            GuideEntry.Heading("Control / network"),
            GuideEntry.Code(listOf(
                "cc_aeroworks_control_override", "cc_aeroworks_control_release",
                "cc_aeroworks_peripheral_attached", "cc_aeroworks_peripheral_detached"
            )),
            GuideEntry.Heading("Telemetry / docking"),
            GuideEntry.Code(listOf(
                "cc_aeroworks_telemetry_added", "cc_aeroworks_telemetry_changed", "cc_aeroworks_telemetry_removed",
                "cc_aeroworks_dock_changed", "cc_aeroworks_remote_telemetry_changed"
            ))
        )),
        GuideSection(GuideSectionId.PIXEL_EDITOR, tr("guide.cc_aeroworks.tab.pixel_editor"), tr("guide.cc_aeroworks.pixel_editor.title"), listOf(
            GuideEntry.Text("guide.cc_aeroworks.pixel_editor.text"), GuideEntry.PixelEditor,
            GuideEntry.Note("guide.cc_aeroworks.pixel_editor.note")
        )),
        GuideSection(GuideSectionId.ERRORS, tr("guide.cc_aeroworks.tab.errors"), tr("guide.cc_aeroworks.errors.title"), listOf(
            GuideEntry.Text("guide.cc_aeroworks.errors.text"), GuideEntry.Warning("guide.cc_aeroworks.errors.warning"),
            GuideEntry.Code(listOf(
                "local network = peripherals.getNetwork()", "print(textutils.serialize(network))",
                "print(textutils.serialize(peripherals.getTypes()))", "peripherals.refresh()"
            )), GuideEntry.Note("guide.cc_aeroworks.errors.note")
        )),
        GuideSection(GuideSectionId.DOCKING, tech("Create: Simulated"), tech("Docking telemetry"), listOf(
            GuideEntry.Api("dock_handle"),
            GuideEntry.Code(listOf(
                "local dock = telemetry.getDock(\"left_cargo\")", "if dock and dock.getInfo().locked then",
                "  local fuel = dock.getTelemetry(\"fuel\")", "end"
            ))
        ), requiredMod = "simulated"),
        GuideSection(GuideSectionId.RADAR, tech("Create: Radars"), tech("Create: Radars integration"), listOf(
            GuideEntry.Api("radar_control_desk"),
            GuideEntry.Code(listOf("local sources = desk.getRadarSources()", "desk.setRadarSource(\"big\", sourceId)"))
        ), requiredMod = "create_radar")
    )

    val sections: List<GuideSection> by lazy {
        allSections.filter { section -> section.requiredMod?.let(ModList.get()::isLoaded) != false }
    }

    fun indexOf(id: GuideSectionId): Int = sections.indexOfFirst { it.id == id }.coerceAtLeast(0)
}
