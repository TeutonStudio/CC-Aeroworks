package de.teutonstudio.ccaeroworks.display

import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows

class DisplayColorSettingsTest {
    @Test
    fun `accepts the full RGB range`() {
        assertEquals(0x000000, DisplayColorSettings.validate(0))
        assertEquals(0x33AAFF, DisplayColorSettings.validate(0x33AAFF))
        assertEquals(0xFFFFFF, DisplayColorSettings.validate(0xFFFFFF))
    }

    @Test
    fun `rejects non integral and out of range colors`() {
        assertThrows(IllegalArgumentException::class.java) { DisplayColorSettings.validate(-1) }
        assertThrows(IllegalArgumentException::class.java) { DisplayColorSettings.validate(0x1000000) }
        assertThrows(IllegalArgumentException::class.java) { DisplayColorSettings.validate(1.5) }
        assertThrows(IllegalArgumentException::class.java) { DisplayColorSettings.validate("0x33AAFF") }
    }

    @Test
    fun `adds an opaque alpha channel for rendering`() {
        assertEquals(0xFF33AAFF.toInt(), DisplayColorSettings.argb(0x33AAFF))
    }
}
