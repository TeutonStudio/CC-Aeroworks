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

/**
 * Geometry and rendering for the third ComputerControlDesk sidebar action.
 * The background deliberately reuses CC:Tweaked's family-specific sidebar sprite.
 */
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

    data class Layout(val x: Int, val y: Int)

    fun layout(leftPos: Int, topPos: Int, sidebarYOffset: Int): Layout =
        Layout(leftPos, topPos + sidebarYOffset + ComputerSidebar.HEIGHT + TAB_GAP)

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

    fun controlsButton(layout: Layout, onPress: () -> Unit): DynamicImageButton {
        val label = Component.translatable("guide.cc_aeroworks.tab.controls")
        return DynamicImageButton(
            layout.x + ICON_X,
            layout.y + ICON_Y,
            ICON_SIZE,
            ICON_SIZE,
            CONTROLS_TEXTURES::get,
            { _ -> onPress() },
            DynamicImageButton.HintedMessage(label, Tooltip.create(label))
        )
    }
}
