package de.teutonstudio.ccaeroworks.mixin.client

import com.mred231.aeroworks.content.controls.ConsoleSocket
import com.mred231.aeroworks.content.controls.ModuleMenu
import com.mred231.aeroworks.content.controls.ModuleScreen
import de.teutonstudio.ccaeroworks.client.ControlDeskNavigationButtons
import de.teutonstudio.ccaeroworks.computer.ControlDeskUiSwitchState
import de.teutonstudio.ccaeroworks.network.SwitchControlDeskUiPayload
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.neoforged.neoforge.network.PacketDistributor
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(value = [ModuleScreen::class], remap = false)
abstract class ModuleScreenSwitchMixin(
    menu: ModuleMenu,
    inventory: Inventory,
    title: Component
) : AbstractContainerScreen<ModuleMenu>(menu, inventory, title) {
    @Inject(method = ["init()V"], at = [At("TAIL")])
    private fun ccaeroworks_addComputerButton(callback: CallbackInfo) {
        // A desk-mounted ModuleScreen has an exact ConsoleSocket, including recursive subPath.
        // This is the authoritative DETAIL return target. Held modules deliberately clear it.
        ControlDeskUiSwitchState.rememberClientControls(menu.contentHolder)
        if (!ControlDeskUiSwitchState.clientCanSwitchToComputer()) return
        val socket = menu.contentHolder as? ConsoleSocket ?: return

        val computerButton = ControlDeskNavigationButtons.computerButton(this, leftPos, Runnable {
            // Refresh the socket at the actual transition in case Aeroworks rebuilt the holder.
            ControlDeskUiSwitchState.rememberClientControls(menu.contentHolder)
            val current = menu.contentHolder as? ConsoleSocket ?: return@Runnable
            PacketDistributor.sendToServer(SwitchControlDeskUiPayload(current.be().blockPos))
        }) ?: return

        addRenderableWidget(computerButton)
    }
}
