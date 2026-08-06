package de.teutonstudio.ccaeroworks.compat.createradar

import de.teutonstudio.ccaeroworks.display.DeskDisplayPixels
import de.teutonstudio.ccaeroworks.display.RadarDisplaySnapshot
import de.teutonstudio.ccaeroworks.display.RadarDisplayType

interface RadarDeskStateAccess {
    fun ccaeroworks_getRadarSnapshot(): RadarDisplaySnapshot?

    fun ccaeroworks_setRadarSnapshot(snapshot: RadarDisplaySnapshot?)

    fun ccaeroworks_getRadarPixels(type: RadarDisplayType, gameTime: Long): DeskDisplayPixels
}
