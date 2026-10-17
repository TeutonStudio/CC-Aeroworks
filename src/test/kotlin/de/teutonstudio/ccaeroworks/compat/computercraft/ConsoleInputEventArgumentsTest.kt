package de.teutonstudio.ccaeroworks.compat.computercraft

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test

class ConsoleInputEventArgumentsTest {
    @Test fun `script metadata follows the unchanged console event arguments`() {
        assertArrayEquals(
            arrayOf<Any?>("desk-b", 2, 2, "big", "aeroworks:joystick", -7, "y", "/flight.lua", 0),
            ConsoleInputEventArguments.create("desk-b", 2, 2, "aeroworks:joystick", -7, "y", "/flight.lua", 0)
        )
    }

    @Test fun `releases and missing input values retain their actual values`() {
        assertArrayEquals(
            arrayOf<Any?>("desk-a", 1, 0, "left", "aeroworks:button", 0, "press", "/gear.lua", 15),
            ConsoleInputEventArguments.create("desk-a", 1, 0, "aeroworks:button", 0, "press", "/gear.lua", 15)
        )
        assertArrayEquals(
            arrayOf<Any?>("desk-a", 1, 0, "left", "aeroworks:button", null, "press", "", 0),
            ConsoleInputEventArguments.create("desk-a", 1, 0, "aeroworks:button", null, "press", "", 0)
        )
    }
}
