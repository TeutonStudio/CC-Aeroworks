package de.teutonstudio.ccaeroworks.client

import de.teutonstudio.ccaeroworks.computer.wire.ControlChannelView
import de.teutonstudio.ccaeroworks.computer.wire.ControlModuleGroupView
import de.teutonstudio.ccaeroworks.computer.wire.WireChannelSnapshotState
import de.teutonstudio.ccaeroworks.computer.wire.WireChannelView
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.network.chat.Component
import java.util.UUID
import kotlin.math.max

/**
 * Scrollable CC-styled view over both immutable Aeroworks control channels and the mutable
 * server-authoritative WireChannelBank. Control rows are deliberately not selectable for deletion.
 */
internal class WireChannelManagerWidget(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    private val font: Font
) : AbstractWidget(x, y, width, height, Component.literal("Channels")) {
    private var scrollIndex = 0
    var selectedId: UUID? = null
        private set

    fun selectedChannel(): WireChannelView? =
        WireChannelSnapshotState.get().wire.channels.firstOrNull { it.id == selectedId }

    override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val snapshot = WireChannelSnapshotState.get()
        graphics.fill(x, y, x + width, y + height, 0xFF111111.toInt())
        graphics.drawString(font, "CHANNELS", x + 6, y + 5, 0xFFF0F0F0.toInt(), false)
        val status = "${snapshot.wire.backend} · ${if (snapshot.wire.enabled) "enabled" else "disabled"}"
        graphics.drawString(font, font.plainSubstrByWidth(status, width - 12), x + 6, y + 15, 0xFF888888.toInt(), false)

        val rows = rows()
        val visibleRows = visibleRowCount()
        val maxScroll = max(0, rows.size - visibleRows)
        scrollIndex = scrollIndex.coerceIn(0, maxScroll)
        if (selectedId != null && snapshot.wire.channels.none { it.id == selectedId }) selectedId = null

        val listTop = y + HEADER_HEIGHT
        graphics.enableScissor(x, listTop, x + width, y + height)
        try {
            rows.drop(scrollIndex).take(visibleRows).forEachIndexed { index, row ->
                val rowY = listTop + index * ROW_HEIGHT
                renderRow(graphics, row, rowY, mouseX, mouseY)
            }
            if (rows.isEmpty()) {
                graphics.drawString(font, "No channels available", x + 6, listTop + 7, 0xFF777777.toInt(), false)
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
        selectedId = (row as? ChannelRow.Wire)?.channel?.id
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

    private fun rows(): List<ChannelRow> = buildList {
        val snapshot = WireChannelSnapshotState.get()
        if (snapshot.controlGroups.isNotEmpty()) {
            add(ChannelRow.Section("CONTROL MODULES"))
            snapshot.controlGroups.forEach { group ->
                add(ChannelRow.Module(group))
                group.channels.forEach { channel -> add(ChannelRow.Control(channel)) }
            }
        }
        add(ChannelRow.Section("WIRE CHANNELS"))
        snapshot.wire.channels.forEach { channel -> add(ChannelRow.Wire(channel)) }
    }

    private fun renderRow(
        graphics: GuiGraphics,
        row: ChannelRow,
        rowY: Int,
        mouseX: Int,
        mouseY: Int
    ) {
        val hovered = mouseX >= x && mouseX < x + width && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT
        when (row) {
            is ChannelRow.Section -> {
                graphics.fill(x + 2, rowY + 1, x + width - 2, rowY + ROW_HEIGHT - 1, 0xFF181818.toInt())
                graphics.drawString(font, row.label, x + 6, rowY + 4, 0xFF8E8E8E.toInt(), false)
            }
            is ChannelRow.Module -> renderModuleRow(graphics, row.group, rowY)
            is ChannelRow.Control -> renderControlRow(graphics, row.channel, rowY, hovered)
            is ChannelRow.Wire -> renderWireRow(graphics, row.channel, rowY, hovered)
        }
    }

    private fun renderModuleRow(graphics: GuiGraphics, group: ControlModuleGroupView, rowY: Int) {
        graphics.fill(x + 2, rowY + 1, x + width - 2, rowY + ROW_HEIGHT - 1, 0xFF242424.toInt())
        val label = "${group.label} · Desk ${group.deskIndex} · ${group.socketName}"
        graphics.drawString(font, font.plainSubstrByWidth(label, width - 12), x + 6, rowY + 4, 0xFFD0D0D0.toInt(), false)
    }

    private fun renderControlRow(graphics: GuiGraphics, channel: ControlChannelView, rowY: Int, hovered: Boolean) {
        if (hovered) graphics.fill(x + 2, rowY + 1, x + width - 2, rowY + ROW_HEIGHT - 1, 0xFF1C1C1C.toInt())
        val name = "  ${channel.name}"
        graphics.drawString(font, font.plainSubstrByWidth(name, width - 80), x + 6, rowY + 4, 0xFFE0E0E0.toInt(), false)
        graphics.drawString(font, channel.value.toString(), x + width - 48, rowY + 4, 0xFFB0B0B0.toInt(), false)
        if (channel.overridden) {
            graphics.drawString(font, "OVR", x + width - 28, rowY + 4, 0xFFFFFF55.toInt(), false)
        }
    }

    private fun renderWireRow(graphics: GuiGraphics, channel: WireChannelView, rowY: Int, hovered: Boolean) {
        val selected = channel.id == selectedId
        if (selected) graphics.fill(x + 2, rowY + 1, x + width - 2, rowY + ROW_HEIGHT - 1, 0xFF303030.toInt())
        else if (hovered) graphics.fill(x + 2, rowY + 1, x + width - 2, rowY + ROW_HEIGHT - 1, 0xFF202020.toInt())

        graphics.drawString(font, font.plainSubstrByWidth(channel.name, width - 86), x + 6, rowY + 4, 0xFFF0F0F0.toInt(), false)
        graphics.drawString(font, "${channel.value}/15", x + width - 76, rowY + 4, if (channel.value > 0) 0xFFFFFF55.toInt() else 0xFF888888.toInt(), false)
        val links = if (channel.connections == 1) "1 link" else "${channel.connections} links"
        graphics.drawString(font, links, x + width - 43, rowY + 4, if (channel.connected) 0xFF55FF55.toInt() else 0xFF777777.toInt(), false)
    }

    private fun visibleRowCount(): Int = max(1, (height - HEADER_HEIGHT) / ROW_HEIGHT)

    private fun inside(mouseX: Double, mouseY: Double): Boolean =
        mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height

    private sealed interface ChannelRow {
        data class Section(val label: String) : ChannelRow
        data class Module(val group: ControlModuleGroupView) : ChannelRow
        data class Control(val channel: ControlChannelView) : ChannelRow
        data class Wire(val channel: WireChannelView) : ChannelRow
    }

    private companion object {
        const val HEADER_HEIGHT = 27
        const val ROW_HEIGHT = 18
    }
}
