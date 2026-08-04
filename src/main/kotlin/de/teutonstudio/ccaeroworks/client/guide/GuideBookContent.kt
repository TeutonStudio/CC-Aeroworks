package de.teutonstudio.ccaeroworks.client.guide

sealed interface GuideEntry {
    data class Text(val key: String) : GuideEntry
    data class Note(val key: String) : GuideEntry
    data class Warning(val key: String) : GuideEntry
    data class InputHint(val key: String) : GuideEntry
    data class Code(val lines: List<String>) : GuideEntry
}

data class GuideSection(
    val labelKey: String,
    val titleKey: String,
    val entries: List<GuideEntry>
)

object GuideBookContent {
    val sections: List<GuideSection> = listOf(
        GuideSection(
            "guide.cc_aeroworks.tab.start",
            "guide.cc_aeroworks.start.title",
            listOf(
                GuideEntry.Text("guide.cc_aeroworks.start.text"),
                GuideEntry.InputHint("guide.cc_aeroworks.start.controls"),
                GuideEntry.Note("guide.cc_aeroworks.start.note")
            )
        ),
        GuideSection(
            "guide.cc_aeroworks.tab.computers",
            "guide.cc_aeroworks.computers.title",
            listOf(
                GuideEntry.Text("guide.cc_aeroworks.computers.text"),
                GuideEntry.InputHint("guide.cc_aeroworks.computers.controls"),
                GuideEntry.Warning("guide.cc_aeroworks.computers.warning"),
                GuideEntry.Note("guide.cc_aeroworks.computers.note")
            )
        ),
        GuideSection(
            "guide.cc_aeroworks.tab.network",
            "guide.cc_aeroworks.network.title",
            listOf(
                GuideEntry.Text("guide.cc_aeroworks.network.text"),
                GuideEntry.Code(
                    listOf(
                        "-- embedded computer",
                        "local desks = aeroworks.getDesks()",
                        "aeroworks.getModules(desks[1].id)",
                        "",
                        "-- external computer",
                        "local desk = peripheral.find(",
                        "  \"cc_aeroworks_control_desk\")",
                        "local desks = desk.getDesks()"
                    )
                ),
                GuideEntry.Note("guide.cc_aeroworks.network.note")
            )
        ),
        GuideSection(
            "guide.cc_aeroworks.tab.modules",
            "guide.cc_aeroworks.modules.title",
            listOf(
                GuideEntry.Text("guide.cc_aeroworks.modules.text"),
                GuideEntry.Code(
                    listOf(
                        "getSockets() -- left, right, big",
                        "getModules() / getModule(socket)",
                        "getInputs() / getInput(socket)",
                        "os.pullEvent(\"cc_aeroworks_desk_input\")"
                    )
                ),
                GuideEntry.Note("guide.cc_aeroworks.modules.note")
            )
        ),
        GuideSection(
            "guide.cc_aeroworks.tab.displays",
            "guide.cc_aeroworks.displays.title",
            listOf(
                GuideEntry.Text("guide.cc_aeroworks.displays.text"),
                GuideEntry.Code(
                    listOf(
                        "setDisplayText(socket, text)",
                        "setDisplayNumber(socket, value, zeroPad)",
                        "setDisplayPixel(socket, x, y, enabled)",
                        "setDisplayPixels(socket, rows)",
                        "clearDisplay(socket)"
                    )
                ),
                GuideEntry.Note("guide.cc_aeroworks.displays.note")
            )
        ),
        GuideSection(
            "guide.cc_aeroworks.tab.controls",
            "guide.cc_aeroworks.controls.title",
            listOf(
                GuideEntry.Text("guide.cc_aeroworks.controls.text"),
                GuideEntry.InputHint("guide.cc_aeroworks.controls.input"),
                GuideEntry.Code(
                    listOf(
                        "Lever / Throttle -> Mouse Y",
                        "Joystick X       -> Mouse X",
                        "Joystick Y       -> Mouse Y"
                    )
                ),
                GuideEntry.Note("guide.cc_aeroworks.controls.note")
            )
        ),
        GuideSection(
            "guide.cc_aeroworks.tab.errors",
            "guide.cc_aeroworks.errors.title",
            listOf(
                GuideEntry.Text("guide.cc_aeroworks.errors.text"),
                GuideEntry.Warning("guide.cc_aeroworks.errors.warning"),
                GuideEntry.Code(
                    listOf(
                        "local network = aeroworks.getNetwork()",
                        "print(network.state)",
                        "print(network.memberCount)",
                        "print(network.revision)"
                    )
                ),
                GuideEntry.Note("guide.cc_aeroworks.errors.note")
            )
        )
    )
}
