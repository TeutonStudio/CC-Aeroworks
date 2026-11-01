package de.teutonstudio.ccaeroworks.client.hud

/** Wrap complete widgets, then fit the resulting rows above Minecraft's hotbar. */
object ControlHudLayout {
    data class Size(val width: Int, val height: Int)
    data class Placement(val index: Int, val x: Int, val y: Int)
    data class Layout(val scale: Float, val placements: List<Placement>)
    data class Flow(val width: Int, val height: Int, val placements: List<Placement>)

    /** Pack binding groups or small axis graphs without splitting a group across rows. */
    fun flow(sizes: List<Size>, maxWidth: Int, gap: Int = 3): Flow {
        if (sizes.isEmpty()) return Flow(0, 0, emptyList())
        val rows = mutableListOf<MutableList<Int>>()
        var used = 0
        sizes.forEachIndexed { index, size ->
            if (rows.isEmpty() || used + gap + size.width > maxWidth) {
                rows += mutableListOf(index)
                used = size.width
            } else {
                rows.last() += index
                used += gap + size.width
            }
        }
        val width = rows.maxOf { row -> row.sumOf { sizes[it].width } + (row.size - 1) * gap }
        var y = 0
        val placements = rows.flatMap { row ->
            val height = row.maxOf { sizes[it].height }
            val rowWidth = row.sumOf { sizes[it].width } + (row.size - 1) * gap
            var x = (width - rowWidth) / 2
            row.map { index ->
                Placement(index, x, y + (height - sizes[index].height) / 2).also { x += sizes[index].width + gap }
            }.also { y += height + gap }
        }
        return Flow(width, y - gap, placements)
    }

    fun arrange(sizes: List<Size>, screenWidth: Int, screenHeight: Int): Layout {
        if (sizes.isEmpty()) return Layout(1f, emptyList())
        val availableWidth = (screenWidth - 16).coerceAtLeast(1)
        val availableHeight = (screenHeight - 88).coerceAtLeast(1)
        val widest = sizes.maxOf { it.width }
        fun wrap(width: Int): List<List<Int>> {
            val rows = mutableListOf<MutableList<Int>>()
            var used = 0
            sizes.forEachIndexed { index, size ->
                if (rows.isEmpty() || used + 8 + size.width > width) {
                    rows += mutableListOf(index)
                    used = size.width
                } else {
                    rows.last() += index
                    used += 8 + size.width
                }
            }
            return rows
        }
        fun height(rows: List<List<Int>>) = rows.sumOf { row -> row.maxOf { sizes[it].height } } + (rows.size - 1) * 8
        fun scale(rows: List<List<Int>>): Float {
            val width = rows.maxOf { row -> row.sumOf { sizes[it].width } + (row.size - 1) * 8 }
            return minOf(1f, availableWidth.toFloat() / width.coerceAtLeast(1), availableHeight.toFloat() / height(rows).coerceAtLeast(1))
        }
        // Try wider virtual rows as well: wrapping at screen width before scaling can make a
        // large multiblock unnecessarily tiny. A bounded search stays linear in widget count.
        val widths = listOf(widest, sizes.sumOf { it.width + 8 }) +
            listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f, 4f, 6f, 8f, 12f, 16f).map { (availableWidth * it).toInt().coerceAtLeast(widest) }
        val rows = widths.distinct().map(::wrap).maxWith(compareBy<List<List<Int>>> { scale(it) }.thenBy { -height(it) })
        val rowHeights = rows.map { row -> row.maxOf { sizes[it].height } }
        val height = height(rows)
        val scale = scale(rows)
        var y = 0
        val placements = rows.flatMapIndexed { rowIndex, row ->
            val rowWidth = row.sumOf { sizes[it].width } + (row.size - 1) * 8
            var x = (screenWidth / scale - rowWidth).toInt() / 2
            val top = (screenHeight - 80) / scale - height
            row.map { index ->
                Placement(index, x, top.toInt() + y).also { x += sizes[index].width + 8 }
            }.also { y += rowHeights[rowIndex] + 8 }
        }
        return Layout(scale, placements)
    }
}
