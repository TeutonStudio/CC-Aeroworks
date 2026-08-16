package de.teutonstudio.ccaeroworks.client

import de.teutonstudio.ccaeroworks.CCAeroworks
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import kotlin.math.max

internal sealed interface SourceSelectorIcon {
    data class Item(val stack: ItemStack) : SourceSelectorIcon
    data class Sprite(val location: ResourceLocation) : SourceSelectorIcon
}

internal data class SourceSelectorPresentation(
    val title: Component,
    val subtitle: Component,
    val icon: SourceSelectorIcon
)

internal data class SourceSelectorOption<T>(
    val key: String,
    val value: T,
    val presentation: SourceSelectorPresentation,
    val selectable: Boolean = true
)

/** Shared one-row selector used by radar and Lua display source bindings. */
internal class SourceSelectorWidget<T>(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    private val font: Font,
    selectedKey: String,
    private val options: (selectedKey: String) -> List<SourceSelectorOption<T>>,
    private val callback: (T) -> Unit,
    message: Component
) : AbstractWidget(x, y, width, height, message) {
    private var selectedKey: String = selectedKey
    private var expanded: Boolean = false
    private var scrollIndex: Int = 0

    override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val entries = options(selectedKey)
        val selected = entries.firstOrNull { it.key == selectedKey } ?: entries.firstOrNull()
        renderSelectorRow(graphics, x, y, width, height, selected?.presentation, isHovered, expanded)
        if (expanded) renderPopup(graphics, mouseX, mouseY, entries)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (!visible || !active || button != 0) return false
        if (inside(mouseX, mouseY, x, y, width, height)) {
            expanded = !expanded
            return true
        }
        if (!expanded) return false

        val entries = options(selectedKey)
        val popup = popupBounds(entries.size)
        if (!inside(mouseX, mouseY, popup.x, popup.y, popup.width, popup.height)) {
            expanded = false
            return false
        }

        val visibleEntries = visibleOptions(entries)
        val row = ((mouseY - popup.y) / POPUP_ROW_HEIGHT).toInt()
        val option = visibleEntries.getOrNull(row) ?: return true
        if (!option.selectable) return true
        selectedKey = option.key
        expanded = false
        callback(option.value)
        return true
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (!expanded || !visible || !active) return false
        val entries = options(selectedKey)
        val popup = popupBounds(entries.size)
        if (!inside(mouseX, mouseY, popup.x, popup.y, popup.width, popup.height)) return false
        val maxScroll = max(0, entries.size - MAX_VISIBLE_OPTIONS)
        val direction = when {
            scrollY > 0.0 -> -1
            scrollY < 0.0 -> 1
            else -> 0
        }
        scrollIndex = (scrollIndex + direction).coerceIn(0, maxScroll)
        return direction != 0
    }

    override fun isMouseOver(mouseX: Double, mouseY: Double): Boolean {
        if (!visible) return false
        if (inside(mouseX, mouseY, x, y, width, height)) return true
        if (!expanded) return false
        val popup = popupBounds(options(selectedKey).size)
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

    fun setSelectedKey(key: String) {
        selectedKey = key
    }

    private fun renderPopup(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        entries: List<SourceSelectorOption<T>>
    ) {
        val visibleEntries = visibleOptions(entries)
        val popup = popupBounds(entries.size)
        graphics.pose().pushPose()
        graphics.pose().translate(0.0f, 0.0f, POPUP_Z)
        visibleEntries.forEachIndexed { index, option ->
            val rowY = popup.y + index * POPUP_ROW_HEIGHT
            val hovered = inside(mouseX.toDouble(), mouseY.toDouble(), popup.x, rowY, popup.width, POPUP_ROW_HEIGHT)
            renderSelectorRow(
                graphics,
                popup.x,
                rowY,
                popup.width,
                POPUP_ROW_HEIGHT,
                option.presentation,
                hovered && option.selectable,
                expanded = false,
                showChevron = false
            )
            if (!option.selectable) {
                graphics.fill(popup.x + 1, rowY + 1, popup.x + popup.width - 1, rowY + POPUP_ROW_HEIGHT - 1, 0x18000000)
            }
        }
        graphics.pose().popPose()
    }

    private fun renderSelectorRow(
        graphics: GuiGraphics,
        left: Int,
        top: Int,
        rowWidth: Int,
        rowHeight: Int,
        presentation: SourceSelectorPresentation?,
        hovered: Boolean,
        expanded: Boolean,
        showChevron: Boolean = true
    ) {
        val background = if (hovered) ROW_HOVER_SPRITE else ROW_SPRITE
        graphics.blitSprite(background, left, top, rowWidth, rowHeight)
        if (presentation == null) return

        val iconX = left + ICON_X
        val iconY = top + ICON_Y
        when (val icon = presentation.icon) {
            is SourceSelectorIcon.Item -> graphics.renderItem(icon.stack, iconX, iconY)
            is SourceSelectorIcon.Sprite -> graphics.blitSprite(icon.location, iconX, iconY, ICON_SIZE, ICON_SIZE)
        }

        val reservedRight = if (showChevron) CHEVRON_AREA_WIDTH else RIGHT_PADDING
        val textWidth = (rowWidth - TEXT_X - reservedRight).coerceAtLeast(8)
        graphics.drawString(
            font,
            font.plainSubstrByWidth(presentation.title.string, textWidth),
            left + TEXT_X,
            top + TITLE_Y,
            TITLE_COLOR,
            false
        )
        graphics.drawString(
            font,
            font.plainSubstrByWidth(presentation.subtitle.string, textWidth),
            left + TEXT_X,
            top + SUBTITLE_Y,
            SUBTITLE_COLOR,
            false
        )

        if (showChevron) {
            val sprite = if (expanded) CHEVRON_UP_SPRITE else CHEVRON_DOWN_SPRITE
            graphics.blitSprite(
                sprite,
                left + rowWidth - CHEVRON_AREA_WIDTH + (CHEVRON_AREA_WIDTH - CHEVRON_WIDTH) / 2,
                top + (rowHeight - CHEVRON_HEIGHT) / 2,
                CHEVRON_WIDTH,
                CHEVRON_HEIGHT
            )
        }
    }

    private fun visibleOptions(entries: List<SourceSelectorOption<T>>): List<SourceSelectorOption<T>> {
        val maxScroll = max(0, entries.size - MAX_VISIBLE_OPTIONS)
        scrollIndex = scrollIndex.coerceIn(0, maxScroll)
        return entries.drop(scrollIndex).take(MAX_VISIBLE_OPTIONS)
    }

    private fun popupBounds(optionCount: Int): PopupBounds {
        val rows = optionCount.coerceAtLeast(1).coerceAtMost(MAX_VISIBLE_OPTIONS)
        val popupHeight = rows * POPUP_ROW_HEIGHT
        val screenHeight = Minecraft.getInstance().screen?.height ?: Int.MAX_VALUE
        val below = y + height + 1
        val popupY = if (below + popupHeight <= screenHeight - SCREEN_MARGIN) below else y - popupHeight - 1
        return PopupBounds(x, popupY, width, popupHeight)
    }

    private fun inside(mouseX: Double, mouseY: Double, left: Int, top: Int, width: Int, height: Int): Boolean =
        mouseX >= left && mouseX < left + width && mouseY >= top && mouseY < top + height

    private data class PopupBounds(val x: Int, val y: Int, val width: Int, val height: Int)

    private companion object {
        val ROW_SPRITE: ResourceLocation = CCAeroworks.id("source_selector/row")
        val ROW_HOVER_SPRITE: ResourceLocation = CCAeroworks.id("source_selector/row_hover")
        val CHEVRON_DOWN_SPRITE: ResourceLocation = CCAeroworks.id("source_selector/dropdown_down")
        val CHEVRON_UP_SPRITE: ResourceLocation = CCAeroworks.id("source_selector/dropdown_up")

        const val ICON_X = 6
        const val ICON_Y = 7
        const val ICON_SIZE = 16
        const val TEXT_X = 28
        const val TITLE_Y = 5
        const val SUBTITLE_Y = 16
        const val CHEVRON_AREA_WIDTH = 22
        const val CHEVRON_WIDTH = 7
        const val CHEVRON_HEIGHT = 4
        const val RIGHT_PADDING = 6
        const val MAX_VISIBLE_OPTIONS = 5
        const val POPUP_ROW_HEIGHT = 30
        const val SCREEN_MARGIN = 8
        const val TITLE_COLOR = 0x202020
        const val SUBTITLE_COLOR = 0x777777
        const val POPUP_Z = 350.0f
    }
}
