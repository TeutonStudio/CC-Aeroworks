package de.teutonstudio.ccaeroworks.mixin.client

import com.mred231.aeroworks.content.controls.ModuleColumn

/**
 * Aeroworks 1.3.0 ModuleScreen list geometry, mirrored from its verified bytecode layout.
 *
 * Keeping these values in one place lets CC-Aeroworks decorate native rows and append its own
 * configuration rows without caching absolute screen coordinates. Every screen-space Y value is
 * derived from Aeroworks' current renderedScroll, so smooth scrolling remains authoritative.
 */
internal object ModuleScreenRowGeometry {
    const val LIST_WIDTH: Int = 251
    const val LIST_HEIGHT: Int = 108

    private const val LIST_CONTENT_INSET_Y: Int = 2
    private const val SINGLE_HEIGHT: Int = 30
    private const val PAIR_HEIGHT: Int = 52
    private const val ROW_GAP: Int = 1
    private const val BOTTOM_PADDING: Int = 4

    private const val MODE_X_OFFSET: Int = 135
    private const val MODE_Y_OFFSET: Int = 17
    private const val MODE_SIZE: Int = 18

    const val EXTENSION_ROW_HEIGHT: Int = SINGLE_HEIGHT
    private const val EXTENSION_ROW_GAP: Int = ROW_GAP

    data class NativeGroup(
        val columnA: Int,
        val columnB: Int?,
        val contentTop: Int,
        val height: Int
    )

    data class Rect(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int
    )

    /** Reproduce ModuleScreen.buildGroups() without depending on its private Group record. */
    fun nativeGroups(columns: List<ModuleColumn>): List<NativeGroup> {
        if (columns.isEmpty()) return emptyList()
        val groups = ArrayList<NativeGroup>()
        var contentTop = 0
        var index = 0
        while (index < columns.size) {
            val column = columns[index]
            val pair = !column.isButton() &&
                !column.positive() &&
                index + 1 < columns.size &&
                columns[index + 1].channel() === column.channel()
            val height = if (pair) PAIR_HEIGHT else SINGLE_HEIGHT
            groups += NativeGroup(
                columnA = index,
                columnB = if (pair) index + 1 else null,
                contentTop = contentTop,
                height = height
            )
            contentTop += height + ROW_GAP
            index += if (pair) 2 else 1
        }
        return groups
    }

    fun modeToggleRect(
        groups: List<NativeGroup>,
        columnIndex: Int,
        rowLeft: Int,
        listTop: Int,
        renderedScroll: Float
    ): Rect? {
        val group = groups.firstOrNull { it.columnA == columnIndex && it.columnB != null } ?: return null
        val groupTop = screenY(group.contentTop, listTop, renderedScroll)
        return Rect(
            x = rowLeft + MODE_X_OFFSET,
            y = groupTop + MODE_Y_OFFSET,
            width = MODE_SIZE,
            height = MODE_SIZE
        )
    }

    /**
     * The native content height already contains its bottom padding. Appended rows deliberately
     * start after it, giving extensions a small native-looking separation from the final control row.
     */
    fun extensionContentTop(nativeContentHeight: Int, rowIndex: Int): Int =
        nativeContentHeight + rowIndex * (EXTENSION_ROW_HEIGHT + EXTENSION_ROW_GAP)

    fun extensionScreenTop(
        nativeContentHeight: Int,
        rowIndex: Int,
        listTop: Int,
        renderedScroll: Float
    ): Int = screenY(extensionContentTop(nativeContentHeight, rowIndex), listTop, renderedScroll)

    fun contentHeightWithExtensions(nativeContentHeight: Int, rowCount: Int): Int {
        if (rowCount <= 0) return nativeContentHeight
        val lastTop = extensionContentTop(nativeContentHeight, rowCount - 1)
        return lastTop + EXTENSION_ROW_HEIGHT + BOTTOM_PADDING
    }

    fun fullyVisible(rowTop: Int, rowHeight: Int, listTop: Int): Boolean =
        rowTop >= listTop && rowTop + rowHeight <= listTop + LIST_HEIGHT

    private fun screenY(contentTop: Int, listTop: Int, renderedScroll: Float): Int =
        (listTop + LIST_CONTENT_INSET_Y + contentTop - renderedScroll).toInt()
}
