package de.teutonstudio.ccaeroworks.client

import de.teutonstudio.ccaeroworks.client.guide.ApiAvailability
import de.teutonstudio.ccaeroworks.client.guide.GuideBookContent
import de.teutonstudio.ccaeroworks.client.guide.GuideEntry
import de.teutonstudio.ccaeroworks.client.guide.GuideNode
import de.teutonstudio.ccaeroworks.client.guide.GuidePage
import de.teutonstudio.ccaeroworks.client.guide.PixelEditorPanel
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth

class GuideBookScreen : Screen(Component.translatable("guide.cc_aeroworks.title")) {
    private var selectedPageId: String = GuideBookContent.firstPage().id
    private var contentScroll: Int = 0
    private var sidebarScroll: Int = 0
    private var measuredContentHeight: Int = 0
    private val expandedCategories = mutableSetOf("start", "desks", "displays", "displays/reactive", "api")
    private val pixelEditorPanel = PixelEditorPanel()
    private val history = mutableListOf(selectedPageId)
    private var historyCursor = 0
    private lateinit var searchBox: EditBox

    override fun isPauseScreen(): Boolean = false

    override fun init() {
        super.init()
        val layout = layout()
        searchBox = EditBox(
            font,
            layout.left + 11,
            layout.top + 10,
            layout.sidebarWidth - 20,
            16,
            Component.translatable("guide.cc_aeroworks.search")
        ).also { box ->
            box.setHint(Component.translatable("guide.cc_aeroworks.search"))
            box.setMaxLength(80)
            box.setResponder {
                sidebarScroll = 0
                if (it.isNotBlank()) {
                    GuideBookContent.search(it).firstOrNull()?.let { result -> selectPage(result.id, true) }
                }
            }
            addRenderableWidget(box)
        }
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        graphics.fill(0, 0, width, height, SCREEN_OVERLAY)
        val layout = layout()

        graphics.fill(layout.left + 4, layout.top + 5, layout.right + 5, layout.bottom + 6, 0x66000000)
        graphics.fill(layout.left, layout.top, layout.right, layout.bottom, PANEL)
        graphics.renderOutline(layout.left, layout.top, layout.width, layout.height, BORDER)
        graphics.fill(layout.left, layout.top, layout.right, layout.headerBottom, HEADER)
        graphics.fill(layout.left, layout.headerBottom - 2, layout.right, layout.headerBottom, CYAN)

        graphics.drawString(font, "CC>", layout.contentLeft + 12, layout.top + 12, CYAN, false)
        graphics.drawString(font, title, layout.contentLeft + 43, layout.top + 12, TEXT, false)
        graphics.drawString(font, "HANDBUCH", layout.right - 65, layout.top + 12, MUTED, false)

        renderSidebar(graphics, layout, mouseX, mouseY)
        renderSection(graphics, layout, mouseX, mouseY)
        renderFooter(graphics, layout, mouseX, mouseY)
        searchBox.render(graphics, mouseX, mouseY, partialTick)
    }

    private fun renderSidebar(graphics: GuiGraphics, layout: Layout, mouseX: Int, mouseY: Int) {
        graphics.fill(layout.left, layout.headerBottom, layout.contentLeft - 1, layout.footerTop, SIDEBAR)
        graphics.fill(layout.contentLeft - 1, layout.headerBottom, layout.contentLeft, layout.footerTop, BORDER_DARK)
        val rows = sidebarRows()
        val clipTop = layout.headerBottom + 7
        val clipBottom = layout.footerTop - 5
        graphics.enableScissor(layout.left, clipTop, layout.contentLeft - 1, clipBottom)
        rows.forEachIndexed { index, row ->
            val y = clipTop + index * TAB_HEIGHT - sidebarScroll
            if (y + TAB_HEIGHT < clipTop || y >= clipBottom) return@forEachIndexed
            val hovered = mouseX in (layout.left + 5) until (layout.contentLeft - 5) &&
                mouseY in y until (y + TAB_HEIGHT - 1)
            val selected = row.page?.id == selectedPageId
            if (selected || hovered) {
                graphics.fill(
                    layout.left + 5,
                    y,
                    layout.contentLeft - 5,
                    y + TAB_HEIGHT - 1,
                    if (selected) SELECTED else HOVER
                )
            }
            if (selected) graphics.fill(layout.left + 5, y, layout.left + 8, y + TAB_HEIGHT - 1, CYAN)

            val x = layout.left + 11 + row.depth * 9
            if (row.category != null) {
                val expanded = row.category.id in expandedCategories
                graphics.drawString(font, if (expanded) "v" else ">", x, y + 5, GOLD, false)
                graphics.drawString(font, Component.translatable(row.category.labelKey), x + 9, y + 5, TEXT, false)
            } else if (row.page != null) {
                graphics.drawString(font, Component.translatable(row.page.labelKey), x + 7, y + 5, if (selected) TEXT else MUTED, false)
            }
        }
        graphics.disableScissor()
        clampSidebarScroll(layout, rows.size)
    }

