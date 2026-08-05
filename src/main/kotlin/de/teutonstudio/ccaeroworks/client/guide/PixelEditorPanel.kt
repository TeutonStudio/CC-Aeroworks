package de.teutonstudio.ccaeroworks.client.guide

import de.teutonstudio.ccaeroworks.display.DeskDisplayType
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import kotlin.math.min

class PixelEditorPanel {
    private val state = PixelEditorState()
    private val displayButtons = mutableListOf<Pair<DeskDisplayType, Box>>()
    private val socketButtons = mutableListOf<Pair<String, Box>>()
    private var pixelOnButton: Box? = null
    private var pixelOffButton: Box? = null
    private var clearButton: Box? = null
    private var fillButton: Box? = null
    private var invertButton: Box? = null
    private var copyPixelButton: Box? = null
    private var copyRasterButton: Box? = null
    private var grid: Grid? = null
    private var dragValue: Boolean? = null
    private var lastDragPixel: PixelCoordinate? = null
    private var copiedUntilMillis: Long = 0L
    private var copiedTarget: CopyTarget? = null

    fun render(
        graphics: GuiGraphics,
        font: Font,
        x: Int,
        y: Int,
        width: Int,
        mouseX: Int,
        mouseY: Int
    ): Int {
        clearHitboxes()
        var cursorY = y

        val typeWidth = (width - GAP) / 2
        val small = Box(x, cursorY, typeWidth, BUTTON_HEIGHT)
        val large = Box(x + typeWidth + GAP, cursorY, width - typeWidth - GAP, BUTTON_HEIGHT)
        displayButtons += DeskDisplayType.TWO_DIGIT to small
        displayButtons += DeskDisplayType.THREE_DIGIT to large
        drawButton(
            graphics,
            font,
            small,
            tr("guide.cc_aeroworks.pixel_editor.small"),
            state.displayType == DeskDisplayType.TWO_DIGIT,
            small.contains(mouseX, mouseY)
        )
        drawButton(
            graphics,
            font,
            large,
            tr("guide.cc_aeroworks.pixel_editor.large"),
            state.displayType == DeskDisplayType.THREE_DIGIT,
            large.contains(mouseX, mouseY)
        )
        cursorY += BUTTON_HEIGHT + 6

        graphics.drawString(font, tr("guide.cc_aeroworks.pixel_editor.socket"), x, cursorY + 4, MUTED, false)
        var socketX = x + 47
        state.availableSockets().forEach { socket ->
            val box = Box(socketX, cursorY, 42, BUTTON_HEIGHT)
            socketButtons += socket to box
            drawButton(graphics, font, box, socket, state.socketName == socket, box.contains(mouseX, mouseY))
            socketX += 46
        }
        cursorY += BUTTON_HEIGHT + 6

        val valueWidth = (width - GAP) / 2
        pixelOnButton = Box(x, cursorY, valueWidth, BUTTON_HEIGHT)
        pixelOffButton = Box(x + valueWidth + GAP, cursorY, width - valueWidth - GAP, BUTTON_HEIGHT)
        drawButton(
            graphics,
            font,
            pixelOnButton!!,
            "■ ${tr("guide.cc_aeroworks.pixel_editor.on")}",
            state.selectedPixelValue,
            pixelOnButton!!.contains(mouseX, mouseY),
            ACTIVE_PIXEL
        )
        drawButton(
            graphics,
            font,
            pixelOffButton!!,
            "□ ${tr("guide.cc_aeroworks.pixel_editor.off")}",
            !state.selectedPixelValue,
            pixelOffButton!!.contains(mouseX, mouseY),
            MUTED
        )
        cursorY += BUTTON_HEIGHT + 7

        val cellSize = min(MAX_CELL_SIZE, ((width - ROW_LABEL_WIDTH) / state.width).coerceAtLeast(MIN_CELL_SIZE))
        val gridX = x + ROW_LABEL_WIDTH
        val gridY = cursorY + COLUMN_LABEL_HEIGHT
        grid = Grid(gridX, gridY, cellSize, state.width, state.height)

        for (column in 0 until state.width) {
            graphics.drawCenteredString(
                font,
                (column + 1).toString(),
                gridX + column * cellSize + cellSize / 2,
                cursorY + 1,
                MUTED
            )
        }
        for (row in 0 until state.height) {
            graphics.drawString(font, (row + 1).toString(), x + 4, gridY + row * cellSize + 3, MUTED, false)
            for (column in 0 until state.width) {
                val left = gridX + column * cellSize
                val top = gridY + row * cellSize
                val hovered = mouseX in left until (left + cellSize) && mouseY in top until (top + cellSize)
                val enabled = state.pixels.get(column, row)
                graphics.fill(
                    left + 1,
                    top + 1,
                    left + cellSize - 1,
                    top + cellSize - 1,
                    if (enabled) ACTIVE_PIXEL else INACTIVE_PIXEL
                )
                graphics.renderOutline(
                    left,
                    top,
                    cellSize,
                    cellSize,
                    if (hovered) GOLD else GRID_BORDER
                )
            }
        }
        cursorY = gridY + state.height * cellSize + 7

        val actionWidth = (width - GAP * 2) / 3
        clearButton = Box(x, cursorY, actionWidth, BUTTON_HEIGHT)
        fillButton = Box(x + actionWidth + GAP, cursorY, actionWidth, BUTTON_HEIGHT)
        invertButton = Box(x + (actionWidth + GAP) * 2, cursorY, width - (actionWidth + GAP) * 2, BUTTON_HEIGHT)
        drawButton(graphics, font, clearButton!!, tr("guide.cc_aeroworks.pixel_editor.clear"), false, clearButton!!.contains(mouseX, mouseY))
        drawButton(graphics, font, fillButton!!, tr("guide.cc_aeroworks.pixel_editor.fill"), false, fillButton!!.contains(mouseX, mouseY))
        drawButton(graphics, font, invertButton!!, tr("guide.cc_aeroworks.pixel_editor.invert"), false, invertButton!!.contains(mouseX, mouseY))
        cursorY += BUTTON_HEIGHT + 8

        graphics.drawString(font, tr("guide.cc_aeroworks.pixel_editor.pixel_code"), x, cursorY, GOLD, false)
        cursorY += 12
        val lastPixel = state.lastEditedPixel
        val singlePixelCode = lastPixel?.let {
            LuaPixelCodeGenerator.singlePixel(state.socketName, it, state.pixels.get(it.x, it.y))
        } ?: tr("guide.cc_aeroworks.pixel_editor.no_pixel")
        cursorY += drawCodeBlock(graphics, font, listOf(singlePixelCode), x, cursorY, width)

        copyPixelButton = Box(x, cursorY, width, BUTTON_HEIGHT)
        drawButton(
            graphics,
            font,
            copyPixelButton!!,
            tr(if (copiedTarget == CopyTarget.PIXEL && System.currentTimeMillis() < copiedUntilMillis)
                "guide.cc_aeroworks.pixel_editor.copied"
            else
                "guide.cc_aeroworks.pixel_editor.copy_pixel"),
            copiedTarget == CopyTarget.PIXEL && System.currentTimeMillis() < copiedUntilMillis,
            copyPixelButton!!.contains(mouseX, mouseY),
            if (lastPixel == null) DISABLED else null,
            lastPixel != null
        )
        cursorY += BUTTON_HEIGHT + 8

        graphics.drawString(font, tr("guide.cc_aeroworks.pixel_editor.raster_code"), x, cursorY, GOLD, false)
        cursorY += 12
        val rasterCode = LuaPixelCodeGenerator.fullRaster(state.socketName, state.pixels)
        cursorY += drawCodeBlock(graphics, font, rasterCode.lines(), x, cursorY, width)

        copyRasterButton = Box(x, cursorY, width, BUTTON_HEIGHT)
        val copied = copiedTarget == CopyTarget.RASTER && System.currentTimeMillis() < copiedUntilMillis
        drawButton(
            graphics,
            font,
            copyRasterButton!!,
            tr(if (copied) "guide.cc_aeroworks.pixel_editor.copied" else "guide.cc_aeroworks.pixel_editor.copy_raster"),
            copied,
            copyRasterButton!!.contains(mouseX, mouseY)
        )
        cursorY += BUTTON_HEIGHT

        return cursorY - y
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0) {
            displayButtons.firstOrNull { it.second.contains(mouseX, mouseY) }?.let { (type, _) ->
                state.selectDisplayType(type)
                return true
            }
            socketButtons.firstOrNull { it.second.contains(mouseX, mouseY) }?.let { (socket, _) ->
                state.selectSocket(socket)
                return true
            }
            if (pixelOnButton?.contains(mouseX, mouseY) == true) {
                state.selectPixelValue(true)
                return true
            }
            if (pixelOffButton?.contains(mouseX, mouseY) == true) {
                state.selectPixelValue(false)
                return true
            }
            if (clearButton?.contains(mouseX, mouseY) == true) {
                state.clear()
                return true
            }
            if (fillButton?.contains(mouseX, mouseY) == true) {
                state.fill()
                return true
            }
            if (invertButton?.contains(mouseX, mouseY) == true) {
                state.invert()
                return true
            }
            if (copyPixelButton?.contains(mouseX, mouseY) == true && state.lastEditedPixel != null) {
                copyToClipboard(
                    LuaPixelCodeGenerator.singlePixel(
                        state.socketName,
                        state.lastEditedPixel!!,
                        state.pixels.get(state.lastEditedPixel!!.x, state.lastEditedPixel!!.y)
                    ),
                    CopyTarget.PIXEL
                )
                return true
            }
            if (copyRasterButton?.contains(mouseX, mouseY) == true) {
                copyToClipboard(LuaPixelCodeGenerator.fullRaster(state.socketName, state.pixels), CopyTarget.RASTER)
                return true
            }
        }

