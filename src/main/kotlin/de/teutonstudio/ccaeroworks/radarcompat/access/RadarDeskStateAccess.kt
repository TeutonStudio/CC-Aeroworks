package de.teutonstudio.ccaeroworks.radarcompat.access

import de.teutonstudio.ccaeroworks.radarcompat.display.RadarDisplaySnapshot

interface RadarDeskStateAccess {
    fun ccaeroworks_getRadarSnapshot(): RadarDisplaySnapshot?

    fun ccaeroworks_setRadarSnapshot(snapshot: RadarDisplaySnapshot?)
}