    private fun renderSection(graphics: GuiGraphics, layout: Layout, mouseX: Int, mouseY: Int) {
        val page = currentPage()
        val x = layout.contentLeft + 14
        val contentWidth = layout.right - x - 14
        val clipTop = layout.headerBottom + 7
        val clipBottom = layout.footerTop - 5
        graphics.enableScissor(layout.contentLeft, clipTop, layout.right, clipBottom)

        var y = clipTop - contentScroll
        y += renderBreadcrumbs(graphics, page, x, y, contentWidth)
        graphics.drawString(font, Component.translatable(page.titleKey), x, y, GOLD, false)
        y += 18
        page.entries.forEach { entry ->
            y += renderEntry(graphics, entry, x, y, contentWidth, mouseX, mouseY)
        }
        measuredContentHeight = y + contentScroll - clipTop
        graphics.disableScissor()
        clampContentScroll(layout)
    }

    private fun renderBreadcrumbs(graphics: GuiGraphics, page: GuidePage, x: Int, y: Int, width: Int): Int {
        val crumbs = GuideBookContent.breadcrumbs(page.id)
        var cursor = x
        crumbs.forEachIndexed { index, crumb ->
            val label = Component.translatable(crumb.labelKey)
            val text = if (cursor + font.width(label) > x + width) "..." else label.string
            graphics.drawString(font, text, cursor, y, MUTED, false)
            cursor += font.width(text)
            if (index < crumbs.lastIndex) {
                graphics.drawString(font, " > ", cursor, y, BORDER, false)
                cursor += font.width(" > ")
            }
        }
        return 14
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
        is GuideEntry.Heading -> {
            graphics.drawString(font, Component.translatable(entry.key), x, y + 2, GOLD, false)
            16
        }
        is GuideEntry.Note -> renderCallout(graphics, entry.key, x, y, width, NOTE_BG, GOLD, NOTE_TEXT)
        is GuideEntry.Warning -> renderCallout(graphics, entry.key, x, y, width, WARNING_BG, WARNING, WARNING_TEXT)
        is GuideEntry.InputHint -> renderCallout(graphics, entry.key, x, y, width, INPUT_BG, CYAN, TEXT)
        is GuideEntry.Code -> {
            val lines = entry.lines.size
            val height = lines * 11 + 10
            graphics.fill(x, y, x + width, y + height, CODE_BG)
            graphics.renderOutline(x, y, width, height, BORDER_DARK)
            entry.lines.forEachIndexed { index, line ->
                graphics.drawString(font, line, x + 8, y + 6 + index * 11, CYAN, false)
            }
            height + 7
        }
        is GuideEntry.Method -> renderMethod(graphics, entry, x, y, width)
        is GuideEntry.Event -> renderEvent(graphics, entry, x, y, width)
        GuideEntry.PixelEditor -> pixelEditorPanel.render(graphics, font, x, y, width, mouseX, mouseY) + 7
    }

