package de.teutonstudio.ccaeroworks.compat.aeroworks

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import com.mred231.aeroworks.content.controls.MountedModule
import de.teutonstudio.ccaeroworks.display.DeskDisplayFormatter
import de.teutonstudio.ccaeroworks.display.DeskDisplayPixels
import de.teutonstudio.ccaeroworks.display.DeskDisplayState
import de.teutonstudio.ccaeroworks.registry.CCModuleTypes
import net.minecraft.network.chat.Component

object AeroworksDeskAccess {
    @JvmStatic
    fun module(desk: ConsoleBlockEntity, socket: Int): MountedModule? =
        if (socket in 0 until desk.socketCount()) desk.module(socket) else null

    @JvmStatic
    fun display(desk: ConsoleBlockEntity, socket: Int): DeskDisplayState? {
        val module = module(desk, socket) ?: return null
        val type = CCModuleTypes.displayType(module.type()) ?: return null
        val stored = module.customName()?.string.orEmpty()
        val decoded = DeskDisplayPixels.decode(type, stored)
        val encodedRaster = DeskDisplayPixels.isEncoded(stored)
        // A PPB change invalidates the old raster dimensions. Keep that state in pixel mode and
        // start with a correctly sized blank raster instead of accidentally displaying the
        // serialized payload as ordinary seven-segment text.
        val pixels = decoded ?: if (encodedRaster) DeskDisplayPixels.blank(type) else null
        val text = if (pixels == null) DeskDisplayFormatter.normalizeText(stored, type.width) else ""
        return DeskDisplayState(socket, type, text, pixels)
    }

    @JvmStatic
    fun displays(desk: ConsoleBlockEntity): List<DeskDisplayState> =
        (0 until desk.socketCount()).mapNotNull { display(desk, it) }

    @JvmStatic
    fun renderedDisplays(desk: ConsoleBlockEntity): List<DeskDisplayState> = displays(desk)

    @JvmStatic
    fun setDisplayText(desk: ConsoleBlockEntity, socket: Int, text: String): DeskDisplayState? {
        val current = display(desk, socket) ?: return null
        val normalized = DeskDisplayFormatter.normalizeText(text, current.type.width)
        desk.setModuleName(socket, "", if (normalized.isEmpty()) null else Component.literal(normalized))
        return current.copy(text = normalized, pixels = null)
    }

    @JvmStatic
    fun setDisplayPixels(desk: ConsoleBlockEntity, socket: Int, pixels: DeskDisplayPixels): DeskDisplayState? {
        val current = display(desk, socket) ?: return null
        if (pixels.width != current.type.pixelWidth || pixels.height != current.type.pixelHeight) return null
        desk.setModuleName(socket, "", Component.literal(pixels.encode()))
        return current.copy(text = "", pixels = pixels)
    }
}
