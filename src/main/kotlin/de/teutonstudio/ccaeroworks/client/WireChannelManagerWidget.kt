package de.teutonstudio.ccaeroworks.client

import de.teutonstudio.ccaeroworks.computer.channel.UserChannelBindingView
import de.teutonstudio.ccaeroworks.computer.channel.UserChannelGroupView
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

internal class WireChannelManagerWidget(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    private val font: Font
) : AbstractWidget(x, y, width, height, Component.literal("Channels")) {
    private enum class SelectionKind { NONE, TARGET, WIRE, GROUP, BINDING }

    private var scrollIndex = 0
    private val collapsedGroupIds = linkedSetOf<String>()
    private var selectedWireId: UUID? = null
    private var selectedTargetId: String? = null
    private var selectedGroup: UUID? = null
    private var selectedBinding: String? = null
    private var selectionKind: SelectionKind = SelectionKind.NONE

    fun selectedChannel(): WireChannelView? = if (selectionKind == SelectionKind.WIRE) {
        WireChannelSnapshotState.get().wire.channels.firstOrNull { it.id == selectedWireId }
    } else null

    fun selectedTargetId(): String? = selectedTargetId
    fun selectedGroupId(): UUID? = selectedGroup
    fun selectedGroupForMutation(): UUID? = selectedGroup.takeIf { selectionKind == SelectionKind.GROUP }
    fun selectedBindingAlias(): String? = selectedBinding.takeIf { selectionKind == SelectionKind.BINDING }

    override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val snapshot = WireChannelSnapshotState.get()
        graphics.fill(x, y, x + width, y + height, 0xFF111111.toInt())
        graphics.drawString(font, "CHANNELS", x + 6, y + 5, 0xFFF0F0F0.toInt(), false)
        val status = "${snapshot.wire.backend} · ${if (snapshot.wire.enabled) "enabled" else "disabled"}"
        graphics.drawString(font, font.plainSubstrByWidth(status, width - 12), x + 6, y + 15, 0xFF888888.toInt(), false)
        val rows = rows(); val visibleRows = visibleRowCount(); val maxScroll = max(0, rows.size - visibleRows)
        scrollIndex = scrollIndex.coerceIn(0, maxScroll)
        if (selectedWireId != null && snapshot.wire.channels.none { it.id == selectedWireId }) {
            selectedWireId = null
            if (selectionKind == SelectionKind.WIRE) selectionKind = SelectionKind.NONE
        }
        if (selectedGroup != null && snapshot.userGroups.none { it.id == selectedGroup }) {
            selectedGroup = null; selectedBinding = null
            if (selectionKind == SelectionKind.GROUP || selectionKind == SelectionKind.BINDING) selectionKind = SelectionKind.NONE
        }
        val listTop = y + HEADER_HEIGHT
        graphics.enableScissor(x, listTop, x + width, y + height)
        try {
            rows.drop(scrollIndex).take(visibleRows).forEachIndexed { index, row -> renderRow(graphics, row, listTop + index * ROW_HEIGHT, mouseX, mouseY) }
            if (rows.isEmpty()) graphics.drawString(font, "No channels available", x + 6, listTop + 7, 0xFF777777.toInt(), false)
        } finally { graphics.disableScissor() }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (!visible || !active || button != 0 || !inside(mouseX, mouseY)) return false
        val rowIndex = ((mouseY - (y + HEADER_HEIGHT)) / ROW_HEIGHT).toInt(); if (rowIndex < 0) return true
        when (val row = rows().getOrNull(scrollIndex + rowIndex)) {
            is ChannelRow.Section -> toggle(row.id)
            is ChannelRow.Module -> toggle(row.group.id)
            is ChannelRow.Control -> {
                selectedTargetId = row.channel.id; selectedWireId = null; selectedBinding = null; selectionKind = SelectionKind.TARGET
            }
            is ChannelRow.Wire -> {
                selectedWireId = row.channel.id; selectedTargetId = "wire:${row.channel.id}"; selectedBinding = null; selectionKind = SelectionKind.WIRE
            }
            is ChannelRow.UserGroup -> {
                selectedGroup = row.group.id; selectedBinding = null; selectedWireId = null; selectionKind = SelectionKind.GROUP; toggle("user:${row.group.id}")
            }
            is ChannelRow.Binding -> {
                selectedGroup = row.groupId; selectedBinding = row.binding.alias; selectedTargetId = row.binding.targetId; selectedWireId = null; selectionKind = SelectionKind.BINDING
            }
            else -> Unit
        }
        return true
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (!visible || !active || !inside(mouseX, mouseY)) return false
        scrollIndex = (scrollIndex - scrollY.toInt()).coerceIn(0, max(0, rows().size - visibleRowCount())); return true
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) { defaultButtonNarrationText(output) }
    private fun toggle(id: String) { if (!collapsedGroupIds.add(id)) collapsedGroupIds.remove(id) }

    private fun rows(): List<ChannelRow> = buildList {
        val snapshot = WireChannelSnapshotState.get()
        if (snapshot.controlGroups.isNotEmpty()) {
            add(ChannelRow.Section(CONTROL_SECTION_ID, "CONTROL MODULES"))
            if (CONTROL_SECTION_ID !in collapsedGroupIds) snapshot.controlGroups.forEach { group ->
                add(ChannelRow.Module(group)); if (group.id !in collapsedGroupIds) group.channels.forEach { channel ->
                    add(ChannelRow.Control(channel)); channel.connections.forEach { add(ChannelRow.Connection(it)) }
                }
            }
        }
        add(ChannelRow.Section(WIRE_SECTION_ID, "WIRE CHANNELS"))
        if (WIRE_SECTION_ID !in collapsedGroupIds) snapshot.wire.channels.forEach { channel ->
            add(ChannelRow.Wire(channel)); channel.targets.forEach { add(ChannelRow.Connection(it)) }
        }
        add(ChannelRow.Section(USER_SECTION_ID, "USER GROUPS"))
        if (USER_SECTION_ID !in collapsedGroupIds) snapshot.userGroups.forEach { group ->
            add(ChannelRow.UserGroup(group)); if ("user:${group.id}" !in collapsedGroupIds) group.bindings.forEach { add(ChannelRow.Binding(group.id, it)) }
        }
    }

    private fun renderRow(graphics: GuiGraphics, row: ChannelRow, rowY: Int, mouseX: Int, mouseY: Int) {
        val hovered = mouseX >= x && mouseX < x + width && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT
        when (row) {
            is ChannelRow.Section -> renderSection(graphics, row.id, row.label, rowY, hovered)
            is ChannelRow.Module -> renderModule(graphics, row.group, rowY, hovered)
            is ChannelRow.Control -> renderControl(graphics, row.channel, rowY, hovered)
            is ChannelRow.Wire -> renderWire(graphics, row.channel, rowY, hovered)
            is ChannelRow.Connection -> renderConnection(graphics, row.connection, rowY)
            is ChannelRow.UserGroup -> renderUserGroup(graphics, row.group, rowY, hovered)
            is ChannelRow.Binding -> renderBinding(graphics, row.groupId, row.binding, rowY, hovered)
        }
    }

    private fun renderSection(graphics: GuiGraphics, id: String, label: String, rowY: Int, hovered: Boolean) {
        graphics.fill(x + 2, rowY + 1, x + width - 2, rowY + ROW_HEIGHT - 1, if (hovered) 0xFF222222.toInt() else 0xFF181818.toInt())
        graphics.drawString(font, "${if (id in collapsedGroupIds) ">" else "v"} $label", x + 6, rowY + 4, 0xFF8E8E8E.toInt(), false)
    }
    private fun renderModule(graphics: GuiGraphics, group: ControlModuleGroupView, rowY: Int, hovered: Boolean) {
        graphics.fill(x + 2, rowY + 1, x + width - 2, rowY + ROW_HEIGHT - 1, if (hovered) 0xFF2C2C2C.toInt() else 0xFF242424.toInt())
        val label = "${if (group.id in collapsedGroupIds) ">" else "v"} ${group.label} · Desk ${group.deskIndex} · ${group.socketName}"
        graphics.drawString(font, font.plainSubstrByWidth(label, width - 12), x + 6, rowY + 4, 0xFFD0D0D0.toInt(), false)
    }
    private fun renderControl(graphics: GuiGraphics, channel: ControlChannelView, rowY: Int, hovered: Boolean) {
        val selected = selectedTargetId == channel.id; if (selected || hovered) graphics.fill(x + 2, rowY + 1, x + width - 2, rowY + ROW_HEIGHT - 1, if (selected) 0xFF303030.toInt() else 0xFF1C1C1C.toInt())
        graphics.drawString(font, font.plainSubstrByWidth("  ${channel.name}", width - 82), x + 6, rowY + 4, 0xFFE0E0E0.toInt(), false)
        graphics.drawString(font, "${channel.value}/15", x + width - 58, rowY + 4, if (channel.value > 0) 0xFFFFFF55.toInt() else 0xFF888888.toInt(), false)
        if (channel.overridden) graphics.drawString(font, "OVR", x + width - 25, rowY + 4, 0xFFFFFF55.toInt(), false)
    }
    private fun renderWire(graphics: GuiGraphics, channel: WireChannelView, rowY: Int, hovered: Boolean) {
        val selected = selectionKind == SelectionKind.WIRE && channel.id == selectedWireId; if (selected || hovered) graphics.fill(x + 2, rowY + 1, x + width - 2, rowY + ROW_HEIGHT - 1, if (selected) 0xFF303030.toInt() else 0xFF202020.toInt())
        graphics.drawString(font, font.plainSubstrByWidth(channel.name, width - 96), x + 6, rowY + 4, 0xFFF0F0F0.toInt(), false)
        graphics.drawString(font, "${channel.value}/15", x + width - 86, rowY + 4, if (channel.value > 0) 0xFFFFFF55.toInt() else 0xFF888888.toInt(), false)
        graphics.drawString(font, channel.connections.toString(), x + width - 22, rowY + 4, if (channel.connected) 0xFF55FF55.toInt() else 0xFF777777.toInt(), false)
    }
    private fun renderConnection(graphics: GuiGraphics, connection: WireConnectionView, rowY: Int) {
        graphics.drawString(font, font.plainSubstrByWidth("    -> ${connection.x}, ${connection.y}, ${connection.z}  ${connection.side}", width - 12), x + 6, rowY + 4, 0xFF777777.toInt(), false)
    }
    private fun renderUserGroup(graphics: GuiGraphics, group: UserChannelGroupView, rowY: Int, hovered: Boolean) {
        val selected = selectionKind == SelectionKind.GROUP && selectedGroup == group.id; if (selected || hovered) graphics.fill(x + 2, rowY + 1, x + width - 2, rowY + ROW_HEIGHT - 1, if (selected) 0xFF303030.toInt() else 0xFF242424.toInt())
        graphics.drawString(font, "${if ("user:${group.id}" in collapsedGroupIds) ">" else "v"} ${group.name}", x + 6, rowY + 4, 0xFFD0D0D0.toInt(), false)
    }
    private fun renderBinding(graphics: GuiGraphics, groupId: UUID, binding: UserChannelBindingView, rowY: Int, hovered: Boolean) {
        val selected = selectionKind == SelectionKind.BINDING && selectedGroup == groupId && selectedBinding == binding.alias; if (selected || hovered) graphics.fill(x + 2, rowY + 1, x + width - 2, rowY + ROW_HEIGHT - 1, if (selected) 0xFF303030.toInt() else 0xFF1C1C1C.toInt())
        val state = if (!binding.available) "MISSING" else "${binding.value ?: 0}/15"
        val label = "  ${binding.alias} -> ${binding.targetLabel}"
        graphics.drawString(font, font.plainSubstrByWidth(label, width - 64), x + 6, rowY + 4, if (binding.available) 0xFFE0E0E0.toInt() else 0xFFFF7777.toInt(), false)
        graphics.drawString(font, state, x + width - 56, rowY + 4, if (binding.available) 0xFFAAAAAA.toInt() else 0xFFFF7777.toInt(), false)
    }

    private fun visibleRowCount(): Int = max(1, (height - HEADER_HEIGHT) / ROW_HEIGHT)
    private fun inside(mouseX: Double, mouseY: Double): Boolean = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height

    private sealed interface ChannelRow {
        data class Section(val id: String, val label: String) : ChannelRow
        data class Module(val group: ControlModuleGroupView) : ChannelRow
        data class Control(val channel: ControlChannelView) : ChannelRow
        data class Wire(val channel: WireChannelView) : ChannelRow
        data class Connection(val connection: WireConnectionView) : ChannelRow
        data class UserGroup(val group: UserChannelGroupView) : ChannelRow
        data class Binding(val groupId: UUID, val binding: UserChannelBindingView) : ChannelRow
    }

    private companion object {
        const val HEADER_HEIGHT = 27; const val ROW_HEIGHT = 18
        const val CONTROL_SECTION_ID = "section:controls"; const val WIRE_SECTION_ID = "section:wires"; const val USER_SECTION_ID = "section:user_groups"
    }
}
