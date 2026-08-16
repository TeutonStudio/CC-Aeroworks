package de.teutonstudio.ccaeroworks.client

import com.mred231.aeroworks.foundation.gui.widget.HoverTintIconButton
import de.teutonstudio.ccaeroworks.client.guide.GuideSectionId
import net.createmod.catnip.gui.element.ScreenElement
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * Adds CC-Aeroworks actions on the left side of Aeroworks' native bottom action row.
 *
 * Aeroworks 1.3.0 right-aligns its own destructive/confirm actions (Delete/Done). Our actions
 * therefore use the screen's left content edge and never move or derive their X position from
 * those native right-aligned actions. Computer is optional; API documentation is always present.
 */
object ControlDeskNavigationButtons {
    private const val UI_INSET = 8
    private const val BUTTON_GAP = 2
    private const val AEROWORKS_HOVER_TINT = 0x80FF80

    private val COMPUTER_ICON = ScreenElement { graphics: GuiGraphics, x: Int, y: Int ->
        val light = 0xFFF2F2F2.toInt()
        val dark = 0xFF202020.toInt()

        graphics.fill(x + 3, y + 3, x + 14, y + 11, light)
        graphics.fill(x + 4, y + 4, x + 13, y + 10, dark)
        graphics.fill(x + 8, y + 11, x + 10, y + 13, light)
        graphics.fill(x + 6, y + 13, x + 12, y + 14, light)
    }

    private val API_ICON = ScreenElement { graphics: GuiGraphics, x: Int, y: Int ->
        val light = 0xFFF2F2F2.toInt()

        // Compact code brackets: < >
        graphics.fill(x + 3, y + 7, x + 5, y + 10, light)
        graphics.fill(x + 5, y + 5, x + 7, y + 7, light)
        graphics.fill(x + 5, y + 10, x + 7, y + 12, light)
        graphics.fill(x + 12, y + 7, x + 14, y + 10, light)
        graphics.fill(x + 10, y + 5, x + 12, y + 7, light)
        graphics.fill(x + 10, y + 10, x + 12, y + 12, light)
    }

    fun navigationButtons(
        screen: Screen,
        uiLeft: Int,
        computerCallback: Runnable?
    ): List<HoverTintIconButton> {
        // Y and chrome still follow Aeroworks' native bottom-row button. X does not: Delete/Done
        // are right-aligned and must remain exactly where Aeroworks put them.
        val anchor = screen.children()
            .filterIsInstance<HoverTintIconButton>()
            .maxByOrNull { it.x }
            ?: return emptyList()

        val buttons = mutableListOf<HoverTintIconButton>()
        var nextX = uiLeft + UI_INSET

        if (computerCallback != null) {
            val computer = createButton(
                nextX,
                anchor.y,
                COMPUTER_ICON,
                Component.translatable("guide.cc_aeroworks.tab.computers"),
                computerCallback
            )
            buttons += computer
            nextX = computer.x + computer.width + BUTTON_GAP
        }

        buttons += createButton(
            nextX,
            anchor.y,
            API_ICON,
            Component.translatable("guide.cc_aeroworks.tab.network"),
            Runnable {
                Minecraft.getInstance().setScreen(
                    GuideBookScreen(screen, GuideSectionId.NETWORK_API)
                )
            }
        )
        return buttons
    }

    private fun createButton(
        x: Int,
        y: Int,
        icon: ScreenElement,
        tooltip: Component,
        callback: Runnable
    ): HoverTintIconButton = HoverTintIconButton(
        x,
        y,
        icon,
        AEROWORKS_HOVER_TINT
    ).also { button ->
        button.withCallback<HoverTintIconButton>(callback)
        button.setToolTip(tooltip)
    }
}
