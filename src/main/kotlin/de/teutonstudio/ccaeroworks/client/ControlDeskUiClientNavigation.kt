package de.teutonstudio.ccaeroworks.client

import com.mred231.aeroworks.content.controls.ConsoleScreenOpener
import de.teutonstudio.ccaeroworks.computer.ControlDeskUiSwitchState

/**
 * Client-only Aeroworks return path.
 *
 * Exact DETAIL returns use ConsoleSocket.reopenModuleMenu(). Otherwise defer to
 * ConsoleScreenOpener.open(), which is Aeroworks' own 0/1/many dispatcher:
 * no controls -> no screen, one control -> ModuleScreen directly, many -> ConsoleScreen.
 */
object ControlDeskUiClientNavigation {
    fun reopenControls(): Boolean {
        if (ControlDeskUiSwitchState.reopenExactClientControls()) return true

        val console = ControlDeskUiSwitchState.clientReturnConsole() ?: return false
        ConsoleScreenOpener.open(console)
        return true
    }
}
