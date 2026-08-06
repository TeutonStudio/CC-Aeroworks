package de.teutonstudio.ccaeroworks.display

data class RadarSurfaceState(
    val socket: Int,
    val type: RadarDisplayType,
    val snapshot: RadarDisplaySnapshot?
)
