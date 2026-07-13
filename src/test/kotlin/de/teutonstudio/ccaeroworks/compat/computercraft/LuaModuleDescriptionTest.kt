package de.teutonstudio.ccaeroworks.compat.computercraft

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class LuaModuleDescriptionTest {
    @Test fun `filters invalid sockets and preserves socket order`() {
        val modules = listOf(
            LuaModuleSnapshot(3, "test:three", "module"),
            LuaModuleSnapshot(-1, "test:negative", "module"),
            LuaModuleSnapshot(1, "test:one", "module"),
            LuaModuleSnapshot(4, "test:outside", "module")
        )
        assertEquals(listOf(1, 3), LuaModuleDescription.validSockets(4, modules).map { it.socket })
    }

    @Test fun `describes scalar input for Lua`() {
        val result = LuaModuleDescription.describe(
            LuaModuleSnapshot(0, "aeroworks:lever", "lever", mapOf("lever" to 4))
        )
        assertEquals(4, result["value"])
        assertFalse(result["display"] as Boolean)
    }

    @Test fun `describes display for Lua`() {
        val result = LuaModuleDescription.describe(
            LuaModuleSnapshot(2, "cc_aeroworks:three_digit_display", "display", displayWidth = 3, displayText = "007")
        )
        assertEquals(3, result["width"])
        assertEquals("007", result["text"])
        assertEquals(true, result["display"])
    }
}
