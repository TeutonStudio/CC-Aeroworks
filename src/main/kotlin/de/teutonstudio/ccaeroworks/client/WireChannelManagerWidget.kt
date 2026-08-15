package de.teutonstudio.ccaeroworks.client

import de.teutonstudio.ccaeroworks.computer.wire.WireChannelSnapshotState
import de.teutonstudio.ccaeroworks.computer.wire.WireChannelView
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.network.chat.Component
import java.util.UUID
import kotlin.math.max

/** Scrollable CC-styled view over the server-authoritative WireChannelBank snapshot. */
internal class WireChannelManagerWidget(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    private val font: Font
) : AbstractWidget(x, y, width, height, Component.literal("Wire channels")) {
    private var scrollIndex = 0
    var selectedId: UUID? = null
        private set

    fun selectedChannel(): WireChannelView? =
        WireChannelSnapshotState.get().channels.firstOrNull { it.id == selectedId }

    override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val snapshot = WireChannelSnapshotState.get()
        graphics.fill(x, y, x + width, y + height, 0xFF111111.toInt())
        graphics.drawString(font, "WIRE CHANNELS", x + 6, y + 5, 0xFFF0F0F0.toInt(), false)
        val status = "${snapshot.backend} · ${if (snapshot.enabled) "enabled" else "disabled"}"
        graphics.drawString(font, font.plainSubstrByWidth(status, width - 12), x + 6, y + 15, 0xFF888888.toInt(), false)

        val channels = snapshot.channels
        val visibleRows = visibleRowCount()
        val maxScroll = max(0, channels.size - visibleRows)
        scrollIndex = scrollIndex.coerceIn(0, maxScroll)
        if (selectedId != null && channels.none { it.id == selectedId }) selectedId = null

        val listTop = y + HEADER_HEIGHT
        graphics.enableScissor(x, listTop, x + width, y + height)
        try {
            channels.drop(scrollIndex).take(visibleRows).forEachIndexed { index, channel ->
                val rowY = listTop + index * ROW_HEIGHT
                renderRow(graphics, channel, rowY, mouseX, mouseY)
            }
            if (channels.isEmpty()) {
                graphics.drawString(font, "No channels configured", x + 6, listTop + 7, 0xFF777777.toInt(), false)
            }
        } finally {
            graphics.disableScissor()
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (!visible || !active || button != 0 || !inside(mouseX, mouseY)) return false
        val row = ((mouseY - (y + HEADER_HEIGHT)) / ROW_HEIGHT).toInt()
        if (row < 0) return true
        selectedId = WireChannelSnapshotState.get().channels.getOrNull(scrollIndex + row)?.id
        return true
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (!visible || !active || !inside(mouseX, mouseY)) return false
        val channels = WireChannelSnapshotState.get().channels
        val maxScroll = max(0, channels.size - visibleRowCount())
        scrollIndex = (scrollIndex - scrollY.toInt()).coerceIn(0, maxScroll)
        return true
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {
        defaultButtonNarrationText(output)
    }

    private fun renderRow(
        graphics: GuiGraphics,
        channel: WireChannelView,
        rowY: Int,
        mouseX: Int,
        mouseY: Int
    ) {
        val selected = channel.id == selectedId
        val hovered = mouseX >= x && mouseX < x + width && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT
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

    private companion object {
        const val HEADER_HEIGHT = 27
        const val ROW_HEIGHT = 18
    }
}
