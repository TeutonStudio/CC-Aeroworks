package de.teutonstudio.ccaeroworks.display.reactive

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag

const val REACTIVE_DISPLAY_TILE_SIZE: Int = 64

data class ReactiveTileKey(val x: Int, val y: Int)

data class ReactiveTilePatch(
    val key: ReactiveTileKey,
    val rows: LongArray
) {
    val cleared: Boolean
        get() = rows.isEmpty()
}

data class ReactiveDisplayPatch(
    val width: Int,
    val height: Int,
    val revision: Long,
    val full: Boolean,
    val tiles: List<ReactiveTilePatch>
)

class ReactiveDisplaySnapshot private constructor(
    val width: Int,
    val height: Int,
    val revision: Long,
    private val tiles: Map<ReactiveTileKey, LongArray>
) {
    init {
        require(width > 0 && height > 0) { "Display dimensions must be positive" }
    }

    fun get(x: Int, y: Int): Boolean {
        if (x !in 0 until width || y !in 0 until height) return false
        val tileX = x / REACTIVE_DISPLAY_TILE_SIZE
        val tileY = y / REACTIVE_DISPLAY_TILE_SIZE
        val localX = x % REACTIVE_DISPLAY_TILE_SIZE
        val localY = y % REACTIVE_DISPLAY_TILE_SIZE
        val rows = tiles[ReactiveTileKey(tileX, tileY)] ?: return false
        return (rows[localY] and (1L shl localX)) != 0L
    }

    fun nonEmptyTileCount(): Int = tiles.size

    fun tileRows(key: ReactiveTileKey): LongArray? = tiles[key]?.copyOf()

    fun tileKeys(): Set<ReactiveTileKey> = tiles.keys.toSet()

    fun forEachSetPixel(consumer: (x: Int, y: Int) -> Unit) {
        tiles.forEach { (key, rows) ->
            val originX = key.x * REACTIVE_DISPLAY_TILE_SIZE
            val originY = key.y * REACTIVE_DISPLAY_TILE_SIZE
            rows.forEachIndexed { localY, rawBits ->
                val y = originY + localY
                if (y >= height) return@forEachIndexed
                var bits = rawBits
                while (bits != 0L) {
                    val localX = java.lang.Long.numberOfTrailingZeros(bits)
                    val x = originX + localX
                    if (x < width) consumer(x, y)
                    bits = bits and (bits - 1L)
                }
            }
        }
    }

    fun toTag(): CompoundTag = CompoundTag().apply {
        putInt("width", width)
        putInt("height", height)
        putLong("revision", revision)
        val encodedTiles = ListTag()
        tiles.forEach { (key, rows) ->
            encodedTiles.add(CompoundTag().apply {
                putInt("x", key.x)
                putInt("y", key.y)
                putLongArray("rows", rows)
            })
        }
        put("tiles", encodedTiles)
    }

    companion object {
        fun blank(width: Int, height: Int, revision: Long = 0L): ReactiveDisplaySnapshot =
            ReactiveDisplaySnapshot(width, height, revision, emptyMap())

        internal fun create(
            width: Int,
            height: Int,
            revision: Long,
            tiles: Map<ReactiveTileKey, LongArray>
        ): ReactiveDisplaySnapshot = ReactiveDisplaySnapshot(
            width,
            height,
            revision,
            tiles.mapValues { (_, rows) -> rows.copyOf() }
        )

        fun fromTag(tag: CompoundTag): ReactiveDisplaySnapshot? {
            val width = tag.getInt("width")
            val height = tag.getInt("height")
            if (width <= 0 || height <= 0) return null
            val tiles = linkedMapOf<ReactiveTileKey, LongArray>()
            val list = tag.getList("tiles", Tag.TAG_COMPOUND.toInt())
            for (index in 0 until list.size) {
                val tile = list.getCompound(index)
                val x = tile.getInt("x")
                val y = tile.getInt("y")
                if (x < 0 || y < 0) continue
                val rows = tile.getLongArray("rows")
                if (rows.size != REACTIVE_DISPLAY_TILE_SIZE || rows.all { it == 0L }) continue
                tiles[ReactiveTileKey(x, y)] = rows
            }
            return create(width, height, tag.getLong("revision"), tiles)
        }
    }
}

