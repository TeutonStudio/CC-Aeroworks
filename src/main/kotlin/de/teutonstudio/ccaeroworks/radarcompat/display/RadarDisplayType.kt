package de.teutonstudio.ccaeroworks.radarcompat.display

import de.teutonstudio.ccaeroworks.display.DeskDisplayType

enum class RadarDisplayType(
    val displayType: DeskDisplayType,
    /** Side length of Create: Radars' square monitor surface in module-local block units. */
    val nativeSurfaceScale: Double
) {
    SMALL(DeskDisplayType.TWO_DIGIT, 0.39),
    LARGE(DeskDisplayType.THREE_DIGIT, 0.40);

    companion object {
        fun from(displayType: DeskDisplayType): RadarDisplayType = when (displayType) {
            DeskDisplayType.TWO_DIGIT -> SMALL
            DeskDisplayType.THREE_DIGIT -> LARGE
        }
    }
}
