package de.teutonstudio.ccaeroworks.mixin.client

import dan200.computercraft.client.gui.AbstractComputerScreen
import dan200.computercraft.shared.computer.inventory.AbstractComputerMenu
import de.teutonstudio.ccaeroworks.client.ControlDeskComputerSidebar
import de.teutonstudio.ccaeroworks.client.ControlDeskUiClientNavigation
import de.teutonstudio.ccaeroworks.computer.ControlDeskUiSwitchState
import de.teutonstudio.ccaeroworks.registry.CCItems
import net.minecraft.client.gui.components.Renderable
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
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
    private fun ccaeroworks_addControlsTab(callback: CallbackInfo) {
        val item = menu.displayStack.item
        if (item !== CCItems.COMPUTER_CONTROL_DESK.get() &&
            item !== CCItems.ADVANCED_COMPUTER_CONTROL_DESK.get()
        ) return
        if (!ControlDeskUiSwitchState.clientCanReturnToControls()) return

        val accessor = this as AbstractComputerScreenAccessor
        val family = accessor.ccaeroworks_getFamily()
        val layout = ControlDeskComputerSidebar.layout(
            leftPos,
            topPos,
            accessor.ccaeroworks_getSidebarYOffset()
        )

        // Draw a third, one-button segment directly below CC:Tweaked's native Power/Terminate
        // sidebar. The helper reuses the native normal/advanced yellow sidebar sprite.
        addRenderableOnly(Renderable { graphics, _, _, _ ->
            ControlDeskComputerSidebar.renderBackground(graphics, layout, family)
        })

        // DynamicImageButton intentionally matches CC:Tweaked's native sidebar widget type.
        // AbstractComputerScreen already restores terminal focus after these buttons are clicked.
        addRenderableWidget(
            ControlDeskComputerSidebar.controlsButton(layout) {
                ControlDeskUiClientNavigation.reopenControls()
            }
        )
    }
}
