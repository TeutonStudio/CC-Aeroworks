package de.teutonstudio.ccaeroworks.client.display

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.display.reactive.ReactiveDisplaySnapshot
import java.util.WeakHashMap

data class ReactiveDisplayRect(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)

object ReactiveDisplayRenderCache {
    private data class Entry(
        val revision: Long,
        val width: Int,
        val height: Int,
        val mergeHorizontal: Boolean,
        val mergeVertical: Boolean,
        val rectangles: List<ReactiveDisplayRect>
    )

    private data class Run(val x: Int, val width: Int)

    private class MutableRect(
        val x: Int,
        val y: Int,
        val width: Int,
        var height: Int
    ) {
        fun freeze(): ReactiveDisplayRect = ReactiveDisplayRect(x, y, width, height)
    }

    private val cache = WeakHashMap<ConsoleBlockEntity, MutableMap<Int, Entry>>()

    @Synchronized
    fun rectangles(
        desk: ConsoleBlockEntity,
        socket: Int,
        frame: ReactiveDisplaySnapshot,
        mergeHorizontal: Boolean,
        mergeVertical: Boolean
    ): List<ReactiveDisplayRect> {
        val deskCache = cache.getOrPut(desk) { hashMapOf() }
        deskCache[socket]
            ?.takeIf {
                it.revision == frame.revision &&
                    it.width == frame.width &&
                    it.height == frame.height &&
                    it.mergeHorizontal == mergeHorizontal &&
                    it.mergeVertical == mergeVertical
            }
            ?.let { return it.rectangles }

        val rows = sortedMapOf<Int, MutableList<Int>>()
        frame.forEachSetPixel { x, y -> rows.getOrPut(y) { arrayListOf() }.add(x) }
        val rectangles = buildRectangles(rows, mergeHorizontal, mergeVertical)
        deskCache[socket] = Entry(
            frame.revision,
            frame.width,
            frame.height,
            mergeHorizontal,
            mergeVertical,
            rectangles
        )
        return rectangles
    }

    private fun buildRectangles(
        rows: Map<Int, MutableList<Int>>,
        mergeHorizontal: Boolean,
        mergeVertical: Boolean
    ): List<ReactiveDisplayRect> {
        val result = arrayListOf<ReactiveDisplayRect>()
        if (!mergeVertical) {
            rows.forEach { (y, xs) ->
                runs(xs, mergeHorizontal).forEach { run ->
                    result += ReactiveDisplayRect(run.x, y, run.width, 1)
                }
            }
            return result
        }

        val active = linkedMapOf<Run, MutableRect>()
        var previousY: Int? = null
        rows.forEach { (y, xs) ->
            if (previousY != null && y != previousY!! + 1) {
                active.values.forEach { result += it.freeze() }
                active.clear()
            }

            val currentRuns = runs(xs, mergeHorizontal)
            val currentKeys = currentRuns.toSet()
            val finished = active.keys.filter { it !in currentKeys }
            finished.forEach { key -> active.remove(key)?.let { result += it.freeze() } }

            currentRuns.forEach { run ->
                val current = active[run]
                if (current != null && current.y + current.height == y) {
                    current.height++
                } else {
                    current?.let { result += it.freeze() }
                    active[run] = MutableRect(run.x, y, run.width, 1)
                }
            }
            previousY = y
        }
        active.values.forEach { result += it.freeze() }
        return result
    }

    private fun runs(xs: MutableList<Int>, mergeHorizontal: Boolean): List<Run> {
        if (xs.isEmpty()) return emptyList()
        xs.sort()
        if (!mergeHorizontal) return xs.map { Run(it, 1) }

        val result = arrayListOf<Run>()
        var start = xs.first()
        var previous = start
        for (index in 1 until xs.size) {
            val x = xs[index]
            if (x == previous + 1) {
                previous = x
                continue
            }
            result += Run(start, previous - start + 1)
            start = x
            previous = x
        }
        result += Run(start, previous - start + 1)
        return result
    }
}
