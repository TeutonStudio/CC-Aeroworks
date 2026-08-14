package de.teutonstudio.ccaeroworks.client

import com.mred231.aeroworks.foundation.gui.widget.HoverTintIconButton
import net.createmod.catnip.gui.element.ScreenElement
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * Adds the Computer action on the left side of Aeroworks' native bottom action row.
 *
 * Aeroworks 1.3.0 right-aligns its own destructive/confirm actions (Delete/Done). The Computer
 * action therefore uses the screen's left content edge and never moves or derives its X position
 * from those native right-aligned actions.
 */
object ControlDeskNavigationButtons {
    private const val UI_INSET = 8
    private const val AEROWORKS_HOVER_TINT = 0x80FF80

    private val COMPUTER_ICON = ScreenElement { graphics: GuiGraphics, x: Int, y: Int ->
        val light = 0xFFF2F2F2.toInt()
        val dark = 0xFF202020.toInt()

        graphics.fill(x + 3, y + 3, x + 14, y + 11, light)
        graphics.fill(x + 4, y + 4, x + 13, y + 10, dark)
        graphics.fill(x + 8, y + 11, x + 10, y + 13, light)
        graphics.fill(x + 6, y + 13, x + 12, y + 14, light)
    }

    fun computerButton(screen: Screen, uiLeft: Int, callback: Runnable): HoverTintIconButton? {
        // Y and chrome still follow Aeroworks' native bottom-row button. X does not: Delete/Done
        // are right-aligned and must remain exactly where Aeroworks put them.
        val anchor = screen.children()
            .filterIsInstance<HoverTintIconButton>()
            .maxByOrNull { it.x }
            ?: return null
        val buttonX = uiLeft + UI_INSET

        return HoverTintIconButton(
            buttonX,
            anchor.y,
            COMPUTER_ICON,
            AEROWORKS_HOVER_TINT
        ).also { button ->
            button.withCallback<HoverTintIconButton>(callback)
            button.setToolTip(Component.translatable("guide.cc_aeroworks.tab.computers"))
        }
    }
}
