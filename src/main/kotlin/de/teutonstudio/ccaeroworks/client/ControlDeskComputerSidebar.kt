package de.teutonstudio.ccaeroworks.client

import dan200.computercraft.client.gui.GuiSprites
import dan200.computercraft.client.gui.widgets.ComputerSidebar
import dan200.computercraft.client.gui.widgets.DynamicImageButton
import dan200.computercraft.shared.computer.core.ComputerFamily
import dan200.computercraft.shared.computer.inventory.AbstractComputerMenu
import de.teutonstudio.ccaeroworks.CCAeroworks
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.network.chat.Component

/** CC:Tweaked-style vertical navigation for ComputerControlDesk-specific pages. */
object ControlDeskComputerSidebar {
    private const val TAB_GAP = 2
    private const val TAB_HEIGHT = 22
    private const val ICON_X = 4
    private const val ICON_Y = 5
    private const val ICON_SIZE = 12

    private val CONTROLS_TEXTURES = GuiSprites.ButtonTextures(
        CCAeroworks.id("buttons/control_desk_controls"),
        CCAeroworks.id("buttons/control_desk_controls_hover")
    )
    private val CHANNELS_TEXTURES = GuiSprites.ButtonTextures(
        CCAeroworks.id("buttons/control_desk_channels"),
        CCAeroworks.id("buttons/control_desk_channels_hover")
    )
    private val SOURCES_TEXTURES = GuiSprites.ButtonTextures(
        CCAeroworks.id("buttons/control_desk_sources"),
        CCAeroworks.id("buttons/control_desk_sources_hover")
    )

    data class Layout(val x: Int, val y: Int)

    fun layout(leftPos: Int, topPos: Int, sidebarYOffset: Int, index: Int): Layout =
        Layout(
            leftPos,
            topPos + sidebarYOffset + ComputerSidebar.HEIGHT + TAB_GAP + index * (TAB_HEIGHT + TAB_GAP)
        )

    fun renderBackground(graphics: GuiGraphics, layout: Layout, family: ComputerFamily) {
        val sidebar = GuiSprites.getComputerTextures(family).sidebar() ?: return
        graphics.blitSprite(
            sidebar,
            layout.x,
            layout.y,
            AbstractComputerMenu.SIDEBAR_WIDTH,
            TAB_HEIGHT
        )
    }

    fun controlsButton(layout: Layout, onPress: () -> Unit): DynamicImageButton =
        button(layout, CONTROLS_TEXTURES, Component.translatable("guide.cc_aeroworks.tab.controls"), onPress)

    fun channelsButton(layout: Layout, onPress: () -> Unit): DynamicImageButton =
        button(layout, CHANNELS_TEXTURES, Component.literal("Channels"), onPress)

    fun sourcesButton(layout: Layout, onPress: () -> Unit): DynamicImageButton =
        button(layout, SOURCES_TEXTURES, Component.literal("Information Sources"), onPress)

    private fun button(
        layout: Layout,
        textures: GuiSprites.ButtonTextures,
        label: Component,
        onPress: () -> Unit
    ): DynamicImageButton = DynamicImageButton(
        layout.x + ICON_X,
        layout.y + ICON_Y,
        ICON_SIZE,
        ICON_SIZE,
        textures::get,
        { _ -> onPress() },
        DynamicImageButton.HintedMessage(label, Tooltip.create(label))
    )
}
