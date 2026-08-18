package de.teutonstudio.ccaeroworks.computer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PeripheralMountRegistryTest {
    @Test
    fun `drain returns every mount once and clears the registry`() {
        val registry = PeripheralMountRegistry()
        registry.add("disk")
        registry.add("rom")
        registry.add("disk")

        assertEquals(listOf("disk", "rom"), registry.drain())
        assertTrue(registry.isEmpty())
        assertEquals(emptyList<String>(), registry.drain())
    }

    @Test
    fun `explicit unmount removes a location from later cleanup`() {
        val registry = PeripheralMountRegistry()
        registry.add("disk")
        registry.add("rom")

        registry.remove("disk")

        assertEquals(listOf("rom"), registry.drain())
    }
}
