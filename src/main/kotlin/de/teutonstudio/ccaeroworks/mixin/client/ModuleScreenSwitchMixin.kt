package de.teutonstudio.ccaeroworks.mixin.client

import com.mred231.aeroworks.content.controls.ModuleMenu
import com.mred231.aeroworks.content.controls.ModuleScreen
import de.teutonstudio.ccaeroworks.computer.ControlDeskUiSwitchState
import de.teutonstudio.ccaeroworks.network.SwitchControlDeskUiPayload
import net.minecraft.client.gui.components.Button
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
        if (!ControlDeskUiSwitchState.clientCanSwitchToComputer()) return

        addRenderableWidget(
            Button.builder(Component.translatable("guide.cc_aeroworks.tab.computers")) {
                PacketDistributor.sendToServer(
                    SwitchControlDeskUiPayload(SwitchControlDeskUiPayload.Target.COMPUTER)
                )
            }.bounds(width - BUTTON_WIDTH - MARGIN, MARGIN, BUTTON_WIDTH, BUTTON_HEIGHT).build()
        )
    }

    private companion object {
        const val BUTTON_WIDTH = 96
        const val BUTTON_HEIGHT = 20
        const val MARGIN = 6
    }
}
