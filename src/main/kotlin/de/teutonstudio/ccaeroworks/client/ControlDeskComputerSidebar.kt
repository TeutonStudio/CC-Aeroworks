package de.teutonstudio.ccaeroworks.client

import dan200.computercraft.shared.computer.core.ComputerFamily
import de.TeutonStudio.TeutonLib.client.computer.ComputerSidebarTabs
import de.teutonstudio.ccaeroworks.CCAeroworks
import net.minecraft.network.chat.Component

/** Pult-specific return action using TeutonLib's shared casing and keyboard behavior. */
object ControlDeskComputerSidebar {
    fun controlsButton(x: Int, y: Int, family: ComputerFamily, onPress: () -> Unit): ComputerSidebarTabs.Tab =
        ComputerSidebarTabs.tab(x, y, family, Component.translatable("guide.cc_aeroworks.tab.controls"),
            CCAeroworks.id("buttons/control_desk_controls"), CCAeroworks.id("buttons/control_desk_controls_hover"),
            { false }, onPress)
}
