package de.teutonstudio.ccaeroworks.client.hud

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ControlHudModulesTest {
    @Test
    fun `joystick and attached buttons form one widget while other sockets stay separate`() {
        val modules = ControlHudModules.combine(
            listOf(ControlHudModules.AxisWidget(4, listOf(0, 1))),
            mapOf(4 to listOf(2, 3), 5 to listOf(0))
        )
        assertEquals(2, modules.size)
        assertEquals(ControlHudModules.Module(4, listOf(listOf(0, 1)), listOf(2, 3)), modules[0])
        assertEquals(ControlHudModules.Module(5, emptyList(), listOf(0)), modules[1])
    }

    @Test
    fun `independent throttle axes share their parent widget and preserve graph grouping`() {
        val groups = listOf(listOf(0), listOf(2), listOf(3))
        val module = ControlHudModules.combine(groups.map { ControlHudModules.AxisWidget(9, it) }, mapOf(9 to listOf(4))).single()
        assertEquals(groups, module.axes)
        assertEquals(listOf(4), module.buttons)
    }

    @Test
    fun `modules with no visible channels consume no HUD space`() {
        assertTrue(ControlHudModules.combine(listOf(ControlHudModules.AxisWidget(0, emptyList())), mapOf(0 to emptyList())).isEmpty())
    }
}
