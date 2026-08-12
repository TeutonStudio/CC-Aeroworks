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

        // Attach the switch directly to Aeroworks' visual screen instead of the
        // physical monitor edge. JEI owns those outer side regions and may consume clicks there.
        val buttonWidth = 96
        val buttonHeight = 20
        val buttonX = leftPos + (imageWidth - buttonWidth) / 2
        val buttonY = (topPos - buttonHeight).coerceAtLeast(0)

        addRenderableWidget(
            Button.builder(Component.translatable("guide.cc_aeroworks.tab.computers")) {
                PacketDistributor.sendToServer(
                    SwitchControlDeskUiPayload(SwitchControlDeskUiPayload.Target.COMPUTER)
                )
            }.bounds(buttonX, buttonY, buttonWidth, buttonHeight).build()
        )
    }
}
