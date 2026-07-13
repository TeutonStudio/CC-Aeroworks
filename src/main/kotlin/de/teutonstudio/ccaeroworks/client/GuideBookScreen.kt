package de.teutonstudio.ccaeroworks.client

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth

class GuideBookScreen : Screen(Component.translatable("guide.cc_aeroworks.title")) {
    private var sectionIndex: Int = 0
    private var scroll: Int = 0
    private var measuredContentHeight: Int = 0

    override fun isPauseScreen(): Boolean = false

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Do not call Screen.renderBackground here: in-world screens enable Minecraft's blur
        // shader there, which makes small API text unnecessarily hard to read.
        graphics.fill(0, 0, width, height, SCREEN_OVERLAY)
        val layout = layout()

        graphics.fill(layout.left + 4, layout.top + 5, layout.right + 5, layout.bottom + 6, 0x66000000)
        graphics.fill(layout.left, layout.top, layout.right, layout.bottom, PANEL)
        graphics.renderOutline(layout.left, layout.top, layout.width, layout.height, BORDER)
        graphics.fill(layout.left, layout.top, layout.right, layout.headerBottom, HEADER)
        graphics.fill(layout.left, layout.headerBottom - 2, layout.right, layout.headerBottom, CYAN)

        graphics.drawString(font, "CC>", layout.left + 12, layout.top + 11, CYAN, false)
        graphics.drawString(font, title, layout.left + 35, layout.top + 11, TEXT, false)
        if (layout.width >= 380) graphics.drawString(font, "API DOCS", layout.right - 62, layout.top + 11, MUTED, false)

