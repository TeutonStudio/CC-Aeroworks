package de.teutonstudio.ccaeroworks.client

import de.teutonstudio.ccaeroworks.computer.source.InformationSourceKind
import de.teutonstudio.ccaeroworks.computer.source.InformationSourceSnapshotState
import de.teutonstudio.ccaeroworks.computer.source.InformationSourceView
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.network.chat.Component
import kotlin.math.max

/** Compact, read-only tree over the server-authoritative information-source snapshot. */
internal class InformationSourceManagerWidget(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    private val font: Font
) : AbstractWidget(x, y, width, height, Component.literal("Information sources")) {
    private var scrollIndex = 0
    private val collapsedKinds = linkedSetOf<InformationSourceKind>()

    override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        graphics.fill(x, y, x + width, y + height, 0xFF111111.toInt())
        graphics.drawString(font, "INFORMATION SOURCES", x + 6, y + 6, 0xFFF0F0F0.toInt(), false)

        val rows = rows()
        val visibleRows = visibleRowCount()
        scrollIndex = scrollIndex.coerceIn(0, max(0, rows.size - visibleRows))
        val listTop = y + HEADER_HEIGHT
        graphics.enableScissor(x, listTop, x + width, y + height)
        try {
            rows.drop(scrollIndex).take(visibleRows).forEachIndexed { index, row ->
                renderRow(graphics, row, listTop + index * ROW_HEIGHT, mouseX, mouseY)
            }
        } finally {
            graphics.disableScissor()
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (!visible || !active || button != 0 || !inside(mouseX, mouseY)) return false
        val rowIndex = ((mouseY - (y + HEADER_HEIGHT)) / ROW_HEIGHT).toInt()
        if (rowIndex < 0) return true
        val row = rows().getOrNull(scrollIndex + rowIndex)
        if (row is SourceRow.Section) {
            if (!collapsedKinds.add(row.kind)) collapsedKinds.remove(row.kind)
        }
        return true
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (!visible || !active || !inside(mouseX, mouseY)) return false
        val maxScroll = max(0, rows().size - visibleRowCount())
        scrollIndex = (scrollIndex - scrollY.toInt()).coerceIn(0, maxScroll)
        return true
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {
        defaultButtonNarrationText(output)
    }

    private fun rows(): List<SourceRow> = buildList {
        val sources = InformationSourceSnapshotState.get().sources
        InformationSourceKind.entries.forEach { kind ->
            val matching = sources.filter { it.kind == kind }
            add(SourceRow.Section(kind, matching.size))
            if (kind in collapsedKinds) return@forEach
            if (matching.isEmpty()) {
                add(SourceRow.Empty)
            } else {
                matching.forEach { source ->
                    add(SourceRow.Source(source))
                    add(SourceRow.Detail(source))
                }
            }
        }
    }

    private fun renderRow(
        graphics: GuiGraphics,
        row: SourceRow,
        rowY: Int,
        mouseX: Int,
        mouseY: Int
    ) {
        val hovered = mouseX >= x && mouseX < x + width && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT
        when (row) {
            is SourceRow.Section -> {
                graphics.fill(
                    x + 2,
                    rowY + 1,
                    x + width - 2,
                    rowY + ROW_HEIGHT - 1,
                    if (hovered) 0xFF242424.toInt() else 0xFF181818.toInt()
                )
                val prefix = if (row.kind in collapsedKinds) ">" else "v"
                graphics.drawString(
                    font,
                    "$prefix ${row.kind.title} (${row.count})",
                    x + 6,
                    rowY + 4,
                    0xFFAAAAAA.toInt(),
                    false
                )
            }
            is SourceRow.Source -> {
                if (hovered) graphics.fill(x + 2, rowY + 1, x + width - 2, rowY + ROW_HEIGHT - 1, 0xFF1D1D1D.toInt())
                val statusWidth = font.width(row.source.status)
                val labelWidth = (width - statusWidth - 24).coerceAtLeast(24)
                graphics.drawString(
                    font,
                    font.plainSubstrByWidth("  ${row.source.label}", labelWidth),
                    x + 6,
                    rowY + 4,
                    0xFFE0E0E0.toInt(),
                    false
                )
                graphics.drawString(
                    font,
                    row.source.status,
                    x + width - statusWidth - 6,
                    rowY + 4,
                    0xFF999999.toInt(),
                    false
                )
            }
            is SourceRow.Detail -> {
                val side = row.source.side.takeIf(String::isNotBlank)?.let { " $it" }.orEmpty()
                val detail = row.source.details.takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty()
                val text = "    ${row.source.x}, ${row.source.y}, ${row.source.z}$side$detail"
                graphics.drawString(
                    font,
                    font.plainSubstrByWidth(text, width - 12),
                    x + 6,
                    rowY + 4,
                    0xFF737373.toInt(),
                    false
                )
            }
            SourceRow.Empty -> graphics.drawString(font, "    none", x + 6, rowY + 4, 0xFF555555.toInt(), false)
        }
    }

    private fun visibleRowCount(): Int = max(1, (height - HEADER_HEIGHT) / ROW_HEIGHT)

    private fun inside(mouseX: Double, mouseY: Double): Boolean =
        mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height

    private sealed interface SourceRow {
        data class Section(val kind: InformationSourceKind, val count: Int) : SourceRow
        data class Source(val source: InformationSourceView) : SourceRow
        data class Detail(val source: InformationSourceView) : SourceRow
        data object Empty : SourceRow
    }

    private companion object {
        const val HEADER_HEIGHT = 22
        const val ROW_HEIGHT = 18
    }
}
