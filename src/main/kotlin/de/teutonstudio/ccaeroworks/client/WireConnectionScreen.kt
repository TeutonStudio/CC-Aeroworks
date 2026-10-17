package de.teutonstudio.ccaeroworks.client

import de.teutonstudio.ccaeroworks.computer.wire.WireConnectionView
import de.teutonstudio.ccaeroworks.network.SetWireConnectionPayload
import de.teutonstudio.ccaeroworks.network.WireConnectionResultPayload
import de.teutonstudio.ccaeroworks.network.WireConnectionResultState
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.network.PacketDistributor

/** Direct coordinate editing of the same physical DBW connections created by the Cable. */
internal class WireConnectionScreen(
    private val parent: Screen,
    private val channelId: String,
    private val channelLabel: String,
    private val connections: List<WireConnectionView>
) : Screen(Component.literal("Verbindungsziel")) {
    private val coordinates = ArrayList<EditBox>()
    private var side: Direction = Direction.NORTH
    private var sideButton: Button? = null
    private var connectionIndex = 0
    private var left = 0
    private var top = 0

    override fun init() {
        val saved = coordinates.map { it.value }
        coordinates.clear()
        left = (width - 290) / 2
        top = (height - 180) / 2
        WireConnectionResultState.result = null
        listOf("X", "Y", "Z").forEachIndexed { index, label ->
            val field = EditBox(font, left + index * 98, top + 36, 94, 20, Component.literal(label))
            field.setMaxLength(11)
            field.setHint(Component.literal(label))
            field.setFilter { value -> value.matches(Regex("-?[0-9]*")) }
            field.value = saved.getOrNull(index).orEmpty()
            coordinates += addRenderableWidget(field)
        }
        sideButton = addRenderableWidget(Button.builder(sideLabel()) {
            side = Direction.entries[(side.ordinal + 1) % Direction.entries.size]
            sideButton?.message = sideLabel()
        }.bounds(left, top + 66, 190, 20).tooltip(Tooltip.create(Component.literal("Blockseite des Ziels, in Blockkoordinaten"))).build())
        addRenderableWidget(Button.builder(Component.literal("Ziel laden")) {
            if (connections.isNotEmpty()) {
                loadConnection(connections[connectionIndex % connections.size])
                connectionIndex++
            }
        }.bounds(left + 198, top + 66, 92, 20).tooltip(Tooltip.create(Component.literal("Vorhandene Verbindungen nacheinander in die Felder laden"))).build()).active = connections.isNotEmpty()
        addRenderableWidget(Button.builder(Component.literal("Verbinden")) { submit(true) }.bounds(left, top + 96, 141, 20).build())
        addRenderableWidget(Button.builder(Component.literal("Trennen")) { submit(false) }.bounds(left + 149, top + 96, 141, 20).build())
        addRenderableWidget(Button.builder(Component.literal("Zurück")) { onClose() }.bounds(left, top + 156, 290, 20).build())
        if (saved.isEmpty()) connections.firstOrNull()?.let(::loadConnection)
        setInitialFocus(coordinates.first())
    }

    private fun sideLabel(): Component = Component.literal("Blockseite: ${side.name.lowercase()}")

    private fun loadConnection(connection: WireConnectionView) {
        listOf(connection.x, connection.y, connection.z).forEachIndexed { index, value -> coordinates[index].value = value.toString() }
        side = Direction.entries.firstOrNull { it.name.equals(connection.side, ignoreCase = true) } ?: Direction.NORTH
        sideButton?.message = sideLabel()
    }

    private fun submit(add: Boolean) {
        val values = coordinates.map { it.value.toIntOrNull() }
        if (values.any { it == null }) {
            WireConnectionResultState.result = WireConnectionResultPayload(false, "X, Y und Z müssen ganze Zahlen sein")
            return
        }
        val x = values[0]!!; val y = values[1]!!; val z = values[2]!!
        // BlockPos's packed network representation must not wrap user-entered coordinates.
        if (x !in -30_000_000..30_000_000 || z !in -30_000_000..30_000_000 || y !in -2048..2047) {
            WireConnectionResultState.result = WireConnectionResultPayload(false, "Koordinaten liegen außerhalb des gültigen Bereichs")
            return
        }
        WireConnectionResultState.result = WireConnectionResultPayload(true, "Verbindung wird geprüft …")
        PacketDistributor.sendToServer(SetWireConnectionPayload(channelId, BlockPos(x, y, z), side, add))
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(graphics, mouseX, mouseY, partialTick)
        graphics.drawCenteredString(font, title, width / 2, top, 0xFFFFFF)
        graphics.drawString(font, font.plainSubstrByWidth(channelLabel, 290), left, top + 17, 0xAAAAAA, false)
        super.render(graphics, mouseX, mouseY, partialTick)
        WireConnectionResultState.result?.let {
            val lines = font.split(Component.literal(it.message), 290)
            lines.take(2).forEachIndexed { index, line -> graphics.drawString(font, line, left, top + 126 + index * 10, if (it.success) 0x88DD88 else 0xFF8888, false) }
        }
    }

    override fun onClose() { minecraft?.setScreen(parent) }
    override fun isPauseScreen(): Boolean = false
}
