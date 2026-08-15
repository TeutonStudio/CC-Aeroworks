package de.teutonstudio.ccaeroworks.client

import de.teutonstudio.ccaeroworks.display.DisplayScriptCatalogState
import de.teutonstudio.ccaeroworks.display.DisplayScriptDescriptor
import de.teutonstudio.ccaeroworks.display.RadarSourceDescriptor
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractButton
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import kotlin.math.max

internal data class RadarSourceChoice(
    val ingressPos: BlockPos?,
    val descriptor: RadarSourceDescriptor?
)

internal class RadarSourceRowButton(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    private val font: Font,
    val choice: RadarSourceChoice,
    private val callback: (RadarSourceChoice) -> Unit
) : AbstractButton(x, y, width, height, Component.literal("Radar source")) {
    var selected: Boolean = false

    override fun onPress() = callback(choice)

    override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (isHovered) graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0x18FFFFFF)

        val stack = radarStack()
        graphics.renderItem(stack, x + 6, y + 7)

        val title = radarName(stack)
        val subtitle = networkLabel()
        graphics.drawString(font, font.plainSubstrByWidth(title, width - 58), x + 28, y + 6, 0x202020, false)
        graphics.drawString(font, font.plainSubstrByWidth(subtitle, width - 58), x + 28, y + 17, 0x777777, false)

        if (selected) renderCheck(graphics, x + width - 19, y + 8)
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {
        defaultButtonNarrationText(output)
    }

    private fun radarStack(): ItemStack {
        val level = Minecraft.getInstance().level
        val radarPos = choice.descriptor?.radarPos
        if (level != null && radarPos != null && level.isLoaded(radarPos)) {
            return ItemStack(level.getBlockState(radarPos).block)
        }
        return ItemStack(Items.COMPASS)
    }

    private fun radarName(stack: ItemStack): String {
        val descriptor = choice.descriptor
        if (descriptor == null) {
            return if (choice.ingressPos == null) "Lokales Radar" else "Nicht verfügbares Radar"
        }
        val level = Minecraft.getInstance().level
        val radarPos = descriptor.radarPos
        if (level != null && radarPos != null && level.isLoaded(radarPos)) return stack.hoverName.string
        return "Radar ${descriptor.memberIndex + 1}"
    }

    private fun networkLabel(): String {
        val source = choice.descriptor
        if (source != null) return "Netzwerk ${source.id}"
        val ingress = choice.ingressPos ?: return "Netzwerk lokal"
        return "Netzwerk ${ingress.x},${ingress.y},${ingress.z}"
    }

    private fun renderCheck(graphics: GuiGraphics, left: Int, top: Int) {
        val dark = 0xFF444444.toInt()
        graphics.fill(left, top, left + 12, top + 1, dark)
        graphics.fill(left, top + 11, left + 12, top + 12, dark)
        graphics.fill(left, top, left + 1, top + 12, dark)
        graphics.fill(left + 11, top, left + 12, top + 12, dark)
        graphics.fill(left + 3, top + 6, left + 5, top + 8, dark)
        graphics.fill(left + 5, top + 8, left + 7, top + 10, dark)
        graphics.fill(left + 7, top + 4, left + 9, top + 9, dark)
    }
}

