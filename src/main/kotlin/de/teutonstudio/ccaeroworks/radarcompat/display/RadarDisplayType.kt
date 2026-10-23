package de.teutonstudio.ccaeroworks.radarcompat.display

import de.teutonstudio.ccaeroworks.display.DeskDisplayType

enum class RadarDisplayType(val displayType: DeskDisplayType) {
    SMALL(DeskDisplayType.TWO_DIGIT),
    LARGE(DeskDisplayType.THREE_DIGIT);

    companion object {
        fun from(displayType: DeskDisplayType): RadarDisplayType = when (displayType) {
            DeskDisplayType.TWO_DIGIT -> SMALL
            DeskDisplayType.THREE_DIGIT -> LARGE
        }
    }
}
