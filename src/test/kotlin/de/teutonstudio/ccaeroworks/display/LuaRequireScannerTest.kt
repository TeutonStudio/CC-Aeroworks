package de.teutonstudio.ccaeroworks.display

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LuaRequireScannerTest {
    @Test fun `collects literal imports and touch declarations`() {
        val analysis = LuaRequireScanner.scan(
            """
            local display = require("display")
            local telemetry = require('cc_aeroworks.telemetry')
            return {
                onTap = function(event) return telemetry.get("fuel") end,
                onDoubleTap = function(event) return nil end,
            }
            """.trimIndent()
        )

        assertEquals(listOf("display", "cc_aeroworks.telemetry"), analysis.imports)
        assertEquals(listOf("onTap", "onDoubleTap"), analysis.declaredTouchEvents)
        assertTrue(analysis.display)
        assertFalse(analysis.touchDisplay)
    }

    @Test fun `ignores requires and callbacks in comments strings and long strings`() {
        val analysis = LuaRequireScanner.scan(
            """
            -- require("touchdisplay")
            local text = "require('display') onTap = function() end"
            local block = [[ require("touchdisplay") onPointer = true ]]
            --[=[ onDoubleTap = function() end require("display") ]=]
            return require("display")
            """.trimIndent()
        )

        assertEquals(listOf("display"), analysis.imports)
        assertTrue(analysis.declaredTouchEvents.isEmpty())
    }

    @Test fun `accepts Lua long bracket require literals`() {
        val analysis = LuaRequireScanner.scan("local display = require [=[touchdisplay]=]")
        assertEquals(listOf("touchdisplay"), analysis.imports)
        assertTrue(analysis.touchDisplay)
    }
}
