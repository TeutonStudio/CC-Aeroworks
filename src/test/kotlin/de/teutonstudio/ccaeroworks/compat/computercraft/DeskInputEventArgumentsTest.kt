package de.teutonstudio.ccaeroworks.compat.computercraft

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test

class DeskInputEventArgumentsTest {
    @Test fun `keeps documented Lua event argument order`() {
        assertArrayEquals(
            arrayOf("left", 2, "aeroworks:lever", -4, "lever", "big"),
            DeskInputEventArguments.create("left", 2, "aeroworks:lever", -4, "lever")
        )
    }
}