        renderSidebar(graphics, layout, mouseX, mouseY)
        renderSection(graphics, layout)
        renderFooter(graphics, layout, mouseX, mouseY)
        super.render(graphics, mouseX, mouseY, partialTick)
    }

    private fun renderSidebar(graphics: GuiGraphics, layout: Layout, mouseX: Int, mouseY: Int) {
        graphics.fill(layout.left, layout.headerBottom, layout.contentLeft - 1, layout.footerTop, SIDEBAR)
        graphics.fill(layout.contentLeft - 1, layout.headerBottom, layout.contentLeft, layout.footerTop, BORDER_DARK)
        SECTIONS.forEachIndexed { index, section ->
            val y = layout.headerBottom + 9 + index * TAB_HEIGHT
            val hovered = mouseX in (layout.left + 5) until (layout.contentLeft - 5) && mouseY in y until (y + TAB_HEIGHT - 2)
            if (index == sectionIndex || hovered) {
                graphics.fill(
                    layout.left + 5, y, layout.contentLeft - 5, y + TAB_HEIGHT - 2,
                    if (index == sectionIndex) SELECTED else HOVER
                )
            }
            if (index == sectionIndex) graphics.fill(layout.left + 5, y, layout.left + 8, y + TAB_HEIGHT - 2, CYAN)
            graphics.drawString(
                font,
                Component.translatable(section.labelKey),
                layout.left + 13,
                y + 5,
                if (index == sectionIndex) TEXT else MUTED,
                false
            )
        }
    }

    private fun renderSection(graphics: GuiGraphics, layout: Layout) {
        val x = layout.contentLeft + 14
        val width = layout.right - x - 14
        val clipTop = layout.headerBottom + 7
        val clipBottom = layout.footerTop - 5
        graphics.enableScissor(layout.contentLeft, clipTop, layout.right, clipBottom)

        var y = clipTop - scroll
        val section = SECTIONS[sectionIndex]
        graphics.drawString(font, Component.translatable(section.titleKey), x, y, GOLD, false)
        y += 18
        section.entries.forEach { entry ->
            y += renderEntry(graphics, entry, x, y, width)
        }
        measuredContentHeight = y + scroll - clipTop
        graphics.disableScissor()
        clampScroll(layout)
    }

    private fun renderEntry(graphics: GuiGraphics, entry: Entry, x: Int, y: Int, width: Int): Int = when (entry) {
        is Entry.Text -> drawWrapped(graphics, Component.translatable(entry.key), x, y, width, TEXT, 10) + 6
        is Entry.Note -> {
            val height = wrappedHeight(Component.translatable(entry.key), width - 15, 10)
            graphics.fill(x, y, x + width, y + height + 10, NOTE_BG)
            graphics.fill(x, y, x + 3, y + height + 10, GOLD)
            drawWrapped(graphics, Component.translatable(entry.key), x + 9, y + 5, width - 15, NOTE_TEXT, 10)
            height + 16
        }
        is Entry.Code -> {
            val lines = entry.lines.size
            val height = lines * 11 + 10
            graphics.fill(x, y, x + width, y + height, CODE_BG)
            graphics.renderOutline(x, y, width, height, BORDER_DARK)
            entry.lines.forEachIndexed { index, line ->
                graphics.drawString(font, line, x + 8, y + 6 + index * 11, CYAN, false)
            }
            height + 7
        }
    }

    private fun renderFooter(graphics: GuiGraphics, layout: Layout, mouseX: Int, mouseY: Int) {
        graphics.fill(layout.left, layout.footerTop, layout.right, layout.bottom, FOOTER)
        graphics.fill(layout.left, layout.footerTop, layout.right, layout.footerTop + 1, BORDER_DARK)
        val previous = NavBox(layout.contentLeft + 12, layout.footerTop + 5, 28, 15)
        val next = NavBox(layout.contentLeft + 45, layout.footerTop + 5, 28, 15)
        val close = NavBox(layout.right - 55, layout.footerTop + 5, 43, 15)
        drawNav(graphics, previous, "<", mouseX, mouseY, sectionIndex > 0)
        drawNav(graphics, next, ">", mouseX, mouseY, sectionIndex < SECTIONS.lastIndex)
        drawNav(graphics, close, Component.translatable("gui.done").string, mouseX, mouseY, true)
        graphics.drawString(
            font,
            "${sectionIndex + 1} / ${SECTIONS.size}",
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
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button)
        val layout = layout()
        SECTIONS.indices.firstOrNull { index ->
            val y = layout.headerBottom + 9 + index * TAB_HEIGHT
            mouseX >= layout.left + 5 && mouseX < layout.contentLeft - 5 && mouseY >= y && mouseY < y + TAB_HEIGHT - 2
        }?.let {
            selectSection(it)
            return true
        }
        if (NavBox(layout.contentLeft + 12, layout.footerTop + 5, 28, 15).contains(mouseX, mouseY) && sectionIndex > 0) {
            selectSection(sectionIndex - 1)
            return true
        }
        if (NavBox(layout.contentLeft + 45, layout.footerTop + 5, 28, 15).contains(mouseX, mouseY) && sectionIndex < SECTIONS.lastIndex) {
            selectSection(sectionIndex + 1)
            return true
        }
        if (NavBox(layout.right - 55, layout.footerTop + 5, 43, 15).contains(mouseX, mouseY)) {
            onClose()
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val layout = layout()
        if (mouseX < layout.contentLeft || mouseX >= layout.right || mouseY < layout.headerBottom || mouseY >= layout.footerTop) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
        }
        scroll = (scroll - (scrollY * 18.0).toInt()).coerceAtLeast(0)
        clampScroll(layout)
        return true
    }

    private fun selectSection(index: Int) {
        sectionIndex = index.coerceIn(SECTIONS.indices)
        scroll = 0
    }

    private fun clampScroll(layout: Layout) {
        val viewport = layout.footerTop - (layout.headerBottom + 7) - 5
        scroll = Mth.clamp(scroll, 0, (measuredContentHeight - viewport).coerceAtLeast(0))
    }

    private fun drawWrapped(graphics: GuiGraphics, component: Component, x: Int, y: Int, width: Int, color: Int, lineHeight: Int): Int {
        val lines = font.split(component, width)
        lines.forEachIndexed { index, line -> graphics.drawString(font, line, x, y + index * lineHeight, color, false) }
        return lines.size * lineHeight
    }

    private fun wrappedHeight(component: Component, width: Int, lineHeight: Int): Int = font.split(component, width).size * lineHeight

    private fun layout(): Layout {
        val panelWidth = (width - 24).coerceIn(280, 440)
        val panelHeight = (height - 24).coerceIn(210, 270)
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
    }

    private data class NavBox(val x: Int, val y: Int, val width: Int, val height: Int) {
        fun contains(mouseX: Double, mouseY: Double): Boolean =
            mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height
    }

    private sealed interface Entry {
        data class Text(val key: String) : Entry
        data class Note(val key: String) : Entry
        data class Code(val lines: List<String>) : Entry
    }

    private data class Section(val labelKey: String, val titleKey: String, val entries: List<Entry>)

    private companion object {
        const val TAB_HEIGHT: Int = 19
        const val PANEL: Int = 0xFF101820.toInt()
        const val SCREEN_OVERLAY: Int = 0xE60A0F14.toInt()
        const val HEADER: Int = 0xFF172733.toInt()
        const val SIDEBAR: Int = 0xFF0C141B.toInt()
        const val FOOTER: Int = 0xFF0C141B.toInt()
        const val CODE_BG: Int = 0xFF071117.toInt()
        const val NOTE_BG: Int = 0xFF282316.toInt()
        const val SELECTED: Int = 0xFF173746.toInt()
        const val HOVER: Int = 0xFF234958.toInt()
        const val BUTTON: Int = 0xFF172733.toInt()
        const val BORDER: Int = 0xFF4BA7BE.toInt()
        const val BORDER_DARK: Int = 0xFF294652.toInt()
        const val CYAN: Int = 0xFF68D4EB.toInt()
        const val GOLD: Int = 0xFFFFC66D.toInt()
        const val TEXT: Int = 0xFFE7F3F6.toInt()
        const val NOTE_TEXT: Int = 0xFFFFE2A6.toInt()
        const val MUTED: Int = 0xFF91A8AF.toInt()
        const val DISABLED: Int = 0xFF526269.toInt()

        val SECTIONS: List<Section> = listOf(
            Section("guide.cc_aeroworks.tab.start", "guide.cc_aeroworks.start.title", listOf(
                Entry.Text("guide.cc_aeroworks.start.text"),
                Entry.Code(listOf("local desk = peripheral.find(", "  \"cc_aeroworks_control_desk\")", "assert(desk, \"No Control Desk found\")")),
                Entry.Note("guide.cc_aeroworks.start.note")
            )),
            Section("guide.cc_aeroworks.tab.modules", "guide.cc_aeroworks.modules.title", listOf(
                Entry.Text("guide.cc_aeroworks.modules.text"),
                Entry.Code(listOf("getSocketCount()", "getModules()", "getModule(socket)")),
                Entry.Note("guide.cc_aeroworks.modules.note")
            )),
            Section("guide.cc_aeroworks.tab.inputs", "guide.cc_aeroworks.inputs.title", listOf(
                Entry.Text("guide.cc_aeroworks.inputs.text"),
                Entry.Code(listOf("getInputs()", "getInput(socket)", "os.pullEvent(\"cc_aeroworks_desk_input\")")),
                Entry.Note("guide.cc_aeroworks.inputs.note")
            )),
            Section("guide.cc_aeroworks.tab.displays", "guide.cc_aeroworks.displays.title", listOf(
                Entry.Text("guide.cc_aeroworks.displays.text"),
                Entry.Code(listOf("getDisplays() / getDisplay(socket)", "setDisplayText(socket, text)", "setDisplayNumber(socket, value, zeroPad)", "clearDisplay(socket) / clearDisplays()")),
                Entry.Note("guide.cc_aeroworks.displays.note")
            )),
            Section("guide.cc_aeroworks.tab.pixels", "guide.cc_aeroworks.pixels.title", listOf(
                Entry.Text("guide.cc_aeroworks.pixels.text"),
                Entry.Code(listOf("getDisplaySize(socket)", "getDisplayPixel(socket, x, y)", "setDisplayPixel(socket, x, y, enabled)", "setDisplayPixels(socket, rows)", "clearDisplayPixels(socket)")),
                Entry.Note("guide.cc_aeroworks.pixels.note")
            )),
            Section("guide.cc_aeroworks.tab.controls", "guide.cc_aeroworks.controls.title", listOf(
                Entry.Text("guide.cc_aeroworks.controls.text"),
                Entry.Code(listOf("Lever / Throttle  -> Mouse Y", "Joystick X        -> Mouse X", "Joystick Y        -> Mouse Y")),
                Entry.Note("guide.cc_aeroworks.controls.note")
            )),
            Section("guide.cc_aeroworks.tab.example", "guide.cc_aeroworks.example.title", listOf(
                Entry.Text("guide.cc_aeroworks.example.text"),
                Entry.Code(listOf("local desk = peripheral.find(", "  \"cc_aeroworks_control_desk\")", "while true do", "  local _,_,socket,_,value = os.pullEvent(", "    \"cc_aeroworks_desk_input\")", "  desk.setDisplayNumber(2, value, false)", "end"))
            ))
        )
    }
}
