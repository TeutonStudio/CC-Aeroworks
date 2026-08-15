package de.teutonstudio.ccaeroworks.mixin.client

import dan200.computercraft.client.gui.AbstractComputerScreen
import dan200.computercraft.shared.computer.inventory.AbstractComputerMenu
import de.teutonstudio.ccaeroworks.client.ComputerDeskPage
import de.teutonstudio.ccaeroworks.client.ControlDeskComputerSidebar
import de.teutonstudio.ccaeroworks.client.ControlDeskUiClientNavigation
import de.teutonstudio.ccaeroworks.client.WireChannelManagerWidget
import de.teutonstudio.ccaeroworks.computer.ControlDeskUiSwitchState
import de.teutonstudio.ccaeroworks.computer.wire.WireChannelSnapshotState
import de.teutonstudio.ccaeroworks.network.MutateWireChannelPayload
import de.teutonstudio.ccaeroworks.network.RequestWireChannelSnapshotPayload
import de.teutonstudio.ccaeroworks.network.WireChannelMutation
import de.teutonstudio.ccaeroworks.registry.CCItems
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.Renderable
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.neoforged.neoforge.network.PacketDistributor
import org.lwjgl.glfw.GLFW
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
import java.util.UUID

@Mixin(value = [AbstractComputerScreen::class], remap = false)
abstract class AbstractComputerScreenSwitchMixin(
    menu: AbstractComputerMenu,
    inventory: Inventory,
    title: Component
) : AbstractContainerScreen<AbstractComputerMenu>(menu, inventory, title) {
    @Unique
    private var ccaeroworks_page: ComputerDeskPage = ComputerDeskPage.TERMINAL

    @Unique
    private var ccaeroworks_channelPanel: WireChannelManagerWidget? = null

    @Unique
    private var ccaeroworks_channelName: EditBox? = null

    @Unique
    private var ccaeroworks_addChannel: Button? = null

    @Unique
    private var ccaeroworks_renameChannel: Button? = null

    @Unique
    private var ccaeroworks_deleteChannel: Button? = null

    @Unique
    private var ccaeroworks_deleteArmed: UUID? = null

    @Unique
    private var ccaeroworks_lastSnapshotRequest: Long = Long.MIN_VALUE

    @Inject(method = ["init()V"], at = [At("TAIL")])
    private fun ccaeroworks_addDeskTabs(callback: CallbackInfo) {
        val item = menu.displayStack.item
        if (item !== CCItems.COMPUTER_CONTROL_DESK.get() &&
            item !== CCItems.ADVANCED_COMPUTER_CONTROL_DESK.get()
        ) return

        val accessor = this as AbstractComputerScreenAccessor
        val family = accessor.ccaeroworks_getFamily()
        val canReturnToControls = ControlDeskUiSwitchState.clientCanReturnToControls()
        var extensionIndex = 0

        if (canReturnToControls) {
            val controlsLayout = ControlDeskComputerSidebar.layout(
                leftPos,
                topPos,
                accessor.ccaeroworks_getSidebarYOffset(),
                extensionIndex++
            )
            addRenderableOnly(Renderable { graphics, _, _, _ ->
                ControlDeskComputerSidebar.renderBackground(graphics, controlsLayout, family)
            })
            addRenderableWidget(
                ControlDeskComputerSidebar.controlsButton(controlsLayout) {
                    ccaeroworks_setPage(ComputerDeskPage.TERMINAL)
                    ControlDeskUiClientNavigation.reopenControls()
                }
            )
        }

        val channelLayout = ControlDeskComputerSidebar.layout(
            leftPos,
            topPos,
            accessor.ccaeroworks_getSidebarYOffset(),
            extensionIndex
        )
        addRenderableOnly(Renderable { graphics, _, _, _ ->
            ControlDeskComputerSidebar.renderBackground(graphics, channelLayout, family)
        })
        addRenderableWidget(
            ControlDeskComputerSidebar.channelsButton(channelLayout) {
                ccaeroworks_setChannelMode(ccaeroworks_page != ComputerDeskPage.CHANNELS)
            }
        )

        ccaeroworks_createChannelManager(accessor)
        ccaeroworks_setPage(ComputerDeskPage.TERMINAL)
    }

    @Inject(method = ["containerTick()V"], at = [At("TAIL")])
    private fun ccaeroworks_refreshWireChannels(callback: CallbackInfo) {
        if (ccaeroworks_page != ComputerDeskPage.CHANNELS) return
        val now = Minecraft.getInstance().level?.gameTime ?: return
        if (now - ccaeroworks_lastSnapshotRequest < 20L) return
        ccaeroworks_requestWireSnapshot(now)
    }

    @Inject(method = ["keyPressed(III)Z"], at = [At("HEAD")], cancellable = true)
    private fun ccaeroworks_escapeChannelManager(
        keyCode: Int,
        scanCode: Int,
        modifiers: Int,
        callback: CallbackInfoReturnable<Boolean>
    ) {
        if (ccaeroworks_page == ComputerDeskPage.TERMINAL || keyCode != GLFW.GLFW_KEY_ESCAPE) return
        ccaeroworks_setPage(ComputerDeskPage.TERMINAL)
        callback.returnValue = true
    }

    @Unique
    private fun ccaeroworks_createChannelManager(accessor: AbstractComputerScreenAccessor) {
        val terminal = accessor.ccaeroworks_getTerminal() ?: return
        val footerHeight = 24
        val buttonWidth = 45
        val gap = 3
        val panel = WireChannelManagerWidget(
            terminal.x,
            terminal.y,
            terminal.width,
            (terminal.height - footerHeight).coerceAtLeast(30),
            font
        )
        panel.visible = false
        panel.active = false
        ccaeroworks_channelPanel = addRenderableWidget(panel)

        val footerY = terminal.y + terminal.height - 20
        val fieldWidth = (terminal.width - buttonWidth * 3 - gap * 5).coerceAtLeast(50)
        val nameField = EditBox(
            font,
            terminal.x + gap,
            footerY,
            fieldWidth,
            18,
            Component.literal("channel name")
        )
        nameField.setMaxLength(32)
        nameField.visible = false
        nameField.active = false
        ccaeroworks_channelName = addRenderableWidget(nameField)

        var buttonX = terminal.x + gap * 2 + fieldWidth
        ccaeroworks_addChannel = addRenderableWidget(
            Button.builder(Component.literal("Add")) { ccaeroworks_addWireChannel() }
                .bounds(buttonX, footerY, buttonWidth, 18)
                .build()
                .also { it.visible = false; it.active = false }
        )
        buttonX += buttonWidth + gap
        ccaeroworks_renameChannel = addRenderableWidget(
            Button.builder(Component.literal("Rename")) { ccaeroworks_renameWireChannel() }
                .bounds(buttonX, footerY, buttonWidth, 18)
                .build()
                .also { it.visible = false; it.active = false }
        )
        buttonX += buttonWidth + gap
        ccaeroworks_deleteChannel = addRenderableWidget(
            Button.builder(Component.literal("Delete")) { ccaeroworks_deleteWireChannel() }
                .bounds(buttonX, footerY, buttonWidth, 18)
                .build()
                .also { it.visible = false; it.active = false }
        )
    }

    /** Compatibility wrapper retained while the page model grows beyond the original wire-only tab. */
    @Unique
    private fun ccaeroworks_setChannelMode(enabled: Boolean) {
        ccaeroworks_setPage(if (enabled) ComputerDeskPage.CHANNELS else ComputerDeskPage.TERMINAL)
    }

    @Unique
    private fun ccaeroworks_setPage(page: ComputerDeskPage) {
        ccaeroworks_page = page
        val channelEnabled = page == ComputerDeskPage.CHANNELS
        val terminal = (this as AbstractComputerScreenAccessor).ccaeroworks_getTerminal()
        terminal?.visible = page == ComputerDeskPage.TERMINAL
        terminal?.active = page == ComputerDeskPage.TERMINAL

        ccaeroworks_channelPanel?.let { it.visible = channelEnabled; it.active = channelEnabled }
        ccaeroworks_channelName?.let { it.visible = channelEnabled; it.active = channelEnabled }
        ccaeroworks_addChannel?.let { it.visible = channelEnabled; it.active = channelEnabled }
        ccaeroworks_renameChannel?.let { it.visible = channelEnabled; it.active = channelEnabled }
        ccaeroworks_deleteChannel?.let {
            it.visible = channelEnabled
            it.active = channelEnabled
            it.message = Component.literal("Delete")
        }
        ccaeroworks_deleteArmed = null

        if (channelEnabled) {
            WireChannelSnapshotState.clear()
            ccaeroworks_requestWireSnapshot(Minecraft.getInstance().level?.gameTime ?: 0L)
            setFocused(ccaeroworks_channelName)
        } else if (terminal != null) {
            setFocused(terminal)
        }
    }

    @Unique
    private fun ccaeroworks_requestWireSnapshot(now: Long) {
        ccaeroworks_lastSnapshotRequest = now
        PacketDistributor.sendToServer(RequestWireChannelSnapshotPayload())
    }

    @Unique
    private fun ccaeroworks_addWireChannel() {
        val name = ccaeroworks_channelName?.value.orEmpty().trim()
        if (name.isEmpty()) return
        PacketDistributor.sendToServer(MutateWireChannelPayload(WireChannelMutation.ADD, null, name))
        ccaeroworks_channelName?.setValue("")
        ccaeroworks_deleteArmed = null
    }

    @Unique
    private fun ccaeroworks_renameWireChannel() {
        val selected = ccaeroworks_channelPanel?.selectedChannel() ?: return
        val name = ccaeroworks_channelName?.value.orEmpty().trim()
        if (name.isEmpty()) return
        PacketDistributor.sendToServer(MutateWireChannelPayload(WireChannelMutation.RENAME, selected.id, name))
        ccaeroworks_deleteArmed = null
        ccaeroworks_deleteChannel?.message = Component.literal("Delete")
    }

    @Unique
    private fun ccaeroworks_deleteWireChannel() {
        val selected = ccaeroworks_channelPanel?.selectedChannel() ?: return
        if (selected.connections > 0 && ccaeroworks_deleteArmed != selected.id) {
            ccaeroworks_deleteArmed = selected.id
            ccaeroworks_deleteChannel?.message = Component.literal("Confirm")
            return
        }
        PacketDistributor.sendToServer(MutateWireChannelPayload(WireChannelMutation.REMOVE, selected.id, ""))
        ccaeroworks_deleteArmed = null
        ccaeroworks_deleteChannel?.message = Component.literal("Delete")
    }
}
