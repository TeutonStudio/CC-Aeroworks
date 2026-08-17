package de.teutonstudio.ccaeroworks.display

/**
 * Server-resolved display input delivered to ComputerCraft.
 *
 * Tap inputs use the current touch only. Draw inputs additionally carry the gesture start point,
 * per-event delta from the previously accepted draw sample, a stable gesture id and sequence, and
 * an explicit end marker. Keeping delta server-resolved means Lua handlers do not need to remember
 * the previous event just to draw a continuous segment.
 */
data class DeskDisplayInput(
    val action: String,
    val touch: DeskDisplayTouch,
    val gestureId: Long? = null,
    val sequence: Int? = null,
    val startX: Int? = null,
    val startY: Int? = null,
    val deltaX: Int? = null,
    val deltaY: Int? = null,
    val isEnd: Boolean = false
) {
    val isDraw: Boolean
        get() = action == "draw"
}
