package de.teutonstudio.ccaeroworks.client

import de.teutonstudio.ccaeroworks.client.guide.ApiReferenceCatalog
import de.teutonstudio.ccaeroworks.client.guide.GuideBookContent
import de.teutonstudio.ccaeroworks.client.guide.GuideEntry
import de.teutonstudio.ccaeroworks.client.guide.GuideSectionId
import de.teutonstudio.ccaeroworks.client.guide.PixelEditorPanel
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import kotlin.math.min

class GuideBookScreen(
    private val parent: Screen? = null,
    initialSection: GuideSectionId = GuideSectionId.START
) : Screen(Component.translatable("guide.cc_aeroworks.title")) {
    private var sectionIndex: Int = GuideBookContent.indexOf(initialSection)
    private var sidebarScroll: Int = 0
    private var scroll: Int = 0
    private var measuredContentHeight: Int = 0
    private val pixelEditorPanel = PixelEditorPanel()

    override fun isPauseScreen(): Boolean = false

    override fun onClose() {
        minecraft?.setScreen(parent)
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        graphics.fill(0, 0, width, height, SCREEN_OVERLAY)
        val layout = layout()

        graphics.fill(layout.left + 4, layout.top + 5, layout.right + 5, layout.bottom + 6, 0x66000000)
        graphics.fill(layout.left, layout.top, layout.right, layout.bottom, PANEL)
        graphics.renderOutline(layout.left, layout.top, layout.width, layout.height, BORDER)
        graphics.fill(layout.left, layout.top, layout.right, layout.headerBottom, HEADER)
        graphics.fill(layout.left, layout.headerBottom - 2, layout.right, layout.headerBottom, CYAN)

        graphics.drawString(font, "CC>", layout.left + 12, layout.top + 11, CYAN, false)
        graphics.drawString(font, title, layout.left + 35, layout.top + 11, TEXT, false)
        if (layout.width >= 380) graphics.drawString(font, "MANUAL / API", layout.right - 82, layout.top + 11, MUTED, false)

        renderSidebar(graphics, layout, mouseX, mouseY)
        renderSection(graphics, layout, mouseX, mouseY)
        renderFooter(graphics, layout, mouseX, mouseY)
    }

    private fun renderSidebar(graphics: GuiGraphics, layout: Layout, mouseX: Int, mouseY: Int) {
        graphics.fill(layout.left, layout.headerBottom, layout.contentLeft - 1, layout.footerTop, SIDEBAR)
        graphics.fill(layout.contentLeft - 1, layout.headerBottom, layout.contentLeft, layout.footerTop, BORDER_DARK)
        clampSidebarScroll(layout)
        val top = sidebarTop(layout)
        val visibleRows = sidebarVisibleRows(layout)
        val end = min(GuideBookContent.sections.size, sidebarScroll + visibleRows)
        for (index in sidebarScroll until end) {
            val section = GuideBookContent.sections[index]
            val y = top + (index - sidebarScroll) * TAB_HEIGHT
            val hovered = mouseX in (layout.left + 5) until (layout.contentLeft - 5) &&
                mouseY in y until (y + TAB_HEIGHT - 2)
            if (index == sectionIndex || hovered) {
                graphics.fill(
                    layout.left + 5,
                    y,
                    layout.contentLeft - 5,
                    y + TAB_HEIGHT - 2,
                    if (index == sectionIndex) SELECTED else HOVER
                )
            }
            if (index == sectionIndex) {
                graphics.fill(layout.left + 5, y, layout.left + 8, y + TAB_HEIGHT - 2, CYAN)
            }
            graphics.drawString(
                font,
                section.label,
                layout.left + 13,
                y + 5,
                if (index == sectionIndex) TEXT else MUTED,
                false
            )
        }
        if (sidebarScroll > 0) graphics.drawString(font, "^", layout.contentLeft - 13, layout.headerBottom + 3, MUTED, false)
        if (end < GuideBookContent.sections.size) graphics.drawString(font, "v", layout.contentLeft - 13, layout.footerTop - 10, MUTED, false)
    }

    private fun renderSection(graphics: GuiGraphics, layout: Layout, mouseX: Int, mouseY: Int) {
        val x = layout.contentLeft + 14
        val contentWidth = layout.right - x - 14
        val clipTop = layout.headerBottom + 7
        val clipBottom = layout.footerTop - 5
        graphics.enableScissor(layout.contentLeft, clipTop, layout.right, clipBottom)

        var y = clipTop - scroll
        val section = GuideBookContent.sections[sectionIndex]
        graphics.drawString(font, section.title, x, y, GOLD, false)
        y += 18
        section.entries.forEach { entry ->
            y += renderEntry(graphics, entry, x, y, contentWidth, mouseX, mouseY)
        }
        measuredContentHeight = y + scroll - clipTop
        graphics.disableScissor()
        clampScroll(layout)
    }

    private fun renderEntry(
        graphics: GuiGraphics,
        entry: GuideEntry,
        x: Int,
        y: Int,
        width: Int,
        mouseX: Int,
        mouseY: Int
    ): Int = when (entry) {
        is GuideEntry.Text -> drawWrapped(graphics, Component.translatable(entry.key), x, y, width, TEXT, 10) + 6
        is GuideEntry.Note -> renderCallout(graphics, entry.key, x, y, width, NOTE_BG, GOLD, NOTE_TEXT)
        is GuideEntry.Warning -> renderCallout(graphics, entry.key, x, y, width, WARNING_BG, WARNING, WARNING_TEXT)
        is GuideEntry.InputHint -> renderCallout(graphics, entry.key, x, y, width, INPUT_BG, CYAN, TEXT)
        is GuideEntry.Heading -> {
            graphics.drawString(font, entry.text, x, y + 2, GOLD, false)
            16
        }
        is GuideEntry.Api -> renderApiReference(graphics, entry.referenceId, x, y, width)
        is GuideEntry.Code -> renderCode(graphics, entry.lines, x, y, width)
        GuideEntry.PixelEditor -> pixelEditorPanel.render(graphics, font, x, y, width, mouseX, mouseY) + 7
    }

    private fun renderApiReference(graphics: GuiGraphics, referenceId: String, x: Int, y: Int, width: Int): Int {
        val reference = ApiReferenceCatalog.find(referenceId)
        val moduleRows = if (reference.moduleName == null) 0 else 1
        val height = 31 + moduleRows * 11 + reference.methods.size * 11
        graphics.fill(x, y, x + width, y + height, API_BG)
        graphics.renderOutline(x, y, width, height, BORDER_DARK)
        graphics.drawString(font, reference.name, x + 8, y + 6, CYAN, false)
        if (reference.preferred) {
            val marker = "PREFERRED"
            graphics.drawString(font, marker, x + width - font.width(marker) - 8, y + 6, GOLD, false)
        }
        graphics.drawString(
            font,
            font.plainSubstrByWidth(reference.availability, width - 16),
            x + 8,
            y + 17,
            MUTED,
            false
        )
        var lineY = y + 28
        reference.moduleName?.let { module ->
            graphics.drawString(font, "require(\"$module\")", x + 8, lineY, NOTE_TEXT, false)
            lineY += 11
        }
        reference.methods.forEach { method ->
            graphics.drawString(font, font.plainSubstrByWidth(method, width - 16), x + 8, lineY, TEXT, false)
            lineY += 11
        }
        return height + 7
    }

    private fun renderCode(graphics: GuiGraphics, lines: List<String>, x: Int, y: Int, width: Int): Int {
        val height = lines.size * 11 + 10
        graphics.fill(x, y, x + width, y + height, CODE_BG)
        graphics.renderOutline(x, y, width, height, BORDER_DARK)
        lines.forEachIndexed { index, line ->
            graphics.drawString(
                font,
                font.plainSubstrByWidth(line, width - 16),
                x + 8,
                y + 6 + index * 11,
                CYAN,
                false
            )
        }
        return height + 7
    }

    private fun renderCallout(
        graphics: GuiGraphics,
        key: String,
        x: Int,
        y: Int,
        width: Int,
        background: Int,
        accent: Int,
        color: Int
    ): Int {
        val component = Component.translatable(key)
        val height = wrappedHeight(component, width - 15, 10)
        graphics.fill(x, y, x + width, y + height + 10, background)
        graphics.fill(x, y, x + 3, y + height + 10, accent)
        drawWrapped(graphics, component, x + 9, y + 5, width - 15, color, 10)
        return height + 16
    }

    private fun renderFooter(graphics: GuiGraphics, layout: Layout, mouseX: Int, mouseY: Int) {
        graphics.fill(layout.left, layout.footerTop, layout.right, layout.bottom, FOOTER)
        graphics.fill(layout.left, layout.footerTop, layout.right, layout.footerTop + 1, BORDER_DARK)
        val previous = NavBox(layout.contentLeft + 12, layout.footerTop + 5, 28, 15)
        val next = NavBox(layout.contentLeft + 45, layout.footerTop + 5, 28, 15)
        val close = NavBox(layout.right - 55, layout.footerTop + 5, 43, 15)
        drawNav(graphics, previous, "<", mouseX, mouseY, sectionIndex > 0)
        drawNav(graphics, next, ">", mouseX, mouseY, sectionIndex < GuideBookContent.sections.lastIndex)
        drawNav(graphics, close, Component.translatable("gui.done").string, mouseX, mouseY, true)
        graphics.drawString(
            font,
            "${sectionIndex + 1} / ${GuideBookContent.sections.size}",
            layout.contentLeft + 84,
            layout.footerTop + 8,
            MUTED,
            false
        )
    }

    private fun drawNav(graphics: GuiGraphics, box: NavBox, label: String, mouseX: Int, mouseY: Int, enabled: Boolean) {
        val hovered = enabled && box.contains(mouseX.toDouble(), mouseY.toDouble())
        graphics.fill(box.x, box.y, box.x + box.width, box.y + box.height, if (hovered) HOVER else BUTTON)
        graphics.renderOutline(box.x, box.y, box.width, box.height, if (enabled) BORDER else BORDER_DARK)
        graphics.drawCenteredString(font, label, box.x + box.width / 2, box.y + 4, if (enabled) TEXT else DISABLED)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val layout = layout()
        if (activeSectionHasPixelEditor() && layout.containsContent(mouseX, mouseY) &&
            pixelEditorPanel.mouseClicked(mouseX, mouseY, button)
        ) return true
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button)

        if (layout.containsSidebar(mouseX, mouseY)) {
            val row = ((mouseY - sidebarTop(layout)) / TAB_HEIGHT).toInt()
            val index = sidebarScroll + row
            if (index in GuideBookContent.sections.indices) {
                selectSection(index, layout)
                return true
            }
        }
        if (NavBox(layout.contentLeft + 12, layout.footerTop + 5, 28, 15).contains(mouseX, mouseY) && sectionIndex > 0) {
            selectSection(sectionIndex - 1, layout)
            return true
        }
        if (NavBox(layout.contentLeft + 45, layout.footerTop + 5, 28, 15).contains(mouseX, mouseY) &&
            sectionIndex < GuideBookContent.sections.lastIndex
        ) {
            selectSection(sectionIndex + 1, layout)
            return true
        }
        if (NavBox(layout.right - 55, layout.footerTop + 5, 43, 15).contains(mouseX, mouseY)) {
            onClose()
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        if (activeSectionHasPixelEditor() && pixelEditorPanel.mouseDragged(mouseX, mouseY, button)) return true
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (pixelEditorPanel.mouseReleased()) return true
        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val layout = layout()
        if (layout.containsSidebar(mouseX, mouseY)) {
            sidebarScroll = (sidebarScroll - scrollY.toInt()).coerceAtLeast(0)
            clampSidebarScroll(layout)
            return true
        }
        if (!layout.containsContent(mouseX, mouseY)) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
        scroll = (scroll - (scrollY * 18.0).toInt()).coerceAtLeast(0)
        clampScroll(layout)
        return true
    }

    private fun activeSectionHasPixelEditor(): Boolean =
        GuideBookContent.sections[sectionIndex].entries.any { it === GuideEntry.PixelEditor }

    private fun selectSection(index: Int, layout: Layout = layout()) {
        sectionIndex = index.coerceIn(GuideBookContent.sections.indices)
        scroll = 0
        ensureSelectedSectionVisible(layout)
    }

    private fun ensureSelectedSectionVisible(layout: Layout) {
        val rows = sidebarVisibleRows(layout)
        if (sectionIndex < sidebarScroll) sidebarScroll = sectionIndex
        if (sectionIndex >= sidebarScroll + rows) sidebarScroll = sectionIndex - rows + 1
        clampSidebarScroll(layout)
    }

    private fun sidebarTop(layout: Layout): Int = layout.headerBottom + 7

    private fun sidebarVisibleRows(layout: Layout): Int =
        ((layout.footerTop - 5 - sidebarTop(layout)) / TAB_HEIGHT).coerceAtLeast(1)

    private fun clampSidebarScroll(layout: Layout) {
        val max = (GuideBookContent.sections.size - sidebarVisibleRows(layout)).coerceAtLeast(0)
        sidebarScroll = Mth.clamp(sidebarScroll, 0, max)
    }

    private fun clampScroll(layout: Layout) {
        val viewport = layout.footerTop - (layout.headerBottom + 7) - 5
        scroll = Mth.clamp(scroll, 0, (measuredContentHeight - viewport).coerceAtLeast(0))
    }

    private fun drawWrapped(
        graphics: GuiGraphics,
        component: Component,
        x: Int,
        y: Int,
        width: Int,
        color: Int,
        lineHeight: Int
    ): Int {
        val lines = font.split(component, width)
        lines.forEachIndexed { index, line -> graphics.drawString(font, line, x, y + index * lineHeight, color, false) }
        return lines.size * lineHeight
    }

    private fun wrappedHeight(component: Component, width: Int, lineHeight: Int): Int =
        font.split(component, width).size * lineHeight

    private fun layout(): Layout {
        val panelWidth = (width - 24).coerceIn(280, 440)
        val panelHeight = (height - 24).coerceIn(210, 300)
        val left = (width - panelWidth) / 2
        val top = (height - panelHeight) / 2
        return Layout(left, top, left + panelWidth, top + panelHeight)
    }

    private data class Layout(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width: Int get() = right - left
        val height: Int get() = bottom - top
        val headerBottom: Int get() = top + 34
        val footerTop: Int get() = bottom - 26
        val contentLeft: Int get() = left + 116

        fun containsContent(mouseX: Double, mouseY: Double): Boolean =
            mouseX >= contentLeft && mouseX < right && mouseY >= headerBottom + 7 && mouseY < footerTop - 5

        fun containsSidebar(mouseX: Double, mouseY: Double): Boolean =
            mouseX >= left + 5 && mouseX < contentLeft - 5 && mouseY >= headerBottom + 7 && mouseY < footerTop - 5
    }

    private data class NavBox(val x: Int, val y: Int, val width: Int, val height: Int) {
        fun contains(mouseX: Double, mouseY: Double): Boolean =
            mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height
    }

    private companion object {
        const val TAB_HEIGHT: Int = 19
        const val PANEL: Int = 0xFF101820.toInt()
        const val SCREEN_OVERLAY: Int = 0xE60A0F14.toInt()
        const val HEADER: Int = 0xFF172733.toInt()
        const val SIDEBAR: Int = 0xFF0C141B.toInt()
        const val FOOTER: Int = 0xFF0C141B.toInt()
        const val CODE_BG: Int = 0xFF071117.toInt()
        const val API_BG: Int = 0xFF0A171E.toInt()
        const val NOTE_BG: Int = 0xFF282316.toInt()
        const val WARNING_BG: Int = 0xFF321919.toInt()
        const val INPUT_BG: Int = 0xFF102832.toInt()
        const val SELECTED: Int = 0xFF173746.toInt()
        const val HOVER: Int = 0xFF234958.toInt()
        const val BUTTON: Int = 0xFF172733.toInt()
        const val BORDER: Int = 0xFF4BA7BE.toInt()
        const val BORDER_DARK: Int = 0xFF294652.toInt()
        const val CYAN: Int = 0xFF68D4EB.toInt()
        const val GOLD: Int = 0xFFFFC66D.toInt()
        const val WARNING: Int = 0xFFFF7676.toInt()
        const val TEXT: Int = 0xFFE7F3F6.toInt()
        const val NOTE_TEXT: Int = 0xFFFFE2A6.toInt()
        const val WARNING_TEXT: Int = 0xFFFFCACA.toInt()
        const val MUTED: Int = 0xFF91A8AF.toInt()
        const val DISABLED: Int = 0xFF526269.toInt()
    }
}
