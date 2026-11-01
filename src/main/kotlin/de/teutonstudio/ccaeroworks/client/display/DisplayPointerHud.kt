package de.teutonstudio.ccaeroworks.client.display

import com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity
import com.mred231.aeroworks.content.controls.module.MountedModule
import de.teutonstudio.ccaeroworks.display.DeskDisplayGeometry
import de.teutonstudio.ccaeroworks.display.DeskDisplayTouch
import de.teutonstudio.ccaeroworks.input.CombinedInputSource
import de.teutonstudio.ccaeroworks.input.DisplayCombinedInputController
import de.teutonstudio.ccaeroworks.mixin.client.ConsoleControlClientAccessor
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics

/** Replaces the native signed-axis readout for display pointers only. */
object DisplayPointerHud {
    interface Widget {
        fun `ccaeroworks$socket`(): Int
        fun `ccaeroworks$module`(): MountedModule
        fun `ccaeroworks$axes`(): List<Int>
    }

    private data class Pointer(val touch: DeskDisplayTouch, val active: Boolean)

    @JvmStatic
    fun width(widget: Widget): Int {
        val font = Minecraft.getInstance().font
        val pointer = pointer(widget)
        val readout = pointer?.let { coordinates(widget, it.touch, active = true, maximum = true) }
        return maxOf(40, readout?.let(font::width) ?: 0) + 4
    }

    @JvmStatic
    fun render(graphics: GuiGraphics, font: Font, widget: Widget, left: Int, bottom: Int, dim: Float) {
        val pointer = pointer(widget)
        val center = left + width(widget) / 2
        if (pointer != null) {
            val touch = pointer.touch
            val surface = DisplayPointerHudLayout.surface(touch.width, touch.height, maxWidth = 40, maxHeight = 28)
            val x = center - surface.width / 2
            val y = bottom - 28 + (28 - surface.height) / 2
            graphics.fill(x - 1, y - 1, x + surface.width + 1, y + surface.height + 1, color(0x80202020.toInt(), dim))
            graphics.fill(x, y, x + surface.width, y + surface.height, color(0x40000000, dim))
            if (pointer.active) {
                val markerX = x + DisplayPointerHudLayout.marker(touch.u, surface.width)
                val markerY = y + DisplayPointerHudLayout.marker(1.0 - touch.v, surface.height)
                graphics.fill(markerX - 2, markerY - 2, markerX + 3, markerY + 3, color(0xc0ffffff.toInt(), dim))
            }
            graphics.drawCenteredString(font, coordinates(widget, touch, pointer.active), center, bottom + 1, color(0xc0ffffff.toInt(), dim))
        }
    }

    /** Native HUD sockets are flattened across all members; pointer sockets are local to a desk. */
    private fun pointer(widget: Widget): Pointer? {
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return null
        val group = ConsoleControlClientAccessor.`ccaeroworks$getActiveGroup`()
        val counts = ConsoleControlClientAccessor.`ccaeroworks$getGroupSocketCounts`()
        var socket = widget.`ccaeroworks$socket`()
        for ((member, count) in counts.withIndex()) {
            if (socket >= count) {
                socket -= count
                continue
            }
            val pos = group.getOrNull(member) ?: return null
            val desk = level.getBlockEntity(pos) as? ConsoleBlockEntity ?: return null
            if (desk.module(socket) !== widget.`ccaeroworks$module`()) return null
            val active = DisplayCombinedInputController.activeTarget()?.takeIf {
                it.dimension == level.dimension() && it.pos == pos && it.socket == socket
            }
            val touch = DeskDisplayGeometry.touch(desk, socket, active?.u ?: 0.5, active?.v ?: 0.5) ?: return null
            return Pointer(touch, active != null)
        }
        return null
    }

    private fun axes(widget: Widget): List<Pair<CombinedInputSource.MouseAxis, String>> {
        val module = widget.`ccaeroworks$module`()
        return widget.`ccaeroworks$axes`().mapNotNull { index ->
            val channel = module.channels().getOrNull(index) ?: return@mapNotNull null
            val axis = CombinedInputSource.mouseAxis(module, channel.id())
            val binding = if (CombinedInputSource.isCombined(module, channel.id())) {
                CombinedInputSource.activationBinding(module, channel.id())
            } else ""
            axis to binding
        }.distinctBy { it.first }
    }

    private fun coordinates(widget: Widget, touch: DeskDisplayTouch, active: Boolean, maximum: Boolean = false): String =
        axes(widget).joinToString("  ") { (axis, _) ->
            val extent = if (axis == CombinedInputSource.MouseAxis.X) touch.width else touch.height
            val value = if (maximum) extent else if (axis == CombinedInputSource.MouseAxis.X) touch.x else touch.y
            if (active) "${axis.name}: $value/$extent" else "${axis.name}: 1..$extent"
        }

    private fun color(argb: Int, dim: Float): Int = ((argb ushr 24) * dim).toInt().shl(24) or (argb and 0xffffff)
}
