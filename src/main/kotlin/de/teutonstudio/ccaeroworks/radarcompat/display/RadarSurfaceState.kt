package de.teutonstudio.ccaeroworks.radarcompat.display

data class RadarSurfaceState(
    val socket: Int,
    val type: RadarDisplayType,
    val snapshot: RadarDisplaySnapshot?
)
