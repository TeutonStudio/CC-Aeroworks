package de.teutonstudio.ccaeroworks.computer

import kotlin.test.Test
import kotlin.test.assertEquals

class ComputerConsoleVariantTest {
    @Test
    fun `all Aeroworks 1_5 console families have stable unique ids`() {
        assertEquals(
            listOf("control_desk", "copycat_control_desk", "control_stand", "copycat_control_stand"),
            ComputerConsoleVariant.entries.map(ComputerConsoleVariant::aeroworksPath)
        )
        assertEquals(
            listOf("computer_control_desk", "computer_copycat_control_desk", "computer_control_stand", "computer_copycat_control_stand"),
            ComputerConsoleVariant.entries.map(ComputerConsoleVariant::itemPath)
        )
    }
}