class ReactiveDisplayFrameBuilder internal constructor(
    private val base: ReactiveDisplaySnapshot
) {
    val width: Int = base.width
    val height: Int = base.height

    private val changed = linkedMapOf<ReactiveTileKey, LongArray>()
    private var replaceAll: Boolean = false

    fun clear() {
        replaceAll = true
        changed.clear()
    }

    fun setPixel(x: Int, y: Int, enabled: Boolean) {
        require(x in 0 until width && y in 0 until height) {
            "Pixel (${x + 1},${y + 1}) is outside 1..$width, 1..$height"
        }
        val key = ReactiveTileKey(x / REACTIVE_DISPLAY_TILE_SIZE, y / REACTIVE_DISPLAY_TILE_SIZE)
        val rows = writableTile(key)
        val localX = x % REACTIVE_DISPLAY_TILE_SIZE
        val localY = y % REACTIVE_DISPLAY_TILE_SIZE
        val mask = 1L shl localX
        rows[localY] = if (enabled) rows[localY] or mask else rows[localY] and mask.inv()
    }

    fun fillRect(x: Int, y: Int, rectWidth: Int, rectHeight: Int, enabled: Boolean) {
        require(rectWidth >= 0 && rectHeight >= 0) { "Rectangle dimensions must not be negative" }
        if (rectWidth == 0 || rectHeight == 0) return
        val endX = x.toLong() + rectWidth.toLong()
        val endY = y.toLong() + rectHeight.toLong()
        require(x >= 0 && y >= 0 && endX <= width.toLong() && endY <= height.toLong()) {
            "Rectangle is outside the display bounds"
        }
        val intEndX = endX.toInt()
        val intEndY = endY.toInt()
        for (row in y until intEndY) {
            var cursor = x
            while (cursor < intEndX) {
                val tileX = cursor / REACTIVE_DISPLAY_TILE_SIZE
                val key = ReactiveTileKey(tileX, row / REACTIVE_DISPLAY_TILE_SIZE)
                val rows = writableTile(key)
                val localY = row % REACTIVE_DISPLAY_TILE_SIZE
                val localX = cursor % REACTIVE_DISPLAY_TILE_SIZE
                val count = minOf(REACTIVE_DISPLAY_TILE_SIZE - localX, intEndX - cursor)
                val mask = when (count) {
                    REACTIVE_DISPLAY_TILE_SIZE -> -1L
                    else -> ((1L shl count) - 1L) shl localX
                }
                rows[localY] = if (enabled) rows[localY] or mask else rows[localY] and mask.inv()
                cursor += count
            }
        }
    }

    internal fun build(nextRevision: Long): Pair<ReactiveDisplaySnapshot, ReactiveDisplayPatch?> {
        val nextTiles = linkedMapOf<ReactiveTileKey, LongArray>()
        if (!replaceAll) {
            base.tileKeys().forEach { key -> base.tileRows(key)?.let { nextTiles[key] = it } }
        }
        changed.forEach { (key, rows) ->
            if (rows.all { it == 0L }) nextTiles.remove(key) else nextTiles[key] = rows.copyOf()
        }

        val patchTiles = if (replaceAll) {
            nextTiles.map { (key, rows) -> ReactiveTilePatch(key, rows.copyOf()) }
        } else {
            changed.mapNotNull { (key, rows) ->
                val before = base.tileRows(key)
                val after = rows.takeUnless { it.all { value -> value == 0L } }
                if (before.contentEqualsNullable(after)) null
                else ReactiveTilePatch(key, after?.copyOf() ?: LongArray(0))
            }
        }
        if (!replaceAll && patchTiles.isEmpty()) return base to null

        val snapshot = ReactiveDisplaySnapshot.create(width, height, nextRevision, nextTiles)
        return snapshot to ReactiveDisplayPatch(width, height, nextRevision, replaceAll, patchTiles)
    }

    private fun writableTile(key: ReactiveTileKey): LongArray = changed.getOrPut(key) {
        if (replaceAll) LongArray(REACTIVE_DISPLAY_TILE_SIZE)
        else base.tileRows(key) ?: LongArray(REACTIVE_DISPLAY_TILE_SIZE)
    }
}

private fun LongArray?.contentEqualsNullable(other: LongArray?): Boolean = when {
    this == null -> other == null || other.all { it == 0L }
    other == null -> all { it == 0L }
    else -> contentEquals(other)
}
