package de.teutonstudio.ccaeroworks.display

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.world.phys.Vec3

data class RadarDisplayTrack(
    val id: String,
    val position: Vec3,
    val velocity: Vec3
) {
    fun toTag(): CompoundTag = CompoundTag().apply {
        putString("id", id)
        put("position", position.toTag())
        put("velocity", velocity.toTag())
    }

    companion object {
        fun fromTag(tag: CompoundTag): RadarDisplayTrack? {
            if (!tag.contains("position", Tag.TAG_COMPOUND)) return null
            val id = tag.getString("id")
            if (id.isEmpty()) return null
            return RadarDisplayTrack(
                id,
                tag.getCompound("position").toVec3(),
                if (tag.contains("velocity", Tag.TAG_COMPOUND)) {
                    tag.getCompound("velocity").toVec3()
                } else {
                    Vec3.ZERO
                }
            )
        }
    }
}

data class RadarDisplaySnapshot(
    val connected: Boolean,
    val center: Vec3,
    val range: Double,
    val selectedTrackId: String?,
    val tracks: List<RadarDisplayTrack>,
    val updatedAt: Long
) {
    fun toTag(): CompoundTag = CompoundTag().apply {
        putBoolean("connected", connected)
        put("center", center.toTag())
        putDouble("range", range)
        selectedTrackId?.let { putString("selected", it) }
        putLong("updatedAt", updatedAt)
        put("tracks", ListTag().apply {
            tracks.take(MAX_SYNCED_TRACKS).forEach { add(it.toTag()) }
        })
    }

    companion object {
        const val MAX_SYNCED_TRACKS: Int = 256

        fun disconnected(center: Vec3, updatedAt: Long): RadarDisplaySnapshot =
            RadarDisplaySnapshot(false, center, 0.0, null, emptyList(), updatedAt)

        fun fromTag(tag: CompoundTag): RadarDisplaySnapshot {
            val tracksTag = tag.getList("tracks", Tag.TAG_COMPOUND)
            val tracks = buildList {
                for (index in 0 until minOf(tracksTag.size, MAX_SYNCED_TRACKS)) {
                    RadarDisplayTrack.fromTag(tracksTag.getCompound(index))?.let(::add)
                }
            }
            return RadarDisplaySnapshot(
                connected = tag.getBoolean("connected"),
                center = if (tag.contains("center", Tag.TAG_COMPOUND)) {
                    tag.getCompound("center").toVec3()
                } else {
                    Vec3.ZERO
                },
                range = tag.getDouble("range").coerceAtLeast(0.0),
                selectedTrackId = tag.getString("selected").takeIf { it.isNotEmpty() },
                tracks = tracks,
                updatedAt = tag.getLong("updatedAt")
            )
        }
    }
}

private fun Vec3.toTag(): CompoundTag = CompoundTag().apply {
    putDouble("x", this@toTag.x)
    putDouble("y", this@toTag.y)
    putDouble("z", this@toTag.z)
}

private fun CompoundTag.toVec3(): Vec3 = Vec3(getDouble("x"), getDouble("y"), getDouble("z"))