        val pixel = grid?.pixelAt(mouseX, mouseY) ?: return false
        val value = if (button == 1) false else if (button == 0) state.selectedPixelValue else return false
        state.setPixel(pixel.x, pixel.y, value)
        dragValue = value
        lastDragPixel = pixel
        return true
    }

    fun mouseDragged(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val value = dragValue ?: return false
        if (button != 0 && button != 1) return false
        val pixel = grid?.pixelAt(mouseX, mouseY) ?: return true
        if (pixel != lastDragPixel) {
            state.setPixel(pixel.x, pixel.y, value)
            lastDragPixel = pixel
        }
        return true
    }

    fun mouseReleased(): Boolean {
        val handled = dragValue != null
        dragValue = null
        lastDragPixel = null
        return handled
    }

    private fun copyToClipboard(code: String, target: CopyTarget) {
        Minecraft.getInstance().keyboardHandler.setClipboard(code)
        copiedTarget = target
        copiedUntilMillis = System.currentTimeMillis() + COPY_FEEDBACK_MILLIS
    }

    private fun drawCodeBlock(
        graphics: GuiGraphics,
        font: Font,
        lines: List<String>,
        x: Int,
        y: Int,
        width: Int
    ): Int {
        val height = lines.size * CODE_LINE_HEIGHT + 10
        graphics.fill(x, y, x + width, y + height, CODE_BG)
        graphics.renderOutline(x, y, width, height, BORDER_DARK)
        lines.forEachIndexed { index, line ->
            graphics.drawString(font, line, x + 7, y + 5 + index * CODE_LINE_HEIGHT, CYAN, false)
        }
        return height + 5
    }

    private fun drawButton(
        graphics: GuiGraphics,
        font: Font,
        box: Box,
        label: String,
        selected: Boolean,
        hovered: Boolean,
        labelColor: Int? = null,
        enabled: Boolean = true
    ) {
        val background = when {
            !enabled -> BUTTON_DISABLED
            selected -> SELECTED
            hovered -> HOVER
            else -> BUTTON
        }
        graphics.fill(box.x, box.y, box.right, box.bottom, background)
        graphics.renderOutline(box.x, box.y, box.width, box.height, if (enabled) BORDER_DARK else BUTTON_DISABLED)
        graphics.drawCenteredString(
            font,
            label,
            box.x + box.width / 2,
            box.y + 5,
            labelColor ?: if (enabled) TEXT else DISABLED
        )
    }

    private fun clearHitboxes() {
        displayButtons.clear()
        socketButtons.clear()
        pixelOnButton = null
        pixelOffButton = null
        clearButton = null
        fillButton = null
        invertButton = null
        copyPixelButton = null
        copyRasterButton = null
        grid = null
    }

    private fun tr(key: String): String = Component.translatable(key).string

    private enum class CopyTarget { PIXEL, RASTER }

    private data class Box(val x: Int, val y: Int, val width: Int, val height: Int) {
        val right: Int get() = x + width
        val bottom: Int get() = y + height

        fun contains(mouseX: Double, mouseY: Double): Boolean =
            mouseX >= x && mouseX < right && mouseY >= y && mouseY < bottom

        fun contains(mouseX: Int, mouseY: Int): Boolean = contains(mouseX.toDouble(), mouseY.toDouble())
    }

    private data class Grid(
        val x: Int,
        val y: Int,
        val cellSize: Int,
        val width: Int,
        val height: Int
    ) {
        fun pixelAt(mouseX: Double, mouseY: Double): PixelCoordinate? {
            if (mouseX < x || mouseY < y || mouseX >= x + width * cellSize || mouseY >= y + height * cellSize) {
                return null
            }
            return PixelCoordinate(((mouseX - x) / cellSize).toInt(), ((mouseY - y) / cellSize).toInt())
        }
    }

    private companion object {
        const val BUTTON_HEIGHT: Int = 18
        const val GAP: Int = 4
        const val ROW_LABEL_WIDTH: Int = 18
        const val COLUMN_LABEL_HEIGHT: Int = 12
        const val MIN_CELL_SIZE: Int = 10
        const val MAX_CELL_SIZE: Int = 16
        const val CODE_LINE_HEIGHT: Int = 10
        const val COPY_FEEDBACK_MILLIS: Long = 1500L

        const val CODE_BG: Int = 0xFF071117.toInt()
        const val INACTIVE_PIXEL: Int = 0xFF081117.toInt()
        const val ACTIVE_PIXEL: Int = 0xFF68D4EB.toInt()
        const val GRID_BORDER: Int = 0xFF294652.toInt()
        const val SELECTED: Int = 0xFF173746.toInt()
        const val HOVER: Int = 0xFF234958.toInt()
        const val BUTTON: Int = 0xFF172733.toInt()
        const val BUTTON_DISABLED: Int = 0xFF101820.toInt()
        const val BORDER_DARK: Int = 0xFF294652.toInt()
        const val CYAN: Int = 0xFF68D4EB.toInt()
        const val GOLD: Int = 0xFFFFC66D.toInt()
        const val TEXT: Int = 0xFFE7F3F6.toInt()
        const val MUTED: Int = 0xFF91A8AF.toInt()
        const val DISABLED: Int = 0xFF526269.toInt()
    }
}
