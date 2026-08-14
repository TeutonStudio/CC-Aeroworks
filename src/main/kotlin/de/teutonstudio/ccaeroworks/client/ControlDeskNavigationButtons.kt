package de.teutonstudio.ccaeroworks.client

import com.mred231.aeroworks.foundation.gui.widget.HoverTintIconButton
import net.createmod.catnip.gui.element.ScreenElement
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import kotlin.math.abs

/**
 * Adds CC-Aeroworks navigation without replacing Aeroworks' own bottom action row.
 *
 * Aeroworks 1.3.0 uses HoverTintIconButton for both ConsoleScreen and ModuleScreen.
 * The native Done button is the right-most icon in its row. We place the Computer
 * button immediately to the left of the whole row, so ModuleScreen's optional buttons
 * (for example clear-frequency) remain untouched.
 */
object ControlDeskNavigationButtons {
    private const val GAP = 4
    private const val AEROWORKS_HOVER_TINT = 0x80FF80

    private val COMPUTER_ICON = ScreenElement { graphics: GuiGraphics, x: Int, y: Int ->
        val light = 0xFFF2F2F2.toInt()
        val dark = 0xFF202020.toInt()

        // Compact monitor glyph. HoverTintIconButton supplies the native Aeroworks button chrome.
        graphics.fill(x + 3, y + 3, x + 14, y + 11, light)
        graphics.fill(x + 4, y + 4, x + 13, y + 10, dark)
        graphics.fill(x + 8, y + 11, x + 10, y + 13, light)
        graphics.fill(x + 6, y + 13, x + 12, y + 14, light)
    }

    fun computerButton(screen: Screen, callback: Runnable): HoverTintIconButton? {
        val nativeButtons = screen.children()
            .filterIsInstance<HoverTintIconButton>()
        val done = nativeButtons.maxByOrNull { it.x } ?: return null
        val row = nativeButtons.filter { abs(it.y - done.y) <= 1 }
        val leftmost = row.minOfOrNull { it.x } ?: done.x
        val size = done.width

        return HoverTintIconButton(
            leftmost - GAP - size,
            done.y,
            COMPUTER_ICON,
            AEROWORKS_HOVER_TINT
        ).also { button ->
            // Catnip exposes withCallback as <T extends AbstractSimiWidget> T withCallback(...).
            // Kotlin cannot infer T when the generic return value is ignored, so keep the
            // concrete Aeroworks widget type explicit here.
            button.withCallback<HoverTintIconButton>(callback)
            button.setToolTip(Component.translatable("guide.cc_aeroworks.tab.computers"))
        }
    }
}
