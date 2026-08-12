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

        // Attach the switch directly to the top edge of CC:Tweaked's visual screen.
        // Keeping it inside the GUI's horizontal footprint also keeps JEI's side panels
        // from claiming the same click area.
        val buttonWidth = 82
        val buttonHeight = 20
        val buttonX = leftPos + (imageWidth - buttonWidth) / 2
        val buttonY = (topPos - buttonHeight).coerceAtLeast(0)

        addRenderableWidget(
            Button.builder(Component.translatable("guide.cc_aeroworks.tab.controls")) {
                ControlDeskUiSwitchState.prepareClientControlsScreen()
                PacketDistributor.sendToServer(
                    SwitchControlDeskUiPayload(SwitchControlDeskUiPayload.Target.CONTROLS)
                )
            }.bounds(buttonX, buttonY, buttonWidth, buttonHeight).build()
        )
    }
}
