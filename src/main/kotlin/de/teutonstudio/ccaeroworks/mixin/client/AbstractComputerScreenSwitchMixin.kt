package de.teutonstudio.ccaeroworks.mixin.client

import dan200.computercraft.client.gui.AbstractComputerScreen
import dan200.computercraft.shared.computer.inventory.AbstractComputerMenu
import de.teutonstudio.ccaeroworks.client.ControlDeskComputerSidebar
import de.teutonstudio.ccaeroworks.client.ControlDeskUiClientNavigation
import de.teutonstudio.ccaeroworks.client.DeskIoOverviewClient
import de.teutonstudio.ccaeroworks.client.DeskIoOverviewScreen
import de.teutonstudio.ccaeroworks.computer.ControlDeskUiSwitchState
import de.teutonstudio.ccaeroworks.network.RequestDeskIoOverviewPayload
import de.teutonstudio.ccaeroworks.registry.CCItems
import net.minecraft.client.gui.components.Renderable
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
    private fun ccaeroworks_addComputerDeskTabs(callback: CallbackInfo) {
        val item = menu.displayStack.item
        if (item !== CCItems.COMPUTER_CONTROL_DESK.get() &&
            item !== CCItems.ADVANCED_COMPUTER_CONTROL_DESK.get()
        ) return
        if (!ControlDeskUiSwitchState.clientCanReturnToControls()) return

        val console = ControlDeskUiSwitchState.clientReturnConsole() ?: return
        val accessor = this as AbstractComputerScreenAccessor
        val family = accessor.ccaeroworks_getFamily()
        val layouts = (0..2).map { index ->
            ControlDeskComputerSidebar.layout(
                leftPos,
                topPos,
                accessor.ccaeroworks_getSidebarYOffset(),
                index
            )
        }

        layouts.forEach { layout ->
            addRenderableOnly(Renderable { graphics, _, _, _ ->
                ControlDeskComputerSidebar.renderBackground(graphics, layout, family)
            })
        }

        addRenderableWidget(
            ControlDeskComputerSidebar.controlsButton(layouts[0]) {
                ControlDeskUiClientNavigation.reopenControls()
            }
        )
        addRenderableWidget(
            ControlDeskComputerSidebar.channelsButton(layouts[1]) {
                DeskIoOverviewClient.preferCategory(DeskIoOverviewScreen.CATEGORY_CONTROL)
                PacketDistributor.sendToServer(RequestDeskIoOverviewPayload(console.blockPos))
            }
        )
        addRenderableWidget(
            ControlDeskComputerSidebar.sourcesButton(layouts[2]) {
                DeskIoOverviewClient.preferCategory(DeskIoOverviewScreen.CATEGORY_INFORMATION)
                PacketDistributor.sendToServer(RequestDeskIoOverviewPayload(console.blockPos))
            }
        )
    }
}
