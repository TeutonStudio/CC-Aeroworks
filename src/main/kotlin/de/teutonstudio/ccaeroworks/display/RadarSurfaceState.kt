package de.teutonstudio.ccaeroworks.display

import net.minecraft.core.Direction

data class RadarSurfaceState(
    val socket: Int,
    val type: RadarDisplayType,
    val snapshot: RadarDisplaySnapshot?,
    val facing: Direction
)
