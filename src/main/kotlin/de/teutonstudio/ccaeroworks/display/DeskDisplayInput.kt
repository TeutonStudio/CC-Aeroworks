package de.teutonstudio.ccaeroworks.display

data class DeskDisplayStrokeSample(
    val x: Int,
    val y: Int,
    val u: Double,
    val v: Double,
    val directionU: Double,
    val directionV: Double,
    val speed: Double
) {
    fun toLuaMap(): Map<String, Any> = linkedMapOf(
        "x" to x,
        "y" to y,
        "u" to u,
        "v" to v,
        "directionU" to directionU,
        "directionV" to directionV,
        "speed" to speed
    )
}

/**
 * Server-resolved display input delivered to ComputerCraft.
 *
 * Draw inputs keep the backwards-compatible top-level point/delta fields and additionally expose a
 * bounded, server-resolved stroke path. For non-start events [samples] begins with the previous
 * accepted endpoint, so Lua can rasterize the complete current segment without keeping hidden
 * cross-event state.
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
    val directionU: Double? = null,
    val directionV: Double? = null,
    val speed: Double? = null,
    val samples: List<DeskDisplayStrokeSample> = emptyList(),
    val isEnd: Boolean = false
) {
    val isDraw: Boolean
        get() = action == "draw"

    fun luaSamples(): List<Map<String, Any>> = samples.map(DeskDisplayStrokeSample::toLuaMap)
}
