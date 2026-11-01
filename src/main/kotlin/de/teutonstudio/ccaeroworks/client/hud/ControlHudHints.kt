package de.teutonstudio.ccaeroworks.client.hud

import com.mojang.blaze3d.systems.RenderSystem
import com.mred231.aeroworks.content.controls.channel.ControlChannel
import com.mred231.aeroworks.foundation.input.InputSource
import de.teutonstudio.ccaeroworks.client.display.DisplayPointerHud
import de.teutonstudio.ccaeroworks.input.CombinedInputSource
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

object ControlHudHints {
    const val ROW_HEIGHT = 16
    private const val ICON_SIZE = 16
    private const val PART_GAP = 2
    private val textures = hashMapOf<String, ResourceLocation>()

    data class Binding(
        val label: String,
        val axis: String? = null,
        val analog: String? = null,
        val negative: String = "",
        val positive: String = "",
        val combined: Boolean = false,
        val channel: Int = -1
    )
    data class Part(val source: String? = null, val text: String = "", val held: Boolean = false)
    data class Row(val label: String, val parts: List<Part>, val buttons: List<Int> = emptyList())

    fun buttonChannels(channels: List<ControlChannel>, visible: (String) -> Boolean): List<Int> =
        channels.indices.filter { channels[it] is ControlChannel.Button && visible(channels[it].id()) }

    /** Merge a shared hold binding only when it controls both visible mouse axes. */
    fun rows(bindings: List<Binding>): List<Row> {
        val consumed = hashSetOf<Int>()
        val buttonCount = bindings.count { it.axis == null }
        var button = 0
        return bindings.mapIndexedNotNull { index, binding ->
            if (!consumed.add(index)) return@mapIndexedNotNull null
            if (binding.combined) {
                val pair = bindings.indices.firstOrNull { other ->
                    other !in consumed && bindings[other].combined &&
                        binding.negative.isNotBlank() && bindings[other].negative == binding.negative &&
                        setOf(binding.axis, bindings[other].axis) == setOf("X", "Y")
                }
                if (pair != null) consumed += pair
                val source = if (pair != null) "mouse.xy" else "mouse.${binding.axis.orEmpty().lowercase()}"
                Row("", listOf(Part(source = binding.negative, held = true), Part(text = "+"), Part(source = source)))
            } else if (binding.analog != null) {
                Row("", listOf(Part(source = binding.analog)))
            } else if (binding.axis != null) {
                val horizontal = binding.axis == "X"
                Row("", listOf(
                    Part(text = if (horizontal) "←" else "↑"), Part(source = binding.negative),
                    Part(text = if (horizontal) "→" else "↓"), Part(source = binding.positive)
                ))
            } else {
                button++
                Row(if (buttonCount > 1) button.toString() else "", listOf(Part(source = binding.positive)), listOf(binding.channel))
            }
        }
    }

    fun rows(widget: DisplayPointerHud.Widget): List<Row> {
        val module = widget.`ccaeroworks$module`()
        val bindings = widget.`ccaeroworks$axes`().mapNotNull { index ->
            val channel = module.channels().getOrNull(index) ?: return@mapNotNull null
            val config = module.channelConfig(channel)
            val axis = (channel as? ControlChannel.Axis)?.let { CombinedInputSource.mouseAxis(it).name }
            Binding(
                label = Component.translatable(channel.displayNameKey()).string,
                axis = axis,
                analog = module.analogSourceFor(channel.id()).takeIf { axis != null && module.analogActiveFor(channel.id()) },
                negative = config.negativeKey(),
                positive = config.positiveKey(),
                combined = axis != null && CombinedInputSource.isCombined(module, channel.id()),
                channel = index
            )
        }
        return rows(bindings)
    }

    fun width(font: Font, row: Row): Int = labelWidth(font, row) + (if (row.buttons.isEmpty()) 0 else 5) +
        row.parts.sumOf { partWidth(font, it) } + (row.parts.size - 1).coerceAtLeast(0) * PART_GAP

    fun layout(font: Font, rows: List<Row>, maxWidth: Int): ControlHudLayout.Flow =
        ControlHudLayout.flow(rows.map { ControlHudLayout.Size(width(font, it), ROW_HEIGHT) }, maxWidth, gap = 4)

    fun render(
        graphics: GuiGraphics, font: Font, rows: List<Row>, layout: ControlHudLayout.Flow,
        center: Int, top: Int, dim: Float, active: (Int) -> Boolean = { false }
    ) {
        val color = ((220 * dim).toInt() shl 24) or 0xffffff
        layout.placements.forEach { placement ->
            val row = rows[placement.index]
            val y = top + placement.y
            var x = center - layout.width / 2 + placement.x
            val pressed = row.buttons.any(active)
            if (row.buttons.isNotEmpty()) {
                graphics.fill(x, y + 6, x + 3, y + 9,
                    ((220 * dim).toInt() shl 24) or if (pressed) 0x60ff80 else 0x606060)
                x += 5
            }
            if (row.label.isNotBlank()) {
                graphics.drawString(font, row.label, x, y + 4, color)
                x += labelWidth(font, row)
            }
            row.parts.forEach { part ->
                if (part.source == null) {
                    graphics.drawString(font, part.text, x, y + 4, color)
                } else {
                    val glyph = glyph(part).let { if (pressed) InputPromptIcons.negative(it) else it }
                    val texture = textures.getOrPut(glyph.asset) {
                        ResourceLocation.fromNamespaceAndPath("cc_aeroworks", "textures/gui/input/${glyph.asset}.png")
                    }
                    RenderSystem.enableBlend()
                    RenderSystem.setShaderColor(1f, 1f, 1f, dim)
                    try {
                        graphics.blit(texture, x, y, ICON_SIZE, ICON_SIZE, 0f, 0f, 64, 64, 64, 64)
                    } finally {
                        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
                        RenderSystem.disableBlend()
                    }
                    if (glyph.caption.isNotBlank()) graphics.drawString(font, caption(font, glyph), x + ICON_SIZE, y + 4, color)
                }
                x += partWidth(font, part) + PART_GAP
            }
        }
    }

    private fun labelWidth(font: Font, row: Row): Int = if (row.label.isBlank()) 0 else font.width(row.label) + 2

    private fun partWidth(font: Font, part: Part): Int = if (part.source == null) font.width(part.text) else {
        val glyph = glyph(part)
        ICON_SIZE + font.width(caption(font, glyph))
    }

    private fun glyph(part: Part): InputPromptIcons.Glyph {
        val source = part.source.orEmpty()
        val glyph = InputPromptIcons.glyph(source, if (source.isBlank()) "—" else InputSource.displayName(source).string)
        return if (part.held) InputPromptIcons.negative(glyph) else glyph
    }

    private fun caption(font: Font, glyph: InputPromptIcons.Glyph): String = font.plainSubstrByWidth(glyph.caption, 72)
}