internal class ScriptSourceDropdownWidget(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    private val font: Font,
    private val deskPos: BlockPos,
    private val socket: Int,
    selectedPath: String,
    private val callback: (String) -> Unit
) : AbstractWidget(x, y, width, height, Component.literal("Script source")) {
    private var selectedPath: String = selectedPath
    private var expanded = false
    private var scrollIndex = 0

    override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val entries = entries()
        val selected = entries.firstOrNull { it.path == selectedPath }
        val title = when {
            selected != null -> selected.name
            selectedPath.isNotBlank() -> "Fehlende Skriptquelle"
            entries.isEmpty() -> "Keine gültigen Skripte"
            else -> "Skriptquelle auswählen"
        }
        val subtitle = selected?.path ?: selectedPath.ifBlank { "require(\"display\") / require(\"touchdisplay\")" }

        if (isHovered) graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0x18FFFFFF)
        graphics.drawString(font, font.plainSubstrByWidth(title, width - 26), x + 8, y + 6, 0x202020, false)
        graphics.drawString(font, font.plainSubstrByWidth(subtitle, width - 26), x + 8, y + 17, 0x777777, false)
        graphics.drawString(font, if (expanded) "^" else "v", x + width - 14, y + 11, 0x444444, false)

        if (expanded) renderPopup(graphics, mouseX, mouseY, entries)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (!visible || !active || button != 0) return false
        if (inside(mouseX, mouseY, x, y, width, height)) {
            expanded = !expanded
            return true
        }
        if (!expanded) return false

        val options = options(entries())
        val popup = popupBounds(options.size)
        if (!inside(mouseX, mouseY, popup.x, popup.y, popup.width, popup.height)) {
            expanded = false
            return false
        }
        val visibleOptions = visibleOptions(options)
        val row = ((mouseY - popup.y) / POPUP_ROW_HEIGHT).toInt()
        val option = visibleOptions.getOrNull(row) ?: return true
        selectedPath = option?.path.orEmpty()
        expanded = false
        callback(selectedPath)
        return true
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (!expanded || !visible || !active) return false
        val options = options(entries())
        val popup = popupBounds(options.size)
        if (!inside(mouseX, mouseY, popup.x, popup.y, popup.width, popup.height)) return false
        val maxScroll = max(0, options.size - MAX_VISIBLE_OPTIONS)
        scrollIndex = (scrollIndex - scrollY.toInt()).coerceIn(0, maxScroll)
        return true
    }

    override fun isMouseOver(mouseX: Double, mouseY: Double): Boolean {
        if (!visible) return false
        if (inside(mouseX, mouseY, x, y, width, height)) return true
        if (!expanded) return false
        val popup = popupBounds(options(entries()).size)
        return inside(mouseX, mouseY, popup.x, popup.y, popup.width, popup.height)
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {
        defaultButtonNarrationText(output)
    }

    fun setRowPosition(rowX: Int, rowY: Int, rowVisible: Boolean) {
        x = rowX
        y = rowY
        visible = rowVisible
        active = rowVisible
        if (!rowVisible) expanded = false
    }

    fun setSelectedPath(path: String) {
        selectedPath = path
    }

    private fun entries(): List<DisplayScriptDescriptor> = DisplayScriptCatalogState.get(deskPos, socket)

    private fun options(entries: List<DisplayScriptDescriptor>): List<DisplayScriptDescriptor?> = listOf(null) + entries

    private fun visibleOptions(options: List<DisplayScriptDescriptor?>): List<DisplayScriptDescriptor?> {
        val maxScroll = max(0, options.size - MAX_VISIBLE_OPTIONS)
        scrollIndex = scrollIndex.coerceIn(0, maxScroll)
        return options.drop(scrollIndex).take(MAX_VISIBLE_OPTIONS)
    }

    private fun renderPopup(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        entries: List<DisplayScriptDescriptor>
    ) {
        val options = options(entries)
        val visible = visibleOptions(options)
        val popup = popupBounds(options.size)
        graphics.pose().pushPose()
        graphics.pose().translate(0.0f, 0.0f, 350.0f)
        graphics.fill(popup.x, popup.y, popup.x + popup.width, popup.y + popup.height, 0xFFF0F0F0.toInt())
        renderBorder(graphics, popup.x, popup.y, popup.width, popup.height, 0xFF555555.toInt())
        visible.forEachIndexed { index, option ->
            val rowY = popup.y + index * POPUP_ROW_HEIGHT
            if (inside(mouseX.toDouble(), mouseY.toDouble(), popup.x, rowY, popup.width, POPUP_ROW_HEIGHT)) {
                graphics.fill(popup.x + 1, rowY + 1, popup.x + popup.width - 1, rowY + POPUP_ROW_HEIGHT - 1, 0xFFD8D8D8.toInt())
            }
            val name = option?.name ?: "Keine Skriptquelle"
            val path = option?.path ?: "Standard / deaktiviert"
            graphics.drawString(font, font.plainSubstrByWidth(name, popup.width - 12), popup.x + 6, rowY + 3, 0x202020, false)
            graphics.drawString(font, font.plainSubstrByWidth(path, popup.width - 12), popup.x + 6, rowY + 13, 0x777777, false)
        }
        graphics.pose().popPose()
    }

    private fun popupBounds(optionCount: Int): PopupBounds {
        val rows = optionCount.coerceAtLeast(1).coerceAtMost(MAX_VISIBLE_OPTIONS)
        val popupHeight = rows * POPUP_ROW_HEIGHT
        val screenHeight = Minecraft.getInstance().screen?.height ?: Int.MAX_VALUE
        val below = y + height + 1
        val popupY = if (below + popupHeight <= screenHeight - 8) below else y - popupHeight - 1
        return PopupBounds(x, popupY, width, popupHeight)
    }

    private fun renderBorder(graphics: GuiGraphics, left: Int, top: Int, width: Int, height: Int, color: Int) {
        graphics.fill(left, top, left + width, top + 1, color)
        graphics.fill(left, top + height - 1, left + width, top + height, color)
        graphics.fill(left, top, left + 1, top + height, color)
        graphics.fill(left + width - 1, top, left + width, top + height, color)
    }

    private fun inside(mouseX: Double, mouseY: Double, left: Int, top: Int, width: Int, height: Int): Boolean =
        mouseX >= left && mouseX < left + width && mouseY >= top && mouseY < top + height

    private data class PopupBounds(val x: Int, val y: Int, val width: Int, val height: Int)

    private companion object {
        const val MAX_VISIBLE_OPTIONS = 5
        const val POPUP_ROW_HEIGHT = 23
    }
}
