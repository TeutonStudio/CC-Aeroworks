package de.teutonstudio.ccaeroworks.compat.teutonlib

import de.TeutonStudio.TeutonLib.api.computer.ComputerWorkspace
import de.TeutonStudio.TeutonLib.api.computer.client.ComputerWorkspaceClientContext
import de.TeutonStudio.TeutonLib.api.computer.client.ComputerWorkspaceClientDescriptor
import de.TeutonStudio.TeutonLib.api.computer.client.ComputerWorkspaceClientTab
import de.TeutonStudio.TeutonLib.api.computer.client.ComputerWorkspaceClients
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.client.ControlDeskUiClientNavigation
import de.teutonstudio.ccaeroworks.client.WireConnectionScreen
import de.teutonstudio.ccaeroworks.computer.ControlDeskUiSwitchState
import de.teutonstudio.ccaeroworks.computer.wire.WireConnectionView
import de.teutonstudio.ccaeroworks.registry.CCDataComponents
import net.minecraft.client.Minecraft
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.network.chat.Component

/** Aeroworks-owned navigation and actions registered into TeutonLib's shared CC screen. */
object DeskComputerWorkspaceClient : ComputerWorkspaceClientDescriptor {
    override val workspace = DeskComputerWorkspace.ID

    fun register() {
        ComputerWorkspaceClients.register(this)
    }

    override fun matches(menu: dan200.computercraft.shared.computer.inventory.AbstractComputerMenu): Boolean =
        menu.displayStack.has(CCDataComponents.DESK_ID.get())

    override fun tabs(context: ComputerWorkspaceClientContext): List<ComputerWorkspaceClientTab> {
        if (!ControlDeskUiSwitchState.clientCanReturnToControls()) return emptyList()
        return listOf(ComputerWorkspaceClientTab(
            label = Component.translatable("guide.cc_aeroworks.tab.controls"),
            icon = CCAeroworks.id("buttons/control_desk_controls"),
            hoverIcon = CCAeroworks.id("buttons/control_desk_controls_hover")
        ) { current ->
            current.showTerminal.run()
            ControlDeskUiClientNavigation.reopenControls()
        })
    }

    override fun handleAction(
        context: ComputerWorkspaceClientContext,
        action: ComputerWorkspace.Action,
        entry: ComputerWorkspace.Entry?
    ) {
        if (action.id != "targets" || entry == null || entry.target.isEmpty()) return
        val connections = entry.metadata.getList("connections", Tag.TAG_COMPOUND.toInt()).map { raw ->
            val tag = raw as CompoundTag
            WireConnectionView(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"), tag.getString("side"))
        }
        Minecraft.getInstance().setScreen(WireConnectionScreen(context.screen, entry.target, entry.label, connections))
    }
}
