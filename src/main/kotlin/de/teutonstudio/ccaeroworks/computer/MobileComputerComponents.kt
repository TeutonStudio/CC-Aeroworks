package de.teutonstudio.ccaeroworks.computer

import dan200.computercraft.api.component.ComputerComponent
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.contraption.MobileConsoleComputer
import java.lang.ref.WeakReference

class MobileConsoleAccess(owner: MobileConsoleComputer) {
    private val owner = WeakReference(owner)
    fun owner(): MobileConsoleComputer? = owner.get()?.takeUnless { it.closed }
}

object MobileComputerComponents {
    @JvmField
    val CONSOLE: ComputerComponent<MobileConsoleAccess> =
        ComputerComponent.create(CCAeroworks.MOD_ID, "mobile_console")
}
