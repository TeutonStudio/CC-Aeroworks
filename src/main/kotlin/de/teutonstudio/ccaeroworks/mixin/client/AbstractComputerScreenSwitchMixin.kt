package de.teutonstudio.ccaeroworks.mixin.client

import dan200.computercraft.client.gui.AbstractComputerScreen
import dan200.computercraft.shared.computer.inventory.AbstractComputerMenu
import de.teutonstudio.ccaeroworks.computer.ControlDeskUiSwitchState
import de.teutonstudio.ccaeroworks.network.SwitchControlDeskUiPayload
import de.teutonstudio.ccaeroworks.registry.CCItems
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.neoforged.neoforge.network.PacketDistributor
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(value = [AbstractComputerScreen::class], remap = false)
abstract class AbstractComputerScreenSwitchMixin(
    menu: AbstractComputerMenu,
    inventory: Inventory,
    title: Component
) : AbstractContainerScreen<AbstractComputerMenu>(menu, inventory, title) {
    @Inject(method = ["init()V"], at = [At("TAIL")])
    private fun ccaeroworks_addControlsButton(callback: CallbackInfo) {
        val item = menu.displayStack.item
        if (item !== CCItems.COMPUTER_CONTROL_DESK.get() &&
            item !== CCItems.ADVANCED_COMPUTER_CONTROL_DESK.get()
        ) return

        addRenderableWidget(
            Button.builder(Component.translatable("guide.cc_aeroworks.tab.controls")) {
                ControlDeskUiSwitchState.prepareClientControlsScreen()
                PacketDistributor.sendToServer(
                    SwitchControlDeskUiPayload(SwitchControlDeskUiPayload.Target.CONTROLS)
                )
            }.bounds(width - BUTTON_WIDTH - MARGIN, MARGIN, BUTTON_WIDTH, BUTTON_HEIGHT).build()
        )
    }

    private companion object {
        const val BUTTON_WIDTH = 82
        const val BUTTON_HEIGHT = 20
        const val MARGIN = 6
    }
}
