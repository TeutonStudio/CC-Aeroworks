package de.teutonstudio.ccaeroworks.client

import com.mred231.aeroworks.foundation.gui.widget.HoverTintIconButton
import net.createmod.catnip.gui.element.ScreenElement
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import kotlin.math.abs

/**
 * Adds CC-Aeroworks navigation inside Aeroworks' native bottom action row.
 *
 * The Computer button owns the original left-most slot. Existing Aeroworks actions are shifted
 * to the right while retaining their order, so the row reads Computer -> native actions -> Done
 * instead of appending Computer after the native row.
 */
object ControlDeskNavigationButtons {
    private const val GAP = 4
    private const val AEROWORKS_HOVER_TINT = 0x80FF80

    private val COMPUTER_ICON = ScreenElement { graphics: GuiGraphics, x: Int, y: Int ->
        val light = 0xFFF2F2F2.toInt()
        val dark = 0xFF202020.toInt()

        graphics.fill(x + 3, y + 3, x + 14, y + 11, light)
        graphics.fill(x + 4, y + 4, x + 13, y + 10, dark)
        graphics.fill(x + 8, y + 11, x + 10, y + 13, light)
        graphics.fill(x + 6, y + 13, x + 12, y + 14, light)
    }

    fun computerButton(screen: Screen, callback: Runnable): HoverTintIconButton? {
        val nativeButtons = screen.children()
            .filterIsInstance<HoverTintIconButton>()
        val anchor = nativeButtons.maxByOrNull { it.x } ?: return null
        val row = nativeButtons.filter { abs(it.y - anchor.y) <= 1 }
        val leftmost = row.minOfOrNull { it.x } ?: anchor.x
        val size = anchor.width
        val shift = size + GAP

        // Keep the row left-aligned at Aeroworks' original position. The Computer action takes
        // that first slot and all native actions move one slot to the right.
        row.forEach { it.setX(it.x + shift) }

        return HoverTintIconButton(
            leftmost,
            anchor.y,
            COMPUTER_ICON,
            AEROWORKS_HOVER_TINT
        ).also { button ->
            // Catnip exposes withCallback as <T extends AbstractSimiWidget> T withCallback(...).
            // Kotlin cannot infer T when the generic return value is ignored.
            button.withCallback<HoverTintIconButton>(callback)
            button.setToolTip(Component.translatable("guide.cc_aeroworks.tab.computers"))
        }
    }
}
