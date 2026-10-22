package de.teutonstudio.ccaeroworks.mixin.client

import dan200.computercraft.client.gui.AbstractComputerScreen
import dan200.computercraft.shared.computer.inventory.AbstractComputerMenu
import de.TeutonStudio.TeutonLib.client.computer.ComputerPanels
import de.TeutonStudio.TeutonLib.client.computer.ComputerSidebarTabs
import de.TeutonStudio.TeutonLib.client.computer.WorkspacePanels
import de.teutonstudio.ccaeroworks.client.ControlDeskComputerSidebar
import de.teutonstudio.ccaeroworks.client.ControlDeskUiClientNavigation
import de.teutonstudio.ccaeroworks.client.WireConnectionScreen
import de.teutonstudio.ccaeroworks.compat.teutonlib.DeskComputerWorkspace
import de.teutonstudio.ccaeroworks.computer.ControlDeskUiSwitchState
import de.teutonstudio.ccaeroworks.computer.wire.WireConnectionView
import de.teutonstudio.ccaeroworks.registry.CCDataComponents
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.nbt.Tag
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import org.lwjgl.glfw.GLFW
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

/** Aeroworks supplies navigation and its backend; TeutonLib owns the workspace. */
@Mixin(value = [AbstractComputerScreen::class], remap = false)
abstract class AbstractComputerScreenSwitchMixin(menu: AbstractComputerMenu, inventory: Inventory, title: Component) :
    AbstractContainerScreen<AbstractComputerMenu>(menu, inventory, title) {
    @Unique private var ccaeroworks_workspace: WorkspacePanels? = null

    @Inject(method = ["init()V"], at = [At("TAIL")])
    private fun ccaeroworks_addDeskTabs(callback: CallbackInfo) {
        if (!menu.displayStack.has(CCDataComponents.DESK_ID.get())) return
        val accessor = this as AbstractComputerScreenAccessor
        val terminal = accessor.ccaeroworks_getTerminal() ?: return
        val page = ccaeroworks_workspace?.page() ?: 0
        val family = accessor.ccaeroworks_getFamily()
        val workspace = ComputerPanels.workspace(menu.containerId, DeskComputerWorkspace.ID,
            terminal.x, terminal.y, terminal.width, terminal.height, font,
            { addRenderableWidget(it) }, { setFocused(terminal) }) { action, entry ->
            if (action.id == "targets" && entry != null && entry.target.isNotEmpty()) {
                val connections = entry.metadata.getList("connections", Tag.TAG_COMPOUND.toInt()).map { raw ->
                    val tag = raw as net.minecraft.nbt.CompoundTag
                    WireConnectionView(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"), tag.getString("side"))
                }
                minecraft?.setScreen(WireConnectionScreen(this, entry.target, entry.label, connections))
            }
        }
        workspace.family(family)
        ccaeroworks_workspace = workspace
        (this as WorkspacePanels.Host).`teutonlib$setWorkspace`(workspace)
        var index = 0
        if (ControlDeskUiSwitchState.clientCanReturnToControls()) {
            addRenderableWidget(ControlDeskComputerSidebar.controlsButton(leftPos,
                ComputerSidebarTabs.top(topPos, accessor.ccaeroworks_getSidebarYOffset(), index++), family) {
                ccaeroworks_show(0); ControlDeskUiClientNavigation.reopenControls()
            })
        }
        for ((target, name) in listOf(1 to "sources", 2 to "channels")) {
            addRenderableWidget(ComputerSidebarTabs.tab(leftPos,
                ComputerSidebarTabs.top(topPos, accessor.ccaeroworks_getSidebarYOffset(), index++), family, name,
                { workspace.page() == target }, { ccaeroworks_show(if (workspace.page() == target) 0 else target) }))
        }
        ccaeroworks_show(page)
    }
    @Unique private fun ccaeroworks_show(page: Int) {
        val workspace = ccaeroworks_workspace ?: return
        workspace.show(page)
        val terminal = (this as AbstractComputerScreenAccessor).ccaeroworks_getTerminal()
        terminal?.visible = page == 0; terminal?.active = page == 0
        setFocused(if (page == 0) terminal else null)
        ccaeroworks_applyFocus()
    }
    @Unique private fun ccaeroworks_applyFocus() {
        val workspace = ccaeroworks_workspace ?: return
        val requested = workspace.takeRequestedFocus()
        if (requested != null) { setFocused(requested); requested.isFocused = true }
        else if (focused is ComputerSidebarTabs.Tab) setFocused(if (workspace.page() == 0) (this as AbstractComputerScreenAccessor).ccaeroworks_getTerminal() else null)
    }
    @Inject(method = ["containerTick()V"], at = [At("TAIL")])
    private fun ccaeroworks_tickWorkspace(callback: CallbackInfo) { ccaeroworks_workspace?.tick(); ccaeroworks_applyFocus() }
    @Inject(method = ["mouseClicked(DDI)Z"], at = [At("TAIL")])
    private fun ccaeroworks_clickFocus(x: Double, y: Double, button: Int, callback: CallbackInfoReturnable<Boolean>) { ccaeroworks_applyFocus() }
    @Inject(method = ["keyPressed(III)Z"], at = [At("TAIL")])
    private fun ccaeroworks_keyFocus(key: Int, scan: Int, modifiers: Int, callback: CallbackInfoReturnable<Boolean>) { ccaeroworks_applyFocus() }
    @Inject(method = ["keyPressed(III)Z"], at = [At("HEAD")], cancellable = true)
    private fun ccaeroworks_workspaceKeys(key: Int, scan: Int, modifiers: Int, callback: CallbackInfoReturnable<Boolean>) {
        if ((ccaeroworks_workspace?.page() ?: 0) == 0) return
        if (key == GLFW.GLFW_KEY_ESCAPE) { if (ccaeroworks_workspace?.closeSourceApi() != true) ccaeroworks_show(0); callback.returnValue = true }
        else if (ccaeroworks_isEditingText() && minecraft!!.options.keyInventory.matches(key, scan)) callback.returnValue = true
    }

    /** Keep container hotkeys out of every active text input hosted by this screen. */
    @Unique private fun ccaeroworks_isEditingText(): Boolean =
        children().filterIsInstance<EditBox>().any { it.isFocused && it.visible && it.active }
}
