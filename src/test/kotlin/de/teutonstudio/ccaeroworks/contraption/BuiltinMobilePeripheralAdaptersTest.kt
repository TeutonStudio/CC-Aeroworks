package de.teutonstudio.ccaeroworks.contraption

import de.teutonstudio.ccaeroworks.api.mobile.MobilePeripheralAdapters
import kotlin.test.Test
import kotlin.test.assertEquals

class BuiltinMobilePeripheralAdaptersTest {
    @Test
    fun `all promised built-in mobile adapter families are explicitly registered`() {
        BuiltinMobilePeripheralAdapters.register()

        assertEquals(
            setOf(
                "cc_aeroworks:contraption_control",
                "cc_aeroworks:aeroworks_control_desk",
                "cc_aeroworks:computercraft_wireless_modem",
                "cc_aeroworks:computercraft_wired_network",
                "cc_aeroworks:computercraft_speaker",
                "cc_aeroworks:computercraft_monitor",
                "cc_aeroworks:computercraft_disk_drive",
                "cc_aeroworks:computercraft_printer",
                "cc_aeroworks:computercraft_redstone_relay"
            ),
            MobilePeripheralAdapters.all().map { it.id().toString() }.toSet()
        )
    }
}
