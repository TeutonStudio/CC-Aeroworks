package de.teutonstudio.ccaeroworks.radarcompat.display

import de.teutonstudio.ccaeroworks.display.DeskDisplayType

enum class RadarDisplayType(
    val displayType: DeskDisplayType,
    val modulePath: String
) {
    SMALL(DeskDisplayType.TWO_DIGIT, "small_radar_display"),
    LARGE(DeskDisplayType.THREE_DIGIT, "large_radar_display")
}
