package de.teutonstudio.ccaeroworks.client

import de.teutonstudio.ccaeroworks.computer.wire.ControlChannelView
import de.teutonstudio.ccaeroworks.computer.wire.ControlModuleGroupView
import de.teutonstudio.ccaeroworks.computer.wire.WireChannelSnapshotState
import de.teutonstudio.ccaeroworks.computer.wire.WireChannelView
import de.teutonstudio.ccaeroworks.computer.wire.WireConnectionView
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.network.chat.Component
import java.util.UUID
import kotlin.math.max

/**
 * Scrollable CC-styled channel tree. Module/section rows are collapsible; control channels remain
 * immutable configuration targets while wire-channel rows retain Add/Rename/Delete selection.
 */
internal class WireChannelManagerWidget(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    private val font: Font
) : AbstractWidget(x, y, width, height, Component.literal("Channels")) {
    private var scrollIndex = 0
    private val collapsedGroupIds = linkedSetOf<String>()
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
        when (val row = rows().getOrNull(scrollIndex + rowIndex)) {
            is ChannelRow.Section -> toggle(row.id)
            is ChannelRow.Module -> toggle(row.group.id)
            is ChannelRow.Wire -> selectedId = row.channel.id
            else -> Unit
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

    private fun toggle(id: String) {
        if (!collapsedGroupIds.add(id)) collapsedGroupIds.remove(id)
        scrollIndex = scrollIndex.coerceAtLeast(0)
    }

    private fun rows(): List<ChannelRow> = buildList {
        val snapshot = WireChannelSnapshotState.get()
        if (snapshot.controlGroups.isNotEmpty()) {
            add(ChannelRow.Section(CONTROL_SECTION_ID, "CONTROL MODULES"))
            if (CONTROL_SECTION_ID !in collapsedGroupIds) {
                snapshot.controlGroups.forEach { group ->
                    add(ChannelRow.Module(group))
                    if (group.id !in collapsedGroupIds) {
                        group.channels.forEach { channel ->
                            add(ChannelRow.Control(channel))
                            channel.connections.forEach { connection ->
                                add(ChannelRow.Connection(connection))
                            }
                        }
                    }
                }
            }
        }
        add(ChannelRow.Section(WIRE_SECTION_ID, "WIRE CHANNELS"))
        if (WIRE_SECTION_ID !in collapsedGroupIds) {
            snapshot.wire.channels.forEach { channel ->
                add(ChannelRow.Wire(channel))
                channel.targets.forEach { connection -> add(ChannelRow.Connection(connection)) }
            }
        }
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
            is ChannelRow.Section -> renderSectionRow(graphics, row, rowY, hovered)
            is ChannelRow.Module -> renderModuleRow(graphics, row.group, rowY, hovered)
            is ChannelRow.Control -> renderControlRow(graphics, row.channel, rowY, hovered)
            is ChannelRow.Wire -> renderWireRow(graphics, row.channel, rowY, hovered)
            is ChannelRow.Connection -> renderConnectionRow(graphics, row.connection, rowY)
        }
    }

    private fun renderSectionRow(graphics: GuiGraphics, row: ChannelRow.Section, rowY: Int, hovered: Boolean) {
        graphics.fill(x + 2, rowY + 1, x + width - 2, rowY + ROW_HEIGHT - 1, if (hovered) 0xFF222222.toInt() else 0xFF181818.toInt())
        val prefix = if (row.id in collapsedGroupIds) ">" else "v"
        graphics.drawString(font, "$prefix ${row.label}", x + 6, rowY + 4, 0xFF8E8E8E.toInt(), false)
    }

    private fun renderModuleRow(graphics: GuiGraphics, group: ControlModuleGroupView, rowY: Int, hovered: Boolean) {
        graphics.fill(x + 2, rowY + 1, x + width - 2, rowY + ROW_HEIGHT - 1, if (hovered) 0xFF2C2C2C.toInt() else 0xFF242424.toInt())
        val prefix = if (group.id in collapsedGroupIds) ">" else "v"
        val label = "$prefix ${group.label} · Desk ${group.deskIndex} · ${group.socketName}"
        graphics.drawString(font, font.plainSubstrByWidth(label, width - 12), x + 6, rowY + 4, 0xFFD0D0D0.toInt(), false)
    }

    private fun renderControlRow(graphics: GuiGraphics, channel: ControlChannelView, rowY: Int, hovered: Boolean) {
        if (hovered) graphics.fill(x + 2, rowY + 1, x + width - 2, rowY + ROW_HEIGHT - 1, 0xFF1C1C1C.toInt())
        val name = "  ${channel.name}"
        graphics.drawString(font, font.plainSubstrByWidth(name, width - 82), x + 6, rowY + 4, 0xFFE0E0E0.toInt(), false)
        graphics.drawString(font, "${channel.value}/15", x + width - 58, rowY + 4, if (channel.value > 0) 0xFFFFFF55.toInt() else 0xFF888888.toInt(), false)
        if (channel.overridden) graphics.drawString(font, "OVR", x + width - 25, rowY + 4, 0xFFFFFF55.toInt(), false)
    }

    private fun renderWireRow(graphics: GuiGraphics, channel: WireChannelView, rowY: Int, hovered: Boolean) {
        val selected = channel.id == selectedId
        if (selected) graphics.fill(x + 2, rowY + 1, x + width - 2, rowY + ROW_HEIGHT - 1, 0xFF303030.toInt())
        else if (hovered) graphics.fill(x + 2, rowY + 1, x + width - 2, rowY + ROW_HEIGHT - 1, 0xFF202020.toInt())

        graphics.drawString(font, font.plainSubstrByWidth(channel.name, width - 96), x + 6, rowY + 4, 0xFFF0F0F0.toInt(), false)
        graphics.drawString(font, "${channel.value}/15", x + width - 86, rowY + 4, if (channel.value > 0) 0xFFFFFF55.toInt() else 0xFF888888.toInt(), false)
        val links = channel.connections.toString()
        graphics.drawString(font, links, x + width - 22, rowY + 4, if (channel.connected) 0xFF55FF55.toInt() else 0xFF777777.toInt(), false)
    }

    private fun renderConnectionRow(graphics: GuiGraphics, connection: WireConnectionView, rowY: Int) {
        val label = "    -> ${connection.x}, ${connection.y}, ${connection.z}  ${connection.side}"
        graphics.drawString(font, font.plainSubstrByWidth(label, width - 12), x + 6, rowY + 4, 0xFF777777.toInt(), false)
    }

    private fun visibleRowCount(): Int = max(1, (height - HEADER_HEIGHT) / ROW_HEIGHT)

    private fun inside(mouseX: Double, mouseY: Double): Boolean =
        mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height

    private sealed interface ChannelRow {
        data class Section(val id: String, val label: String) : ChannelRow
        data class Module(val group: ControlModuleGroupView) : ChannelRow
        data class Control(val channel: ControlChannelView) : ChannelRow
        data class Wire(val channel: WireChannelView) : ChannelRow
        data class Connection(val connection: WireConnectionView) : ChannelRow
    }

    private companion object {
        const val HEADER_HEIGHT = 27
        const val ROW_HEIGHT = 18
        const val CONTROL_SECTION_ID = "section:controls"
        const val WIRE_SECTION_ID = "section:wires"
    }
}
