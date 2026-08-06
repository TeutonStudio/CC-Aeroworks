package de.teutonstudio.ccaeroworks.compat.createradar

import de.teutonstudio.ccaeroworks.display.RadarDisplaySnapshot

interface RadarDeskStateAccess {
    fun ccaeroworks_getRadarSnapshot(): RadarDisplaySnapshot?

    fun ccaeroworks_setRadarSnapshot(snapshot: RadarDisplaySnapshot?)
}
