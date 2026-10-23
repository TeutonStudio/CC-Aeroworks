package de.teutonstudio.ccaeroworks.compat.teutonlib

import de.TeutonStudio.TeutonLib.api.computer.SourceView
import de.teutonstudio.ccaeroworks.computer.source.InformationSourceKinds
import de.teutonstudio.ccaeroworks.computer.source.InformationSourceView
import kotlin.test.Test
import kotlin.test.assertEquals

class DeskWorkspaceProjectionTest {
    @Test
    fun `peripherals use the four physical connection categories`() {
        val sources = listOf(
            source("link", "wireless"),
            source("remote", "wired"),
            source("other-desk", "cable_network"),
            source("side", "local")
        ).map(DeskWorkspaceProjection::categorize)

        assertEquals(
            listOf("adjacent", "cable_network", "modem_network", "peripheral_link"),
            DeskWorkspaceProjection.connectionGroups(sources)
        )
    }

    @Test
    fun `gps row includes the precise fix before its status`() {
        val source = InformationSourceView(
            "gps:test", InformationSourceKinds.GPS, "GPS fix", "ready",
            13, 65, -8, "", "12.75, 64.50, -8.25 · 4 hosts · age 1s"
        )

        assertEquals(
            "12.75, 64.50, -8.25 · 4 hosts · age 1s · ready",
            DeskWorkspaceProjection.sourceDetail(source)
        )
    }

    private fun source(name: String, connection: String) =
        SourceView(name, listOf("test"), connection, null, null)
}