    private fun renderMethod(graphics: GuiGraphics, entry: GuideEntry.Method, x: Int, y: Int, width: Int): Int {
        val doc = entry.documentation
        val height = if (doc.result.isBlank()) 25 else 36
        graphics.fill(x, y, x + width, y + height, CODE_BG)
        graphics.renderOutline(x, y, width, height, BORDER_DARK)
        graphics.drawString(font, doc.signature, x + 8, y + 6, CYAN, false)
        if (doc.result.isNotBlank()) graphics.drawString(font, "-> ${doc.result}", x + 8, y + 17, MUTED, false)
        if (doc.reactive) graphics.drawString(font, "REACTIVE", x + width - 53, y + 6, GOLD, false)
        return height + 5
    }

    private fun renderEvent(graphics: GuiGraphics, entry: GuideEntry.Event, x: Int, y: Int, width: Int): Int {
        val doc = entry.documentation
        val args = if (doc.arguments.isBlank()) doc.name else "${doc.name}(${doc.arguments})"
        val height = wrappedHeight(Component.literal(args), width - 16, 10) + 10
        graphics.fill(x, y, x + width, y + height, INPUT_BG)
        graphics.fill(x, y, x + 3, y + height, CYAN)
        drawWrapped(graphics, Component.literal(args), x + 9, y + 5, width - 16, CYAN, 10)
        return height + 5
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
        val previous = NavBox(layout.contentLeft + 12, layout.footerTop + 5, 34, 15)
        val next = NavBox(layout.contentLeft + 51, layout.footerTop + 5, 34, 15)
        val close = NavBox(layout.right - 55, layout.footerTop + 5, 43, 15)
        drawNav(graphics, previous, "<", mouseX, mouseY, historyCursor > 0)
        drawNav(graphics, next, ">", mouseX, mouseY, historyCursor < history.lastIndex)
        drawNav(graphics, close, Component.translatable("gui.done").string, mouseX, mouseY, true)
        val pageIndex = GuideBookContent.pages.indexOfFirst { it.id == selectedPageId }.coerceAtLeast(0)
        graphics.drawString(
            font,
            "${pageIndex + 1} / ${GuideBookContent.pages.size}",
            layout.contentLeft + 96,
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
        if (searchBox.mouseClicked(mouseX, mouseY, button)) return true
        val layout = layout()
        if (activePageHasPixelEditor() && layout.containsContent(mouseX, mouseY) &&
            pixelEditorPanel.mouseClicked(mouseX, mouseY, button)
        ) return true
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button)

        if (mouseX >= layout.left + 5 && mouseX < layout.contentLeft - 5 &&
            mouseY >= layout.headerBottom + 7 && mouseY < layout.footerTop - 5
        ) {
            val index = ((mouseY - (layout.headerBottom + 7) + sidebarScroll) / TAB_HEIGHT).toInt()
            sidebarRows().getOrNull(index)?.let { row ->
                row.category?.let { category ->
                    if (!expandedCategories.add(category.id)) expandedCategories.remove(category.id)
                    return true
                }
                row.page?.let { page -> selectPage(page.id, true); return true }
            }
        }

        val previous = NavBox(layout.contentLeft + 12, layout.footerTop + 5, 34, 15)
        val next = NavBox(layout.contentLeft + 51, layout.footerTop + 5, 34, 15)
        if (previous.contains(mouseX, mouseY) && historyCursor > 0) {
            historyCursor--
            selectPage(history[historyCursor], false)
            return true
        }
        if (next.contains(mouseX, mouseY) && historyCursor < history.lastIndex) {
            historyCursor++
            selectPage(history[historyCursor], false)
            return true
        }
        if (NavBox(layout.right - 55, layout.footerTop + 5, 43, 15).contains(mouseX, mouseY)) {
            onClose()
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseDragged(
        mouseX: Double,
        mouseY: Double,
        button: Int,
        dragX: Double,
        dragY: Double
    ): Boolean {
        if (activePageHasPixelEditor() && pixelEditorPanel.mouseDragged(mouseX, mouseY, button)) return true
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (pixelEditorPanel.mouseReleased()) return true
        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val layout = layout()
        if (mouseY < layout.headerBottom || mouseY >= layout.footerTop) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
        }
        if (mouseX < layout.contentLeft) {
            sidebarScroll = (sidebarScroll - (scrollY * 18.0).toInt()).coerceAtLeast(0)
            clampSidebarScroll(layout, sidebarRows().size)
        } else if (mouseX < layout.right) {
            contentScroll = (contentScroll - (scrollY * 18.0).toInt()).coerceAtLeast(0)
            clampContentScroll(layout)
        } else {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
        }
        return true
    }

    private fun currentPage(): GuidePage = GuideBookContent.page(selectedPageId) ?: GuideBookContent.firstPage()

    private fun activePageHasPixelEditor(): Boolean = currentPage().entries.any { it === GuideEntry.PixelEditor }

    private fun selectPage(id: String, addHistory: Boolean) {
        val page = GuideBookContent.page(id) ?: return
        selectedPageId = page.id
        contentScroll = 0
        if (addHistory && history.getOrNull(historyCursor) != page.id) {
            while (history.size > historyCursor + 1) history.removeAt(history.lastIndex)
            history += page.id
            historyCursor = history.lastIndex
        }
    }

    private fun sidebarRows(): List<SidebarRow> {
        val query = if (::searchBox.isInitialized) searchBox.value.trim() else ""
        if (query.isNotEmpty()) return GuideBookContent.search(query).map { SidebarRow(0, null, it) }
        val rows = mutableListOf<SidebarRow>()
        fun visit(nodes: List<GuideNode>, depth: Int) {
            nodes.forEach { node ->
                when (node) {
                    is GuideNode.Category -> {
                        rows += SidebarRow(depth, node, null)
                        if (node.id in expandedCategories) visit(node.children, depth + 1)
                    }
                    is GuideNode.Page -> rows += SidebarRow(depth, null, node.page)
                }
            }
        }
        visit(GuideBookContent.roots, 0)
        return rows
    }

    private fun clampContentScroll(layout: Layout) {
        val viewport = layout.footerTop - (layout.headerBottom + 7) - 5
        contentScroll = Mth.clamp(contentScroll, 0, (measuredContentHeight - viewport).coerceAtLeast(0))
    }

    private fun clampSidebarScroll(layout: Layout, rows: Int) {
        val viewport = layout.footerTop - (layout.headerBottom + 7) - 5
        val content = rows * TAB_HEIGHT
        sidebarScroll = Mth.clamp(sidebarScroll, 0, (content - viewport).coerceAtLeast(0))
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
        lines.forEachIndexed { index, line ->
            graphics.drawString(font, line, x, y + index * lineHeight, color, false)
        }
        return lines.size * lineHeight
    }

    private fun wrappedHeight(component: Component, width: Int, lineHeight: Int): Int =
        font.split(component, width).size * lineHeight

    private fun layout(): Layout {
        val panelWidth = (width - 24).coerceIn(360, 620)
        val panelHeight = (height - 24).coerceIn(230, 340)
        val left = (width - panelWidth) / 2
        val top = (height - panelHeight) / 2
        return Layout(left, top, left + panelWidth, top + panelHeight)
    }

    private data class SidebarRow(
        val depth: Int,
        val category: GuideNode.Category?,
        val page: GuidePage?
    )

    private data class Layout(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width: Int get() = right - left
        val height: Int get() = bottom - top
        val headerBottom: Int get() = top + 38
        val footerTop: Int get() = bottom - 26
        val sidebarWidth: Int get() = 154
        val contentLeft: Int get() = left + sidebarWidth

        fun containsContent(mouseX: Double, mouseY: Double): Boolean =
            mouseX >= contentLeft && mouseX < right && mouseY >= headerBottom + 7 && mouseY < footerTop - 5
    }

    private data class NavBox(val x: Int, val y: Int, val width: Int, val height: Int) {
        fun contains(mouseX: Double, mouseY: Double): Boolean =
            mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height
    }

    private companion object {
        const val TAB_HEIGHT: Int = 18
        const val PANEL: Int = 0xFF101820.toInt()
        const val SCREEN_OVERLAY: Int = 0xE60A0F14.toInt()
        const val HEADER: Int = 0xFF172733.toInt()
        const val SIDEBAR: Int = 0xFF0C141B.toInt()
        const val FOOTER: Int = 0xFF0C141B.toInt()
        const val CODE_BG: Int = 0xFF071117.toInt()
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
