package de.teutonstudio.ccaeroworks.compat.createradar

import de.teutonstudio.ccaeroworks.display.DeskDisplayPixels
import de.teutonstudio.ccaeroworks.display.RadarDisplaySnapshot
import de.teutonstudio.ccaeroworks.display.RadarDisplayType
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag

data class RadarControllerLink(
    val position: BlockPos,
    val dimension: String
) {
    fun toTag(): CompoundTag = CompoundTag().apply {
        putLong("position", this@RadarControllerLink.position.asLong())
        putString("dimension", this@RadarControllerLink.dimension)
    }

    companion object {
        fun fromTag(tag: CompoundTag): RadarControllerLink? {
            if (!tag.contains("position", Tag.TAG_LONG.toInt())) return null
            val dimension = tag.getString("dimension").takeIf(String::isNotEmpty) ?: return null
            return RadarControllerLink(BlockPos.of(tag.getLong("position")), dimension)
        }
    }
}

data class RadarRasterCache(
    val snapshot: RadarDisplaySnapshot?,
    val width: Int,
    val height: Int,
    val fresh: Boolean,
    val pixels: DeskDisplayPixels
)

interface RadarDeskStateAccess {
    fun ccaeroworks_getRadarControllerLink(): RadarControllerLink?

    fun ccaeroworks_setRadarControllerLink(link: RadarControllerLink?)

    fun ccaeroworks_getRadarSnapshot(): RadarDisplaySnapshot?

    fun ccaeroworks_setRadarSnapshot(snapshot: RadarDisplaySnapshot?)

    fun ccaeroworks_getRadarPixels(type: RadarDisplayType, gameTime: Long): DeskDisplayPixels
}
