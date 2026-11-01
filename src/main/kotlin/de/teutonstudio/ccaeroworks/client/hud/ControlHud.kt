package de.teutonstudio.ccaeroworks.client.hud

import com.mred231.aeroworks.AeroworksKeys
import com.mred231.aeroworks.content.controls.ScrollAnimation
import com.mred231.aeroworks.content.controls.channel.ControlChannel
import com.mred231.aeroworks.content.controls.console.ConsoleControlClient
import com.mred231.aeroworks.content.controls.module.ModuleItem
import com.mred231.aeroworks.content.controls.module.ModuleType
import com.mred231.aeroworks.content.controls.module.MountedModule
import de.teutonstudio.ccaeroworks.client.display.DisplayPointerHud
import de.teutonstudio.ccaeroworks.input.CombinedInputSource
import de.teutonstudio.ccaeroworks.mixin.client.ConsoleControlClientAccessor
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import kotlin.math.abs
import kotlin.math.roundToInt

/** One HUD for the active multiblock, including assembled child-module button channels. */
object ControlHud {
    private const val PAD = 28
    private const val HALF_PAD = PAD / 2
    private const val HEADER = 14
    private const val HINT_WIDTH = 112

    private data class ModuleWidget(val socket: Int, val module: MountedModule, val channels: List<Int>) : DisplayPointerHud.Widget {
        override fun `ccaeroworks$socket`() = socket
        override fun `ccaeroworks$module`() = module
        override fun `ccaeroworks$axes`() = channels
    }
    private data class Tile(
        val widget: DisplayPointerHud.Widget,
        val title: Component?,
        val icon: ItemStack,
        val origin: Component,
        val axes: List<ModuleWidget>,
        val hints: List<ControlHudHints.Row>,
        val hintLayout: ControlHudLayout.Flow,
        val graphLayout: ControlHudLayout.Flow,
        val size: ControlHudLayout.Size,
        val pointer: Boolean
    )
    private val smoothed = hashMapOf<String, Float>()
    private val moduleIcons = hashMapOf<ModuleType, ItemStack>()
    private var session: List<BlockPos> = emptyList()

    @JvmStatic
    fun render(graphics: GuiGraphics, nativeAxes: List<DisplayPointerHud.Widget>) {
        val minecraft = Minecraft.getInstance()
        if (!ConsoleControlClient.isActive() || minecraft.level == null) {
            smoothed.clear()
            session = emptyList()
            return
        }
        if (minecraft.options.hideGui || minecraft.screen != null) return
        val group = ConsoleControlClientAccessor.`ccaeroworks$getActiveGroup`()
        if (session != group) {
            session = group.toList()
            smoothed.clear()
        }
        val font = minecraft.font
        val modules = (0 until ConsoleControlClient.activeSocketCount()).mapNotNull { socket ->
            ConsoleControlClient.activeModule(socket)?.let { socket to it }
        }.toMap()
        val groups = ControlHudModules.combine(
            nativeAxes.map { ControlHudModules.AxisWidget(it.`ccaeroworks$socket`(), it.`ccaeroworks$axes`()) },
            modules.mapValues { (_, module) -> ControlHudHints.buttonChannels(module.channels(), module::showHudFor) }
        )
        val tiles = groups.mapNotNull { group ->
            modules[group.socket]?.let { tile(group, it, font) }
        }
        val layout = ControlHudLayout.arrange(tiles.map { it.size }, graphics.guiWidth(), graphics.guiHeight())
        val dim = if (AeroworksKeys.isFreeCameraHeld()) 0.45f else 1f
        graphics.pose().pushPose()
        try {
            graphics.pose().scale(layout.scale, layout.scale, 1f)
            layout.placements.forEach { placement -> renderTile(graphics, font, tiles[placement.index], placement.x, placement.y, dim) }
        } finally {
            graphics.pose().popPose()
        }
    }

