package de.teutonstudio.ccaeroworks.display

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DeskDisplayFormatterTest {
    @Test fun `normalizes two and three character displays`() {
        assertEquals("12", DeskDisplayFormatter.normalizeText("123", 2))
        assertEquals("123", DeskDisplayFormatter.normalizeText("1234", 3))
    }

    @Test fun `replaces unsupported characters with spaces`() {
        assertEquals("1 ", DeskDisplayFormatter.normalizeText("1x", 2))
    }

    @Test fun `zero pads positive and negative numbers`() {
        assertEquals("07", DeskDisplayFormatter.formatNumber(7.9, 2, true))
        assertEquals("-07", DeskDisplayFormatter.formatNumber(-7.9, 3, true))
    }

    @Test fun `clamps overflow and truncates decimals toward zero`() {
        assertEquals("99", DeskDisplayFormatter.formatNumber(128.0, 2, false))
        assertEquals("-9", DeskDisplayFormatter.formatNumber(-42.0, 2, false))
        assertEquals("12", DeskDisplayFormatter.formatNumber(12.99, 3, false))
    }

    @Test fun `rejects non finite numbers`() {
        assertThrows(IllegalArgumentException::class.java) { DeskDisplayFormatter.formatNumber(Double.NaN, 2, false) }
        assertThrows(IllegalArgumentException::class.java) { DeskDisplayFormatter.formatNumber(Double.POSITIVE_INFINITY, 3, false) }
    }
}
