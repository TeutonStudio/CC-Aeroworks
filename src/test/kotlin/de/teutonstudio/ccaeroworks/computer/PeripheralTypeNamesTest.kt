package de.teutonstudio.ccaeroworks.computer

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PeripheralTypeNamesTest {
    @Test
    fun `namespaced ender modem exposes full path and compact aliases`() {
        val aliases = PeripheralTypeNames.aliases(
            listOf("advanced_peripherals:ender_modem")
        )

        assertTrue("advanced_peripherals:ender_modem" in aliases)
        assertTrue("advancedperipherals:endermodem" in aliases)
        assertTrue("ender_modem" in aliases)
        assertTrue("endermodem" in aliases)
    }

    @Test
    fun `lookup is case insensitive and separator tolerant`() {
        val aliases = PeripheralTypeNames.aliases(listOf("some_mod:ender_modem"))

        for (query in listOf("EnderModem", "ender-modem", "ender_modem", "SOME_MOD:ENDER_MODEM")) {
            assertTrue(
                PeripheralTypeNames.lookupKeys(query).any(aliases::contains),
                "Expected '$query' to match the registered Ender Modem type"
            )
        }
    }

    @Test
    fun `ControlDesk collection type accepts canonical spellings only`() {
        assertTrue(PeripheralTypeNames.isControlDesk("ControlDesk"))
        assertTrue(PeripheralTypeNames.isControlDesk("control_desk"))
        assertTrue(PeripheralTypeNames.isControlDesk("control-desk"))
        assertFalse(PeripheralTypeNames.isControlDesk("cc_aeroworks:control_desk"))
        assertFalse(PeripheralTypeNames.isControlDesk("desk"))
    }
}