    private fun tile(group: ControlHudModules.Module, module: MountedModule, font: Font): Tile {
        val axes = group.axes.map { ModuleWidget(group.socket, module, it) }
        val widget = ModuleWidget(group.socket, module, group.axes.flatten())
        val title = module.customName()
        val icon = moduleIcons.getOrPut(module.type()) {
            ModuleItem.byType(module.type())?.let(::ItemStack) ?: ItemStack.EMPTY
        }
        var socket = group.socket
        var member = 0
        for (count in ConsoleControlClientAccessor.`ccaeroworks$getGroupSocketCounts`()) {
            if (socket < count) break
            socket -= count
            member++
        }
        val origin = Component.translatable("gui.cc_aeroworks.hud.origin", member + 1, socket + 1)
        val hints = ControlHudHints.rows(widget) + ControlHudHints.rows(ModuleWidget(group.socket, module, group.buttons))
        val hintLayout = ControlHudHints.layout(font, hints, HINT_WIDTH)
        val pointer = axes.isNotEmpty() && CombinedInputSource.isDisplayPointerModule(module)
        val graphLayout = if (pointer) ControlHudLayout.Flow(DisplayPointerHud.width(widget), PAD, emptyList()) else {
            ControlHudLayout.flow(axes.map { axisWidget ->
                val channels = axisWidget.channels
                when {
                    channels.size >= 2 -> ControlHudLayout.Size(PAD, PAD + (channels.size - 2) * 6)
                    horizontal(axisWidget, channels.single()) -> ControlHudLayout.Size(PAD, 6)
                    else -> ControlHudLayout.Size(6, PAD)
                }
            }, HINT_WIDTH)
        }
        val width = maxOf(
            40, 16 + (title?.let { minOf(72, font.width(it)) + 3 } ?: 0) + font.width(origin),
            hintLayout.width, graphLayout.width,
            if (axes.isEmpty() || pointer) 0 else font.width(readout(widget))
        ) + 6
        val height = HEADER + graphLayout.height + (if (axes.isEmpty()) 0 else 10) + hintLayout.height + 3
        return Tile(widget, title, icon, origin, axes, hints, hintLayout, graphLayout, ControlHudLayout.Size(width, height), pointer)
    }

    private fun renderTile(graphics: GuiGraphics, font: Font, tile: Tile, left: Int, top: Int, dim: Float) {
        val center = left + tile.size.width / 2
        graphics.fill(left, top, left + tile.size.width, top + tile.size.height, color(0x70000000, dim))
        if (!tile.icon.isEmpty) {
            graphics.pose().pushPose()
            try {
                graphics.pose().translate((left + 2).toFloat(), (top + 1).toFloat(), 0f)
                graphics.pose().scale(0.75f, 0.75f, 1f)
                graphics.renderItem(tile.icon, 0, 0)
            } finally {
                graphics.pose().popPose()
            }
        }
        val originWidth = font.width(tile.origin)
        tile.title?.let {
            graphics.drawString(font, font.plainSubstrByWidth(it.string, tile.size.width - originWidth - 22), left + 16, top + 3, color(0xe0ffffff.toInt(), dim))
        }
        graphics.drawString(font, tile.origin, left + tile.size.width - originWidth - 3, top + 3, color(0xa0c0c0c0.toInt(), dim))
        val bottom = top + HEADER + tile.graphLayout.height
        if (tile.pointer) {
            DisplayPointerHud.render(graphics, font, tile.widget, center - DisplayPointerHud.width(tile.widget) / 2, bottom, dim)
        } else if (tile.axes.isNotEmpty()) {
            tile.graphLayout.placements.forEach { placement ->
                val axis = tile.axes[placement.index]
                val width = if (axis.channels.size >= 2 || horizontal(axis, axis.channels.single())) PAD else 6
                renderAxes(graphics, axis, center - tile.graphLayout.width / 2 + placement.x + width / 2, top + HEADER + placement.y, dim)
            }
            graphics.drawCenteredString(font, readout(tile.widget), center, bottom + 1, color(0xc0ffffff.toInt(), dim))
        }
        val hintsTop = bottom + if (tile.axes.isEmpty()) 0 else 10
        ControlHudHints.render(graphics, font, tile.hints, tile.hintLayout, center, hintsTop, dim) { rawValue(tile.widget, it) != 0 }
    }

