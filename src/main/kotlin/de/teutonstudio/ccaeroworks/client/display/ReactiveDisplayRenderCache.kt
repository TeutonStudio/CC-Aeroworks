package de.teutonstudio.ccaeroworks.client.display

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.display.reactive.ReactiveDisplaySnapshot
import java.util.WeakHashMap

data class ReactiveDisplayPixel(val x: Int, val y: Int)

object ReactiveDisplayRenderCache {
    private data class Entry(
        val revision: Long,
        val width: Int,
        val height: Int,
        val pixels: List<ReactiveDisplayPixel>
    )

    private val cache = WeakHashMap<ConsoleBlockEntity, MutableMap<Int, Entry>>()

    @Synchronized
    fun pixels(
        desk: ConsoleBlockEntity,
        socket: Int,
        frame: ReactiveDisplaySnapshot
    ): List<ReactiveDisplayPixel> {
        val deskCache = cache.getOrPut(desk) { hashMapOf() }
        deskCache[socket]
            ?.takeIf { it.revision == frame.revision && it.width == frame.width && it.height == frame.height }
            ?.let { return it.pixels }

        val pixels = ArrayList<ReactiveDisplayPixel>()
        frame.forEachSetPixel { x, y -> pixels += ReactiveDisplayPixel(x, y) }
        deskCache[socket] = Entry(frame.revision, frame.width, frame.height, pixels)
        return pixels
    }
}
