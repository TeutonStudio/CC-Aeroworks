package de.teutonstudio.ccaeroworks.mixin.client

import com.mred231.aeroworks.content.controls.ConsoleScreen
import de.teutonstudio.ccaeroworks.client.ControlDeskNavigationButtons
import de.teutonstudio.ccaeroworks.computer.ControlDeskUiSwitchState
import de.teutonstudio.ccaeroworks.network.SwitchControlDeskUiPayload
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.network.PacketDistributor
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(value = [ConsoleScreen::class], remap = false)
abstract class ConsoleScreenSwitchMixin(title: Component) : Screen(title) {
    @Inject(method = ["init()V"], at = [At("TAIL")])
    private fun ccaeroworks_addNavigationButtons(callback: CallbackInfo) {
        val accessor = this as ConsoleScreenAccessor
        val console = accessor.ccaeroworks_getConsole()

        // ConsoleScreen only exists when Aeroworks' ConsoleScreenOpener.hasOverview() is true.
        // Remember OVERVIEW independently from ModuleScreen's DETAIL context. With one control
        // Aeroworks skips this screen entirely, so no overview is ever assumed to exist.
        ControlDeskUiSwitchState.rememberClientOverview(console)

        val computerAction = if (ControlDeskUiSwitchState.clientCanSwitchToComputer()) {
            Runnable {
                ControlDeskUiSwitchState.rememberClientOverview(console)
                PacketDistributor.sendToServer(SwitchControlDeskUiPayload(console.blockPos))
            }
        } else {
            null
        }

        ControlDeskNavigationButtons.navigationButtons(
            this,
            accessor.ccaeroworks_getWindowLeft(),
            computerAction
        ).forEach { addRenderableWidget(it) }
    }
}