    private fun renderAxes(graphics: GuiGraphics, widget: DisplayPointerHud.Widget, center: Int, top: Int, dim: Float) {
        val axes = widget.`ccaeroworks$axes`()
        if (axes.size >= 2) {
            val xIndex = if (axes.size == 2 && horizontal(widget, axes[1]) && !horizontal(widget, axes[0])) 1 else 0
            val yIndex = 1 - xIndex
            frame(graphics, center - HALF_PAD, top, PAD, PAD, dim)
            graphics.fill(center, top, center + 1, top + PAD, color(0x50808080, dim))
            graphics.fill(center - HALF_PAD, top + HALF_PAD, center + HALF_PAD, top + HALF_PAD + 1, color(0x50808080, dim))
            val x = center + (position(widget, axes[xIndex]) * (HALF_PAD - 2) / 15f).roundToInt()
            val y = top + HALF_PAD + (position(widget, axes[yIndex]) * (HALF_PAD - 2) / 15f).roundToInt()
            graphics.fill(x - 1, y - 1, x + 2, y + 2, color(0xc0ffffff.toInt(), dim))
            axes.drop(2).forEachIndexed { index, channel -> bar(graphics, widget, channel, center, top + PAD + 2 + index * 6, true, 4, dim) }
        } else if (axes.isNotEmpty()) {
            bar(graphics, widget, axes.single(), center, top, horizontal(widget, axes.single()), 6, dim)
        }
    }

    private fun bar(graphics: GuiGraphics, widget: DisplayPointerHud.Widget, channel: Int, center: Int, top: Int, horizontal: Boolean, thickness: Int, dim: Float) {
        val left = center - if (horizontal) HALF_PAD else thickness / 2
        val width = if (horizontal) PAD else thickness
        val height = if (horizontal) thickness else PAD
        frame(graphics, left, top, width, height, dim)
        val offset = (position(widget, channel) * (HALF_PAD - 2) / 15f).roundToInt()
        if (horizontal) {
            graphics.fill(center, top, center + 1, top + height, color(0x50808080, dim))
            graphics.fill(center + offset - 1, top, center + offset + 2, top + height, color(0xc0ffffff.toInt(), dim))
        } else {
            graphics.fill(left, top + HALF_PAD, left + width, top + HALF_PAD + 1, color(0x50808080, dim))
            graphics.fill(left, top + HALF_PAD + offset - 1, left + width, top + HALF_PAD + offset + 2, color(0xc0ffffff.toInt(), dim))
        }
    }

    private fun frame(graphics: GuiGraphics, left: Int, top: Int, width: Int, height: Int, dim: Float) {
        graphics.fill(left - 1, top - 1, left + width + 1, top + height + 1, color(0x80202020.toInt(), dim))
        graphics.fill(left, top, left + width, top + height, color(0x40000000, dim))
    }

    private fun rawValue(widget: DisplayPointerHud.Widget, index: Int): Int {
        val module = widget.`ccaeroworks$module`()
        val channel = module.channels()[index]
        return ConsoleControlClient.clientValue(widget.`ccaeroworks$socket`(), channel.id(), module.value(channel.id()))
    }

    private fun position(widget: DisplayPointerHud.Widget, index: Int): Float {
        val module = widget.`ccaeroworks$module`()
        val channel = module.channels()[index]
        val target = module.channelConfig(channel).travel().display(rawValue(widget, index).toFloat()).roundToInt().coerceIn(-15, 15)
        val key = "${widget.`ccaeroworks$socket`()}:${channel.id()}"
        return smoothed.getOrPut(key) { target.toFloat() }.let { previous ->
            ScrollAnimation.approach(previous, target, 0.01f).also { smoothed[key] = it }
        }
    }

    private fun horizontal(widget: DisplayPointerHud.Widget, index: Int): Boolean =
        (widget.`ccaeroworks$module`().channels()[index] as? ControlChannel.Axis)?.horizontalHud() == true

    private fun readout(widget: DisplayPointerHud.Widget): Component {
        val module = widget.`ccaeroworks$module`()
        val result = Component.empty()
        widget.`ccaeroworks$axes`().forEachIndexed { index, channelIndex ->
            if (index > 0) result.append(", ")
            val channel = module.channels()[channelIndex]
            val value = rawValue(widget, channelIndex)
            val output = module.channelConfig(channel).tuning().outputFor(abs(value))
            val positive = if (horizontal(widget, channelIndex)) value > 0 else value < 0
            val number = Component.literal(output.toString())
            if (value != 0) number.withStyle { it.withColor(if (positive) 0xffb000 else 0x66aaff) }
            result.append(number)
        }
        return result
    }

    private fun color(argb: Int, dim: Float): Int = ((argb ushr 24) * dim).toInt().shl(24) or (argb and 0xffffff)
}
